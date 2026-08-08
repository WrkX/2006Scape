package com.rs2.script.interfacehook;

import org.graalvm.polyglot.Value;

import com.rs2.game.players.Player;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.context.ButtonScriptContext;
import com.rs2.script.context.InterfaceHookScriptContext;

/**
 * Java-owned runtime for {@code defineInterfaceHook}.
 *
 * <p>Button handlers are scoped to the hook's interface id and run before
 * bare {@code onButton} routes. Open/close callbacks fire when presentation
 * APIs show or close the registered interface.
 */
public final class ScriptInterfaceHookRuntime {

	private static volatile ScriptInterfaceHookRuntime INSTANCE =
			new ScriptInterfaceHookRuntime();

	private ScriptInterfaceHookRuntime() {
	}

	public static ScriptInterfaceHookRuntime getInstance() {
		return INSTANCE;
	}

	public static ScriptInterfaceHookRuntime installForTesting() {
		ScriptInterfaceHookRuntime runtime = new ScriptInterfaceHookRuntime();
		INSTANCE = runtime;
		return runtime;
	}

	public void register(InterfaceHookDefinition definition) {
		InterfaceHookDefinitionRegistry.put(definition);
	}

	/**
	 * Dispatches a button when the player's main frame matches a hook that
	 * owns the button id. Returns {@code true} when a hook consumed the click.
	 */
	public boolean handleButton(Player player, int buttonId) {
		// Only interfaces opened through the scripted showInterface path arm
		// their hook buttons; a legacy interface sharing the same id (e.g. the
		// generic quest-detail interface) must fall through to legacy handling.
		if (player == null || player.lastMainFrameInterface < 0
				|| !player.scriptHookArmed) {
			return false;
		}
		long generation = ScriptHost.getInstance().getActiveGeneration();
		if (generation == 0L) {
			return false;
		}
		InterfaceHookDefinition hook = InterfaceHookDefinitionRegistry
				.getByInterfaceId(player.lastMainFrameInterface);
		if (hook == null) {
			return false;
		}
		Value handler = hook.buttons().get(Integer.valueOf(buttonId));
		if (handler == null) {
			return false;
		}
		ScriptedPlayer scripted = new ScriptedPlayer(player, generation);
		return ScriptExecutor.execute(handler, "interfaceHook",
				hook.id() + ":" + buttonId, "button",
				new ButtonScriptContext(scripted, buttonId));
	}

	public void notifyOpen(Player player, int interfaceId) {
		if (player == null) {
			return;
		}
		long generation = ScriptHost.getInstance().getActiveGeneration();
		if (generation == 0L) {
			return;
		}
		InterfaceHookDefinition hook = InterfaceHookDefinitionRegistry
				.getByInterfaceId(interfaceId);
		if (hook == null || hook.onOpen() == null) {
			return;
		}
		ScriptedPlayer scripted = new ScriptedPlayer(player, generation);
		ScriptExecutor.execute(hook.onOpen(), "interfaceHook", hook.id(),
				"open", new InterfaceHookScriptContext(scripted, "open",
						interfaceId, hook.id()));
	}

	public void notifyClose(Player player, int interfaceId) {
		if (player == null || interfaceId < 0) {
			return;
		}
		long generation = ScriptHost.getInstance().getActiveGeneration();
		if (generation == 0L) {
			return;
		}
		InterfaceHookDefinition hook = InterfaceHookDefinitionRegistry
				.getByInterfaceId(interfaceId);
		if (hook == null || hook.onClose() == null) {
			return;
		}
		ScriptedPlayer scripted = new ScriptedPlayer(player, generation);
		ScriptExecutor.execute(hook.onClose(), "interfaceHook", hook.id(),
				"close", new InterfaceHookScriptContext(scripted, "close",
						interfaceId, hook.id()));
	}

	public void notifyClosingMainFrame(Player player) {
		if (player == null) {
			return;
		}
		notifyClose(player, player.lastMainFrameInterface);
	}
}
