package com.rs2.net.packets.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.rs2.event.Event;
import com.rs2.event.impl.NpcFirstClickEvent;
import com.rs2.event.impl.ObjectFirstClickEvent;
import com.rs2.game.players.Player;
import com.rs2.script.registries.NpcHandlerRegistry;
import com.rs2.script.registries.ObjectHandlerRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.script.ScriptRuntimeTestFixture;

public class ClickDispatchTest {

	private Context context;

	@After
	public void cleanUp() {
		ScriptRuntimeTestFixture.reset();
		if (context != null) {
			context.close();
		}
	}

	@Test
	public void scriptedNpcClickSuppressesLegacyPluginEvent() {
		context = Context.create("js");
		ScriptRuntimeTestFixture.publish(context, () ->
				NpcHandlerRegistry.put(1, "first",
						context.eval("js", "(function () { throw new Error('boom'); })")));
		RecordingPlayer player = new RecordingPlayer();

		boolean scripted = ClickNPC.isScriptedClick(1, "first");
		assertTrue(scripted);
		assertFalse(ClickNPC.postLegacyEventIfUnscripted(player, scripted,
				new NpcFirstClickEvent(1)));
		assertEquals(0, player.postedEvents);
	}

	@Test
	public void scriptedObjectClickSuppressesLegacyPluginEvent() {
		context = Context.create("js");
		ScriptRuntimeTestFixture.publish(context, () ->
				ObjectHandlerRegistry.put(2213, "first",
						context.eval("js", "(function () {})")));
		RecordingPlayer player = new RecordingPlayer();

		boolean scripted = ClickObject.isScriptedClick(2213, "first");
		assertTrue(scripted);
		assertFalse(ClickObject.postLegacyEventIfUnscripted(player, scripted,
				new ObjectFirstClickEvent(2213)));
		assertEquals(0, player.postedEvents);
	}

	@Test
	public void missingScriptPreservesLegacyPluginEvent() {
		RecordingPlayer player = new RecordingPlayer();

		boolean scripted = ClickNPC.isScriptedClick(99, "first");
		assertFalse(scripted);
		assertTrue(ClickNPC.postLegacyEventIfUnscripted(player, scripted,
				new NpcFirstClickEvent(99)));
		assertEquals(1, player.postedEvents);
	}

	private static final class RecordingPlayer extends Player {
		private int postedEvents;

		private RecordingPlayer() {
			super(-1);
		}

		@Override
		public <E extends Event> void post(E event) {
			postedEvents++;
		}
	}
}
