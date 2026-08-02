package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.apollo.cache.def.ItemDefinition;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.event.Event;
import com.rs2.event.impl.ItemFirstClickEvent;
import com.rs2.event.impl.ItemOnItemEvent;
import com.rs2.event.impl.ItemOnNpcEvent;
import com.rs2.event.impl.ItemOnObjectEvent;
import com.rs2.event.impl.ItemSecondClickEvent;
import com.rs2.event.impl.ItemThirdClickEvent;
import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.objects.Objects;
import com.rs2.game.players.Player;
import com.rs2.script.registries.ItemHandlerRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.world.clip.Region;
import com.rs2.world.clip.RegionFactory;

/**
 * Exercises production packet decoding, validation, authority, and fallback
 * rather than calling only the package-private dispatch helpers.
 */
public class ItemPacketBehaviorTest {

	private static final int ITEM_ID = 1000;
	private static final int TARGET_ITEM_ID = 1001;
	private static final int OBJECT_ID = 2000;
	private static final int NPC_ID = 3000;
	private static final int NPC_INDEX = 10;
	private static final int WORLD_X = 3200;
	private static final int WORLD_Y = 3200;

	private Context context;
	private ItemDefinition[] previousDefinitions;
	private Region[] previousRegions;
	private Npc previousNpc;

	@Before
	public void setUp() throws Exception {
		resetRegistries();
		context = Context.create("js");

		Field definitions = ItemDefinition.class.getDeclaredField("definitions");
		definitions.setAccessible(true);
		previousDefinitions = (ItemDefinition[]) definitions.get(null);
		ItemDefinition[] testDefinitions = new ItemDefinition[TARGET_ITEM_ID + 1];
		testDefinitions[ITEM_ID] = definition(ITEM_ID);
		testDefinitions[TARGET_ITEM_ID] = definition(TARGET_ITEM_ID);
		definitions.set(null, testDefinitions);

		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		previousRegions = (Region[]) regions.get(null);
		Region testRegion = new Region(Region.getRegionId(WORLD_X, WORLD_Y), false);
		Field realObjects = Region.class.getDeclaredField("realObjects");
		realObjects.setAccessible(true);
		@SuppressWarnings("unchecked")
		List<Objects> objects = (List<Objects>) realObjects.get(testRegion);
		objects.add(new Objects(OBJECT_ID, WORLD_X + 1, WORLD_Y, 0, 0, 10, 0));
		regions.set(null, new Region[] {testRegion});

		previousNpc = NpcHandler.npcs[NPC_INDEX];
		Npc npc = new Npc(NPC_INDEX, NPC_ID);
		npc.absX = WORLD_X + 1;
		npc.absY = WORLD_Y;
		npc.heightLevel = 0;
		NpcHandler.npcs[NPC_INDEX] = npc;
	}

	@After
	public void tearDown() throws Exception {
		resetRegistries();
		if (context != null) {
			context.close();
		}
		NpcHandler.npcs[NPC_INDEX] = previousNpc;

		Field definitions = ItemDefinition.class.getDeclaredField("definitions");
		definitions.setAccessible(true);
		definitions.set(null, previousDefinitions);
		Field regions = RegionFactory.class.getDeclaredField("regions");
		regions.setAccessible(true);
		regions.set(null, previousRegions);
	}

	@Test
	public void ordinalItemPacketsHonorExactHandlersAndUnmatchedEvents() {
		publish(() -> {
			ItemHandlerRegistry.putItem(ITEM_ID, "first", counter("firstCalls"));
			ItemHandlerRegistry.putItem(ITEM_ID, "second", counter("secondCalls"));
			ItemHandlerRegistry.putItem(ITEM_ID, "third", counter("thirdCalls"));
		});

		RecordingPlayer first = playerWithItem(ITEM_ID, 3);
		new ClickItem().processPacket(first, PacketFixtures.firstItemClick(ITEM_ID, 3));
		assertEquals(1, bindingInt("firstCalls"));
		assertEquals(0, first.countEvents(ItemFirstClickEvent.class));

		RecordingPlayer second = playerWithItem(ITEM_ID, 4);
		new ItemClick2().processPacket(second, PacketFixtures.secondItemClick(ITEM_ID, 4));
		assertEquals(1, bindingInt("secondCalls"));
		assertEquals(0, second.countEvents(ItemSecondClickEvent.class));

		RecordingPlayer third = playerWithItem(ITEM_ID, 5);
		new ItemClick3().processPacket(third, PacketFixtures.thirdItemClick(ITEM_ID, 5));
		assertEquals(1, bindingInt("thirdCalls"));
		assertEquals(0, third.countEvents(ItemThirdClickEvent.class));

		clearRegistrations();
		RecordingPlayer unmatchedFirst = playerWithItem(ITEM_ID, 3);
		new ClickItem().processPacket(unmatchedFirst,
				PacketFixtures.firstItemClick(ITEM_ID, 3));
		assertEquals(1, unmatchedFirst.countEvents(ItemFirstClickEvent.class));

		RecordingPlayer unmatchedSecond = playerWithItem(ITEM_ID, 4);
		new ItemClick2().processPacket(unmatchedSecond,
				PacketFixtures.secondItemClick(ITEM_ID, 4));
		assertEquals(1, unmatchedSecond.countEvents(ItemSecondClickEvent.class));

		RecordingPlayer unmatchedThird = playerWithItem(ITEM_ID, 5);
		new ItemClick3().processPacket(unmatchedThird,
				PacketFixtures.thirdItemClick(ITEM_ID, 5));
		assertEquals(1, unmatchedThird.countEvents(ItemThirdClickEvent.class));
	}

	@Test
	public void thirdClickBlockingPrecedesScriptsAndLegacyEvents() {
		publish(() -> ItemHandlerRegistry.putItem(
				ITEM_ID, "third", counter("blockedCalls")));
		RecordingPlayer registered = playerWithItem(ITEM_ID, 0);
		registered.duelStatus = 1;
		new ItemClick3().processPacket(registered,
				PacketFixtures.thirdItemClick(ITEM_ID, 0));
		assertFalse(hasBinding("blockedCalls"));
		assertEquals(0, registered.countEvents(ItemThirdClickEvent.class));
		assertEquals(1, registered.getItemAssistant().getItemAmount(ITEM_ID));

		clearRegistrations();
		RecordingPlayer unmatched = playerWithItem(ITEM_ID, 0);
		unmatched.tradeStatus = 1;
		new ItemClick3().processPacket(unmatched,
				PacketFixtures.thirdItemClick(ITEM_ID, 0));
		assertEquals(0, unmatched.countEvents(ItemThirdClickEvent.class));
		assertEquals(1, unmatched.getItemAssistant().getItemAmount(ITEM_ID));
	}

	@Test
	public void throwingAndInvalidOrdinalClicksAreConsumedOrRejected() {
		publish(() -> ItemHandlerRegistry.putItem(ITEM_ID, "third",
				context.eval("js", "(function () { throw new Error('boom'); })")));
		RecordingPlayer throwing = playerWithItem(ITEM_ID, 2);
		new ItemClick3().processPacket(throwing,
				PacketFixtures.thirdItemClick(ITEM_ID, 2));
		assertEquals(0, throwing.countEvents(ItemThirdClickEvent.class));

		publish(() -> ItemHandlerRegistry.putItem(
				ITEM_ID, "first", counter("invalidClickCalls")));
		RecordingPlayer invalid = playerWithItem(TARGET_ITEM_ID, 2);
		new ClickItem().processPacket(invalid,
				PacketFixtures.firstItemClick(ITEM_ID, 2));
		assertFalse(hasBinding("invalidClickCalls"));
		assertEquals(0, invalid.countEvents(ItemFirstClickEvent.class));
	}

	@Test
	public void itemOnItemPacketPreservesDirectionAndUnmatchedFallbackEvents() {
		publish(() -> ItemHandlerRegistry.putItemOnItem(
				TARGET_ITEM_ID, ITEM_ID, context.eval("js",
						"(function (ctx) { globalThis.pairOrder = "
								+ "ctx.usedItem.getId() + ':' + ctx.usedSlot + ':' + "
								+ "ctx.targetItem.getId() + ':' + ctx.targetSlot; })")));
		RecordingPlayer matched = playerWithPair(ITEM_ID, 1, TARGET_ITEM_ID, 2);
		new ItemOnItem().processPacket(matched, PacketFixtures.itemOnItem(1, 2));
		assertEquals("1000:1:1001:2",
				context.getBindings("js").getMember("pairOrder").asString());
		assertEquals(0, matched.countEvents(ItemOnItemEvent.class));

		clearRegistrations();
		RecordingPlayer unmatched = playerWithPair(ITEM_ID, 1, TARGET_ITEM_ID, 2);
		new ItemOnItem().processPacket(unmatched, PacketFixtures.itemOnItem(1, 2));
		assertEquals(2, unmatched.countEvents(ItemOnItemEvent.class));

		publish(() -> ItemHandlerRegistry.putItemOnItem(
				ITEM_ID, TARGET_ITEM_ID, counter("invalidPairCalls")));
		RecordingPlayer invalid = playerWithPair(ITEM_ID, 1, TARGET_ITEM_ID, 2);
		new ItemOnItem().processPacket(invalid, PacketFixtures.itemOnItem(1, 30));
		assertFalse(hasBinding("invalidPairCalls"));
		assertEquals(0, invalid.countEvents(ItemOnItemEvent.class));

		publish(() -> ItemHandlerRegistry.putItemOnItem(
				ITEM_ID, TARGET_ITEM_ID, context.eval("js",
						"(function () { throw new Error('boom'); })")));
		RecordingPlayer throwing = playerWithPair(ITEM_ID, 1, TARGET_ITEM_ID, 2);
		new ItemOnItem().processPacket(throwing, PacketFixtures.itemOnItem(1, 2));
		assertEquals(0, throwing.countEvents(ItemOnItemEvent.class));
	}

	@Test
	public void itemOnObjectPacketHonorsAuthorityFallbackAndWorldValidation() {
		publish(() -> ItemHandlerRegistry.putItemOnObject(
				ITEM_ID, OBJECT_ID, counter("objectCalls")));
		RecordingPlayer matched = playerWithItem(ITEM_ID, 0);
		new ItemOnObject().processPacket(matched, PacketFixtures.itemOnObject(
				ITEM_ID, 0, OBJECT_ID, WORLD_X + 1, WORLD_Y));
		assertEquals(1, bindingInt("objectCalls"));
		assertEquals(0, matched.countEvents(ItemOnObjectEvent.class));

		clearRegistrations();
		RecordingPlayer unmatched = playerWithItem(ITEM_ID, 0);
		new ItemOnObject().processPacket(unmatched, PacketFixtures.itemOnObject(
				ITEM_ID, 0, OBJECT_ID, WORLD_X + 1, WORLD_Y));
		assertEquals(1, unmatched.countEvents(ItemOnObjectEvent.class));

		publish(() -> ItemHandlerRegistry.putItemOnObject(
				ITEM_ID, OBJECT_ID, counter("invalidObjectCalls")));
		RecordingPlayer wrongSlot = playerWithItem(ITEM_ID, 0);
		new ItemOnObject().processPacket(wrongSlot, PacketFixtures.itemOnObject(
				ITEM_ID, 1, OBJECT_ID, WORLD_X + 1, WORLD_Y));
		assertFalse(hasBinding("invalidObjectCalls"));
		assertEquals(0, wrongSlot.countEvents(ItemOnObjectEvent.class));

		RecordingPlayer far = playerWithItem(ITEM_ID, 0);
		far.absX = WORLD_X - 10;
		new ItemOnObject().processPacket(far, PacketFixtures.itemOnObject(
				ITEM_ID, 0, OBJECT_ID, WORLD_X + 1, WORLD_Y));
		assertFalse(hasBinding("invalidObjectCalls"));
		assertEquals(0, far.countEvents(ItemOnObjectEvent.class));

		RecordingPlayer missing = playerWithItem(ITEM_ID, 0);
		new ItemOnObject().processPacket(missing, PacketFixtures.itemOnObject(
				ITEM_ID, 0, OBJECT_ID, WORLD_X + 2, WORLD_Y));
		assertFalse(hasBinding("invalidObjectCalls"));
		assertEquals(0, missing.countEvents(ItemOnObjectEvent.class));
	}

	@Test
	public void throwingItemOnObjectHandlerStillConsumesPacket() {
		publish(() -> ItemHandlerRegistry.putItemOnObject(
				ITEM_ID, OBJECT_ID, context.eval("js",
						"(function () { throw new Error('boom'); })")));
		RecordingPlayer player = playerWithItem(ITEM_ID, 0);
		new ItemOnObject().processPacket(player, PacketFixtures.itemOnObject(
				ITEM_ID, 0, OBJECT_ID, WORLD_X + 1, WORLD_Y));
		assertEquals(0, player.countEvents(ItemOnObjectEvent.class));
	}

	@Test
	public void itemOnNpcPacketHonorsAuthorityAndValidFallback() {
		publish(() -> ItemHandlerRegistry.putItemOnNpc(
				ITEM_ID, NPC_ID, counter("npcCalls")));
		RecordingPlayer matched = playerWithItem(ITEM_ID, 0);
		new ItemOnNpc().processPacket(matched,
				PacketFixtures.itemOnNpc(ITEM_ID, 0, NPC_INDEX));
		assertEquals(1, bindingInt("npcCalls"));
		assertEquals(0, matched.countEvents(ItemOnNpcEvent.class));

		clearRegistrations();
		RecordingPlayer unmatched = playerWithItem(ITEM_ID, 0);
		new ItemOnNpc().processPacket(unmatched,
				PacketFixtures.itemOnNpc(ITEM_ID, 0, NPC_INDEX));
		assertEquals(1, unmatched.countEvents(ItemOnNpcEvent.class));

		publish(() -> ItemHandlerRegistry.putItemOnNpc(
				ITEM_ID, NPC_ID, context.eval("js",
						"(function () { throw new Error('boom'); })")));
		RecordingPlayer throwing = playerWithItem(ITEM_ID, 0);
		new ItemOnNpc().processPacket(throwing,
				PacketFixtures.itemOnNpc(ITEM_ID, 0, NPC_INDEX));
		assertEquals(0, throwing.countEvents(ItemOnNpcEvent.class));
	}

	@Test
	public void itemOnNpcPacketRejectsEntitySlotPlaneAndDistanceFailures() {
		publish(() -> ItemHandlerRegistry.putItemOnNpc(
				ITEM_ID, NPC_ID, counter("invalidNpcCalls")));

		RecordingPlayer wrongSlot = playerWithItem(ITEM_ID, 0);
		new ItemOnNpc().processPacket(wrongSlot,
				PacketFixtures.itemOnNpc(ITEM_ID, 1, NPC_INDEX));

		RecordingPlayer far = playerWithItem(ITEM_ID, 0);
		far.absX = WORLD_X - 10;
		new ItemOnNpc().processPacket(far,
				PacketFixtures.itemOnNpc(ITEM_ID, 0, NPC_INDEX));

		RecordingPlayer wrongPlane = playerWithItem(ITEM_ID, 0);
		wrongPlane.heightLevel = 1;
		new ItemOnNpc().processPacket(wrongPlane,
				PacketFixtures.itemOnNpc(ITEM_ID, 0, NPC_INDEX));

		NpcHandler.npcs[NPC_INDEX].isDead = true;
		RecordingPlayer dead = playerWithItem(ITEM_ID, 0);
		new ItemOnNpc().processPacket(dead,
				PacketFixtures.itemOnNpc(ITEM_ID, 0, NPC_INDEX));
		NpcHandler.npcs[NPC_INDEX].isDead = false;

		RecordingPlayer invalidIndex = playerWithItem(ITEM_ID, 0);
		new ItemOnNpc().processPacket(invalidIndex,
				PacketFixtures.itemOnNpc(ITEM_ID, 0, NpcHandler.npcs.length));

		NpcHandler.npcs[NPC_INDEX] = null;
		RecordingPlayer missingNpc = playerWithItem(ITEM_ID, 0);
		new ItemOnNpc().processPacket(missingNpc,
				PacketFixtures.itemOnNpc(ITEM_ID, 0, NPC_INDEX));

		assertFalse(hasBinding("invalidNpcCalls"));
		assertEquals(0, wrongSlot.countEvents(ItemOnNpcEvent.class));
		assertEquals(0, far.countEvents(ItemOnNpcEvent.class));
		assertEquals(0, wrongPlane.countEvents(ItemOnNpcEvent.class));
		assertEquals(0, dead.countEvents(ItemOnNpcEvent.class));
		assertEquals(0, invalidIndex.countEvents(ItemOnNpcEvent.class));
		assertEquals(0, missingNpc.countEvents(ItemOnNpcEvent.class));
	}

	private Value counter(String name) {
		return context.eval("js", "(function () { globalThis." + name
				+ " = (globalThis." + name + " || 0) + 1; })");
	}

	private void publish(Runnable registrations) {
		ScriptRuntimeTestFixture.publish(context, registrations);
	}

	private void clearRegistrations() {
		ScriptRuntimeTestFixture.publishEmpty(context);
	}

	private int bindingInt(String name) {
		return context.getBindings("js").getMember(name).asInt();
	}

	private boolean hasBinding(String name) {
		return context.getBindings("js").hasMember(name);
	}

	private static RecordingPlayer playerWithItem(int itemId, int slot) {
		RecordingPlayer player = new RecordingPlayer();
		player.playerItems[slot] = itemId + 1;
		player.playerItemsN[slot] = 1;
		return player;
	}

	private static RecordingPlayer playerWithPair(int usedItem, int usedSlot,
			int targetItem, int targetSlot) {
		RecordingPlayer player = playerWithItem(usedItem, usedSlot);
		player.playerItems[targetSlot] = targetItem + 1;
		player.playerItemsN[targetSlot] = 1;
		return player;
	}

	private static ItemDefinition definition(int id) {
		ItemDefinition definition = new ItemDefinition(id);
		definition.setName("packet-test-" + id);
		return definition;
	}

	private static void resetRegistries() {
		ScriptRuntimeTestFixture.reset();
	}

	private static final class RecordingPlayer extends Player {
		private final List<Event> events = new ArrayList<>();

		private RecordingPlayer() {
			super(-1);
			absX = WORLD_X;
			absY = WORLD_Y;
			heightLevel = 0;
			tutorialProgress = 36;
			playerName = "packet-test";
			playerRights = 2;
		}

		@Override
		public <E extends Event> void post(E event) {
			events.add(event);
		}

		private int countEvents(Class<? extends Event> type) {
			int count = 0;
			for (Event event : events) {
				if (type.isInstance(event)) {
					count++;
				}
			}
			return count;
		}
	}
}
