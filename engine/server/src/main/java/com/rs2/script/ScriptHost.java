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
import org.graalvm.polyglot.Value;
import org.graalvm.polyglot.io.IOAccess;

import com.rs2.event.CycleEventHandler;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.activation.HookResult;
import com.rs2.script.activation.ProjectionAdapter;
import com.rs2.script.activation.RuntimeActivationTransaction;
import com.rs2.script.activation.RuntimeSnapshot;
import com.rs2.script.activation.ScriptRuntimeReport;
import com.rs2.script.diagnostics.ScriptReloadResult;
import com.rs2.script.diagnostics.ScriptRuntimeStatus;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.scheduler.ScriptScheduler;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.util.LoggerUtils;

/**
 * Singleton that owns the GraalVM JavaScript context in which TypeScript
 * content modules are evaluated.
 *
 * <p>One {@link Context} is created on the first {@link #load()}. Reloads are
 * evaluated in isolation and replace the live context only after every module
 * succeeds. Publication is a two-phase runtime activation transaction: the
 * last abortable point is the final pre-publication checkpoint, after which
 * old-generation unload observers are attempted (registration closed) and the
 * no-throw commit publishes context, generation, registries, routes,
 * manifest, and report together.
 */
public final class ScriptHost {

	@FunctionalInterface
	public interface ActiveGenerationOperation {
		void run(long generation);
	}

	@FunctionalInterface
	public interface RouteLookup {
		ExecutableRouteRecord find(RegistryStore.State state);
	}

	@FunctionalInterface
	public interface RouteInvocation {
		void invoke(long generation, ExecutableRouteRecord route);
	}

	@FunctionalInterface
	public interface ObserverLookup {
		Value find(RegistryStore.State state);
	}

	@FunctionalInterface
	public interface ObserverInvocation {
		void invoke(long generation, Value handler);
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
		private final ScriptRuntimeReport report;

		private ActiveState(Context context, RegistryStore.State registry,
				long generation, ScriptRuntimeReport report) {
			this.context = context;
			this.registry = registry;
			this.generation = generation;
			this.report = report;
		}
	}

	private static final int MAX_DIAGNOSTICS = 32;
	private static final int MAX_DIAGNOSTIC_LENGTH = 512;

	private static final ScriptHost INSTANCE = new ScriptHost();

	private static final Logger logger = LoggerUtils.getLogger(ScriptHost.class);
	private static final String CONTENT_DIR_PROPERTY = "singlescape.contentDir";
	private static final String CONTENT_DIR_ENV = "SINGLESCAPE_CONTENT_DIR";
	private static final String DEFAULT_CONTENT_DIR = "../../content/dist";

	private ActiveState activeState;
	private long nextGeneration;
	private ProjectionAdapter projectionAdapter = com.rs2.script.area.ScriptAreaRuntime
			.getInstance();
	private final List<String> diagnostics = new ArrayList<>();
	private String lastReloadFailure;

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
	 * The last known-good context remains live when evaluation or the
	 * activation handoff fails. Returns the bounded outcome of the attempt.
	 */
	public synchronized ScriptReloadResult reloadWithResult() {
		boolean hadContext = activeState != null;
		long previousGeneration = activeState == null ? 0L : activeState.generation;
		replaceContext();
		if (activeState == null) {
			return ScriptReloadResult.failure(previousGeneration,
					lastFailedMessage());
		}
		if (hadContext && activeState.generation == previousGeneration) {
			return ScriptReloadResult.failure(previousGeneration,
					lastFailedMessage());
		}
		return ScriptReloadResult.success(activeState.generation,
				activeState.registry.manifest.size());
	}

	/**
	 * Evaluates a fresh candidate and atomically publishes it on success.
	 * The last known-good context remains live when evaluation or the
	 * activation handoff fails.
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
	 * Returns the immutable report of the active generation, or {@code null}
	 * when no generation has been committed.
	 */
	public synchronized ScriptRuntimeReport getRuntimeReport() {
		return activeState == null ? null : activeState.report;
	}

	/**
	 * Returns an immutable bounded copy of post-commit observer and
	 * finalization diagnostics of the active runtime.
	 */
	public synchronized List<String> getRuntimeDiagnostics() {
		return Collections.unmodifiableList(new ArrayList<>(diagnostics));
	}

	/**
	 * Returns the most recent bounded failure message of a rejected reload
	 * attempt, or {@code null} when no failure has been recorded.
	 */
	public synchronized String lastFailedMessage() {
		return lastReloadFailure;
	}

	/**
	 * Returns an immutable logical status snapshot of the active runtime.
	 *
	 * <p>Generation and registry are read under one {@code ScriptHost} monitor
	 * acquisition so a concurrent reload cannot mix snapshots. Counts are then
	 * derived from the immutable registry and Java-owned runtime singletons
	 * after the monitor is released; no raw guest value, engine object, or
	 * host path is exposed.
	 */
	public ScriptRuntimeStatus getRuntimeStatus() {
		final long generation;
		final RegistryStore.State state;
		synchronized (this) {
			if (activeState == null) {
				generation = 0L;
				state = RegistryStore.emptyState();
			} else {
				generation = activeState.generation;
				state = activeState.registry;
			}
		}
		int scheduled = ScriptScheduler.getInstance().taskCount();
		int encounters = ScriptEncounterService.getInstance()
				.activeEncounterCount();
		int bosses = com.rs2.script.boss.StandaloneBossService.getInstance()
				.sessionCount();
		int areas = com.rs2.script.area.ScriptAreaRuntime.getInstance()
				.selectedAreaCount();
		int shops = com.rs2.script.shop.ScriptShopRuntime.getInstance()
				.shopCount();
		int raidLobbies = com.rs2.script.raid.ScriptRaidRuntime.getInstance()
				.lobbyCount();
		int raidSessions = com.rs2.script.raid.ScriptRaidRuntime.getInstance()
				.sessionCount();
		int resources = com.rs2.script.resource.ScriptResourceRuntime
				.getInstance().sessionCount();
		int journalRows = com.rs2.script.quest.ScriptQuestJournalService
				.getInstance().mappedRowCount();
		return new ScriptRuntimeStatus(generation,
				state.manifest.size(), state.definitions.size(),
				state.routes.size(), scheduled, encounters, bosses, areas,
				shops, raidLobbies, raidSessions, resources, journalRows);
	}

	/**
	 * Looks up and invokes one exact route while holding the active context
	 * lease. Registry state, generation, and route record therefore always
	 * belong to the same Graal context. Guest and host invokers share the
	 * same consumed/unmatched authority.
	 */
	public synchronized DispatchResult dispatchActive(RouteLookup lookup,
			RouteInvocation invocation) {
		if (activeState == null) {
			return DispatchResult.NO_ACTIVE_CONTEXT;
		}
		ExecutableRouteRecord route = lookup.find(activeState.registry);
		if (route == null) {
			return DispatchResult.UNMATCHED;
		}
		invocation.invoke(activeState.generation, route);
		return DispatchResult.CONSUMED;
	}

	/**
	 * Generation-leased invocation of a lifecycle observer. Observers are
	 * deliberately not routes: they never own a consumed-versus-legacy
	 * decision.
	 */
	public synchronized DispatchResult dispatchObserverActive(
			ObserverLookup lookup, ObserverInvocation invocation) {
		if (activeState == null) {
			return DispatchResult.NO_ACTIVE_CONTEXT;
		}
		Value handler = lookup.find(activeState.registry);
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

	/**
	 * Installs the projection adapter used by the activation transaction.
	 * Package-private test seam; production always uses the no-op adapter
	 * until a consumer work package supplies world projections.
	 */
	synchronized void setProjectionAdapterForTesting(
			ProjectionAdapter adapter) {
		projectionAdapter = adapter == null
				? com.rs2.script.area.ScriptAreaRuntime.getInstance()
						: adapter;
	}

	private void replaceContext() {
		File contentDir = resolveContentDir().getAbsoluteFile();
		RegistryStore.State candidateState = RegistryStore.beginStaging();
		Context candidateContext = null;
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
			com.rs2.script.quest.ScriptQuestJournalService.getInstance()
					.validateCandidate(candidateState);
			RegistryStore.State committedState =
					RegistryStore.finish(candidateState);
			candidateState = null;

			ActiveState previous = activeState;
			long candidateGeneration = nextGeneration + 1L;
			RuntimeSnapshot predecessor = previous == null ? null
					: RuntimeSnapshot.committed(previous.context,
							previous.registry, previous.generation,
							previous.report);
			RuntimeSnapshot candidate = RuntimeSnapshot.candidate(
					candidateContext, committedState, candidateGeneration);
			RuntimeActivationTransaction transaction =
					new RuntimeActivationTransaction(predecessor, candidate,
							projectionAdapter);
			RuntimeSnapshot published = transaction.execute();
			candidateContext = null;

			// One no-throw commit assignment: context, generation, frozen
			// registries/routes, manifest, and report become visible together.
			activeState = new ActiveState(published.context(),
					published.registry(), published.generation(),
					published.report());
			nextGeneration = published.generation();
			long previousGeneration =
					previous == null ? 0L : previous.generation;

			HookResult loadFailure = transaction.runLoadObservers();
			if (loadFailure != null && loadFailure.threw()) {
				appendDiagnostic("script reload onLoad observer failed for "
						+ loadFailure.identity() + ": "
						+ loadFailure.message());
			}
			runPostCommit("publish encounter generation",
					() -> ScriptEncounterService.getInstance()
							.onGenerationPublished(published.generation()));
			runPostCommit("close old-generation encounters",
					() -> ScriptEncounterService.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("close old-generation standalone boss sessions",
					() -> com.rs2.script.boss.StandaloneBossService
							.getInstance().closeGeneration(previousGeneration));
			runPostCommit("publish area generation",
					() -> com.rs2.script.area.ScriptAreaRuntime.getInstance()
							.onGenerationPublished(published.generation()));
			runPostCommit("close old-generation area sessions",
					() -> com.rs2.script.area.ScriptAreaRuntime.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("close old-generation scripted shops",
					() -> com.rs2.script.shop.ScriptShopRuntime.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("close old-generation raid lobbies and sessions",
					() -> com.rs2.script.raid.ScriptRaidRuntime.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("publish resource generation",
					() -> com.rs2.script.resource.ScriptResourceRuntime
							.getInstance()
							.onGenerationPublished(published.generation()));
			runPostCommit("close old-generation resource sessions",
					() -> com.rs2.script.resource.ScriptResourceRuntime
							.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("publish processing generation",
					() -> com.rs2.script.processing.ScriptProcessingRuntime
							.getInstance()
							.onGenerationPublished(published.generation()));
			runPostCommit("close old-generation processing sessions",
					() -> com.rs2.script.processing.ScriptProcessingRuntime
							.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("close old-generation mob callbacks",
					() -> com.rs2.script.mob.ScriptMobRuntime.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("publish mob generation",
					() -> com.rs2.script.mob.ScriptMobRuntime.getInstance()
							.onGenerationPublished(published.generation()));
			runPostCommit("close old-generation overlays",
					() -> com.rs2.script.overlay.ScriptOverlayRuntime
							.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("publish overlay generation",
					() -> com.rs2.script.overlay.ScriptOverlayRuntime
							.getInstance()
							.publishGeneration(published.generation()));
			runPostCommit("publish scripted quest journal mapping",
					() -> com.rs2.script.quest.ScriptQuestJournalService
							.getInstance()
							.onGenerationPublished(published.generation()));
			runPostCommit("close old-generation quest journal mapping",
					() -> com.rs2.script.quest.ScriptQuestJournalService
							.getInstance()
							.closeGeneration(previousGeneration));
			runPostCommit("cancel old-generation tasks",
					() -> ScriptScheduler.getInstance()
							.cancelGeneration(previousGeneration));
			runPostCommit("re-baseline lifecycle state",
					() -> ScriptLifecycleService.getInstance()
							.onGenerationCommitted(published.generation()));
			runPostCommit("clear pending script callbacks",
					ScriptHost::clearPendingScriptCallbacks);
			String finalizeFailure = transaction.finalizeQuietly();
			if (finalizeFailure != null) {
				appendDiagnostic("script reload finalize degraded: "
						+ finalizeFailure);
			}
			lastReloadFailure = null;
			logger.log(Level.INFO, "Loaded " + modules.size()
					+ " script modules (generation " + published.generation()
					+ ")");
		} catch (Throwable e) {
			RegistryStore.rollback(candidateState);
			closeQuietly(candidateContext);
			lastReloadFailure = boundMessage(e.getMessage());
			appendDiagnostic("script load failed; retaining the "
					+ "last-known-good context: " + boundMessage(e.getMessage()));
			if (e instanceof RuntimeActivationTransaction.Aborted) {
				String quarantine = ((RuntimeActivationTransaction.Aborted) e)
						.quarantine();
				if (quarantine != null) {
					logger.log(Level.SEVERE, quarantine, e);
				}
			}
			logger.log(Level.SEVERE,
					"Script load failed; retaining the last known-good context", e);
		}
	}

	/**
	 * Coherent publication seam used by tests that construct handlers
	 * directly. It commits one frozen candidate without the world-activation
	 * handoff; production reloads use the activation transaction.
	 */
	synchronized void publishForTesting(Context testContext,
			RegistryStore.State candidate) {
		if (testContext == null) {
			throw new IllegalArgumentException("test context must not be null");
		}
		RegistryStore.State committed = RegistryStore.finish(candidate);
		long previousGeneration =
				activeState == null ? 0L : activeState.generation;
		long generation = nextGeneration + 1L;
		ScriptRuntimeReport report = new ScriptRuntimeReport(
				ScriptRuntimeReport.Status.LOADED, generation,
				committed.manifest.size(), committed.definitions.size(),
				committed.routes.size(), null);
		activeState = new ActiveState(testContext, committed, generation,
				report);
		nextGeneration = generation;
		ScriptEncounterService.getInstance()
				.onGenerationPublished(generation);
		ScriptEncounterService.getInstance()
				.closeGeneration(previousGeneration);
		com.rs2.script.boss.StandaloneBossService.getInstance()
				.closeGeneration(previousGeneration);
		com.rs2.script.area.ScriptAreaRuntime.getInstance()
				.onGenerationPublished(generation);
		com.rs2.script.area.ScriptAreaRuntime.getInstance()
				.closeGeneration(previousGeneration);
		com.rs2.script.shop.ScriptShopRuntime.getInstance()
				.closeGeneration(previousGeneration);
		com.rs2.script.raid.ScriptRaidRuntime.getInstance()
				.closeGeneration(previousGeneration);
		com.rs2.script.resource.ScriptResourceRuntime.getInstance()
				.onGenerationPublished(generation);
		com.rs2.script.resource.ScriptResourceRuntime.getInstance()
				.closeGeneration(previousGeneration);
		com.rs2.script.processing.ScriptProcessingRuntime.getInstance()
				.onGenerationPublished(generation);
		com.rs2.script.processing.ScriptProcessingRuntime.getInstance()
				.closeGeneration(previousGeneration);
		com.rs2.script.mob.ScriptMobRuntime.getInstance()
				.closeGeneration(previousGeneration);
		com.rs2.script.mob.ScriptMobRuntime.getInstance()
				.onGenerationPublished(generation);
		com.rs2.script.overlay.ScriptOverlayRuntime.getInstance()
				.closeGeneration(previousGeneration);
		com.rs2.script.overlay.ScriptOverlayRuntime.getInstance()
				.publishGeneration(generation);
		com.rs2.script.quest.ScriptQuestJournalService.getInstance()
				.onGenerationPublished(generation);
		com.rs2.script.quest.ScriptQuestJournalService.getInstance()
				.closeGeneration(previousGeneration);
		clearPendingScriptCallbacks();
	}

	synchronized void resetForTesting() {
		clearPendingScriptCallbacks();
		CycleEventHandler.getSingleton().resetForTesting();
		ScriptEncounterService.getInstance().resetForTesting();
		com.rs2.script.boss.StandaloneBossService.getInstance()
				.resetForTesting();
		com.rs2.script.area.ScriptAreaRuntime.getInstance()
				.resetForTesting();
		com.rs2.script.shop.ScriptShopRuntime.getInstance()
				.resetForTesting();
		com.rs2.script.raid.ScriptRaidRuntime.getInstance()
				.resetForTesting();
		com.rs2.script.resource.ScriptResourceRuntime.getInstance()
				.resetForTesting();
		com.rs2.script.processing.ScriptProcessingRuntime.getInstance()
				.resetForTesting();
		com.rs2.script.mob.ScriptMobRuntime.getInstance()
				.resetForTesting();
		com.rs2.script.overlay.ScriptOverlayRuntime.getInstance()
				.resetForTesting();
		com.rs2.script.quest.ScriptQuestJournalService.getInstance()
				.resetForTesting();
		projectionAdapter = com.rs2.script.area.ScriptAreaRuntime
				.getInstance();
		diagnostics.clear();
		lastReloadFailure = null;
		activeState = null;
	}

	private void appendDiagnostic(String message) {
		synchronized (diagnostics) {
			diagnostics.add(boundMessage(message));
			while (diagnostics.size() > MAX_DIAGNOSTICS) {
				diagnostics.remove(0);
			}
		}
	}

	private static String boundMessage(String value) {
		if (value == null) {
			return "unknown failure";
		}		String trimmed = value.trim();
		return trimmed.length() <= MAX_DIAGNOSTIC_LENGTH ? trimmed
				: trimmed.substring(0, MAX_DIAGNOSTIC_LENGTH) + "...";
	}

	private void runPostCommit(String operation, Runnable callback) {
		try {
			callback.run();
		} catch (RuntimeException e) {
			logger.log(Level.SEVERE,
					"Script reload post-commit operation failed: " + operation, e);
			appendDiagnostic("script reload post-commit operation failed: "
					+ operation + ": " + boundMessage(e.getMessage()));
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
