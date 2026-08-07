package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.content.combat.npcs.NpcCombat;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.mob.MobDefinitionRegistry;
import com.rs2.script.mob.ScriptMobRuntime;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

/**
 * Phase 3 world-mob proof: registered npc ids suppress {@code NpcCombat}
 * and drive declarative attack stats; unregistered ids fall through.
 */
public class ScriptMobPortE2ETest {

	private static final int GOBLIN = 100;
	private static final int UNREGISTERED_GOBLIN = 101;

	private String previousContentDir;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;
	private Region[] previousRegions;
	private Player player;
	private int npcSlot = -1;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		previousContentDir = System.getProperty("singlescape.contentDir");
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		previousRegions = (Region[]) regions.get(null);
		Arrays.fill(PlayerHandler.players, null);
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		player = Wp5PlayerSupport.player(94);
		player.absX = 3200;
		player.absY = 3200;
		player.heightLevel = 0;
		player.respawnTimer = 0;
	}

	@After
	public void restore() throws Exception {
		if (npcSlot >= 0 && NpcHandler.npcs[npcSlot] != null) {
			NpcHandler.npcs[npcSlot] = null;
			npcSlot = -1;
		}
		if (player != null) {
			ScriptLifecycleService.getInstance().onPlayerRemoved(player);
			PlayerHandler.players[94] = null;
		}
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
		setDefinitions(ItemDefinition.class, previousItems);
		setDefinitions(ObjectDefinition.class, previousObjects);
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		regions.set(null, previousRegions);
	}

	@Test
	public void registeredMobSuppressesNpcCombatAndAppliesAttackSpeed()
			throws Exception {
		activateGoblinMob();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));
		assertTrue(ScriptMobRuntime.getInstance().owns(GOBLIN));
		assertFalse(ScriptMobRuntime.getInstance().owns(UNREGISTERED_GOBLIN));

		Npc npc = spawnNpc(GOBLIN, 3200, 3201);
		npc.killerId = player.playerId;
		npc.underAttack = true;
		npc.attackTimer = 0;

		NpcCombat.attackPlayer(player, npcSlot);

		assertEquals("scripted attack speed must arm the next attack timer",
				4, npc.attackTimer);
		assertEquals(1, ScriptMobRuntime.getInstance().maxHit(GOBLIN));
		assertEquals(player.playerId, npc.oldIndex);
	}

	@Test
	public void unregisteredNpcFallsThroughWithoutMobOwnership()
			throws Exception {
		activateGoblinMob();
		assertNull(MobDefinitionRegistry.get(UNREGISTERED_GOBLIN));
		assertFalse(ScriptMobRuntime.getInstance().owns(UNREGISTERED_GOBLIN));
		assertEquals(-1, ScriptMobRuntime.getInstance()
				.maxHit(UNREGISTERED_GOBLIN));
	}

	@Test
	public void rangedMobArmsProjectileAndHoldsProjectileRange()
			throws Exception {
		Path root = Files.createTempDirectory("script-mob-ranged");
		Files.write(root.resolve("loader.js"), (
				"defineMob({"
				+ "id:'ranger',npcId:100,name:'Ranger',aggression:0,"
				+ "combatStyle:'ranged',attackSpeed:4,maxHit:6"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));

		Npc npc = spawnNpc(GOBLIN, 3200, 3201);
		player.absX = 3200;
		player.absY = 3206; // 5 tiles away: beyond melee, inside ranged range
		npc.killerId = player.playerId;
		npc.underAttack = true;
		npc.attackTimer = 0;
		npc.attackType = 0; // stale melee default

		NpcCombat.attackPlayer(player, npcSlot);

		assertEquals("ranged style must resolve to the RANGE attack type",
				com.rs2.game.content.combat.AttackType.RANGE.getValue(),
				npc.attackType);
		assertTrue("ranged mob must arm a visible projectile",
				npc.projectileId > 0);
		assertTrue("ranged mob must arm an impact graphic", npc.endGfx > 0);
		assertEquals("ranged mob must keep its projectile hit delay",
				3, npc.hitDelayTimer);
	}

	@Test
	public void onTickAndOnDeathInvalidateOnReload() throws Exception {
		Path root = Files.createTempDirectory("script-mob-callbacks");
		Files.write(root.resolve("loader.js"), (
				"defineMob({"
				+ "id:'goblin',npcId:100,name:'Goblin',aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1,"
				+ "onTick:function(ctx){},"
				+ "onDeath:function(ctx){}"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));
		assertNotNull(MobDefinitionRegistry.get(GOBLIN).onTick());
		assertNotNull(MobDefinitionRegistry.get(GOBLIN).onDeath());

		Npc npc = spawnNpc(GOBLIN, 3200, 3201);
		long generation = ScriptHost.getInstance().getActiveGeneration();
		ScriptMobRuntime.getInstance().processGameTick(generation);
		assertTrue("onSpawn+onTick should track the live allocation",
				ScriptMobRuntime.getInstance()
						.hasSpawnedTokenForTesting(npc.allocationToken()));
		assertTrue(ScriptMobRuntime.getInstance().trackedCount() >= 1);

		ScriptedPosition position = new ScriptedPosition(npc.absX, npc.absY,
				npc.heightLevel);
		ScriptMobRuntime.getInstance().onNpcDeath(npc, player, generation,
				position);
		assertFalse(ScriptMobRuntime.getInstance()
				.hasSpawnedTokenForTesting(npc.allocationToken()));

		Files.write(root.resolve("loader.js"), (
				"defineMob({"
				+ "id:'goblin',npcId:100,name:'Goblin',aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));
		assertNull(MobDefinitionRegistry.get(GOBLIN).onTick());
		assertNull(MobDefinitionRegistry.get(GOBLIN).onDeath());
		assertEquals(0, ScriptMobRuntime.getInstance().trackedCount());
	}

	@Test
	public void aggressionSkipsDeadAndRespawningPlayers() throws Exception {
		Path root = Files.createTempDirectory("script-mob-aggro");
		Files.write(root.resolve("loader.js"), (
				"defineMob({"
				+ "id:'goblin',npcId:100,name:'Goblin',aggression:5,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));

		Npc npc = spawnNpc(GOBLIN, 3200, 3201); // (3200,3201) radius 5
		ScriptMobRuntime runtime = ScriptMobRuntime.getInstance();

		player.absX = 3200;
		player.absY = 3203; // inside radius 5
		player.respawnTimer = 0;
		player.isDead = false;
		assertEquals("a live player inside the radius must be targeted",
				player.playerId, runtime.findAggressionTarget(npcSlot));

		player.respawnTimer = 1;
		assertEquals("a player in the respawn window must be skipped",
				0, runtime.findAggressionTarget(npcSlot));
		player.respawnTimer = 0;

		player.isDead = true;
		assertEquals("a dead player must be skipped",
				0, runtime.findAggressionTarget(npcSlot));
	}

	@Test
	public void closeGenerationClearsTrackedCallbacks() throws Exception {
		Path root = Files.createTempDirectory("script-mob-close");
		Files.write(root.resolve("loader.js"), (
				"defineMob({"
				+ "id:'goblin',npcId:100,name:'Goblin',aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1,"
				+ "onSpawn:function(ctx){},onTick:function(ctx){}"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));

		Npc npc = spawnNpc(GOBLIN, 3200, 3201);
		long generation = ScriptHost.getInstance().getActiveGeneration();
		ScriptMobRuntime runtime = ScriptMobRuntime.getInstance();
		runtime.processGameTick(generation);
		assertTrue(runtime.trackedCount() >= 1);
		assertTrue(runtime.hasSpawnedTokenForTesting(npc.allocationToken()));

		runtime.closeGeneration(generation);
		assertEquals("closing the active generation must drop tracked state",
				0, runtime.trackedCount());
		assertTrue("closing the active generation must preserve onSpawn dedup",
				runtime.hasSpawnedTokenForTesting(npc.allocationToken()));
	}

	@Test
	public void reloadDoesNotRefireOnSpawnForLiveNpcs() throws Exception {
		Path root = Files.createTempDirectory("script-mob-reload-spawn");
		Files.write(root.resolve("loader.js"), (
				"globalThis.spawnCount = 0;"
				+ "defineMob({"
				+ "id:'goblin',npcId:100,name:'Goblin',aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1,"
				+ "onSpawn:function(ctx){globalThis.spawnCount++;}"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost host = ScriptHost.getInstance();
		host.reload();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));

		Npc npc = spawnNpc(GOBLIN, 3200, 3201);
		long generation = host.getActiveGeneration();
		ScriptMobRuntime runtime = ScriptMobRuntime.getInstance();
		runtime.processGameTick(generation);
		assertEquals(1, host.getContext().eval("js", "globalThis.spawnCount")
				.asInt());

		host.reload();
		generation = host.getActiveGeneration();
		runtime.processGameTick(generation);
		assertEquals("reload must not re-fire onSpawn for a live allocation",
				0, host.getContext().eval("js", "globalThis.spawnCount")
						.asInt());
	}

	@Test
	public void singleCombatMobGivesUpWhenPlayerTargetedElsewhere()
			throws Exception {
		Path root = Files.createTempDirectory("script-mob-single");
		Files.write(root.resolve("loader.js"), (
				"defineMob({"
				+ "id:'goblin',npcId:100,name:'Goblin',aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));

		Npc npc = spawnNpc(GOBLIN, 3200, 3201); // (3200,3201) is not inMulti
		npc.killerId = player.playerId;
		npc.underAttack = true;
		npc.attackTimer = 0;
		player.underAttackBy = 999; // another mob already owns the player

		NpcCombat.attackPlayer(player, npcSlot);

		assertEquals("a single-combat mob must give up when the player is "
				+ "already targeted elsewhere",
				0, npc.killerId);
		assertEquals("no attack may be armed for a given-up attack",
				0, npc.attackTimer);
	}

	@Test
	public void corpseDoesNotRefireOnSpawnDuringRespawnWindow()
			throws Exception {
		Path root = Files.createTempDirectory("script-mob-corpse");
		Files.write(root.resolve("loader.js"), (
				"defineMob({"
				+ "id:'goblin',npcId:100,name:'Goblin',aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1,"
				+ "onSpawn:function(ctx){}"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));

		Npc npc = spawnNpc(GOBLIN, 3200, 3201);
		long token = npc.allocationToken();
		long generation = ScriptHost.getInstance().getActiveGeneration();
		ScriptMobRuntime runtime = ScriptMobRuntime.getInstance();
		runtime.processGameTick(generation);
		assertTrue("live spawn must receive onSpawn",
				runtime.hasSpawnedTokenForTesting(token));

		ScriptedPosition position = new ScriptedPosition(npc.absX, npc.absY,
				npc.heightLevel);
		runtime.onNpcDeath(npc, player, generation, position);
		assertFalse("death must revoke onSpawn",
				runtime.hasSpawnedTokenForTesting(token));

		// NpcHandler keeps the corpse in npcs[] with HP restored to MaxHP
		// through the respawn countdown; the allocation is unchanged until a
		// fresh Npc takes the slot. The next tick must not treat the corpse as
		// first sight.
		npc.HP = npc.MaxHP;
		npc.isDead = true;
		npc.applyDead = true;
		runtime.processGameTick(generation);
		assertFalse("corpse in the respawn window must not re-fire onSpawn",
				runtime.hasSpawnedTokenForTesting(token));
	}

	@Test
	public void compiledContentRegistersGoblinMob() throws Exception {
		File contentDir = findCompiledContent();
		assertTrue("Run pnpm build:content before Maven tests",
				contentDir.isDirectory());
		Wp5PlayerSupport.ensureNpcDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull("compiled world-mobs module must register goblin 100",
				MobDefinitionRegistry.get(GOBLIN));
		assertEquals("goblin", MobDefinitionRegistry.get(GOBLIN).id());
		assertEquals(4, MobDefinitionRegistry.get(GOBLIN).attackSpeed());
		assertEquals(1, MobDefinitionRegistry.get(GOBLIN).maxHit());
	}

	private void activateGoblinMob() throws Exception {
		Path root = Files.createTempDirectory("script-mob-port");
		Files.write(root.resolve("loader.js"), (
				"defineMob({"
				+ "id:'goblin',npcId:100,name:'Goblin',aggression:0,"
				+ "combatStyle:'melee',attackSpeed:4,maxHit:1"
				+ "});")
				.getBytes(StandardCharsets.UTF_8));
		System.setProperty("singlescape.contentDir",
				root.toFile().getAbsolutePath());
		ScriptHost.getInstance().reload();
		assertNotNull(MobDefinitionRegistry.get(GOBLIN));
	}

	private Npc spawnNpc(int npcType, int x, int y) {
		for (int i = 1; i < NpcHandler.npcs.length; i++) {
			if (NpcHandler.npcs[i] == null) {
				Npc npc = new Npc(i, npcType);
				npc.absX = x;
				npc.absY = y;
				npc.makeX = x;
				npc.makeY = y;
				npc.heightLevel = 0;
				npc.HP = 5;
				npc.MaxHP = 5;
				npc.maxHit = 1;
				npc.attackTimer = 0;
				npc.isDead = false;
				npc.applyDead = false;
				NpcHandler.npcs[i] = npc;
				npcSlot = i;
				return npc;
			}
		}
		throw new IllegalStateException("no free npc slot");
	}

	private static File findCompiledContent() {
		File root = new File(".").getAbsoluteFile();
		while (root != null) {
			File candidate = new File(root, "content/dist");
			if (new File(candidate, "loader.js").isFile()) {
				return candidate;
			}
			root = root.getParentFile();
		}
		return new File("content/dist");
	}

	private static void setDefinitions(Class<?> type, Object value)
			throws Exception {
		Field field = type.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, value);
	}
}
