package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.game.items.GroundItem;
import com.rs2.net.packets.PacketHandler;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.script.registries.InteractionHandlerRegistry;

public class RemainingInteractionDispatchTest {

	private InteractionPacketTestSupport support;
	private Context context;

	@Before
	public void setUp() throws Exception {
		ScriptRuntimeTestFixture.reset();
		support = new InteractionPacketTestSupport();
		context = Context.create("js");
	}

	@After
	public void tearDown() throws Exception {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
		support.restore();
	}

	@Test
	public void realMappedPacketsDispatchAllFiveExactRoutes() {
		ScriptRuntimeTestFixture.publish(context, () -> {
			InteractionHandlerRegistry.putButton(42001,
					context.eval("js", "(c)=>globalThis.button=c.buttonId"));
			InteractionHandlerRegistry.putItemOnGroundItem(
					InteractionPacketTestSupport.ITEM,
					InteractionPacketTestSupport.GROUND_ITEM,
					context.eval("js", "(c)=>globalThis.ground="
							+ "c.item.getId()+':'+c.target.id()+':'+c.slot"));
			InteractionHandlerRegistry.putItemOnPlayer(
					InteractionPacketTestSupport.ITEM,
					context.eval("js", "(c)=>globalThis.player="
							+ "c.item.getId()+':'+c.slot+':'"
							+ "+c.target.getUsername()"));
			InteractionHandlerRegistry.putMagicOnItem(50,
					InteractionPacketTestSupport.ITEM,
					context.eval("js", "(c)=>globalThis.magicItem="
							+ "c.spellId+':'+c.target.getId()+':'+c.slot"));
			InteractionHandlerRegistry.putMagicOnObject(50,
					InteractionPacketTestSupport.OBJECT,
					context.eval("js", "(c)=>globalThis.magicObject="
							+ "c.spellId+':'+c.target.getId()+':'"
							+ "+c.target.getType()"));
		});

		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		InteractionPacketTestSupport.TestPlayer target = support.livePlayer(2);
		player.playerItems[3] = InteractionPacketTestSupport.ITEM + 1;
		player.playerItemsN[3] = 1;
		GroundItem ground = support.addGroundItem(player,
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);

		PacketHandler.processPacket(player, PacketFixtures.actionButton(42001));
		PacketHandler.processPacket(player, PacketFixtures.itemOnGroundItem(
				InteractionPacketTestSupport.ITEM,
				InteractionPacketTestSupport.GROUND_ITEM, 3,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		PacketHandler.processPacket(player, PacketFixtures.itemOnPlayer(2, 3));
		PacketHandler.processPacket(player, PacketFixtures.magicOnItem(
				50, InteractionPacketTestSupport.ITEM, 3));
		PacketHandler.processPacket(player, PacketFixtures.magicOnObject(
				50, InteractionPacketTestSupport.OBJECT,
				InteractionPacketTestSupport.X + 1,
				InteractionPacketTestSupport.Y));

		assertEquals(42001, binding("button").asInt());
		assertEquals("1000:1001:3", binding("ground").asString());
		assertEquals("1000:3:" + target.playerName,
				binding("player").asString());
		assertEquals("50:1000:3", binding("magicItem").asString());
		assertEquals("50:2000:10", binding("magicObject").asString());
		assertEquals(ground, GameEngine.itemHandler.items.get(0));
		assertEquals(0, player.endedTasks);
	}

	@Test
	public void throwingExactHandlerConsumesWithoutLegacySideEffects() {
		ScriptRuntimeTestFixture.publish(context, () ->
				InteractionHandlerRegistry.putMagicOnItem(50,
						InteractionPacketTestSupport.ITEM,
						context.eval("js", "()=>{throw new Error('expected')}")));
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		player.playerItems[0] = InteractionPacketTestSupport.ITEM + 1;
		player.playerItemsN[0] = 1;

		PacketHandler.processPacket(player, PacketFixtures.magicOnItem(
				50, InteractionPacketTestSupport.ITEM, 0));

		assertEquals(0, player.endedTasks);
	}

	private org.graalvm.polyglot.Value binding(String name) {
		return context.getBindings("js").getMember(name);
	}
}
