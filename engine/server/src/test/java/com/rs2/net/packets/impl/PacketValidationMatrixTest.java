package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import java.util.concurrent.atomic.AtomicInteger;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.game.objects.Objects;
import com.rs2.net.packets.PacketHandler;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.registries.InteractionHandlerRegistry;
import com.rs2.script.registries.ItemHandlerRegistry;
import com.rs2.script.registries.ObjectHandlerRegistry;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.world.ResolvedWorldObject;
import com.rs2.world.WorldObjectResolver;
import com.rs2.world.WorldObjectService;

public class PacketValidationMatrixTest {

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
	public void invalidRegisteredAndUnregisteredPacketsHaveNoSideEffects() {
		ScriptRuntimeTestFixture.publish(context, () -> {
			InteractionHandlerRegistry.putButton(42001, counter("buttonCalls"));
			InteractionHandlerRegistry.putItemOnGroundItem(
					InteractionPacketTestSupport.ITEM,
					InteractionPacketTestSupport.GROUND_ITEM,
					counter("groundCalls"));
			InteractionHandlerRegistry.putItemOnPlayer(
					InteractionPacketTestSupport.ITEM, counter("playerCalls"));
			InteractionHandlerRegistry.putMagicOnItem(50,
					InteractionPacketTestSupport.ITEM, counter("magicItemCalls"));
			InteractionHandlerRegistry.putMagicOnObject(50,
					InteractionPacketTestSupport.OBJECT,
					counter("magicObjectCalls"));
		});
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		support.livePlayer(2);
		player.playerItems[0] = InteractionPacketTestSupport.ITEM + 1;
		player.playerItemsN[0] = 1;
		support.addGroundItem(player, InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);

		PacketHandler.processPacket(player, PacketFixtures.rawPacket(185, 42));
		PacketHandler.processPacket(player, PacketFixtures.itemOnGroundItem(
				InteractionPacketTestSupport.ITEM,
				InteractionPacketTestSupport.GROUND_ITEM, 1,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		PacketHandler.processPacket(player, PacketFixtures.itemOnPlayer(
				com.rs2.game.players.PlayerHandler.players.length, 0));
		PacketHandler.processPacket(player, PacketFixtures.magicOnItem(
				50, InteractionPacketTestSupport.ITEM, 1));
		PacketHandler.processPacket(player, PacketFixtures.magicOnObject(
				50, InteractionPacketTestSupport.OBJECT,
				InteractionPacketTestSupport.X + 20,
				InteractionPacketTestSupport.Y));

		assertFalse(hasBinding("buttonCalls"));
		assertFalse(hasBinding("groundCalls"));
		assertFalse(hasBinding("playerCalls"));
		assertFalse(hasBinding("magicItemCalls"));
		assertFalse(hasBinding("magicObjectCalls"));
		assertEquals(0, player.endedTasks);

		ScriptRuntimeTestFixture.publishEmpty(context);
		PacketHandler.processPacket(player, PacketFixtures.itemOnPlayer(0, 30));
		assertEquals(0, player.endedTasks);
	}

	@Test
	public void dynamicObjectMasksCacheAndLocksPrecedeDispatch() {
		ScriptRuntimeTestFixture.publish(context, () -> {
			InteractionHandlerRegistry.putButton(42001, counter("lockedButton"));
			InteractionHandlerRegistry.putMagicOnObject(50,
					InteractionPacketTestSupport.OBJECT,
					counter("maskedObject"));
		});
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		GameEngine.objectHandler.addObject(new Objects(
				InteractionPacketTestSupport.OTHER_OBJECT,
				InteractionPacketTestSupport.X + 1,
				InteractionPacketTestSupport.Y, 0, 0, 10, 0));

		ScriptInteractionGate.setActionLockedForTest(player, true);
		PacketHandler.processPacket(player, PacketFixtures.actionButton(42001));
		PacketHandler.processPacket(player, PacketFixtures.magicOnObject(
				50, InteractionPacketTestSupport.OBJECT,
				InteractionPacketTestSupport.X + 1,
				InteractionPacketTestSupport.Y));

		assertFalse(hasBinding("lockedButton"));
		assertFalse(hasBinding("maskedObject"));
		assertFalse(player.hasFaceUpdate());
	}

	@Test
	public void objectResolverUsesTimedThenGlobalThenCachePrecedence() {
		assertEquals(ResolvedWorldObject.Layer.CACHE,
				WorldObjectResolver.resolve(
						InteractionPacketTestSupport.X + 1,
						InteractionPacketTestSupport.Y, 0).getLayer());
		GameEngine.objectHandler.addObject(new Objects(
					InteractionPacketTestSupport.OTHER_OBJECT,
					InteractionPacketTestSupport.X + 1,
					InteractionPacketTestSupport.Y, 0, 1, 4, 0));
		assertEquals(ResolvedWorldObject.Layer.GLOBAL,
				WorldObjectResolver.resolve(
						InteractionPacketTestSupport.X + 1,
						InteractionPacketTestSupport.Y, 0).getLayer());
		new com.rs2.game.objects.Object(
				InteractionPacketTestSupport.OBJECT,
				InteractionPacketTestSupport.X + 1,
				InteractionPacketTestSupport.Y, 0, 2, 10, -1, 20);
		assertEquals(ResolvedWorldObject.Layer.TIMED,
				WorldObjectResolver.resolve(
						InteractionPacketTestSupport.X + 1,
						InteractionPacketTestSupport.Y, 0).getLayer());
	}

	@Test
	public void productionObjectPacketsUseSecondarySlotAndRejectAmbiguity() {
		ScriptRuntimeTestFixture.publish(context, () -> {
			ObjectHandlerRegistry.put(InteractionPacketTestSupport.OBJECT, "first",
					context.eval("js", "c=>globalThis.clickType=c.target.getType()"));
			ItemHandlerRegistry.putItemOnObject(InteractionPacketTestSupport.ITEM,
					InteractionPacketTestSupport.OBJECT,
					context.eval("js", "c=>globalThis.itemType=c.target.getType()"));
			InteractionHandlerRegistry.putMagicOnObject(50,
					InteractionPacketTestSupport.OBJECT,
					context.eval("js", "c=>globalThis.magicType=c.target.getType()"));
		});
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		player.playerItems[0] = InteractionPacketTestSupport.ITEM + 1;
		player.playerItemsN[0] = 1;
		WorldObjectService service = WorldObjectService.getInstance();
		service.applyCacheMutation(new Objects(InteractionPacketTestSupport.OBJECT,
				InteractionPacketTestSupport.X, InteractionPacketTestSupport.Y,
				0, 1, 0, 0));

		PacketHandler.processPacket(player, PacketFixtures.firstObjectClick(
				InteractionPacketTestSupport.OBJECT, InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		PacketHandler.processPacket(player, PacketFixtures.itemOnObject(
				InteractionPacketTestSupport.ITEM, 0,
				InteractionPacketTestSupport.OBJECT, InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		PacketHandler.processPacket(player, PacketFixtures.magicOnObject(50,
				InteractionPacketTestSupport.OBJECT, InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));

		assertEquals(0, context.getBindings("js").getMember("clickType").asInt());
		assertEquals(0, context.getBindings("js").getMember("itemType").asInt());
		assertEquals(0, context.getBindings("js").getMember("magicType").asInt());
		context.eval("js", "globalThis.clickType=9;globalThis.itemType=9;"
				+ "globalThis.magicType=9");
		service.applyCacheMutation(new Objects(InteractionPacketTestSupport.OBJECT,
				InteractionPacketTestSupport.X, InteractionPacketTestSupport.Y,
				0, 0, 10, 0));
		player.clickDelay = 0L;
		PacketHandler.processPacket(player, PacketFixtures.firstObjectClick(
				InteractionPacketTestSupport.OBJECT, InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		PacketHandler.processPacket(player, PacketFixtures.itemOnObject(
				InteractionPacketTestSupport.ITEM, 0,
				InteractionPacketTestSupport.OBJECT, InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));
		PacketHandler.processPacket(player, PacketFixtures.magicOnObject(50,
				InteractionPacketTestSupport.OBJECT, InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y));

		assertEquals(9, context.getBindings("js").getMember("clickType").asInt());
		assertEquals(9, context.getBindings("js").getMember("itemType").asInt());
		assertEquals(9, context.getBindings("js").getMember("magicType").asInt());
	}

	@Test
	public void authenticatedDialogueOptionIsTheOnlyButtonLockEscape() {
		ScriptRuntimeTestFixture.publishEmpty(context);
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		AtomicInteger selected = new AtomicInteger(-1);
		ScriptedPlayer scripted = new ScriptedPlayer(player);
		ScriptEncounterService.getInstance().armDialogueOption(
				player, scripted.generation(), scripted.facadeEpoch(),
				2, selected::set);
		ScriptInteractionGate.setActionLockedForTest(player, true);

		PacketHandler.processPacket(player, PacketFixtures.actionButton(9157));

		assertEquals(0, selected.get());
		assertEquals(null, player.pendingScriptOption);
	}

	@Test
	public void validUnmatchedItemOnPlayerKeepsLegacyContinuation() {
		ScriptRuntimeTestFixture.publishEmpty(context);
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		support.livePlayer(2);
		player.playerItems[0] = InteractionPacketTestSupport.ITEM + 1;
		player.playerItemsN[0] = 1;

		PacketHandler.processPacket(player, PacketFixtures.itemOnPlayer(2, 0));

		assertEquals(1, player.endedTasks);
	}

	private org.graalvm.polyglot.Value counter(String name) {
		return context.eval("js", "()=>globalThis." + name
				+ "=(globalThis." + name + "||0)+1");
	}

	private boolean hasBinding(String name) {
		return context.getBindings("js").hasMember(name);
	}
}
