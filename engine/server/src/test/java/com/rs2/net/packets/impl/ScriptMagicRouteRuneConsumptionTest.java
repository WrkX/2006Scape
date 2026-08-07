package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Field;

import org.apollo.cache.def.ItemDefinition;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.game.npcs.Npc;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.script.registries.InteractionHandlerRegistry;

/**
 * Regression: {@code onMagicOnNpc} / {@code onMagicOnPlayer} routes consume the
 * packet and skip legacy combat rune checks. Handlers must call
 * {@code player.getMagic().consumeRunes(spellButtonId)} themselves.
 */
public class ScriptMagicRouteRuneConsumptionTest {

	private static final int WIND_STRIKE = 1152;
	private static final int AIR_RUNE = 556;
	private static final int MIND_RUNE = 558;
	private static final int NPC_TYPE = 100;

	private InteractionPacketTestSupport support;
	private Context context;
	private InteractionPacketTestSupport.TestPlayer player;
	private InteractionPacketTestSupport.TestPlayer target;
	private Npc npc;
	private int npcSlot = -1;
	private ItemDefinition[] previousItems;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		support = new InteractionPacketTestSupport();
		context = Context.create("js");
		ensureRuneDefinitions();
		player = support.livePlayer(90);
		target = support.livePlayer(91);
		giveWindStrikeRunes(player);
		npc = spawnNpc(NPC_TYPE, InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y + 1);
		npcSlot = npc.npcId;
	}

	@After
	public void tearDown() throws Exception {
		if (npcSlot >= 0) {
			NpcHandler.npcs[npcSlot] = null;
			npcSlot = -1;
		}
		setItemDefinitions(previousItems);
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
		support.restore();
	}

	@Test
	public void magicOnNpcRouteWithoutConsumeRunesLeavesInventoryUnchanged() {
		ScriptRuntimeTestFixture.publish(context, () ->
				InteractionHandlerRegistry.putMagicOnNpc(WIND_STRIKE, NPC_TYPE,
						context.eval("js", "(c) => { globalThis.npcMagic = true; }")));
		int airBefore = itemAmount(player, AIR_RUNE);
		int mindBefore = itemAmount(player, MIND_RUNE);

		assertTrue(ClickNPC.executeScriptMagicOnNpc(
				player, WIND_STRIKE, NPC_TYPE, npc));
		assertTrue(context.getBindings("js").getMember("npcMagic").asBoolean());

		assertEquals(airBefore, itemAmount(player, AIR_RUNE));
		assertEquals(mindBefore, itemAmount(player, MIND_RUNE));
	}

	@Test
	public void magicOnPlayerRouteWithoutConsumeRunesLeavesInventoryUnchanged() {
		ScriptRuntimeTestFixture.publish(context, () ->
				InteractionHandlerRegistry.putMagicOnPlayer(WIND_STRIKE,
						context.eval("js", "(c) => { globalThis.playerMagic = true; }")));
		int airBefore = itemAmount(player, AIR_RUNE);
		int mindBefore = itemAmount(player, MIND_RUNE);

		assertTrue(AttackPlayer.executeScriptMagicOnPlayer(
				player, WIND_STRIKE, target));
		assertTrue(context.getBindings("js").getMember("playerMagic").asBoolean());

		assertEquals(airBefore, itemAmount(player, AIR_RUNE));
		assertEquals(mindBefore, itemAmount(player, MIND_RUNE));
	}

	@Test
	public void magicOnNpcHandlerCanConsumeRunesExplicitly() {
		ScriptRuntimeTestFixture.publish(context, () ->
				InteractionHandlerRegistry.putMagicOnNpc(WIND_STRIKE, NPC_TYPE,
						context.eval("js",
								"(c) => c.player.getMagic().consumeRunes("
								+ WIND_STRIKE + ")")));
		assertTrue(ClickNPC.executeScriptMagicOnNpc(
				player, WIND_STRIKE, NPC_TYPE, npc));
		assertEquals(0, itemAmount(player, AIR_RUNE));
		assertEquals(0, itemAmount(player, MIND_RUNE));
	}

	private void ensureRuneDefinitions() throws Exception {
		previousItems = ItemDefinition.getDefinitions();
		int length = Math.max(previousItems == null ? 0 : previousItems.length,
				MIND_RUNE + 1);
		ItemDefinition[] items = new ItemDefinition[length];
		if (previousItems != null) {
			System.arraycopy(previousItems, 0, items, 0, previousItems.length);
		}
		for (int id : new int[] {AIR_RUNE, MIND_RUNE}) {
			if (items[id] == null) {
				ItemDefinition definition = new ItemDefinition(id);
				definition.setStackable(true);
				items[id] = definition;
			}
		}
		setItemDefinitions(items);
	}

	private static void setItemDefinitions(ItemDefinition[] definitions)
			throws Exception {
		Field field = ItemDefinition.class.getDeclaredField("definitions");
		field.setAccessible(true);
		field.set(null, definitions);
	}

	private static void giveWindStrikeRunes(Player player) {
		player.playerItems[0] = AIR_RUNE + 1;
		player.playerItemsN[0] = 1;
		player.playerItems[1] = MIND_RUNE + 1;
		player.playerItemsN[1] = 1;
	}

	private static int itemAmount(Player player, int itemId) {
		return player.getItemAssistant().getItemAmount(itemId);
	}

	private static Npc spawnNpc(int npcType, int x, int y) {
		for (int slot = 1; slot < NpcHandler.npcs.length; slot++) {
			if (NpcHandler.npcs[slot] == null) {
				Npc spawned = new Npc(slot, npcType);
				spawned.absX = x;
				spawned.absY = y;
				spawned.makeX = x;
				spawned.makeY = y;
				spawned.heightLevel = 0;
				NpcHandler.npcs[slot] = spawned;
				return spawned;
			}
		}
		throw new IllegalStateException("no free npc slot");
	}
}
