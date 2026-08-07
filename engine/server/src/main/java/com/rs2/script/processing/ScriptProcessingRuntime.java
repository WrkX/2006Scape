package com.rs2.script.processing;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.rs2.Constants;
import com.rs2.game.items.ItemConstants;
import com.rs2.game.players.Player;
import com.rs2.script.ItemOnObjectScriptContext;
import com.rs2.script.ScriptContext;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedObject;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.quest.QuestSkill;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.HostRoute;
import com.rs2.script.route.RouteRegistry;
import com.rs2.script.scheduler.ScriptScheduler;
import com.rs2.script.scheduler.ScriptTaskHandle;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.util.Misc;

/**
 * Java-owned processing runtime for {@code defineProcessingSkill}.
 *
 * <p>Each definition registers an exact host item-on-object route. Sessions
 * cook/process one input every {@code intervalTicks} until the player runs
 * out of input, walks away, logs out, dies, or a new generation publishes.
 */
public final class ScriptProcessingRuntime {

	private static final int MAX_SESSIONS_PER_GENERATION = 2048;
	private static final int INTERACT_DISTANCE = 2;

	private static volatile ScriptProcessingRuntime INSTANCE =
			new ScriptProcessingRuntime();

	private final SecureRandom cookingRandom = new SecureRandom();
	private final Map<String, ProcessingSkillDefinition> definitions =
			new HashMap<String, ProcessingSkillDefinition>();
	private final Map<Long, Session> sessionsByToken =
			new HashMap<Long, Session>();
	private final Map<Player, Long> sessionsByPlayer =
			new HashMap<Player, Long>();

	private long activeGeneration;
	private long nextToken = 1L;

	private ScriptProcessingRuntime() {
	}

	public static ScriptProcessingRuntime getInstance() {
		return INSTANCE;
	}

	public static ScriptProcessingRuntime installForTesting() {
		ScriptProcessingRuntime runtime = new ScriptProcessingRuntime();
		INSTANCE = runtime;
		return runtime;
	}

	public void registerRoutes(ProcessingSkillDefinition definition) {
		definitions.put(definition.id(), definition);
		RouteRegistry.putHost(ExecutableRouteKey.itemOnObject(
				definition.inputItemId(), definition.objectId()),
				itemOnObjectRoute(definition));
	}

	public void onPlayerRemoved(Player player) {
		cancelPlayerSessions(player);
	}

	public void onPlayerDeath(Player player) {
		cancelPlayerSessions(player);
	}

	public synchronized void onGenerationPublished(long generation) {
		activeGeneration = generation;
	}

	public synchronized void closeGeneration(long generation) {
		if (generation == 0L) {
			return;
		}
		for (Session session : new ArrayList<Session>(
				sessionsByToken.values())) {
			if (session.generation == generation) {
				closeSession(session);
			}
		}
	}

	public synchronized void resetForTesting() {
		for (Session session : new ArrayList<Session>(
				sessionsByToken.values())) {
			closeSession(session);
		}
		sessionsByToken.clear();
		sessionsByPlayer.clear();
		definitions.clear();
		activeGeneration = 0L;
		nextToken = 1L;
	}

	public synchronized int sessionCount() {
		return sessionsByToken.size();
	}

	public synchronized long sessionToken(Player player) {
		Long token = sessionsByPlayer.get(player);
		return token == null ? 0L : token.longValue();
	}

	private HostRoute itemOnObjectRoute(
			final ProcessingSkillDefinition definition) {
		return new HostRoute() {
			@Override
			public void invoke(Object... arguments) {
				Player player = playerOf(arguments);
				if (player == null) {
					return;
				}
				int objectX = player.objectX;
				int objectY = player.objectY;
				if (arguments != null && arguments.length > 0
						&& arguments[0] instanceof ItemOnObjectScriptContext) {
					ItemOnObjectScriptContext context =
							(ItemOnObjectScriptContext) arguments[0];
					if (context.target instanceof ScriptedObject) {
						ScriptedObject object = (ScriptedObject) context.target;
						objectX = object.getX();
						objectY = object.getY();
					}
				}
				onItemOnObject(player, definition, objectX, objectY);
			}
		};
	}

	private synchronized void onItemOnObject(Player player,
			ProcessingSkillDefinition definition, int objectX, int objectY) {
		if (!authoritativeLive(player)
				|| ScriptEncounterService.getInstance().isActionLocked(player)) {
			return;
		}
		if (player.playerLevel.length <= definition.skill()
				|| player.playerLevel[definition.skill()] < definition
						.level()) {
			player.getPacketSender().sendMessage(
					"You need a " + skillDisplayName(definition.skill())
							+ " level of " + definition.level()
							+ " to process this.");
			return;
		}
		if (!player.getItemAssistant().playerHasItem(definition.inputItemId(),
				1)) {
			return;
		}
		if (sessionsByToken.size() >= MAX_SESSIONS_PER_GENERATION
				&& !sessionsByPlayer.containsKey(player)) {
			player.getPacketSender().sendMessage(
					"The processing runtime is at capacity; try again later.");
			return;
		}
		cancelPlayerSessions(player);
		beginSession(player, definition, objectX, objectY);
	}

	private synchronized boolean beginSession(Player player,
			ProcessingSkillDefinition definition, int objectX, int objectY) {
		long generation = ScriptHost.getInstance().getActiveGeneration();
		long token = nextToken++;
		final Session session = new Session(player, generation, token,
				definition, objectX, objectY);
		sessionsByToken.put(Long.valueOf(token), session);
		sessionsByPlayer.put(player, Long.valueOf(token));
		player.getPacketSender().closeAllWindows();
		animate(player, definition);
		session.task = ScriptScheduler.getInstance().schedule(player,
				generation, definition.intervalTicks(), true,
				new Runnable() {
					@Override
					public void run() {
						tickSession(session);
					}
				}, new Runnable() {
					@Override
					public void run() {
						closeSession(session);
					}
				});
		if (session.task == null || session.task.isCancelled()) {
			closeSession(session);
			return false;
		}
		return true;
	}

	private void tickSession(Session session) {
		synchronized (this) {
			if (session.closed || session.generation != activeGeneration
					|| !authoritativeLive(session.player)) {
				closeSession(session);
				return;
			}
			if (!Misc.goodDistance(session.objectX, session.objectY,
					session.player.absX, session.player.absY,
					INTERACT_DISTANCE)) {
				closeSession(session);
				return;
			}
			if (!session.player.getItemAssistant().playerHasItem(
					session.definition.inputItemId(), 1)) {
				session.player.getPacketSender().sendMessage(
						"You have run out of " + session.definition.name()
								+ " to cook.");
				closeSession(session);
				return;
			}
			boolean success = cooksSuccessfully(session.player,
					session.definition);
			session.player.getItemAssistant().deleteItem(
					session.definition.inputItemId(), 1);
			if (success) {
				session.player.getPacketSender().sendMessage(
						"You successfully cook the "
								+ session.definition.name().toLowerCase()
								+ ".");
				if (session.definition.sound() >= 0 && Constants.SOUND) {
					session.player.getPacketSender().sendSound(
							session.definition.sound(), 100, 0);
				}
				session.player.getPlayerAssistant().addSkillXP(
						session.definition.experience(),
						session.definition.skill());
				session.player.getItemAssistant().addItem(
						session.definition.productItemId(), 1);
			} else {
				session.player.getPacketSender().sendMessage(
						"Oops! You accidentally burnt the "
								+ session.definition.name().toLowerCase()
								+ "!");
				if (session.definition.failProductItemId() >= 0) {
					session.player.getItemAssistant().addItem(
							session.definition.failProductItemId(), 1);
				}
			}
			if (session.player.getItemAssistant().playerHasItem(
					session.definition.inputItemId(), 1)) {
				animate(session.player, session.definition);
			} else {
				closeSession(session);
			}
		}
	}

	private boolean cooksSuccessfully(Player player,
			ProcessingSkillDefinition definition) {
		int level = player.playerLevel[definition.skill()];
		int stopBurn = definition.stopBurnLevel();
		if (definition.glovesItemId() >= 0
				&& player.playerEquipment[ItemConstants.HANDS]
						== definition.glovesItemId()
				&& definition.stopBurnLevelWithGloves() >= 0) {
			stopBurn = definition.stopBurnLevelWithGloves();
		}
		if (level >= stopBurn) {
			return true;
		}
		double burnChance = 55.0 - definition.burnBonus();
		double span = stopBurn - definition.level();
		if (span <= 0.0d) {
			return true;
		}
		burnChance -= (level - definition.level()) * (burnChance / span);
		double randNum = cookingRandom.nextDouble() * 100.0;
		return burnChance <= randNum;
	}

	private synchronized void cancelPlayerSessions(Player player) {
		Long token = sessionsByPlayer.remove(player);
		if (token == null) {
			return;
		}
		Session session = sessionsByToken.get(token);
		if (session != null) {
			closeSession(session);
		}
	}

	private synchronized void closeSession(Session session) {
		if (session.closed) {
			return;
		}
		session.closed = true;
		sessionsByToken.remove(Long.valueOf(session.token));
		Long owned = sessionsByPlayer.get(session.player);
		if (owned != null && owned.longValue() == session.token) {
			sessionsByPlayer.remove(session.player);
		}
		if (session.task != null && !session.task.isCancelled()) {
			session.task.cancel();
		}
	}

	private static void animate(Player player,
			ProcessingSkillDefinition definition) {
		if (definition.animation() >= 0) {
			player.startAnimation(definition.animation());
		}
	}

	private static boolean authoritativeLive(Player player) {
		return player != null && player.isActive && !player.disconnected
				&& !player.isDead;
	}

	private static String skillDisplayName(int skill) {
		String name = QuestSkill.values()[skill].getScriptName();
		if (name == null || name.isEmpty()) {
			return "Cooking";
		}
		return Character.toUpperCase(name.charAt(0)) + name.substring(1);
	}

	private static Player playerOf(Object[] arguments) {
		if (arguments == null || arguments.length < 1) {
			return null;
		}
		if (arguments[0] instanceof ScriptContext) {
			return ((ScriptContext) arguments[0]).player.backingPlayer();
		}
		if (arguments[0] instanceof ScriptedPlayer) {
			return ((ScriptedPlayer) arguments[0]).backingPlayer();
		}
		return null;
	}

	private static final class Session {
		private final Player player;
		private final long generation;
		private final long token;
		private final ProcessingSkillDefinition definition;
		private final int objectX;
		private final int objectY;
		private ScriptTaskHandle task;
		private boolean closed;

		Session(Player player, long generation, long token,
				ProcessingSkillDefinition definition, int objectX,
				int objectY) {
			this.player = player;
			this.generation = generation;
			this.token = token;
			this.definition = definition;
			this.objectX = objectX;
			this.objectY = objectY;
		}
	}
}
