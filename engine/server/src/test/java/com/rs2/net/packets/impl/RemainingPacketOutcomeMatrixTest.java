package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.GameEngine;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketHandler;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.script.registries.InteractionHandlerRegistry;

/**
 * Runs every authority outcome through production mappings and wire bytes.
 */
public class RemainingPacketOutcomeMatrixTest {

	private enum Route {
		BUTTON,
		ITEM_ON_GROUND,
		ITEM_ON_PLAYER,
		MAGIC_ON_ITEM,
		MAGIC_ON_OBJECT
	}

	private enum Outcome {
		INVALID_REGISTERED,
		INVALID_UNREGISTERED,
		MATCHED_SUCCESS,
		MATCHED_THROW,
		VALID_UNMATCHED,
		LOCKED_REGISTERED,
		LOCKED_UNREGISTERED
	}

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
	public void everyRouteHonorsEveryAuthorityOutcome() {
		for (Route route : Route.values()) {
			for (Outcome outcome : Outcome.values()) {
				run(route, outcome);
			}
		}
	}

	private void run(Route route, Outcome outcome) {
		GameEngine.itemHandler.items.clear();
		InteractionPacketTestSupport.TestPlayer player = support.livePlayer(1);
		support.livePlayer(2);
		player.playerItems[0] = InteractionPacketTestSupport.ITEM + 1;
		player.playerItemsN[0] = 1;
		support.addGroundItem(player,
				InteractionPacketTestSupport.GROUND_ITEM,
				InteractionPacketTestSupport.X,
				InteractionPacketTestSupport.Y, 100);
		context.eval("js", "globalThis.calls=0");

		boolean registered = outcome == Outcome.INVALID_REGISTERED
				|| outcome == Outcome.MATCHED_SUCCESS
				|| outcome == Outcome.MATCHED_THROW
				|| outcome == Outcome.LOCKED_REGISTERED;
		if (registered) {
			Value callback = outcome == Outcome.MATCHED_THROW
					? context.eval("js",
							"()=>{globalThis.calls++;throw new Error('expected')}")
					: context.eval("js", "()=>globalThis.calls++");
			ScriptRuntimeTestFixture.publish(context,
					() -> register(route, callback));
		} else {
			ScriptRuntimeTestFixture.publishEmpty(context);
		}

		if (outcome == Outcome.LOCKED_REGISTERED
				|| outcome == Outcome.LOCKED_UNREGISTERED) {
			ScriptInteractionGate.setActionLockedForTest(player, true);
		}
		boolean invalid = outcome == Outcome.INVALID_REGISTERED
				|| outcome == Outcome.INVALID_UNREGISTERED;
		PacketHandler.processPacket(player, packet(route, invalid));

		int expectedCalls = outcome == Outcome.MATCHED_SUCCESS
				|| outcome == Outcome.MATCHED_THROW ? 1 : 0;
		assertEquals(label(route, outcome), expectedCalls,
				context.eval("js", "globalThis.calls").asInt());

		boolean legacy = outcome == Outcome.VALID_UNMATCHED;
		assertEquals(label(route, outcome), legacy && route != Route.BUTTON
				&& route != Route.MAGIC_ON_OBJECT ? 1 : 0,
				player.endedTasks);
		assertEquals(label(route, outcome),
				legacy && (route == Route.BUTTON
						|| route == Route.MAGIC_ON_ITEM) ? 1 : 0,
				player.postedEvents);
		assertEquals(label(route, outcome),
				legacy && route == Route.BUTTON, player.mouseButton);
		assertEquals(label(route, outcome),
				legacy && route == Route.MAGIC_ON_OBJECT
						? 2 * (InteractionPacketTestSupport.X + 1) + 1 : -1,
				player.FocusPointX);
		assertEquals(label(route, outcome), 1,
				player.getItemAssistant().getItemAmount(
						InteractionPacketTestSupport.ITEM));
		assertTrue(label(route, outcome),
				GameEngine.itemHandler.itemExists(
						InteractionPacketTestSupport.GROUND_ITEM,
						InteractionPacketTestSupport.X,
						InteractionPacketTestSupport.Y));
		ScriptInteractionGate.setActionLockedForTest(player, false);
	}

	private void register(Route route, Value callback) {
		switch (route) {
		case BUTTON:
			InteractionHandlerRegistry.putButton(74176, callback);
			break;
		case ITEM_ON_GROUND:
			InteractionHandlerRegistry.putItemOnGroundItem(
					InteractionPacketTestSupport.ITEM,
					InteractionPacketTestSupport.GROUND_ITEM, callback);
			break;
		case ITEM_ON_PLAYER:
			InteractionHandlerRegistry.putItemOnPlayer(
					InteractionPacketTestSupport.ITEM, callback);
			break;
		case MAGIC_ON_ITEM:
			InteractionHandlerRegistry.putMagicOnItem(50,
					InteractionPacketTestSupport.ITEM, callback);
			break;
		case MAGIC_ON_OBJECT:
			InteractionHandlerRegistry.putMagicOnObject(50,
					InteractionPacketTestSupport.OBJECT, callback);
			break;
		default:
			throw new AssertionError(route);
		}
	}

	private Packet packet(Route route, boolean invalid) {
		switch (route) {
		case BUTTON:
			return invalid ? PacketFixtures.rawPacket(185, 74)
					: PacketFixtures.actionButton(74176);
		case ITEM_ON_GROUND:
			return PacketFixtures.itemOnGroundItem(
					InteractionPacketTestSupport.ITEM,
					InteractionPacketTestSupport.GROUND_ITEM,
					invalid ? 1 : 0, InteractionPacketTestSupport.X,
					InteractionPacketTestSupport.Y);
		case ITEM_ON_PLAYER:
			return PacketFixtures.itemOnPlayer(invalid ? 1 : 2, 0);
		case MAGIC_ON_ITEM:
			return PacketFixtures.magicOnItem(50,
					InteractionPacketTestSupport.ITEM, invalid ? 1 : 0);
		case MAGIC_ON_OBJECT:
			return PacketFixtures.magicOnObject(50,
					InteractionPacketTestSupport.OBJECT,
					invalid ? InteractionPacketTestSupport.X + 20
							: InteractionPacketTestSupport.X + 1,
					InteractionPacketTestSupport.Y);
		default:
			throw new AssertionError(route);
		}
	}

	private static String label(Route route, Outcome outcome) {
		return route + " / " + outcome;
	}
}
