package com.rs2.script.diagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rs2.game.players.Player;
import com.rs2.script.ScriptHost;
import com.rs2.script.activation.ScriptRuntimeReport;
import com.rs2.script.definition.DefinitionKind;
import com.rs2.script.definition.DefinitionRecord;

/**
 * Permission-gated operator diagnostics for the TypeScript content runtime.
 *
 * <p>Backs {@code ::scripts status}, {@code ::scripts list [kind] [page]},
 * {@code ::scripts reload}, and the sanitized, deprecated {@code ::scriptdir}
 * alias. Only callers with administrator rights (2 or higher) may list module
 * sources or trigger a reload; denied callers receive the generic denial with
 * no inventory or detail.
 *
 * <p>Output is bounded and logical-only: module and definition ids, never host
 * paths, stack traces, raw {@code Value}s, engine objects, or credentials.
 * Lines are capped, listing is sorted and paged at at most 20 entries, and
 * inspection never executes guest code.
 */
public final class ScriptAdminCommands {

	/** Required minimum player rights for source listing and reload. */
	public static final int MIN_ADMIN_RIGHTS = 2;

	private static final int PAGE_SIZE = 20;
	private static final int MAX_LINE_LENGTH = 96;
	private static final int MAX_STATUS_DIAGNOSTICS = 3;

	private ScriptAdminCommands() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

	/**
	 * Handles one {@code scripts} subcommand. Returns {@code true} when the
	 * subcommand was recognized and consumed.
	 */
	public static boolean handle(Player player, String[] arguments) {
		if (player == null) {
			return true;
		}
		String subcommand = arguments.length > 0
				? arguments[0].toLowerCase(java.util.Locale.ROOT)
				: "";
		if ("status".equals(subcommand) || "".equals(subcommand)) {
			status(player);
			return true;
		}
		if ("list".equals(subcommand)) {
			list(player, arguments);
			return true;
		}
		if ("reload".equals(subcommand)) {
			reload(player);
			return true;
		}
		if (!authorized(player)) {
			denied(player);
			return true;
		}
		message(player, "Unknown scripts subcommand: " + boundLine(subcommand)
				+ ". Use: status, list [kind] [page], reload.");
		return true;
	}

	/**
	 * Handles the deprecated, sanitized {@code scriptdir} alias. It accepts no
	 * arguments and never returns a filesystem string; it emits a deprecation
	 * line plus the same bounded logical status snapshot as
	 * {@code ::scripts status}.
	 */
	public static boolean handleScriptdir(Player player, String[] arguments) {
		if (player == null) {
			return true;
		}
		if (arguments.length > 0) {
			message(player, "scriptdir accepts no arguments; use ::scripts status.");
			return true;
		}
		if (!authorized(player)) {
			denied(player);
			return true;
		}
		message(player, "scriptdir is deprecated; use ::scripts status.");
		status(player);
		return true;
	}

	private static void status(Player player) {
		if (!authorized(player)) {
			denied(player);
			return;
		}
		ScriptRuntimeStatus status = ScriptHost.getInstance().getRuntimeStatus();
		message(player, "Scripts: active generation " + status.generation()
				+ ", " + status.moduleCount() + " modules, "
				+ status.definitionCount() + " definitions, "
				+ status.routeCount() + " routes");
		message(player, "Script tasks: " + status.scheduledTasks()
				+ " scheduled; " + status.activeEncounters() + " encounters, "
				+ status.activeBossSessions() + " boss sessions");
		message(player, "Script runtime: " + status.activeAreaSessions()
				+ " areas, " + status.activeShops() + " shops, "
				+ status.activeRaidLobbies() + " raid lobbies, "
				+ status.activeRaidSessions() + " raid sessions, "
				+ status.activeMinigameLobbies() + " minigame lobbies, "
				+ status.activeMinigameSessions() + " minigame sessions, "
				+ status.activeResourceSessions() + " resource sessions");
		message(player, "Script journal: " + status.mappedQuestRows()
				+ " scripted quest rows");
		String failure = ScriptHost.getInstance().lastFailedMessage();
		if (failure != null) {
			message(player, "Last reload failed: " + boundLine(failure));
		}
		ScriptRuntimeReport report = ScriptHost.getInstance().getRuntimeReport();
		if (report != null && report.quarantineWarning() != null) {
			message(player, "Reload quarantine: "
					+ boundLine(report.quarantineWarning()));
		}
		List<String> diagnostics = ScriptHost.getInstance()
				.getRuntimeDiagnostics();
		if (!diagnostics.isEmpty()) {
			int from = Math.max(0, diagnostics.size() - MAX_STATUS_DIAGNOSTICS);
			for (int index = from; index < diagnostics.size(); index++) {
				message(player, "Reload diagnostic: "
						+ boundLine(diagnostics.get(index)));
			}
		}
	}

	/** Triggers one operator reload and reports the truthful outcome. */
	public static void reload(Player player) {
		if (!authorized(player)) {
			denied(player);
			return;
		}
		ScriptReloadResult result = ScriptHost.getInstance().reloadWithResult();
		if (result.succeeded()) {
			message(player, "Scripts reloaded: generation "
					+ result.generation() + " with " + result.moduleCount()
					+ " modules.");
		} else {
			message(player, "Script reload failed; keeping generation "
					+ result.generation() + " live.");
			String failure = result.failure();
			if (failure != null) {
				message(player, "Reason: " + boundLine(failure));
			}
		}
	}

	private static void list(Player player, String[] arguments) {
		if (!authorized(player)) {
			denied(player);
			return;
		}
		String kind = arguments.length > 1
				? arguments[1].toLowerCase(java.util.Locale.ROOT) : "modules";
		int page = 1;
		if (arguments.length > 2) {
			try {
				page = Integer.parseInt(arguments[2]);
			} catch (NumberFormatException ignored) {
				page = 1;
			}
		}
		if (page < 1) {
			page = 1;
		}
		if ("modules".equals(kind)) {
			listModules(player, page);
			return;
		}
		DefinitionKind definitionKind = parseKind(kind);
		if (definitionKind == null) {
			message(player, "Unknown definition kind: " + boundLine(kind)
					+ ". Use: modules, boss, raid, area, quest, drop, reward, shop, resource.");
			return;
		}
		listDefinitions(player, definitionKind, page);
	}

	private static void listModules(Player player, int page) {
		List<String> modules = ScriptHost.getInstance().readActiveRegistry(
				state -> {
					List<String> ids = new ArrayList<String>();
					for (com.rs2.script.definition.ModuleRecord module
							: state.manifest) {
						ids.add(module.id());
					}
					Collections.sort(ids);
					return ids;
				});
		message(player, "Content modules (page " + page + " of "
				+ pageCount(modules.size()) + "):");
		for (String id : paged(modules, page)) {
			message(player, "  " + boundLine(id));
		}
	}

	private static void listDefinitions(Player player, DefinitionKind kind,
			int page) {
		Map<String, DefinitionRecord> records = ScriptHost.getInstance()
				.readActiveRegistry(
						state -> new LinkedHashMap<String, DefinitionRecord>(
								com.rs2.script.definition.DefinitionRegistry
										.all(state, kind)));
		List<String> keys = new ArrayList<String>(records.keySet());
		Collections.sort(keys);
		message(player, "Definitions: " + kind.name().toLowerCase(
				java.util.Locale.ROOT) + " (page " + page + " of "
				+ pageCount(keys.size()) + "):");
		for (String key : paged(keys, page)) {
			DefinitionRecord record = records.get(key);
			String source = record == null ? "unknown"
					: record.source();
			message(player, "  " + boundLine(key) + " (source: "
					+ boundLine(source) + ")");
		}
	}

	private static DefinitionKind parseKind(String value) {
		if ("drop".equals(value) || "drop_table".equals(value)
				|| "droptable".equals(value)) {
			return DefinitionKind.DROP_TABLE;
		}
		for (DefinitionKind kind : DefinitionKind.values()) {
			if (kind.name().toLowerCase(java.util.Locale.ROOT).equals(value)) {
				return kind;
			}
		}
		return null;
	}

	private static boolean authorized(Player player) {
		return player.playerRights >= MIN_ADMIN_RIGHTS;
	}

	private static void denied(Player player) {
		message(player, "You do not have permission to use that command.");
	}

	private static void message(Player player, String text) {
		player.getPacketSender().sendMessage(boundLine(text));
	}

	private static String boundLine(String value) {
		if (value == null) {
			return "unknown";
		}
		String trimmed = value.trim().replace('\n', ' ').replace('\r', ' ');
		return trimmed.length() <= MAX_LINE_LENGTH ? trimmed
				: trimmed.substring(0, MAX_LINE_LENGTH) + "...";
	}

	private static List<String> paged(List<String> values, int page) {
		int from = (page - 1) * PAGE_SIZE;
		if (from >= values.size()) {
			return Collections.emptyList();
		}
		int to = Math.min(values.size(), from + PAGE_SIZE);
		return values.subList(from, to);
	}

	private static int pageCount(int size) {
		return size == 0 ? 1 : (size + PAGE_SIZE - 1) / PAGE_SIZE;
	}

}
