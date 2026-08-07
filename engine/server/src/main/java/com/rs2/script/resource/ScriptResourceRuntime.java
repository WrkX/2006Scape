package com.rs2.script.resource;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.rs2.GameEngine;
import com.rs2.game.items.ItemAssistant;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.script.ScriptContext;
import com.rs2.script.ScriptHost;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.HostRoute;
import com.rs2.script.route.RouteRegistry;
import com.rs2.script.scheduler.ScriptScheduler;
import com.rs2.script.scheduler.ScriptTaskHandle;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.util.LoggerUtils;
import com.rs2.world.ResolvedWorldObject;
import com.rs2.world.WorldObjectService;

/**
 * Java-owned gathering/resource-loop runtime.
 *
 * <p>For every registered {@code defineGatheringResource} the service
 * registers the definition's exact WP1 host object route at the canonical
 * object-id/action key. A live player clicking the object validates skill
 * level, an ordered tool alternative (inventory or equipped), and the exact
 * world-object identity, then opens a bounded per-player resource session.
 * The session runs a game-cycle tick loop that revalidates live identity and
 * position (movement away cancels), animates, performs a deterministic
 * success check on the Java-owned {@link ResourceSessionRng}, and on success
 * commits the item and XP rewards as one rollback-safe transaction, then
 * depletes the authoritative object to the declared empty id through the
 * timed-object path (which restores the original object after the respawn
 * interval through the same deferred/reservation-aware machinery the legacy
 * skilling uses). Every stop path — harvest/depletion, movement-away, logout,
 * death, object replacement, reload, or runtime failure — cancels the
 * session's task with zero residue.
 *
 * <p>Only the canonical object-id/action key is a host route; an equal-id
 * cache or legacy object at any other tile has no route key and retains its
 * complete legacy behavior. Route conflicts with guest {@code onObject},
 * another host consumer, or another resource definition reject the whole
 * candidate through the unified route registry.
 */
public final class ScriptResourceRuntime {

	private static final int MAX_SESSIONS_PER_GENERATION = 64;
	private static final int MAX_RANGE = 25;
	private static final long NO_SESSION = 0L;

	private static volatile ScriptResourceRuntime INSTANCE =
			new ScriptResourceRuntime();

	private static final Logger logger = LoggerUtils
			.getLogger(ScriptResourceRuntime.class);

	private final long processSeed;
	private long nextToken = 1L;
	private long nextOwnerToken = 1L;
	private long nextOrdinal = 1L;
	private long activeGeneration;
	private long currentTick;
	private final Map<Long, Session> sessionsByToken =
			new HashMap<Long, Session>();
	private final Map<Player, Long> sessionsByPlayer =
			new java.util.IdentityHashMap<Player, Long>();
	private final Map<ObjectKey, ObjectState> objectStates =
			new HashMap<ObjectKey, ObjectState>();
	private final Map<String, GatheringResourceDefinition> definitions =
			new java.util.LinkedHashMap<String, GatheringResourceDefinition>();
	private long failAttemptTokenForTesting = 0L;

	private ScriptResourceRuntime() {
		this(new SecureRandom().nextLong());
	}

	ScriptResourceRuntime(long processSeed) {
		this.processSeed = processSeed;
	}

	public static ScriptResourceRuntime getInstance() {
		return INSTANCE;
	}

	/** Test-only installation with a deterministic process seed. */
	public static ScriptResourceRuntime installForTesting(long processSeed) {
		ScriptResourceRuntime runtime = new ScriptResourceRuntime(processSeed);
		INSTANCE = runtime;
		return runtime;
	}

	/**
	 * Registers the definition's exact host object route in the loading
	 * candidate. Duplicate keys and reserved admin aliases reject the whole
	 * candidate through the unified route registry.
	 */
	public void registerRoutes(GatheringResourceDefinition definition) {
		definitions.put(definition.id(), definition);
		if (definition.npcId() > 0) {
			RouteRegistry.putHost(ExecutableRouteKey.npc(definition.npcId(),
					definition.action()), npcRoute(definition));
			return;
		}
		RouteRegistry.putHost(ExecutableRouteKey.object(definition.objectId(),
				definition.action()), objectRoute(definition));
	}

	// ─── Lifecycle hooks (game-cycle owner) ────────────────────────────────

	/** Cancels every session of one player on logout/removal/death. */
	public void onPlayerRemoved(Player player) {
		cancelPlayerSessions(player);
	}

	/** Cancels every session of one player immediately on death. */
	public void onPlayerDeath(Player player) {
		cancelPlayerSessions(player);
	}

	/** Marks the published generation active so sessions may claim. */
	public synchronized void onGenerationPublished(long generation) {
		activeGeneration = generation;
	}

	/** Closes every session of a retired generation before the old context. */
	public synchronized void closeGeneration(long generation) {
		if (generation == 0L) {
			return;
		}
		for (Session session : new ArrayList<Session>(
				sessionsByToken.values())) {
			if (session.generation == generation) {
				closeSession(session, "generation close");
			}
		}
		// Depletion/respawn state is generation-owned; a retired generation's
		// object states are dropped so the successor re-projects its own. The
		// timed objects themselves are restored by ObjectManager as their
		// respawn ticks expire; only the runtime's bookkeeping is removed.
		Iterator<Map.Entry<ObjectKey, ObjectState>> iterator =
				objectStates.entrySet().iterator();
		while (iterator.hasNext()) {
			if (iterator.next().getValue().generation == generation) {
				iterator.remove();
			}
		}
		// Registered definitions are replaced by the next candidate (same ids
		// update, retired ids become unreachable); resetForTesting clears all.
	}

	/** Game-cycle respawn reconciliation of every registered resource. */
	public synchronized void processGameTick() {
		currentTick++;
		for (ObjectState state : objectStates.values()) {
			if (!state.depleted) {
				continue;
			}
			ResolvedWorldObject current = WorldObjectService.getInstance()
					.resolve(state.key.x, state.key.y, state.key.plane);
			if (current != null
					&& current.getObjectId() == state.definition.objectId()) {
				state.depleted = false;
			}
		}
	}

	/** Test-only lifecycle reset. */
	public synchronized void resetForTesting() {
		for (Session session : new ArrayList<Session>(
				sessionsByToken.values())) {
			closeSession(session, "test reset");
		}
		sessionsByToken.clear();
		sessionsByPlayer.clear();
		objectStates.clear();
		definitions.clear();
		failAttemptTokenForTesting = 0L;
		activeGeneration = 0L;
		currentTick = 0L;
	}

	/** Engine-visible session count for tests; never exported to guest code. */
	public synchronized int sessionCount() {
		return sessionsByToken.size();
	}

	/** Engine-visible depleted-object count for tests. */
	public synchronized int depletedObjectCount() {
		int count = 0;
		for (ObjectState state : objectStates.values()) {
			if (state.depleted) {
				count++;
			}
		}
		return count;
	}

	/** Exact resource-session RNG state for deterministic success tests. */
	public synchronized long resourceRngStateForTesting(long token) {
		Session session = sessionsByToken.get(Long.valueOf(token));
		return session == null ? 0L : session.rng.state();
	}

	/** Active session token of one player, or {@code 0L} when idle. */
	public synchronized long sessionToken(Player player) {
		Long token = sessionsByPlayer.get(player);
		return token == null ? NO_SESSION : token.longValue();
	}

	/** Test-only: the first registered resource definition for assertions. */
	public synchronized GatheringResourceDefinition resourceForTesting() {
		for (GatheringResourceDefinition definition : definitions.values()) {
			return definition;
		}
		return null;
	}

	/** Test-only: makes the next attempt of one session throw. */
	public synchronized void failNextAttemptForTesting(long token) {
		failAttemptTokenForTesting = token;
	}

	/** Test-only: whether the resource object at the tile is depleted. */
	public synchronized boolean isDepleted(GatheringResourceDefinition def,
			int x, int y, int plane) {
		ObjectState state = objectStates.get(ObjectKey.of(def, x, y, plane));
		return state != null && state.depleted;
	}

	// ─── Session lifecycle ──────────────────────────────────────────────────

	private synchronized void cancelPlayerSessions(Player player) {
		Long token = sessionsByPlayer.get(player);
		if (token != null) {
			Session session = sessionsByToken.get(token);
			if (session != null) {
				closeSession(session, "player removed");
			}
		}
	}

	private void closeSession(Session session, String reason) {
		if (session.closed) {
			return;
		}
		session.closed = true;
		if (session.task != null) {
			session.task.cancel();
		}
		session.task = null;
		sessionsByPlayer.remove(session.player);
		sessionsByToken.remove(Long.valueOf(session.token));
		session.player.scriptResourceSessionToken = NO_SESSION;
		if (logger.isLoggable(Level.FINE)) {
			logger.log(Level.FINE, "Closed resource session " + session.token
					+ " for player " + session.player.playerName + ": " + reason);
		}
	}

	// ─── Object route authority ─────────────────────────────────────────────

	private HostRoute objectRoute(final GatheringResourceDefinition definition) {
		return new HostRoute() {
			@Override
			public void invoke(Object... arguments) {
				Player player = playerOf(arguments);
				if (player == null) {
					return;
				}
				onObjectClicked(player, definition, player.objectX,
						player.objectY, player.heightLevel);
			}
		};
	}

	private HostRoute npcRoute(final GatheringResourceDefinition definition) {
		return new HostRoute() {
			@Override
			public void invoke(Object... arguments) {
				Player player = playerOf(arguments);
				if (player == null) {
					return;
				}
				onNpcClicked(player, definition);
			}
		};
	}

	private synchronized void onObjectClicked(Player player,
			GatheringResourceDefinition definition, int x, int y, int plane) {
		if (!authoritativeLive(player)
				|| ScriptEncounterService.getInstance().isActionLocked(player)) {
			return;
		}
		if (sessionsByPlayer.containsKey(player)) {
			player.getPacketSender().sendMessage(
					"You are already gathering from a resource.");
			return;
		}
		if (sessionsByToken.size() >= MAX_SESSIONS_PER_GENERATION) {
			player.getPacketSender().sendMessage(
					"The resource runtime is at capacity; try again later.");
			return;
		}
		ObjectState state = objectState(definition, x, y, plane);
		if (state.depleted) {
			player.getPacketSender().sendMessage(
					"This resource has not yet respawned.");
			return;
		}
		if (!beginSession(player, definition, state.key, -1, x, y, plane)) {
			return;
		}
	}

	private synchronized void onNpcClicked(Player player,
			GatheringResourceDefinition definition) {
		if (!authoritativeLive(player)
				|| ScriptEncounterService.getInstance().isActionLocked(player)) {
			return;
		}
		if (sessionsByPlayer.containsKey(player)) {
			player.getPacketSender().sendMessage(
					"You are already gathering from a resource.");
			return;
		}
		if (sessionsByToken.size() >= MAX_SESSIONS_PER_GENERATION) {
			player.getPacketSender().sendMessage(
					"The resource runtime is at capacity; try again later.");
			return;
		}
		int npcSlot = player.rememberNpcIndex;
		Npc npc = npcAt(npcSlot);
		if (npc == null || npc.npcType != definition.npcId()) {
			return;
		}
		if (player.playerLevel.length <= definition.skill()
				|| player.playerLevel[definition.skill()] < definition.level()) {
			player.getPacketSender().sendMessage(
					"You need a " + skillName(definition.skill())
							+ " level of " + definition.level()
							+ " to gather from this resource.");
			return;
		}
		ToolResult tool = findTool(player, definition);
		if (tool == null) {
			player.getPacketSender().sendMessage(
					"You need a suitable tool to gather from this resource.");
			return;
		}
		beginSession(player, definition, null, npcSlot, npc.absX, npc.absY,
				npc.heightLevel);
	}

	private synchronized boolean beginSession(Player player,
			GatheringResourceDefinition definition, ObjectKey key, int npcSlot,
			int x, int y, int plane) {
		if (player.playerLevel.length <= definition.skill()
				|| player.playerLevel[definition.skill()] < definition.level()) {
			player.getPacketSender().sendMessage(
					"You need a " + skillName(definition.skill())
							+ " level of " + definition.level()
							+ " to gather from this resource.");
			return false;
		}
		ToolResult tool = findTool(player, definition);
		if (tool == null) {
			player.getPacketSender().sendMessage(
					"You need a suitable tool to gather from this resource.");
			return false;
		}
		if (key != null) {
			ResolvedWorldObject resolved = WorldObjectService.getInstance()
					.resolve(player, x, y, plane);
			if (resolved == null || resolved.getObjectId() != definition
					.objectId()) {
				return false;
			}
		}
		long generation = ScriptHost.getInstance().getActiveGeneration();
		long token = nextToken++;
		ResourceSessionRng rng = new ResourceSessionRng(processSeed,
				generation, nextOwnerToken++, nextOrdinal++);
		final Session session = new Session(player, generation, token,
				definition, key, npcSlot, rng);
		sessionsByToken.put(Long.valueOf(token), session);
		sessionsByPlayer.put(player, Long.valueOf(token));
		player.scriptResourceSessionToken = token;
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
						closeSession(session, "scheduler failure");
					}
				});
		if (session.task == null || session.task.isCancelled()) {
			closeSession(session, "scheduler rejected");
			return false;
		}
		session.attempts++;
		animate(player, definition);
		return true;
	}

	private void tickSession(Session session) {
		synchronized (this) {
			if (session.closed || session.generation != activeGeneration
					|| !authoritativeLive(session.player)) {
				closeSession(session, "inactive");
				return;
			}
			if (session.npcSlot >= 0) {
				Npc npc = npcAt(session.npcSlot);
				if (npc == null || npc.npcType != session.definition.npcId()) {
					closeSession(session, "npc replaced");
					return;
				}
				if (!withinNpcRange(session.player, npc)) {
					session.player.getPacketSender().sendMessage(
							"You move away from the resource and stop gathering.");
					closeSession(session, "moved away");
					return;
				}
			} else {
				ObjectState state = objectStates.get(session.key);
				if (state == null || state.depleted) {
					closeSession(session, "resource depleted or replaced");
					return;
				}
				ResolvedWorldObject live = WorldObjectService.getInstance()
						.resolve(session.player, session.key.x,
								session.key.y, session.key.plane);
				if (live == null || live.getObjectId() != session.definition
						.objectId()) {
					closeSession(session, "resource replaced");
					return;
				}
				if (!withinRange(session.player, session.key)) {
					session.player.getPacketSender().sendMessage(
							"You move away from the resource and stop gathering.");
					closeSession(session, "moved away");
					return;
				}
			}
			ToolResult tool = findTool(session.player, session.definition);
			if (tool == null) {
				session.player.getPacketSender().sendMessage(
						"You no longer have a suitable tool.");
				closeSession(session, "tool removed");
				return;
			}
			if (session.player.playerLevel.length <= session.definition
					.skill()
					|| session.player.playerLevel[session.definition.skill()]
							< session.definition.level()) {
				session.player.getPacketSender().sendMessage(
						"You no longer meet the skill requirement.");
				closeSession(session, "level dropped");
				return;
			}
			session.attempts++;
			animate(session.player, session.definition);
			if (session.token == failAttemptTokenForTesting) {
				failAttemptTokenForTesting = 0L;
				throw new IllegalStateException(
						"injected resource attempt failure");
			}
			if (!session.rng.chance(session.definition.successNumerator(),
					session.definition.successDenominator())) {
				return;
			}
			RewardResult reward = commitReward(session.player,
					session.definition, tool);
			if (reward != RewardResult.OK) {
				String message;
				switch (reward) {
					case TOOL_MISSING:
						message = "You no longer have the tool you need.";
						break;
					case INVENTORY_FULL:
						message = "Your inventory is too full to gather more.";
						break;
					case XP_CAP:
						message = "You have reached the experience cap.";
						break;
					default:
						message = "You fail to gather from this resource.";
						break;
				}
				session.player.getPacketSender().sendMessage(message);
				closeSession(session, "reward failed: " + reward.name());
				return;
			}
			if (!session.definition.depletes()) {
				return;
			}
			ObjectState state = objectStates.get(session.key);
			closeSession(session, "harvested");
			if (state != null) {
				deplete(state);
			}
		}
	}

	/** Result of one resource reward commit; the reward rolled back on failure. */
	private enum RewardResult {
		OK,
		TOOL_MISSING,
		INVENTORY_FULL,
		XP_CAP,
		FAILED
	}

	private RewardResult commitReward(Player player,
			GatheringResourceDefinition definition, ToolResult tool) {
		int[] oldItems = player.playerItems.clone();
		int[] oldAmounts = player.playerItemsN.clone();
		double oldWeight = player.weight;
		int[] oldXp = player.playerXP.clone();
		int[] oldLevels = player.playerLevel.clone();
		try {
			if (tool.consume
					&& !player.getItemAssistant().playerHasItem(tool.itemId, 1)) {
				return RewardResult.TOOL_MISSING;
			}
			if (tool.consume) {
				player.getItemAssistant().deleteItem(tool.itemId, 1);
			}
			for (GatheringResourceDefinition.ItemReward reward
					: definition.rewards()) {
				if (!player.getItemAssistant().addItem(reward.itemId(),
						reward.amount())) {
					restore(player, oldItems, oldAmounts, oldWeight, oldXp,
							oldLevels);
					return RewardResult.INVENTORY_FULL;
				}
			}
			int xpIndex = definition.skill();
			if (xpIndex >= oldXp.length
					|| (long) oldXp[xpIndex] + definition.experience()
							> 200000000) {
				restore(player, oldItems, oldAmounts, oldWeight, oldXp,
						oldLevels);
				return RewardResult.XP_CAP;
			}
			player.getPlayerAssistant().addSkillXP(definition.experience(),
					xpIndex);
			player.weight = com.rs2.game.items.Weight.calculateWeight(
					player.playerItems, player.playerEquipment);
			player.getPacketSender().writeWeight((int) player.weight);
			return RewardResult.OK;
		} catch (RuntimeException failure) {
			restore(player, oldItems, oldAmounts, oldWeight, oldXp, oldLevels);
			logger.log(Level.WARNING,
					"Resource reward failed and was rolled back for player "
							+ player.playerName,
					failure);
			return RewardResult.FAILED;
		}
	}

	private static void restore(Player player, int[] items, int[] amounts,
			double weight, int[] xp, int[] levels) {
		System.arraycopy(items, 0, player.playerItems, 0, items.length);
		System.arraycopy(amounts, 0, player.playerItemsN, 0, amounts.length);
		System.arraycopy(xp, 0, player.playerXP, 0, xp.length);
		System.arraycopy(levels, 0, player.playerLevel, 0, levels.length);
		player.weight = weight;
		player.getItemAssistant().resetItems(3214);
	}

	private void animate(Player player,
			GatheringResourceDefinition definition) {
		if (definition.animation() >= 0) {
			player.startAnimation(definition.animation());
		}
	}

	// ─── Object depletion / respawn ─────────────────────────────────────────

	private synchronized ObjectState objectState(
			GatheringResourceDefinition definition, int x, int y, int plane) {
		ObjectKey key = ObjectKey.of(definition, x, y, plane);
		ObjectState state = objectStates.get(key);
		if (state == null) {
			state = new ObjectState(key, definition,
					ScriptHost.getInstance().getActiveGeneration());
			objectStates.put(key, state);
		}
		return state;
	}

	private void deplete(ObjectState state) {
		synchronized (this) {
			if (state.depleted) {
				return;
			}
			state.depleted = true;
			state.respawnAtTick = currentTick + state.definition.respawnTicks();
			ResolvedWorldObject current = WorldObjectService.getInstance()
					.resolve(state.key.x, state.key.y, state.key.plane);
			if (current == null) {
				return;
			}
			// Timed-object depletion exactly like legacy skilling: the empty
			// object id is shown immediately and the original object id is
			// restored by ObjectManager after the respawn interval. The
			// WorldObjectService defers this write when a reserved footprint
			// owns the tile and replays it on release.
			new com.rs2.game.objects.Object(state.definition.depletedObjectId(),
					state.key.x, state.key.y, state.key.plane,
					current.getObjectRotation(), current.getObjectType(),
					state.definition.objectId(),
					state.definition.respawnTicks());
		}
	}

	// ─── Tool resolution ────────────────────────────────────────────────────

	private ToolResult findTool(Player player,
			GatheringResourceDefinition definition) {
		ItemAssistant items = player.getItemAssistant();
		for (GatheringResourceDefinition.Tool tool : definition.tools()) {
			if (items.playerHasItem(tool.itemId(), 1)) {
				return new ToolResult(tool.itemId(), tool.consume());
			}
			if (equipped(player, tool.itemId())) {
				return new ToolResult(tool.itemId(), false);
			}
		}
		return null;
	}

	private static boolean equipped(Player player, int itemId) {
		for (int slot = 0; slot < player.playerEquipment.length; slot++) {
			if (player.playerEquipment[slot] == itemId) {
				return true;
			}
		}
		return false;
	}

	// ─── Validation helpers ─────────────────────────────────────────────────

	private static boolean authoritativeLive(Player player) {
		return ScriptEncounterService.isAuthoritativeLive(player, true);
	}

	private static boolean withinRange(Player player, ObjectKey key) {
		return player.heightLevel == key.plane
				&& Math.max(Math.abs(player.absX - key.x),
						Math.abs(player.absY - key.y)) <= MAX_RANGE;
	}

	private static boolean withinNpcRange(Player player, Npc npc) {
		return player.heightLevel == npc.heightLevel
				&& Math.max(Math.abs(player.absX - npc.absX),
						Math.abs(player.absY - npc.absY)) <= MAX_RANGE;
	}

	private static Npc npcAt(int slot) {
		if (slot < 0 || slot >= NpcHandler.npcs.length) {
			return null;
		}
		return NpcHandler.npcs[slot];
	}

	private static String skillName(int skill) {
		return com.rs2.script.quest.QuestSkill.values()[skill].getScriptName();
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

	private static final class ToolResult {
		private final int itemId;
		private final boolean consume;

		ToolResult(int itemId, boolean consume) {
			this.itemId = itemId;
			this.consume = consume;
		}
	}

	/** The canonical tile identity of one resource object. */
	private static final class ObjectKey {
		private final int objectId;
		private final int x;
		private final int y;
		private final int plane;

		private ObjectKey(int objectId, int x, int y, int plane) {
			this.objectId = objectId;
			this.x = x;
			this.y = y;
			this.plane = plane;
		}

		static ObjectKey of(GatheringResourceDefinition definition, int x,
				int y, int plane) {
			return new ObjectKey(definition.objectId(), x, y, plane);
		}

		@Override
		public int hashCode() {
			return ((objectId * 16384 + x) * 16384 + y) * 4 + plane;
		}

		@Override
		public boolean equals(Object value) {
			if (!(value instanceof ObjectKey)) {
				return false;
			}
			ObjectKey other = (ObjectKey) value;
			return objectId == other.objectId && x == other.x && y == other.y
					&& plane == other.plane;
		}
	}

	/** Per-tile depletion/respawn bookkeeping of one resource object. */
	private static final class ObjectState {
		private final ObjectKey key;
		private final GatheringResourceDefinition definition;
		private final long generation;
		private boolean depleted;
		private long respawnAtTick;

		ObjectState(ObjectKey key, GatheringResourceDefinition definition,
				long generation) {
			this.key = key;
			this.definition = definition;
			this.generation = generation;
		}
	}

	/** One per-player gathering session. */
	private static final class Session {
		private final Player player;
		private final long generation;
		private final long token;
		private final ObjectKey key;
		private final int npcSlot;
		private final GatheringResourceDefinition definition;
		private final ResourceSessionRng rng;
		private ScriptTaskHandle task;
		private int attempts;
		private boolean closed;

		Session(Player player, long generation, long token,
				GatheringResourceDefinition definition, ObjectKey key,
				int npcSlot, ResourceSessionRng rng) {
			this.player = player;
			this.generation = generation;
			this.token = token;
			this.key = key;
			this.npcSlot = npcSlot;
			this.definition = definition;
			this.rng = rng;
		}
	}

}
