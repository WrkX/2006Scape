package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.rs2.game.npcs.Npc;
import com.rs2.game.players.Player;
import com.rs2.script.registries.ItemHandlerRegistry;
import com.rs2.script.ScriptRuntimeTestFixture;

public class ItemDispatchTest {

	private Context context;

	@After
	public void cleanUp() {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void exactItemClicksAreAuthoritativeAndMissingClicksFallThrough() {
		context = Context.create("js");
		ScriptRuntimeTestFixture.publish(context, () ->
				ItemHandlerRegistry.putItem(100, "first", context.eval("js",
						"(function (ctx) { globalThis.slot = ctx.slot; })")));
		Player player = new Player(-1) { };

		assertTrue(ClickItem.executeScriptItemClick(player, 100, 4, "first"));
		assertEquals(4, context.getBindings("js").getMember("slot").asInt());
		assertFalse(ClickItem.executeScriptItemClick(player, 100, 4, "second"));
	}

	@Test
	public void throwingItemHandlerStillConsumesDispatch() {
		context = Context.create("js");
		ScriptRuntimeTestFixture.publish(context, () ->
				ItemHandlerRegistry.putItem(100, "third", context.eval("js",
						"(function () { throw new Error('boom'); })")));

		assertTrue(ClickItem.executeScriptItemClick(new Player(-1) { }, 100, 0, "third"));
	}

	@Test
	public void symmetricPairLookupPreservesUsedTargetOrder() {
		context = Context.create("js");
		ScriptRuntimeTestFixture.publish(context, () ->
				ItemHandlerRegistry.putItemOnItem(100, 200, context.eval("js",
						"(function (ctx) { globalThis.order = "
								+ "ctx.usedItem.getId() + ':' + ctx.usedSlot + ':' + "
								+ "ctx.targetItem.getId() + ':' + ctx.targetSlot; })")));

		assertTrue(ItemOnItem.executeScriptItemOnItem(
				new Player(-1) { }, 200, 7, 100, 3));
		assertEquals("200:7:100:3",
				context.getBindings("js").getMember("order").asString());
	}

	@Test
	public void objectAndNpcRoutesUseTheirExactTargets() {
		context = Context.create("js");
		ScriptRuntimeTestFixture.publish(context, () -> {
			ItemHandlerRegistry.putItemOnObject(100, 2213,
					context.eval("js", "(function (ctx) { globalThis.objectId = ctx.target.getId(); })"));
			ItemHandlerRegistry.putItemOnNpc(100, 1,
					context.eval("js", "(function (ctx) { globalThis.npcId = ctx.target.getId(); })"));
		});
		Player player = new Player(-1) { };
		Npc npc = new Npc(0, 1);

		assertTrue(ItemOnObject.executeScriptItemOnObject(player, 100, 2, 2213, 10, 20));
		assertTrue(ItemOnNpc.executeScriptItemOnNpc(player, 100, 2, npc));
		assertEquals(2213, context.getBindings("js").getMember("objectId").asInt());
		assertEquals(1, context.getBindings("js").getMember("npcId").asInt());
	}
}
