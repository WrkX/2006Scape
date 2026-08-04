package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.script.raid.RaidDefinition;
import com.rs2.script.raid.RaidDefinitionRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.route.ExecutableRouteKey;
import com.rs2.script.route.ExecutableRouteRecord;
import com.rs2.script.route.RouteRegistry;

/**
 * Proves the canonical {@code defineRaid} schema-v1 contract: strict members
 * and bounds, the required exact command route, bounded muster/entrance and
 * player limits, ordered non-overlapping rooms inside the raid bounds,
 * candidate-scoped boss/reward/drop-table resolution, reward/private-TTL
 * coupling, duplicate-id rejection, and the exact WP1 host command route
 * registered by the raid runtime.
 */
public class RaidDefinitionParserTest {

	private Context context;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;
	private NpcList[] previousNpcList;

	@Before
	public void setUp() throws Exception {
		context = Context.create("js");
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		previousNpcList = NpcHandler.NpcList.clone();
		ItemDefinition[] items = new ItemDefinition[1500];
		items[995] = named(995, "Coins");
		items[1149] = named(1149, "Dragon med helm");
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, items);
		ObjectDefinition[] objects = new ObjectDefinition[500];
		objects[409] = new ObjectDefinition(409);
		Field objectField = ObjectDefinition.class.getDeclaredField("definitions");
		objectField.setAccessible(true);
		objectField.set(null, objects);
		NpcHandler.NpcList = new NpcList[NpcHandler.maxListedNPCs];
	}

	@After
	public void restore() throws Exception {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, previousItems);
		Field objectField = ObjectDefinition.class.getDeclaredField("definitions");
		objectField.setAccessible(true);
		objectField.set(null, previousObjects);
		System.arraycopy(previousNpcList, 0, NpcHandler.NpcList, 0,
				previousNpcList.length);
	}

	@Test
	public void canonicalRaidParsesIntoJavaOwnedDescriptorWithRoutes() {
		register(canonical("demo-raid", "demo-raid"));

		RaidDefinition raid = RaidDefinitionRegistry.get("demo-raid");
		assertNotNull(raid);
		assertEquals("demo-raid", raid.id());
		assertEquals("demo-raid", raid.command());
		assertEquals(3200, raid.bounds().minX());
		assertEquals(3210, raid.bounds().maxX());
		assertEquals(0, raid.bounds().plane());
		assertEquals(3200, raid.muster().minX());
		assertEquals(3204, raid.muster().maxY());
		assertEquals(3205, raid.entranceX());
		assertEquals(3205, raid.entranceY());
		assertEquals(0, raid.entrancePlane());
		assertEquals(1, raid.minPlayers());
		assertEquals(4, raid.maxPlayers());
		assertEquals(6000, raid.timeLimitTicks());
		assertEquals(1, raid.rooms().size());
		assertEquals("room-one", raid.rooms().get(0).id());
		assertNull(raid.rooms().get(0).boss());
		assertEquals(1, raid.rewards().size());
		assertEquals("demo-reward", raid.rewards().get(0).id());
		assertTrue(raid.hasRewardTable());
		assertEquals("demo-table", raid.rewardTable());
		assertEquals(200, raid.privateTicks());
		assertNotNull(raid.onStart());
		assertNotNull(raid.onComplete());
		assertNotNull(raid.onWipe());

		// The exact WP1 host command route is registered with the candidate.
		ExecutableRouteRecord route = ScriptHost.getInstance()
				.readActiveRegistry(state -> RouteRegistry.get(state,
						ExecutableRouteKey.command("demo-raid")));
		assertNotNull(route);
		assertFalse("the raid route must be a Java host consumer",
				route.isGuest());
	}

	@Test
	public void bossRoomResolvesTheRegisteredBossAndRegistersItsSlice()
			throws Exception {
		registerWithBoss("boss-raid", "boss-raid", "demo-boss", 3205, 3205, 0);

		RaidDefinition raid = RaidDefinitionRegistry.get("boss-raid");
		assertNotNull(raid);
		assertEquals(1, raid.rooms().size());
		assertNotNull(raid.rooms().get(0).boss());
		assertEquals("demo-boss", raid.rooms().get(0).boss().id());
		assertEquals(153, raid.rooms().get(0).boss().npcId());
		assertEquals(3200, raid.rooms().get(0).bounds().minX());
	}

	@Test
	public void duplicateStableIdsRejectTheCandidate() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'demo-reward',items:[{id:995,"
							+ "amount:50000}],experience:[],questPoints:0,"
							+ "state:[]})"));
			ScriptFunctions.getInstance().getDefineDropTable().accept(context
					.eval("js", "({id:'demo-table',entries:[{itemId:995,"
							+ "minAmount:1,maxAmount:1,weight:0,always:true}]})"));
			ScriptFunctions.getInstance().getDefineRaid()
					.accept(canonical("dup-raid", "dup-raid"));
			ScriptFunctions.getInstance().getDefineRaid()
					.accept(canonical("dup-raid", "dup-raid"));
			fail("Expected the duplicate raid id to reject the candidate");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage()
					.contains("duplicate raid id 'dup-raid'"));
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void reservedCommandAndUppercaseCommandRejectTheCandidate() {
		expectFailure(canonical("reserved-raid", "reload"),
				"command alias is reserved for the engine admin transport",
				null);
		expectFailure(canonical("upper-raid", "Demo-Raid"),
				"must be a lower-case command", null);
	}

	@Test
	public void invalidDefinitionsRejectWithSourceDiagnostics() {
		String[] invalid = {
				// Unknown member
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}],"
						+ "extra:1}",
				// Missing command
				"{id:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// Inverted bounds
				"{id:'x',command:'x',bounds:{minX:3210,minY:3200,"
						+ "maxX:3200,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// Oversized bounds side
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3264,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// Muster outside the bounds
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3205,"
						+ "minY:3205,maxX:3215,maxY:3215},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// Entrance outside the bounds
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3211,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// minPlayers above maxPlayers
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:5,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// Player limit above the encounter cap
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:9,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// Empty rooms
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[]}",
				// Duplicate room ids
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3205,maxY:3205,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}},"
						+ "{id:'one',name:'One',bounds:{minX:3206,minY:3206,"
						+ "maxX:3210,maxY:3210,plane:0},onEnter:function(){},"
						+ "onTick:function(){return {status:'completed'};},"
						+ "onComplete:function(){}}]}",
				// Overlapping room bounds
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3205,maxY:3205,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}},"
						+ "{id:'two',name:'Two',bounds:{minX:3205,minY:3205,"
						+ "maxX:3210,maxY:3210,plane:0},onEnter:function(){},"
						+ "onTick:function(){return {status:'completed'};},"
						+ "onComplete:function(){}}]}",
				// Room outside the raid bounds
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3211,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// Missing rewards
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// Unresolved reward reference
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['missing-reward'],"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// rewardTable without privateTicks
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "rewardTable:'demo-table',"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}",
				// privateTicks without rewardTable
				"{id:'x',command:'x',bounds:{minX:3200,minY:3200,"
						+ "maxX:3210,maxY:3210,plane:0},muster:{minX:3200,"
						+ "minY:3200,maxX:3204,maxY:3204},entrance:{x:3205,"
						+ "y:3205,plane:0},minPlayers:1,maxPlayers:4,"
						+ "timeLimitTicks:6000,rewards:['demo-reward'],"
						+ "privateTicks:200,"
						+ "rooms:[{id:'one',name:'One',bounds:{minX:3200,"
						+ "minY:3200,maxX:3210,maxY:3210,plane:0},"
						+ "onEnter:function(){},onTick:function(){return "
						+ "{status:'completed'};},onComplete:function(){}}]}"
		};
		for (String source : invalid) {
			expectFailure(source, null, null);
		}
	}

	@Test
	public void missingBossAndUnreachableBossSpawnRejectTheCandidate() {
		// Unknown boss id.
		expectFailure(canonicalWithBoss("missing-boss-raid",
				"missing-boss-raid", "no-such-boss"),
				"references boss 'no-such-boss' which is not registered",
				null);
		// Boss registered after the raid (different candidate) is not
		// visible: register only the raid without a staged boss.
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'demo-reward',items:[{id:995,"
							+ "amount:50000}],experience:[],questPoints:0,"
							+ "state:[]})"));
			ScriptFunctions.getInstance().getDefineRaid().accept(
					canonicalWithBoss("late-boss-raid",
							"late-boss-raid", "late-boss"));
			fail("Expected the late boss reference to fail");
		} catch (IllegalArgumentException expected) {
			assertTrue(expected.getMessage()
					.contains("which is not registered"));
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	@Test
	public void bossSpawnOutsideRoomSliceRejectsTheCandidate() {
		// Room slice (3200..3204) does not contain the boss spawn (3209).
		expectFailureWithBoss("slice-raid", "slice-raid", "far-boss",
				3209, 3209, 0, 3200, 3200, 3204, 3204,
				"spawn (3209, 3209) is unreachable", null);
	}

	@Test
	public void bossPlaneMismatchRejectsTheCandidate() {
		expectFailureWithBoss("plane-raid", "plane-raid", "plane-boss",
				3205, 3205, 1, 3200, 3200, 3210, 3210,
				"arena plane 1 differs from the room plane 0", null);
	}

	private String bossSource(String id, int spawnX, int spawnY, int plane) {
		return "({id:'" + id + "',npcId:153,name:'Test Boss',"
				+ "combatLevel:100,maxHitpoints:100,maxHit:10,"
				+ "attack:50,defence:50,"
				+ "arena:{minX:3200,minY:3200,maxX:3210,"
				+ "maxY:3210,plane:" + plane + "},"
				+ "spawn:{x:" + spawnX + ",y:" + spawnY + "},"
				+ "command:'" + id + "',onSpawn:function(){}})";
	}

	/** Registers one candidate with the boss, reward, table, and raid. */
	private void registerWithBoss(String raidId, String command,
			String bossId, int bossX, int bossY, int bossPlane) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineBoss().accept(context
					.eval("js", bossSource(bossId, bossX, bossY,
							bossPlane)));
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'demo-reward',items:[{id:995,"
							+ "amount:50000}],experience:[],questPoints:0,"
							+ "state:[]})"));
			ScriptFunctions.getInstance().getDefineDropTable().accept(context
					.eval("js", "({id:'demo-table',entries:[{itemId:995,"
							+ "minAmount:1,maxAmount:1,weight:0,always:true}]})"));
			ScriptFunctions.getInstance().getDefineRaid().accept(
					canonicalWithBoss(raidId, command, bossId));
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	/** Expects a raid rejection while the boss is staged in the candidate. */
	private void expectFailureWithBoss(String raidId, String command,
			String bossId, int bossX, int bossY, int bossPlane,
			int sliceMinX, int sliceMinY, int sliceMaxX, int sliceMaxY,
			String expectedMessage, String expectedDetail) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineBoss().accept(context
					.eval("js", bossSource(bossId, bossX, bossY,
							bossPlane)));
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'demo-reward',items:[{id:995,"
							+ "amount:50000}],experience:[],questPoints:0,"
							+ "state:[]})"));
			ScriptFunctions.getInstance().getDefineDropTable().accept(context
					.eval("js", "({id:'demo-table',entries:[{itemId:995,"
							+ "minAmount:1,maxAmount:1,weight:0,always:true}]})"));
			ScriptFunctions.getInstance().getDefineRaid().accept(
					canonicalWithBossSlice(raidId, command, bossId,
							sliceMinX, sliceMinY, sliceMaxX, sliceMaxY));
			fail("Expected raid registration to fail");
		} catch (IllegalArgumentException expected) {
			if (expectedMessage != null) {
				assertTrue(expected.getMessage().contains(expectedMessage));
			}
			if (expectedDetail != null) {
				assertTrue(expected.getMessage().contains(expectedDetail));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void register(String source) {
		register(raid(source));
	}

	private void register(Value definition) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'demo-reward',items:[{id:995,"
							+ "amount:50000},{id:1149,amount:1}],"
							+ "experience:[],questPoints:0,state:[]})"));
			ScriptFunctions.getInstance().getDefineDropTable().accept(context
					.eval("js", "({id:'demo-table',entries:[{itemId:995,"
							+ "minAmount:1,maxAmount:1,weight:0,always:true}]})"));
			ScriptFunctions.getInstance().getDefineRaid().accept(definition);
			ScriptHost.getInstance().publishForTesting(context, candidate);
		} catch (RuntimeException error) {
			RegistryStore.rollback(candidate);
			throw error;
		}
	}

	private void expectFailure(Value definition, String expectedMessage,
			String expectedDetail) {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineReward().accept(context
					.eval("js", "({id:'demo-reward',items:[{id:995,"
							+ "amount:50000}],experience:[],questPoints:0,"
							+ "state:[]})"));
			ScriptFunctions.getInstance().getDefineDropTable().accept(context
					.eval("js", "({id:'demo-table',entries:[{itemId:995,"
							+ "minAmount:1,maxAmount:1,weight:0,always:true}]})"));
			ScriptFunctions.getInstance().getDefineRaid().accept(definition);
			fail("Expected raid registration to fail");
		} catch (IllegalArgumentException expected) {
			if (expectedMessage != null) {
				assertTrue(expected.getMessage().contains(expectedMessage));
			}
			if (expectedDetail != null) {
				assertTrue(expected.getMessage().contains(expectedDetail));
			}
		} finally {
			RegistryStore.rollback(candidate);
		}
	}

	private void expectFailure(String source, String expectedMessage,
			String expectedDetail) {
		expectFailure(raid(source), expectedMessage, expectedDetail);
	}

	/** Compact canonical raid source without a boss room. */
	private Value canonical(String id, String command) {
		return raid("{id:'" + id + "',command:'" + command
				+ "',bounds:{minX:3200,minY:3200,maxX:3210,maxY:3210,plane:0},"
				+ "muster:{minX:3200,minY:3200,maxX:3204,maxY:3204},"
				+ "entrance:{x:3205,y:3205,plane:0},minPlayers:1,maxPlayers:4,"
				+ "timeLimitTicks:6000,rewards:['demo-reward'],"
				+ "rewardTable:'demo-table',privateTicks:200,"
				+ "onStart:function(){},onComplete:function(){},"
				+ "onWipe:function(){},"
				+ "rooms:[{id:'room-one',name:'Room One',"
				+ "bounds:{minX:3200,minY:3200,maxX:3210,maxY:3210,plane:0},"
				+ "onEnter:function(){},onTick:function(){return "
				+ "{status:'completed'};},onComplete:function(){}}]}");
	}

	/** Canonical raid whose single room is a boss room. */
	private Value canonicalWithBoss(String id, String command,
			String bossId) {
		return canonicalWithBossSlice(id, command, bossId, 3200, 3200,
				3210, 3210);
	}

	private Value canonicalWithBossSlice(String id, String command,
			String bossId, int minX, int minY, int maxX, int maxY) {
		return raid("{id:'" + id + "',command:'" + command
				+ "',bounds:{minX:3200,minY:3200,maxX:3210,maxY:3210,plane:0},"
				+ "muster:{minX:3200,minY:3200,maxX:3204,maxY:3204},"
				+ "entrance:{x:3205,y:3205,plane:0},minPlayers:1,maxPlayers:4,"
				+ "timeLimitTicks:6000,rewards:['demo-reward'],"
				+ "rooms:[{id:'boss-room',name:'Boss Room',"
				+ "bounds:{minX:" + minX + ",minY:" + minY + ",maxX:" + maxX
				+ ",maxY:" + maxY + ",plane:0},"
				+ "onEnter:function(){},onTick:function(){return "
				+ "{status:'in_progress'};},onComplete:function(){},"
				+ "boss:{bossId:'" + bossId + "'}}]}");
	}

	private Value raid(String source) {
		return context.eval("js", "(" + source + ")");
	}

	private static ItemDefinition named(int id, String name) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName(name);
		return definition;
	}

}
