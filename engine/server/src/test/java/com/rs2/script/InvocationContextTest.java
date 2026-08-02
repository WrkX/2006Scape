package com.rs2.script;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

import com.rs2.game.players.Player;

public class InvocationContextTest {

	@Test
	public void commandContextDefensivelyCopiesArguments() {
		Player player = new Player(-1) { };
		player.playerRights = 2;
		String[] source = {"one", "two"};
		CommandScriptContext context = new CommandScriptContext(
				new ScriptedPlayer(player), "echo", "EcHo one  two", source, player.playerRights);

		source[0] = "changed";
		String[] firstRead = context.getArguments();
		firstRead[1] = "changed";

		assertEquals("echo", context.getName());
		assertEquals("EcHo one  two", context.getRawInput());
		assertEquals(2, context.getRights());
		assertArrayEquals(new String[] {"one", "two"}, context.getArguments());
	}

	@Test
	public void itemPairContextPreservesInvocationDirectionAndSlots() {
		Player player = new Player(-1) { };
		ItemOnItemScriptContext context = new ItemOnItemScriptContext(
				new ScriptedPlayer(player), ScriptedItem.byId(200), 7,
				ScriptedItem.byId(100), 3);

		assertEquals(200, context.usedItem.getId());
		assertEquals(7, context.usedSlot);
		assertEquals(100, context.targetItem.getId());
		assertEquals(3, context.targetSlot);
	}
}
