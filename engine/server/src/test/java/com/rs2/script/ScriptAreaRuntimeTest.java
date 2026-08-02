package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.apollo.cache.def.ItemDefinition;
import org.apollo.cache.def.ObjectDefinition;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.npcs.NpcList;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.area.ScriptAreaRuntime;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptObjectHandle;
import com.rs2.world.ResolvedWorldObject;
import com.rs2.world.WorldObjectService;

/**
 * Proves the real two-phase area activation protocol through
 * {@code ScriptHost.reload}: prepare/reserve/shadow-apply/verify/
 * retirement/checkpoint/commit with the production area runtime, exact
 * abort restoration at every injected stage, same-footprint handoff,
 * conflicting-footprint rejection, and generation cleanup. The fixture
 * uses hermetic definitions and the Crandor map regions so the layered
 * object transactions activate through the real collision path.
 */
public class ScriptAreaRuntimeTest {

	private String previousContentDir;
	private NpcList[] previousNpcList;
	private ItemDefinition[] previousItems;
	private ObjectDefinition[] previousObjects;
	private RegionBackup regions;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		ScriptEncounterServiceFixture.install(42L);
		previousContentDir = System.getProperty("singlescape.contentDir");
		previousNpcList = NpcHandler.NpcList.clone();
		previousItems = ItemDefinition.getDefinitions();
		previousObjects = ObjectDefinition.getDefinitions();
		NpcHandler.NpcList = new NpcList[NpcHandler.maxListedNPCs];
		NpcList npc = new NpcList(153);
		npc.npcName = "test_dragon";
		NpcHandler.NpcList[0] = npc;
		Wp5PlayerSupport.ensureItemDefinitions();
		Wp5PlayerSupport.ensureObjectDefinitions();
		Wp5PlayerSupport.ensureAreaRegions();
		regions = RegionBackup.capture();
	}

	@After
	public void restore() throws Exception {
		ScriptRuntimeTestFixture.reset();
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		} else {
			System.setProperty("singlescape.contentDir", previousContentDir);
		}
		System.arraycopy(previousNpcList, 0, NpcHandler.NpcList, 0,
				previousNpcList.length);
		setDefinitions(ItemDefinition.class, previousItems);
		setDefinitions(ObjectDefinition.class, previousObjects);
		if (regions != null) {
			regions.restore();
		}
	}

	@Test
	public void activationSpawnsNpcsAndProjectsObjectsThroughRealReload()
			throws Exception {
		File root = writeLoader(canonicalArea());
		setContentDir(root);
		ScriptHost.getInstance().reload();

		long generation = ScriptHost.getInstance().getActiveGeneration();
		assertTrue(generation > 0L);
		assertEquals(1, ScriptAreaRuntime.getInstance().sessionCount());
		assertEquals(1, ScriptAreaRuntime.getInstance().selectedAreaCount());
		assertTrue(ScriptAreaRuntime.getInstance().sessionToken(
				"runtime-area") > 0L);

		// The exact spawn allocations are live in the world at their tiles.
		assertTrue(hasNpcAt(153, 2830, 9630));
		assertTrue(hasNpcAt(153, 2840, 9640));
		// The layered object projections resolve at their tiles.
		assertObjectAt(409, 2850, 9635);
		assertObjectAt(2213, 2835, 9640);
		assertNull(WorldObjectService.getInstance().resolve(2830, 9630, 0)
				== null ? null : resolveId(2830, 9630));
	}

	@Test
	public void everyInjectedStageAbortsAndRestoresThePreviousWorld()
			throws Exception {
		File root = writeLoader(canonicalArea());
		setContentDir(root);
		ScriptHost.getInstance().reload();
		Context stable = ScriptHost.getInstance().getContext();
		long generation = ScriptHost.getInstance().getActiveGeneration();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"runtime-area");
		assertTrue(token > 0L);
		assertTrue(hasNpcAt(153, 2830, 9630));
		assertObjectAt(409, 2850, 9635);
		assertObjectAt(2213, 2835, 9640);

		String[] stages = { "prepare", "reserve", "applyShadow",
				"verifyShadow", "retire", "verifyRetirement", "checkpoint" };
		for (String stage : stages) {
			inject(stage);
			ScriptHost.getInstance().reload();
			assertSame(stable, ScriptHost.getInstance().getContext());
			assertEquals(generation,
					ScriptHost.getInstance().getActiveGeneration());
			assertEquals(1, ScriptAreaRuntime.getInstance().sessionCount());
			assertTrue("npc must survive a " + stage + " abort",
					hasNpcAt(153, 2830, 9630));
			assertObjectAt(409, 2850, 9635);
			assertObjectAt(2213, 2835, 9640);
		}
	}

	@Test
	public void sameFootprintHandoffReplacesAllocationsAtomically()
			throws Exception {
		File root = writeLoader(canonicalArea());
		setContentDir(root);
		ScriptHost.getInstance().reload();
		long firstGeneration = ScriptHost.getInstance().getActiveGeneration();
		long firstToken = ScriptAreaRuntime.getInstance().sessionToken(
				"runtime-area");
		long firstNpcAllocation = allocationOf(153, 2830, 9630);

		ScriptHost.getInstance().reload();

		long secondGeneration = ScriptHost.getInstance().getActiveGeneration();
		assertTrue(secondGeneration > firstGeneration);
		assertEquals(1, ScriptAreaRuntime.getInstance().sessionCount());
		long secondToken = ScriptAreaRuntime.getInstance().sessionToken(
				"runtime-area");
		assertTrue("the handoff must allocate a fresh session token",
				secondToken != firstToken);
		long secondNpcAllocation = allocationOf(153, 2830, 9630);
		assertTrue("the same-footprint NPC must be a fresh allocation",
				secondNpcAllocation != firstNpcAllocation);
		assertObjectAt(409, 2850, 9635);
		assertObjectAt(2213, 2835, 9640);
		// Exactly the two fresh allocations of the new session remain live;
		// the retired generation's world state is fully removed.
		assertEquals(2, liveAreaNpcsOf(secondToken));
		assertFalse(ScriptAreaRuntime.getInstance().sessionToken(
				"runtime-area") == firstToken);
	}

	@Test
	public void sameFootprintHandoffKeepsShadowInvisibleUntilCommit()
			throws Exception {
		File root = writeLoader(canonicalArea());
		setContentDir(root);
		ScriptHost.getInstance().reload();
		long firstNpcAllocation = allocationOf(153, 2830, 9630);
		assertTrue(firstNpcAllocation != 0L);
		long firstToken = ScriptAreaRuntime.getInstance().sessionToken(
				"runtime-area");

		final List<String> observations = new ArrayList<String>();
		ScriptAreaRuntime.getInstance().setMidHandoffHookForTesting(() -> {
			observations.add("sessions=" + ScriptAreaRuntime.getInstance()
					.sessionCount()
					+ ";selected=" + ScriptAreaRuntime.getInstance()
							.selectedAreaCount()
					+ ";npc=" + allocationOf(153, 2830, 9630)
					+ ";npcs=" + countNpcsOfTypeAt(153, 2830, 9630)
					+ ";objects=" + WorldObjectService.getInstance()
							.areaObjectCount(firstToken));
		});

		ScriptHost.getInstance().reload();

		// The hook fires after applyShadow and after retirePredecessor.
		assertEquals(2, observations.size());
		String[] shadowPhase = observations.get(0).split(";");
		// Shadow staged but invisible: the old world is still selected and
		// live, exactly one old allocation occupies the spawn tile, and no
		// shadow projection is visible before the swap.
		assertTrue(shadowPhase[0].startsWith("sessions=2"));
		assertTrue(shadowPhase[1].startsWith("selected=1"));
		assertEquals("npc=" + firstNpcAllocation, shadowPhase[2]);
		assertTrue(shadowPhase[3].startsWith("npcs=1"));
		assertTrue(shadowPhase[4].startsWith("objects=2"));
		String[] retirePhase = observations.get(1).split(";");
		// After retirement the old allocation is gone and the deferred
		// spawn is still not visible; no half-installed world exists.
		assertTrue(retirePhase[0].startsWith("sessions=2"));
		assertTrue(retirePhase[1].startsWith("selected=1"));
		assertEquals("npc=0", retirePhase[2]);
		assertTrue(retirePhase[3].startsWith("npcs=0"));
		assertTrue(retirePhase[4].startsWith("objects=0"));

		// After the selector swap exactly the fresh allocation is live.
		long secondNpcAllocation = allocationOf(153, 2830, 9630);
		assertTrue(secondNpcAllocation != 0L);
		assertTrue("the handoff must allocate a fresh NPC allocation",
				secondNpcAllocation != firstNpcAllocation);
		assertEquals(1, ScriptAreaRuntime.getInstance().sessionCount());
		assertEquals(1, countNpcsOfTypeAt(153, 2830, 9630));
		assertObjectAt(409, 2850, 9635);
		assertObjectAt(2213, 2835, 9640);
	}

	@Test
	public void competingWriterIsBlockedWhileTheHandoffReservationIsHeld()
			throws Exception {
		File root = writeLoader(canonicalArea());
		setContentDir(root);
		ScriptHost.getInstance().reload();
		// additionalPlayer() never resets the script services, so the live
		// area world of the first reload stays intact.
		com.rs2.game.players.Player thirdParty = Wp5PlayerSupport
				.additionalPlayer(94);
		try {
			// The encounter must belong to the currently published
			// generation (1 in a fresh JVM, higher after earlier reloads
			// in the same JVM because resetForTesting keeps nextGeneration).
			long generation = ScriptHost.getInstance().getActiveGeneration();
			com.rs2.script.ScriptedPlayer owner = new com.rs2.script.ScriptedPlayer(
					thirdParty, generation);
			ScriptEncounterHandle competitor = owner.beginEncounter(
					"competing-writer", 2830, 9630, 2850, 9640, 0);
			assertNotNull(competitor);

			final List<Boolean> attempts = new ArrayList<Boolean>();
			ScriptAreaRuntime.getInstance().setMidHandoffHookForTesting(() -> {
				// Adapt the write expectation to whatever is visible at the
				// reserved tile in the current window (the old projection
				// before retirement, the lower layer after it).
				ResolvedWorldObject visible = WorldObjectService
						.getInstance().resolve(2835, 9640, 0);
				if (visible == null) {
					attempts.add(Boolean.TRUE);
					return;
				}
				ScriptObjectHandle attempt = competitor.replaceObject(2835,
						9640, 0, visible.getObjectId(),
						visible.getObjectType(), visible.getObjectRotation(),
						409, 10, 0);
				attempts.add(Boolean.valueOf(attempt == null));
			});

			ScriptHost.getInstance().reload();

			// Both firings observed a blocked competing writer.
			assertEquals(2, attempts.size());
			assertTrue("the competing write must be rejected mid-handoff",
					attempts.get(0).booleanValue());
			assertTrue("the competing write must stay rejected after "
					+ "retirement while the reservation is held",
					attempts.get(1).booleanValue());
			// The candidate projection installed intact at commit.
			assertObjectAt(2213, 2835, 9640);
		} finally {
			PlayerHandler.players[94] = null;
		}
	}

	@Test
	public void resetForTestingFreesOwnedNpcSlotsBeforeForgettingOwnership()
			throws Exception {
		File root = writeLoader(canonicalArea());
		setContentDir(root);
		ScriptHost.getInstance().reload();
		long token = ScriptAreaRuntime.getInstance().sessionToken(
				"runtime-area");
		assertTrue(token > 0L);
		assertEquals(2, liveAreaNpcsOf(token));

		ScriptRuntimeTestFixture.reset();

		assertEquals(0, liveAreaNpcsOf(token));
		for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
			com.rs2.game.npcs.Npc npc = NpcHandler.npcs[i];
			assertFalse("no ghost area NPC may remain in the slot array",
					npc != null && npc.npcType == 153 && npc.absX == 2830
							&& npc.absY == 9630);
		}
	}

	private static int liveAreaNpcsOf(long token) {
		int count = 0;
		for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
			com.rs2.game.npcs.Npc npc = NpcHandler.npcs[i];
			if (npc != null && npc.npcType == 153) {
				String[] binding = com.rs2.script.world.ScriptNpcService
						.getInstance().areaSpawnOf(npc);
				if (binding != null) {
					count++;
				}
			}
		}
		return count;
	}

	@Test
	public void conflictingObjectFootprintsAcrossAreasRejectTheCandidate()
			throws Exception {
		File root = writeLoader(canonicalArea());
		setContentDir(root);
		ScriptHost.getInstance().reload();
		Context stable = ScriptHost.getInstance().getContext();
		long generation = ScriptHost.getInstance().getActiveGeneration();

		Files.write(new File(root, "loader.js").toPath(),
				(canonicalArea() + "defineArea({id:'overlap-area',"
						+ "name:'Overlap',"
						+ "bounds:{minX:2830,minY:9630,maxX:2850,"
						+ "maxY:9635,plane:0},"
						+ "npcs:[],"
						+ "objects:[{key:'clash',objectId:409,x:2850,"
						+ "y:9635}],"
						+ "shops:[],quests:[],bosses:[],raids:[]});")
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();

		assertSame(stable, ScriptHost.getInstance().getContext());
		assertEquals(generation,
				ScriptHost.getInstance().getActiveGeneration());
		assertEquals(1, ScriptAreaRuntime.getInstance().sessionCount());
		assertObjectAt(409, 2850, 9635);
	}

	@Test
	public void reloadWithoutAreasClosesTheGenerationWorldState()
			throws Exception {
		File root = writeLoader(canonicalArea());
		setContentDir(root);
		ScriptHost.getInstance().reload();
		assertTrue(hasNpcAt(153, 2830, 9630));

		Files.write(new File(root, "loader.js").toPath(),
				"onCommand('no-areas', function () {});"
						.getBytes(StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();

		assertEquals(0, ScriptAreaRuntime.getInstance().sessionCount());
		assertEquals(0, ScriptAreaRuntime.getInstance().selectedAreaCount());
		assertFalse("the retired NPC allocation must be despawned",
				hasNpcAt(153, 2830, 9630));
		assertNull(WorldObjectService.getInstance().resolve(2850, 9635, 0));
		assertNull(WorldObjectService.getInstance().resolve(2835, 9640, 0));
	}

	@Test
	public void rejectedReloadKeepsTheRunningAreaIntact() throws Exception {
		File root = writeLoader(canonicalArea());
		setContentDir(root);
		ScriptHost.getInstance().reload();
		Context stable = ScriptHost.getInstance().getContext();
		long generation = ScriptHost.getInstance().getActiveGeneration();

		Files.write(new File(root, "loader.js").toPath(),
				"this is not valid javascript !!!".getBytes(
						StandardCharsets.UTF_8));
		ScriptHost.getInstance().reload();

		assertSame(stable, ScriptHost.getInstance().getContext());
		assertEquals(generation,
				ScriptHost.getInstance().getActiveGeneration());
		assertEquals(1, ScriptAreaRuntime.getInstance().sessionCount());
		assertTrue(hasNpcAt(153, 2830, 9630));
		assertObjectAt(409, 2850, 9635);
	}

	private static void assertSame(Object expected, Object actual) {
		org.junit.Assert.assertSame(expected, actual);
	}

	private static void inject(String stage) {
		ScriptAreaRuntime runtime = ScriptAreaRuntime.getInstance();
		if ("prepare".equals(stage)) {
			runtime.failNextPrepare();
		} else if ("reserve".equals(stage)) {
			runtime.failNextReserve();
		} else if ("applyShadow".equals(stage)) {
			runtime.failNextApplyShadow();
		} else if ("verifyShadow".equals(stage)) {
			runtime.failNextVerifyShadow();
		} else if ("retire".equals(stage)) {
			runtime.failNextRetire();
		} else if ("verifyRetirement".equals(stage)) {
			runtime.failNextVerifyRetirement();
		} else if ("checkpoint".equals(stage)) {
			runtime.failNextCheckpoint();
		} else {
			fail("unknown stage: " + stage);
		}
	}

	private static long allocationOf(int npcType, int x, int y) {
		for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
			com.rs2.game.npcs.Npc npc = NpcHandler.npcs[i];
			if (npc != null && npc.npcType == npcType && npc.absX == x
					&& npc.absY == y) {
				return npc.allocationToken();
			}
		}
		return 0L;
	}

	private static boolean hasNpcAt(int npcType, int x, int y) {
		return allocationOf(npcType, x, y) != 0L;
	}

	private static int countNpcsOfTypeAt(int npcType, int x, int y) {
		int count = 0;
		for (int i = 1; i < NpcHandler.MAX_NPCS; i++) {
			com.rs2.game.npcs.Npc npc = NpcHandler.npcs[i];
			if (npc != null && npc.npcType == npcType && npc.absX == x
					&& npc.absY == y) {
				count++;
			}
		}
		return count;
	}

	private static void assertObjectAt(int objectId, int x, int y) {
		ResolvedWorldObject resolved = WorldObjectService.getInstance()
				.resolve(x, y, 0);
		assertNotNull("object " + objectId + " must resolve at " + x + ","
				+ y, resolved);
		assertEquals(objectId, resolved.getObjectId());
	}

	private static Integer resolveId(int x, int y) {
		ResolvedWorldObject resolved = WorldObjectService.getInstance()
				.resolve(x, y, 0);
		return resolved == null ? null : Integer.valueOf(resolved.getObjectId());
	}

	private static File writeLoader(String content) throws Exception {
		Path root = Files.createTempDirectory("script-area-runtime");
		Files.write(root.resolve("loader.js"),
				content.getBytes(StandardCharsets.UTF_8));
		return root.toFile();
	}

	private static String canonicalArea() {
		return "defineArea({id:'runtime-area',name:'Runtime Area',"
				+ "bounds:{minX:2830,minY:9630,maxX:2850,maxY:9640,plane:0},"
				+ "npcs:[{key:'guard-1',npcId:153,x:2830,y:9630,"
				+ "respawnTicks:20},"
				+ "{key:'guard-2',npcId:153,x:2840,y:9640}],"
				+ "objects:[{key:'altar',objectId:409,x:2850,y:9635},"
				+ "{key:'chest',objectId:2213,x:2835,y:9640}],"
				+ "shops:[],quests:[],bosses:[],raids:[]});";
	}

	private void setContentDir(File contentDir) {
		if (previousContentDir == null) {
			System.clearProperty("singlescape.contentDir");
		}
		System.setProperty("singlescape.contentDir",
				contentDir.getAbsolutePath());
	}

	private static void setDefinitions(Class<?> definitionType, Object value)
			throws Exception {
		Field field = definitionType.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, value);
	}

	private static final class ScriptEncounterServiceFixture {
		static void install(long seed) {
			com.rs2.script.world.ScriptEncounterService
					.installForTesting(seed);
		}
	}

	private static final class RegionBackup {
		private final Object value;

		private RegionBackup(Object value) {
			this.value = value;
		}

		static RegionBackup capture() throws Exception {
			Field field = com.rs2.world.clip.RegionFactory.class
					.getDeclaredField("regions");
			field.setAccessible(true);
			return new RegionBackup(field.get(null));
		}

		void restore() throws Exception {
			Field field = com.rs2.world.clip.RegionFactory.class
					.getDeclaredField("regions");
			field.setAccessible(true);
			field.set(null, value);
		}
	}

}
