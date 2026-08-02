package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.items.GroundItem;
import com.rs2.game.players.Player;
import com.rs2.script.drop.DropRngTransactionOwner;
import com.rs2.script.drop.DropTransaction;
import com.rs2.script.drop.GroundDeliveryPolicy;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.world.ScriptDropEntry;
import com.rs2.script.world.ScriptDropResult;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptEncounterRng;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptGroundItemHandle;

/**
 * Proves the owner-neutral drop transaction: the same literal WP6 table
 * rolls identically through the encounter adapter and a synthetic
 * non-encounter owner, every stage/verify/final-revalidation failure removes
 * every staged identity and leaves the RNG owner and visible ground state
 * exact, and the joint commit publishes identities and RNG state together.
 */
public class DropTransactionTest {

	private static final long SEED = 0x0123456789abcdefL;
	private static final Context CONTEXT = Context.create("js");

	private Player owner;
	private ScriptEncounterService service;

	@Before
	public void setUp() throws Exception {
		Wp5PlayerSupport.ensureItemDefinitions();
		service = ScriptEncounterService.installForTesting(SEED);
		service.resetForTesting();
		service.onGenerationPublished(1L);
		owner = Wp5PlayerSupport.player(91);
	}

	@After
	public void tearDown() {
		Wp5PlayerSupport.cleanup(owner);
	}

	@Test
	public void sameLiteralTableRollsIdenticallyThroughEncounterAndSyntheticOwner() {
		List<ScriptDropEntry> table = canonicalTable();
		ScriptEncounterHandle encounter = Wp5PlayerSupport.scripted(owner)
				.beginEncounter("drop-owner-neutral", 3199, 3199, 3201, 3201, 0);
		assertNotNull(encounter);
		long initial = service.rngStateForTesting(encounter.token());

		SyntheticOwner synthetic = new SyntheticOwner(initial);
		SyntheticDelivery delivery = new SyntheticDelivery(owner,
				new ScriptedPlayer(owner, 1L), 3200, 3200, 0, 200);
		List<ScriptDropResult> syntheticResults = DropTransaction.execute(
				synthetic, delivery, table, Wp5PlayerSupport.scripted(owner));

		ScriptArray encounterResults = encounter.rollDrops(
				Wp5PlayerSupport.scripted(owner), 3200, 3200, 0, 200,
				entriesValue());

		assertNotNull(syntheticResults);
		assertEquals(encounterResults.length(), syntheticResults.size());
		ScriptEncounterRng expected = new ScriptEncounterRng(initial);
		expected.nextInt(100);
		expected.nextInt(1);
		expected.nextInt(1);
		assertEquals(expected.state(), synthetic.state);
		assertEquals(service.rngStateForTesting(encounter.token()),
				synthetic.state);
		for (int index = 0; index < syntheticResults.size(); index++) {
			ScriptDropResult syntheticResult = syntheticResults.get(index);
			ScriptDropResult encounterResult =
					(ScriptDropResult) encounterResults.get(index);
			assertEquals(encounterResult.itemId(), syntheticResult.itemId());
			assertEquals(encounterResult.amount(), syntheticResult.amount());
			assertEquals(encounterResult.groundItems().length(),
					syntheticResult.groundItems().length());
			ScriptGroundItemHandle handle = (ScriptGroundItemHandle)
					syntheticResult.groundItems().get(0);
			assertTrue(!handle.isAttached());
			assertTrue(!handle.isClaimed());
		}
		assertEquals(1L, synthetic.version);
	}

	@Test
	public void stagingFailureRemovesEveryIdentityAndLeavesOwnerExact() {
		SyntheticOwner synthetic = new SyntheticOwner(12345L);
		SyntheticDelivery delivery = new SyntheticDelivery(owner,
				new ScriptedPlayer(owner, 1L), 3200, 3200, 0, 200);
		delivery.failStageAt = 0;

		assertNull(DropTransaction.execute(synthetic, delivery, canonicalTable(),
				Wp5PlayerSupport.scripted(owner)));
		assertEquals(12345L, synthetic.state);
		assertEquals(0L, synthetic.version);
		assertEquals(0, remainingIdentityCount());
	}

	@Test
	public void secondStagingFailureRemovesTheEarlierIdentityToo() {
		SyntheticOwner synthetic = new SyntheticOwner(12345L);
		SyntheticDelivery delivery = new SyntheticDelivery(owner,
				new ScriptedPlayer(owner, 1L), 3200, 3200, 0, 200);
		delivery.failStageAt = 1;

		assertNull(DropTransaction.execute(synthetic, delivery, canonicalTable(),
				Wp5PlayerSupport.scripted(owner)));
		assertEquals(12345L, synthetic.state);
		assertEquals(0L, synthetic.version);
		assertEquals(0, remainingIdentityCount());
	}

	@Test
	public void budgetFailureStagesNothingAndNeverAdvancesRng() {
		SyntheticOwner synthetic = new SyntheticOwner(12345L);
		SyntheticDelivery delivery = new SyntheticDelivery(owner,
				new ScriptedPlayer(owner, 1L), 3200, 3200, 0, 200);
		delivery.budget = 0L;

		assertNull(DropTransaction.execute(synthetic, delivery, canonicalTable(),
				Wp5PlayerSupport.scripted(owner)));
		assertEquals(12345L, synthetic.state);
		assertEquals(0L, synthetic.version);
		assertEquals(0, remainingIdentityCount());
	}

	@Test
	public void ineligibleDeliveryCommitsNothing() {
		SyntheticOwner synthetic = new SyntheticOwner(12345L);
		SyntheticDelivery delivery = new SyntheticDelivery(owner,
				new ScriptedPlayer(owner, 1L), 3200, 3200, 0, 200);
		delivery.eligible = false;

		assertNull(DropTransaction.execute(synthetic, delivery,
				singleEntryTable(), Wp5PlayerSupport.scripted(owner)));
		assertEquals(12345L, synthetic.state);
		assertEquals(0, remainingIdentityCount());
	}

	@Test
	public void verifyFailureRemovesStagedIdentitiesAndRestoresOwner() {
		SyntheticOwner synthetic = new SyntheticOwner(12345L);
		SyntheticDelivery delivery = new SyntheticDelivery(owner,
				new ScriptedPlayer(owner, 1L), 3200, 3200, 0, 200);
		delivery.failVerify = true;

		assertNull(DropTransaction.execute(synthetic, delivery,
				singleEntryTable(), Wp5PlayerSupport.scripted(owner)));
		assertEquals(12345L, synthetic.state);
		assertEquals(0L, synthetic.version);
		assertEquals(0, remainingIdentityCount());
	}

	@Test
	public void detachFailureRemovesStagedIdentitiesAndRestoresOwner() {
		SyntheticOwner synthetic = new SyntheticOwner(12345L);
		SyntheticDelivery delivery = new SyntheticDelivery(owner,
				new ScriptedPlayer(owner, 1L), 3200, 3200, 0, 200);
		delivery.failDetach = true;

		assertNull(DropTransaction.execute(synthetic, delivery,
				singleEntryTable(), Wp5PlayerSupport.scripted(owner)));
		assertEquals(12345L, synthetic.state);
		assertEquals(0L, synthetic.version);
		assertEquals(0, remainingIdentityCount());
	}

	@Test
	public void finalRevalidationDetectsInterveningOwnerVersionChange() {
		SyntheticOwner synthetic = new SyntheticOwner(12345L);
		SyntheticDelivery delivery = new SyntheticDelivery(owner,
				new ScriptedPlayer(owner, 1L), 3200, 3200, 0, 200);
		delivery.owner = synthetic;
		delivery.bumpOwnerVersionOnStage = true;

		assertNull(DropTransaction.execute(synthetic, delivery,
				singleEntryTable(), Wp5PlayerSupport.scripted(owner)));
		assertEquals(12345L, synthetic.state);
		assertEquals(0, remainingIdentityCount());
	}

	@Test
	public void unknownItemDefinitionRejectsBeforeAnyStagingOrAdvance() {
		SyntheticOwner synthetic = new SyntheticOwner(12345L);
		SyntheticDelivery delivery = new SyntheticDelivery(owner,
				new ScriptedPlayer(owner, 1L), 3200, 3200, 0, 200);
		List<ScriptDropEntry> table = new ArrayList<ScriptDropEntry>();
		table.add(new ScriptDropEntry(50000, 1, 1, 0, true));

		assertNull(DropTransaction.execute(synthetic, delivery, table,
				Wp5PlayerSupport.scripted(owner)));
		assertEquals(12345L, synthetic.state);
		assertEquals(0, remainingIdentityCount());
	}

	@Test
	public void namedDropTableResolvesAndRollsThroughARealEncounter() {
		RegistryStore.State candidate = RegistryStore.beginStaging();
		try {
			ScriptFunctions.getInstance().getDefineDropTable().accept(CONTEXT
					.eval("js", "({id:'named_bones',entries:["
							+ "{itemId:536,minAmount:1,maxAmount:1,weight:0,"
							+ "always:true},{itemId:995,minAmount:500,"
							+ "maxAmount:500,weight:100,always:false}]})"));
			ScriptHost.getInstance().publishForTesting(CONTEXT, candidate);
		} finally {
			RegistryStore.rollback(candidate);
		}

		com.rs2.script.drop.DropTableDefinition named =
				com.rs2.script.drop.DropTableRegistry.get("named_bones");
		assertNotNull(named);
		long hostGeneration = ScriptHost.getInstance().getActiveGeneration();
		ScriptedPlayer scripted = new ScriptedPlayer(owner, hostGeneration);
		ScriptEncounterHandle encounter = scripted.beginEncounter(
				"drop-named", 3199, 3199, 3201, 3201, 0);
		assertNotNull(encounter);
		long stateBefore = service.rngStateForTesting(encounter.token());

		ScriptArray results = encounter.rollDrops(scripted, 3200, 3200, 0, 200,
				entriesValueFrom(named.entries()));

		assertEquals(2, results.length());
		ScriptDropResult bones = (ScriptDropResult) results.get(0);
		assertEquals(536, bones.itemId());
		assertEquals(1, bones.amount());
		ScriptGroundItemHandle handle = (ScriptGroundItemHandle)
				bones.groundItems().get(0);
		assertTrue(!handle.isAttached());
		assertTrue(!handle.isClaimed());
		for (GroundItem identity : handle.identities()) {
			assertTrue(com.rs2.GameEngine.itemHandler.containsExact(identity));
			assertTrue(identity.isScriptPrivate());
			assertTrue(identity.isDetached());
		}
		ScriptEncounterRng expected = new ScriptEncounterRng(stateBefore);
		expected.nextInt(100);
		expected.nextInt(1);
		expected.nextInt(1);
		assertEquals(expected.state(),
				service.rngStateForTesting(encounter.token()));
	}

	private static Value entriesValueFrom(List<ScriptDropEntry> entries) {
		StringBuilder source = new StringBuilder("[");
		for (ScriptDropEntry entry : entries) {
			if (source.length() > 1) {
				source.append(',');
			}
			source.append("{itemId:").append(entry.itemId())
					.append(",minAmount:").append(entry.minAmount())
					.append(",maxAmount:").append(entry.maxAmount())
					.append(",weight:").append(entry.weight())
					.append(",always:").append(entry.always()).append('}');
		}
		source.append(']');
		return CONTEXT.eval("js", source.toString());
	}

	private int remainingIdentityCount() {
		int count = 0;
		for (GroundItem item : com.rs2.GameEngine.itemHandler.items) {
			if (item.isScriptPrivate() && item.getEncounterToken() == 4242L) {
				count++;
			}
		}
		return count;
	}

	private static List<ScriptDropEntry> canonicalTable() {
		List<ScriptDropEntry> entries = new ArrayList<ScriptDropEntry>();
		entries.add(new ScriptDropEntry(536, 1, 1, 0, true));
		entries.add(new ScriptDropEntry(995, 500, 500, 100, false));
		return entries;
	}

	private static List<ScriptDropEntry> singleEntryTable() {
		List<ScriptDropEntry> entries = new ArrayList<ScriptDropEntry>();
		entries.add(new ScriptDropEntry(536, 1, 1, 0, true));
		return entries;
	}

	private static Value entriesValue() {
		return CONTEXT.eval("js",
				"[{itemId:536,minAmount:1,maxAmount:1,weight:0,always:true},"
						+ "{itemId:995,minAmount:500,maxAmount:500,weight:100,"
						+ "always:false}]");
	}

	/** Synthetic non-encounter RNG owner. */
	static final class SyntheticOwner implements DropRngTransactionOwner {
		long state;
		long version;

		SyntheticOwner(long state) {
			this.state = state;
		}

		@Override
		public void lock() {
		}

		@Override
		public void unlock() {
		}

		@Override
		public long version() {
			return version;
		}

		@Override
		public long state() {
			return state;
		}

		@Override
		public void publishState(long nextState) {
			state = nextState;
			version++;
		}
	}

	/** Synthetic delivery policy staging real invisible ground identities. */
	static final class SyntheticDelivery implements GroundDeliveryPolicy {
		private final Player player;
		private final ScriptedPlayer recipient;
		private final int x;
		private final int y;
		private final int plane;
		private final int privateTicks;
		SyntheticOwner owner;
		boolean eligible = true;
		long budget = 128;
		int failStageAt = -1;
		boolean failVerify;
		boolean failDetach;
		boolean bumpOwnerVersionOnStage;
		final List<ScriptGroundItemHandle> staged = new ArrayList<>();
		private int stageIndex;

		SyntheticDelivery(Player player, ScriptedPlayer recipient, int x,
				int y, int plane, int privateTicks) {
			this.player = player;
			this.recipient = recipient;
			this.x = x;
			this.y = y;
			this.plane = plane;
			this.privateTicks = privateTicks;
		}

		@Override
		public boolean eligible() {
			return eligible;
		}

		@Override
		public int x() {
			return x;
		}

		@Override
		public int y() {
			return y;
		}

		@Override
		public int plane() {
			return plane;
		}

		@Override
		public boolean isPrivate() {
			return true;
		}

		@Override
		public int privateTicks() {
			return privateTicks;
		}

		@Override
		public long identityBudgetRemaining() {
			return budget;
		}

		@Override
		public ScriptGroundItemHandle stage(ScriptedPlayer ignored,
				int itemId, int amount) {
			if (stageIndex == failStageAt) {
				throw new IllegalStateException("injected staging failure");
			}
			ScriptGroundItemHandle handle = com.rs2.GameEngine.itemHandler
					.createScriptGroundItems(player, 4242L, itemId, amount, x,
							y, plane, 0);
			stageIndex++;
			if (handle != null) {
				staged.add(handle);
			}
			if (bumpOwnerVersionOnStage && owner != null) {
				owner.version++;
			}
			return handle;
		}

		@Override
		public void verifyStaged() {
			if (failVerify) {
				throw new IllegalStateException("injected verify failure");
			}
		}

		@Override
		public boolean detach(List<ScriptGroundItemHandle> handles) {
			if (failDetach) {
				return false;
			}
			return com.rs2.GameEngine.itemHandler.detachExact(
					flat(handles), privateTicks);
		}

		@Override
		public void publish(List<ScriptGroundItemHandle> handles) {
			// Nothing to count in the synthetic world.
		}

		@Override
		public void removeExact(List<ScriptGroundItemHandle> handles) {
			if (!handles.isEmpty()) {
				com.rs2.GameEngine.itemHandler.removeExact(flat(handles));
			}
			staged.clear();
		}

		private static List<GroundItem> flat(
				List<ScriptGroundItemHandle> handles) {
			List<GroundItem> identities = new ArrayList<GroundItem>();
			for (ScriptGroundItemHandle handle : handles) {
				identities.addAll(handle.identities());
			}
			return identities;
		}
	}

}
