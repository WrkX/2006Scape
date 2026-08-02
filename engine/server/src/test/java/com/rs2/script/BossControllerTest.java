package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.game.items.GroundItem;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.script.boss.BossArena;
import com.rs2.script.boss.BossController;
import com.rs2.script.boss.BossDefinition;
import com.rs2.script.boss.BossDefinitionParser;
import com.rs2.script.boss.BossRuntimeContext;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptNpcHandle;
import com.rs2.script.world.ScriptNpcService;

/**
 * Proves the encounter-agnostic {@code BossController} contract: the
 * borrowed handle is never begun, joined, or closed by the controller;
 * spawn/phase/special/death/named-drop behavior is driven by the descriptor
 * and the narrow context; callbacks run exactly once at their cadence in
 * deterministic order; a throwing callback fails the controller; stale
 * generations never run callbacks; and named drops create exact private
 * identities through the WP2 transaction.
 */
public class BossControllerTest {

	private static final long SEED = 0x0123456789abcdefL;
	private static final int MIN_X = 3200;
	private static final int MIN_Y = 3200;
	private static final int MAX_X = 3210;
	private static final int MAX_Y = 3210;
	private static final int PLANE = 0;

	private ScriptEncounterTestSupport support;
	private Context context;
	private Npc[] previousNpcs;
	private ArrayList<GroundItem> previousItems;
	private ScriptEncounterTestSupport.TestClient owner;
	private ScriptEncounterTestSupport.TestClient other;

	@Before
	public void setUp() throws Exception {
		support = new ScriptEncounterTestSupport();
		ScriptEncounterService.installForTesting(SEED);
		ScriptEncounterService.getInstance().resetForTesting();
		context = support.publishEmpty();
		previousNpcs = NpcHandler.npcs.clone();
		Arrays.fill(NpcHandler.npcs, null);
		previousItems = new ArrayList<GroundItem>(GameEngine.itemHandler.items);
		GameEngine.itemHandler.items.clear();
		Wp5PlayerSupport.ensureItemDefinitions();
		owner = support.player(1, MIN_X, MIN_Y, PLANE);
		other = support.player(2, MIN_X, MIN_Y, PLANE);
	}

	@After
	public void tearDown() throws Exception {
		System.arraycopy(previousNpcs, 0, NpcHandler.npcs, 0,
				previousNpcs.length);
		GameEngine.itemHandler.items.clear();
		GameEngine.itemHandler.items.addAll(previousItems);
		support.close();
	}

	@Test
	public void borrowedHandleCreatesNoSecondReservationAndNeverClosesIt()
			throws Exception {
		context.eval("js", "globalThis.spawns=0;globalThis.ticks=0;");
		ScriptEncounterHandle handle = encounter();
		BossController controller = start(handle, bossSource(
				"id:'borrowed',command:'borrowed',maxHitpoints:50,"
						+ "onTick:function(){globalThis.ticks++;}"));
		assertEquals(BossController.Status.RUNNING, controller.status());
		ScriptNpcHandle boss = controller.context().boss;
		assertNotNull(boss);
		assertNotNull(ownedNpc(153));

		// The controller creates no second encounter/reservation.
		assertNull("a second overlapping reservation must be rejected",
				support.encounter(other, "second", MIN_X, MIN_Y, MAX_X,
						MAX_Y, PLANE));
		assertNull("the owner cannot join a second encounter",
				support.encounter(owner, "owner-second", MIN_X, MIN_Y,
						MAX_X, MAX_Y, PLANE));

		// Poll ticks run the onTick callback under the generation lease.
		tick();
		tick();
		assertEquals(2, context.eval("js", "ticks").asInt());
		assertEquals(1, context.eval("js", "spawns").asInt());

		// Normal death reaches the terminal listener but never closes the
		// borrowed handle; only the owning adapter may close it.
		killBoss(boss);
		assertEquals(BossController.Status.DEFEATED, controller.status());
		assertTrue("the controller must never close the borrowed handle",
				handle.isOpen());
		assertNull(ownedNpc(153));

		handle.close();
		assertFalse(handle.isOpen());
	}

	@Test
	public void phasesRunExactlyOnceInDescendingOrderAndSpecialsFireAtCadence()
			throws Exception {
		context.eval("js", "globalThis.phases=[];globalThis.specials=0;");
		ScriptEncounterHandle handle = encounter();
		BossController controller = start(handle, bossSource(
				"id:'phased',command:'phased',maxHitpoints:100,"
						+ "phases:["
						+ "{name:'P1',hpPercentThreshold:100,"
						+ "onEnter:function(ctx){globalThis.phases.push('P1');}},"
						+ "{name:'P2',hpPercentThreshold:50,"
						+ "onEnter:function(ctx){globalThis.phases.push('P2');"
						+ "ctx.useSpecial('s');}}],"
						+ "specials:{s:{cooldownTicks:4,"
						+ "handler:function(){globalThis.specials++;}}}"));
		assertEquals(BossController.Status.RUNNING, controller.status());
		ScriptNpcHandle boss = controller.context().boss;
		assertNotNull(boss);

		// Poll 1: P1 fires at threshold 100 (HP 100 <= 100).
		tick();
		// Drop HP to 40: P2 fires on poll 2 and arms the special (first
		// fire at poll 2 + 4 = 6).
		assertEquals(60, boss.damage(60, null));
		tick();
		assertEquals(Arrays.asList("P1", "P2"), jsStringArray("phases"));
		assertEquals(0, context.eval("js", "specials").asInt());
		assertEquals(0.4, controller.context().hpPercent(), 0.0001);

		// Polls 3..5: no special. Poll 6: first fire; then every 4 ticks.
		tick();
		tick();
		tick();
		assertEquals(0, context.eval("js", "specials").asInt());
		tick();
		assertEquals(1, context.eval("js", "specials").asInt());
		tick();
		tick();
		tick();
		tick();
		assertEquals(2, context.eval("js", "specials").asInt());
		// Phases never re-fire.
		assertEquals(2, jsStringArray("phases").size());
	}

	@Test
	public void staleGenerationNeverRunsCallbacksAfterReload()
			throws Exception {
		context.eval("js", "globalThis.spawns=0;globalThis.ticks=0;"
				+ "globalThis.specials=0;globalThis.deaths=0;");
		final Context oldContext = context;
		ScriptEncounterHandle handle = encounter();
		BossController controller = start(handle, bossSource(
				"id:'stale',command:'stale',maxHitpoints:100,"
						+ "onTick:function(){globalThis.ticks++;},"
						+ "onDeath:function(){globalThis.deaths++;},"
						+ "specials:{s:{cooldownTicks:1,"
						+ "handler:function(){globalThis.specials++;}}}"));
		assertNotNull(controller.context().boss);
		tick();
		assertEquals(1, oldContext.eval("js", "ticks").asInt());

		// A successful reload publishes a new generation, closes the old
		// encounter (cancelling the poll), and despawns the boss. The old
		// generation lease prevents any further callback.
		context = support.publishEmpty();
		assertNull(ownedNpc(153));
		assertFalse(handle.isOpen());
		tick();
		assertEquals(1, oldContext.eval("js", "ticks").asInt());
		assertEquals(0, oldContext.eval("js", "specials").asInt());
		assertEquals(0, oldContext.eval("js", "deaths").asInt());
	}

	@Test
	public void throwingPhaseCallbackFailsControllerWithoutDrops()
			throws Exception {
		ScriptEncounterHandle handle = encounter();
		final List<BossController.Status> terminals =
				new ArrayList<BossController.Status>();
		BossController controller = start(handle, bossSource(
				"id:'thrower',command:'thrower',maxHitpoints:100,"
						+ "phases:[{name:'Boom',hpPercentThreshold:100,"
						+ "onEnter:function(){throw new Error('phase-boom');}}],"
						+ "dropTable:'boss_loot',privateTicks:200"),
				terminals);
		assertNotNull(controller.context().boss);
		tick();
		assertEquals(BossController.Status.FAILED, controller.status());
		assertEquals(1, terminals.size());
		assertEquals(BossController.Status.FAILED, terminals.get(0));
		assertTrue(GameEngine.itemHandler.items.isEmpty());
	}

	@Test
	public void deathRollsNamedTableWithExactPrivateIdentitiesAndReportsOnce()
			throws Exception {
		registerDropTable();
		context.eval("js", "globalThis.deaths=0;");
		ScriptEncounterHandle handle = encounter();
		final List<BossController.Status> terminals =
				new ArrayList<BossController.Status>();
		final List<ScriptedPlayer> killers =
				new ArrayList<ScriptedPlayer>();
		BossController controller = start(handle, bossSource(
				"id:'looter',command:'looter',maxHitpoints:100,"
						+ "onDeath:function(){globalThis.deaths++;},"
						+ "dropTable:'boss_loot',privateTicks:200"),
				terminals, killers);
		ScriptNpcHandle boss = controller.context().boss;
		assertNotNull(boss);
		long rngBefore = ScriptEncounterService.getInstance()
				.rngStateForTesting(handle.token());

		killBoss(boss);

		assertEquals(BossController.Status.DEFEATED, controller.status());
		assertEquals(1, terminals.size());
		assertEquals(1, context.eval("js", "deaths").asInt());
		assertEquals(owner.playerName, killers.get(0).getUsername());
		assertTrue("the named roll must advance the encounter RNG exactly "
				+ "once", rngBefore != ScriptEncounterService.getInstance()
						.rngStateForTesting(handle.token()));

		GroundItem bones = groundItem(536);
		GroundItem coins = groundItem(995);
		assertNotNull(bones);
		assertNotNull(coins);
		assertTrue(bones.isScriptPrivate());
		assertTrue(bones.isDetached());
		assertEquals(1, bones.getItemAmount());
		assertTrue(coins.isScriptPrivate());
		assertTrue(coins.isDetached());
		assertEquals(500, coins.getItemAmount());
		assertEquals(2, GameEngine.itemHandler.items.size());

		// The controller still did not close the borrowed handle; the
		// adapter decides. The detached rewards survive the close.
		assertTrue(handle.isOpen());
		handle.close();
		assertEquals(2, GameEngine.itemHandler.items.size());
	}

	@Test
	public void droppingBossWithMissingTableFailsController() {
		ScriptEncounterHandle handle = encounter();
		final List<BossController.Status> terminals =
				new ArrayList<BossController.Status>();
		BossController controller = start(handle, bossSource(
				"id:'orphan',command:'orphan',maxHitpoints:100,"
						+ "dropTable:'missing_table',privateTicks:200"),
				terminals);
		killBoss(controller.context().boss);
		assertEquals(BossController.Status.FAILED, controller.status());
		assertEquals(1, terminals.size());
		assertTrue(GameEngine.itemHandler.items.isEmpty());
	}

	private ScriptEncounterHandle encounter() {
		return support.encounter(owner, "boss-test", MIN_X, MIN_Y, MAX_X,
				MAX_Y, PLANE);
	}

	private BossController start(ScriptEncounterHandle handle, org.graalvm.polyglot.Value source) {
		return start(handle, source, new ArrayList<BossController.Status>());
	}

	private BossController start(ScriptEncounterHandle handle,
			org.graalvm.polyglot.Value source,
			final List<BossController.Status> terminals) {
		return start(handle, source, terminals,
				new ArrayList<ScriptedPlayer>());
	}

	private BossController start(ScriptEncounterHandle handle,
			org.graalvm.polyglot.Value source,
			final List<BossController.Status> terminals,
			final List<ScriptedPlayer> killers) {
		BossDefinition definition = new BossDefinitionParser(
				"legacy-unscoped", 0).parse(source);
		List<ScriptedPlayer> participants = new ArrayList<ScriptedPlayer>();
		participants.add(new ScriptedPlayer(owner));
		BossController.TerminalListener listener =
				new BossController.TerminalListener() {
					@Override
					public void onTerminal(BossController controller,
							BossController.Status status,
							ScriptedPosition deathPosition,
							ScriptedPlayer killer) {
						terminals.add(status);
						killers.add(killer);
					}
				};
		return BossController.start(definition, handle,
				new BossArena(MIN_X, MIN_Y, MAX_X, MAX_Y, PLANE),
				new ScriptedPlayer(owner), participants, listener);
	}

	private org.graalvm.polyglot.Value bossSource(String members) {
		return context.eval("js", "({npcId:153,name:'Test Boss',"
				+ "combatLevel:100,maxHit:20,attack:100,defence:100,"
				+ "arena:{minX:" + MIN_X + ",minY:" + MIN_Y + ",maxX:"
				+ MAX_X + ",maxY:" + MAX_Y + ",plane:" + PLANE + "},"
				+ "spawn:{x:3205,y:3205},"
				+ "onSpawn:function(){globalThis.spawns++;}," + members
				+ "})");
	}

	private void registerDropTable() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineDropTable().accept(
					context.eval("js", "({id:'boss_loot',entries:["
							+ "{itemId:536,minAmount:1,maxAmount:1,weight:0,"
							+ "always:true},"
							+ "{itemId:995,minAmount:500,maxAmount:500,"
							+ "weight:100,always:false}]})"));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	/** Drives the real owned-death seam: guard, dispatch, deferred drain. */
	private void killBoss(ScriptNpcHandle boss) {
		Npc npc = npcOf(boss);
		assertNotNull(npc);
		npc.HP = 0;
		npc.isDead = true;
		ScriptNpcService service = ScriptNpcService.getInstance();
		assertTrue(service.beginDeath(npc));
		service.dispatchDeath(npc, owner,
				new ScriptedPosition(npc.absX, npc.absY, npc.heightLevel));
		service.finishDeath(npc);
	}

	private static Npc npcOf(ScriptNpcHandle handle) {
		for (int index = 1; index < NpcHandler.MAX_NPCS; index++) {
			Npc npc = NpcHandler.npcs[index];
			if (npc != null && Long.toString(npc.allocationToken())
					.equals(handle.token())) {
				return npc;
			}
		}
		return null;
	}

	private static void tick() {
		ScriptLifecycleService.getInstance().processGameTick();
	}

	private List<String> jsStringArray(String expression) {
		org.graalvm.polyglot.Value value = context.eval("js", expression);
		List<String> result = new ArrayList<String>();
		for (int index = 0; index < value.getArraySize(); index++) {
			result.add(value.getArrayElement(index).asString());
		}
		return result;
	}

	private static Npc ownedNpc(int npcType) {
		for (int index = 1; index < NpcHandler.MAX_NPCS; index++) {
			Npc npc = NpcHandler.npcs[index];
			if (npc != null && npc.npcType == npcType
					&& ScriptNpcService.getInstance().isOwned(npc)) {
				return npc;
			}
		}
		return null;
	}

	private static GroundItem groundItem(int itemId) {
		for (GroundItem item : GameEngine.itemHandler.items) {
			if (item.getItemId() == itemId) {
				return item;
			}
		}
		return null;
	}

}
