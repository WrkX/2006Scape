package com.rs2.script;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.EnvironmentAccess;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.io.IOAccess;

import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.scheduler.ScriptScheduler;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.util.LoggerUtils;

/**
 * Singleton that owns the GraalVM JavaScript context in which TypeScript
 * content modules are evaluated.
 *
 * <p>One {@link Context} is created on the first {@link #load()}. Reloads are
 * evaluated in isolation and replace the live context only after every module
 * succeeds.
 */
public final class ScriptHost {

	@FunctionalInterface
	public interface ActiveGenerationOperation {
		void run(long generation);
	}

	@FunctionalInterface
	public interface RegistryLookup {
		org.graalvm.polyglot.Value find(RegistryStore.State state);
	}

	@FunctionalInterface
	public interface RegisteredInvocation {
		void invoke(long generation, org.graalvm.polyglot.Value handler);
	}

	@FunctionalInterface
	public interface ActiveRegistryRead<T> {
		T read(RegistryStore.State state);
	}

	public enum DispatchResult {
		NO_ACTIVE_CONTEXT,
		UNMATCHED,
		CONSUMED
	}

	private static final class ActiveState {
		private final Context context;
		private final RegistryStore.State registry;
		private final long generation;

		private ActiveState(Context context, RegistryStore.State registry,
				long generation) {
			this.context = context;
			this.registry = registry;
			this.generation = generation;
		}
	}

	private static final ScriptHost INSTANCE = new ScriptHost();

	private static final Logger logger = LoggerUtils.getLogger(ScriptHost.class);
	private static final String CONTENT_DIR_PROPERTY = "singlescape.contentDir";
	private static final String CONTENT_DIR_ENV = "SINGLESCAPE_CONTENT_DIR";
	private static final String DEFAULT_CONTENT_DIR = "../../content/dist";

	private ActiveState activeState;
	private long nextGeneration;

	private ScriptHost() {
	}

	public static ScriptHost getInstance() {
		return INSTANCE;
	}

	/**
	 * Returns the content directory used by the script bridge.
	 *
	 * <p>Configuration precedence is the {@code singlescape.contentDir} JVM
	 * property, the {@code SINGLESCAPE_CONTENT_DIR} environment variable, and
	 * finally the root-relative default for the consolidated workspace. Relative
	 * values are resolved from the JVM working directory.
	 */
	public static File resolveContentDir() {
		String configured = System.getProperty(CONTENT_DIR_PROPERTY);
		if (isBlank(configured)) {
			configured = System.getenv(CONTENT_DIR_ENV);
		}

		if (isBlank(configured)) {
			return new File(System.getProperty("user.dir"), DEFAULT_CONTENT_DIR);
		}

		return new File(configured);
	}

	private static boolean isBlank(String value) {
		return value == null || value.trim().isEmpty();
	}

	/**
	 * Builds the GraalVM context (if needed) and evaluates every content
	 * module under the content root.
	 *
	 * <p>If a {@code loader.js} file is present in the content root it is
	 * used as the single entry point; the loader's imports pull in the
	 * remaining modules transitively. Otherwise the content root is walked
	 * recursively and every {@code .js} file is evaluated in alphabetical
	 * order.
	 *
	 * <p>A missing or empty content root is logged and silently ignored, so
	 * the engine boots cleanly when no scripts have been built yet.
	 */
	public synchronized void load() {
		if (activeState != null) {
			return;
		}
		replaceContext();
	}

	/**
	 * Evaluates a fresh candidate and atomically publishes it on success.
	 * The last known-good context remains live when evaluation fails.
	 */
	public synchronized void reload() {
		replaceContext();
	}

	/**
	 * Returns the live context, or {@code null} if {@link #load()} has not
	 * been called yet.
	 */
	public synchronized Context getContext() {
		return activeState == null ? null : activeState.context;
	}

	public synchronized long getActiveGeneration() {
		return activeState == null ? 0L : activeState.generation;
	}

	/**
	 * Looks up and invokes one exact registration while holding the active
	 * context lease. Registry state, generation, and callback value therefore
	 * always belong to the same Graal context.
	 */
	public synchronized DispatchResult dispatchActive(RegistryLookup lookup,
			RegisteredInvocation invocation) {
		if (activeState == null) {
			return DispatchResult.NO_ACTIVE_CONTEXT;
		}
		org.graalvm.polyglot.Value handler = lookup.find(activeState.registry);
		if (handler == null) {
			return DispatchResult.UNMATCHED;
		}
		invocation.invoke(activeState.generation, handler);
		return DispatchResult.CONSUMED;
	}

	/**
	 * Reads one value or immutable aggregate from the authoritative active
	 * registry snapshot.
	 */
	public synchronized <T> T readActiveRegistry(ActiveRegistryRead<T> read) {
		RegistryStore.State state = activeState == null
				? RegistryStore.emptyState() : activeState.registry;
		return read.read(state);
	}

	/**
	 * Runs one callback while holding the active context's generation lease.
	 * Reload takes the same monitor, so an old callback either finishes before
	 * invalidation or cannot begin.
	 */
	public synchronized boolean executeIfGenerationActive(long generation,
			Runnable callback) {
		if (activeState == null || generation != activeState.generation) {
			return false;
		}
		callback.run();
		return true;
	}

	/**
	 * Runs an operation against the current context and supplies the generation
	 * protected by this execution lease.
	 */
	public synchronized boolean executeInActiveGeneration(
			ActiveGenerationOperation operation) {
		if (activeState == null) {
			return false;
		}
		operation.run(activeState.generation);
		return true;
	}

	private void replaceContext() {
		File contentDir = resolveContentDir().getAbsoluteFile();
		RegistryStore.State candidateState = RegistryStore.beginStaging();
		Context candidateContext = null;
		boolean published = false;
		try {
			candidateContext = buildContext(contentDir);
			List<File> modules = collectLoadModules(contentDir);
			for (File file : modules) {
				Source source = Source.newBuilder("js", file)
						.mimeType("application/javascript+module")
						.build();
				candidateContext.eval(source);
			}
			QuestRegistry.validateCandidate(candidateState);
			RegistryStore.State committedState = RegistryStore.finish(candidateState);

			ActiveState previous = activeState;
			long candidateGeneration = nextGeneration + 1L;
			activeState = new ActiveState(candidateContext, committedState,
					candidateGeneration);
			nextGeneration = candidateGeneration;
			candidateContext = null;
			published = true;
			long previousGeneration = previous == null ? 0L : previous.generation;
			runPostCommit("publish encounter generation",
					() -> ScriptEncounterService.getInstance()
							.onGenerationPublished(candidateGeneration));
			runPostCommit("close old-generation encounters",
					() -> ScriptEncounterService.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("cancel old-generation tasks",
					() -> ScriptScheduler.getInstance()
							.cancelGeneration(previousGeneration));
			runPostCommit("re-baseline lifecycle state",
					() -> ScriptLifecycleService.getInstance()
							.onGenerationCommitted(candidateGeneration));
			runPostCommit("clear pending script callbacks",
					ScriptHost::clearPendingScriptCallbacks);
			closeQuietly(previous == null ? null : previous.context);
			logger.log(Level.INFO, "Loaded " + modules.size() + " script modules");
		} catch (Throwable e) {
			if (!published) {
				RegistryStore.rollback(candidateState);
				closeQuietly(candidateContext);
				logger.log(Level.SEVERE,
						"Script load failed; retaining the last known-good context", e);
			} else {
				logger.log(Level.SEVERE,
						"Script context published but post-commit processing failed", e);
			}
		}
	}

	/**
	 * Coherent publication seam used by tests that construct handlers directly.
	 */
	synchronized void publishForTesting(Context testContext,
			RegistryStore.State candidate) {
		if (testContext == null) {
			throw new IllegalArgumentException("test context must not be null");
		}
		RegistryStore.State committed = RegistryStore.finish(candidate);
		long previousGeneration =
				activeState == null ? 0L : activeState.generation;
		activeState = new ActiveState(testContext, committed, nextGeneration + 1L);
		nextGeneration = activeState.generation;
		ScriptEncounterService.getInstance()
				.onGenerationPublished(activeState.generation);
		ScriptEncounterService.getInstance()
				.closeGeneration(previousGeneration);
		clearPendingScriptCallbacks();
	}

	synchronized void resetForTesting() {
		clearPendingScriptCallbacks();
		ScriptEncounterService.getInstance().resetForTesting();
		activeState = null;
	}

	private static void runPostCommit(String operation, Runnable callback) {
		try {
			callback.run();
		} catch (RuntimeException e) {
			logger.log(Level.SEVERE,
					"Script reload post-commit operation failed: " + operation, e);
		}
	}

	static Context buildContext(File contentDir) {
		HostAccess hostAccess = HostAccess.newBuilder(HostAccess.EXPLICIT)
				.allowArrayAccess(true)
				.build();
		IOAccess ioAccess = IOAccess.newBuilder()
				.allowHostFileAccess(false)
				.allowHostSocketAccess(false)
				.fileSystem(new ReadOnlyContentFileSystem(contentDir.toPath()))
				.build();
		Context ctx = Context.newBuilder("js")
				.allowHostAccess(hostAccess)
				.allowHostClassLookup(className -> false)
				.allowHostClassLoading(false)
				.allowIO(ioAccess)
				.allowCreateThread(false)
				.allowNativeAccess(false)
				.allowCreateProcess(false)
				.allowEnvironmentAccess(EnvironmentAccess.NONE)
				.currentWorkingDirectory(contentDir.toPath().toAbsolutePath().normalize())
				.option("engine.WarnInterpreterOnly", "false")
				.build();
		ScriptBindings.install(ctx);
		return ctx;
	}

	private static List<File> collectLoadModules(File contentDir) {
		String contentPath = contentDir.getAbsolutePath();
		if (!contentDir.isDirectory()) {
			logger.log(Level.WARNING, "No script content found at {0}", contentPath);
			return Collections.emptyList();
		}
		List<File> modules = collectModules(contentDir);
		if (modules.isEmpty()) {
			logger.log(Level.WARNING, "No script content found at {0}", contentPath);
		}
		return modules;
	}

	private static void clearPendingScriptCallbacks() {
		for (Player player : PlayerHandler.players) {
			if (player != null) {
				ScriptEncounterService.getInstance().invalidateDialogue(player);
			}
		}
	}

	private static void closeQuietly(Context contextToClose) {
		if (contextToClose == null) {
			return;
		}
		try {
			contextToClose.close();
		} catch (RuntimeException e) {
			logger.log(Level.WARNING, "Failed to close script context", e);
		}
	}

	private static List<File> collectModules(File contentDir) {
		File loader = new File(contentDir, "loader.js");
		if (loader.isFile()) {
			List<File> single = new ArrayList<>();
			single.add(loader);
			return single;
		}

		final List<File> files = new ArrayList<>();
		Path root = contentDir.toPath();
		try {
			Files.walkFileTree(root, new SimpleFileVisitor<Path>() {
				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (file.getFileName().toString().endsWith(".js")) {
						files.add(file.toFile());
					}
					return FileVisitResult.CONTINUE;
				}
			});
		} catch (IOException e) {
			logger.log(Level.WARNING, "Failed to walk content directory: " + contentDir, e);
		}
		Collections.sort(files);
		return files;
	}

}
