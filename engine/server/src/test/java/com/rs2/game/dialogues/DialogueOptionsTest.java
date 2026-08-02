package com.rs2.game.dialogues;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.rs2.Constants;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.ScriptRuntimeTestFixture;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.util.Stream;
import org.apollo.util.security.IsaacRandom;
import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

public class DialogueOptionsTest {

	private Player installed;
	private Player previous;

	@After
	public void resetScripts() {
		ScriptRuntimeTestFixture.reset();
		if (installed != null) {
			PlayerHandler.players[installed.playerId] = previous;
		}
	}

	@Test
	public void decodesEverySupportedOptionInterfaceToZeroBasedIndexes() {
		assertMenu(2, 9157);
		assertMenu(3, 9167);
		assertMenu(4, 9178);
		assertMenu(5, 9190);
	}

	@Test
	public void rejectsButtonsFromOtherInterfaces() {
		assertEquals(-1, DialogueOptions.decodeOptionChoice(9167, 2));
		assertEquals(-1, DialogueOptions.decodeOptionChoice(9157, 3));
		assertEquals(-1, DialogueOptions.decodeOptionChoice(9194, 4));
		assertEquals(-1, DialogueOptions.decodeOptionChoice(9190, 6));
	}

	@Test
	public void validScriptChoiceIsConsumedAndCanArmAFollowUp() {
		final Context context = Context.create("js");
		ScriptRuntimeTestFixture.publishEmpty(context);
		final Player player = livePlayer(122);
		final ScriptedPlayer scripted = new ScriptedPlayer(player);
		final AtomicInteger selected = new AtomicInteger(-1);
		final Consumer<Integer> followUp = choice -> { };
		ScriptEncounterService.getInstance().armDialogueOption(
				player, scripted.generation(), scripted.facadeEpoch(), 2, choice -> {
			selected.set(choice);
			ScriptEncounterService.getInstance().armDialogueOption(
					player, scripted.generation(), scripted.facadeEpoch(),
					2, followUp);
		});

		assertTrue(DialogueOptions.handleScriptDialogueOption(player, 9158));
		assertEquals(1, selected.get());
		assertSame(followUp, player.pendingScriptOption);
		assertEquals(2, player.pendingOptionCount);
		context.close();
	}

	@Test
	public void foreignButtonIsNotConsumedAndKeepsCallbackArmed() {
		final Context context = Context.create("js");
		ScriptRuntimeTestFixture.publishEmpty(context);
		final Player player = livePlayer(122);
		final ScriptedPlayer scripted = new ScriptedPlayer(player);
		final Consumer<Integer> callback = choice -> { };
		ScriptEncounterService.getInstance().armDialogueOption(
				player, scripted.generation(), scripted.facadeEpoch(),
				2, callback);

		assertFalse(DialogueOptions.handleScriptDialogueOption(player, 9167));
		assertSame(callback, player.pendingScriptOption);
		assertEquals(2, player.pendingOptionCount);
		context.close();
	}

	private Player livePlayer(int slot) {
		previous = PlayerHandler.players[slot];
		Player player = new Player(slot) {
			@Override
			public void flushOutStream() {
				if (outStream != null) {
					outStream.currentOffset = 0;
				}
			}
		};
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.respawnTimer = 0;
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption = new IsaacRandom(new int[4]);
		PlayerHandler.players[slot] = player;
		installed = player;
		return player;
	}

	private static void assertMenu(int count, int firstButton) {
		for (int index = 0; index < count; index++) {
			assertEquals(index, DialogueOptions.decodeOptionChoice(firstButton + index, count));
		}
		assertEquals(-1, DialogueOptions.decodeOptionChoice(firstButton - 1, count));
		assertEquals(-1, DialogueOptions.decodeOptionChoice(firstButton + count, count));
	}
}
