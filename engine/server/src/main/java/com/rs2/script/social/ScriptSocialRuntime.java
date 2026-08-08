package com.rs2.script.social;

import org.graalvm.polyglot.Value;

import com.rs2.game.players.Player;
import com.rs2.script.ScriptExecutor;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.context.PrivateMessageScriptContext;
import com.rs2.script.context.TradeRequestScriptContext;
import com.rs2.script.registries.LifecycleRegistry;

/**
 * Social observer and gate runtime for trade initiation and private messages.
 * Trade hooks are allow/deny only; offer mutation stays host-owned.
 */
public final class ScriptSocialRuntime {

	private static final String TRADE_REQUEST_EVENT = "trade-request";
	private static final String PRIVATE_MESSAGE_EVENT = "private-message";

	private static final ScriptSocialRuntime INSTANCE = new ScriptSocialRuntime();

	public static ScriptSocialRuntime getInstance() {
		return INSTANCE;
	}

	/**
	 * Returns {@code false} when a registered handler denied the request.
	 * When no handler is registered, trade proceeds unchanged.
	 */
	public boolean allowTradeRequest(Player requester, Player target) {
		if (requester == null || target == null) {
			return true;
		}
		final boolean[] allowed = { true };
		ScriptHost.getInstance().executeInActiveGeneration(
				new ScriptHost.ActiveGenerationOperation() {
			@Override
			public void run(long generation) {
				Value handler = LifecycleRegistry.getSingleton(TRADE_REQUEST_EVENT);
				if (handler == null) {
					return;
				}
				TradeRequestScriptContext context = new TradeRequestScriptContext(
						new ScriptedPlayer(requester, generation),
						new ScriptedPlayer(target, generation));
				ScriptExecutor.execute(handler, "social",
						requester.playerName, "trade-request", context);
				if (!context.isAllowed()) {
					allowed[0] = false;
					String message = context.denialMessage();
					if (message != null) {
						requester.getPacketSender().sendMessage(message);
					}
				}
			}
		});
		return allowed[0];
	}

	/** Observe-only hook for delivered private messages. */
	public void observePrivateMessage(Player sender, Player recipient,
			String message) {
		if (sender == null || recipient == null) {
			return;
		}
		ScriptHost.getInstance().executeInActiveGeneration(
				new ScriptHost.ActiveGenerationOperation() {
			@Override
			public void run(long generation) {
				Value handler = LifecycleRegistry.getSingleton(
						PRIVATE_MESSAGE_EVENT);
				if (handler == null) {
					return;
				}
				PrivateMessageScriptContext context =
						new PrivateMessageScriptContext(
								new ScriptedPlayer(sender, generation),
								new ScriptedPlayer(recipient, generation),
								message);
				ScriptExecutor.execute(handler, "social",
						sender.playerName, "private-message", context);
			}
		});
	}

	private ScriptSocialRuntime() {
	}
}
