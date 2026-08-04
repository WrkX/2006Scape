package com.rs2.script;

import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;

import org.graalvm.polyglot.Context;
import org.junit.After;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.game.players.Player;
import com.rs2.game.players.PlayerHandler;
import com.rs2.script.quest.QuestDefinition;
import com.rs2.script.registries.QuestRegistry;
import com.rs2.script.registries.RegistryStore;
import com.rs2.util.Stream;
import org.apollo.util.security.IsaacRandom;

public class QuestBridgeIntegrationTest {

	@After
	public void clear() {
		ScriptRuntimeTestFixture.reset();
	}

	@Test
	public void graalSeesMethodsTypedNullsAndExpectedStageCas() throws Exception {
		int slot = 121;
		Player previous = PlayerHandler.players[slot];
		Player player = new TestPlayer(slot);
		player.initialized = true;
		player.isActive = true;
		player.disconnected = false;
		player.isDead = false;
		player.respawnTimer = 0;
		player.outStream = new Stream(new byte[Constants.BUFFER_SIZE]);
		player.outStream.packetEncryption = new IsaacRandom(new int[4]);
		PlayerHandler.players[slot] = player;

		try (Context context = ScriptHost.buildContext(
				Files.createTempDirectory("quest-bridge").toFile())) {
			RegistryStore.State candidate = RegistryStore.beginStaging();
			QuestRegistry.put("bridge-contract", definition());
			QuestRegistry.validateCandidate(candidate);
			ScriptRuntimeTestFixture.publishCandidate(context, candidate);
			context.getBindings("js").putMember("player",
					new ScriptedPlayer(player));
			assertTrue(context.eval("js",
					"player.state('contract').getBoolean('missing') === null"
					+ " && player.quest('missing') === null"
					+ " && player.questPoints() === 0").asBoolean());
			assertTrue(context.eval("js",
					"player.state('contract').setBoolean('flag', true);"
					+ "let wrong=false;try{player.state('contract').getString('flag')}"
					+ "catch(e){wrong=true};wrong").asBoolean());
			assertTrue(context.eval("js",
					"const before=player.quest('bridge-contract');"
					+ "const eligible=before.canStart();"
					+ "before.stage()===null"
					+ " && before.objective()===null"
					+ " && eligible.ok() && !eligible.changed()"
					+ " && eligible.code()==='can_start'").asBoolean());
			assertTrue(context.eval("js",
					"const q=player.quest('bridge-contract');"
					+ "const started=q.start();"
					+ "started.ok() && started.changed() && started.code()==='started'"
					+ " && q.id()==='bridge-contract' && q.state()==='in_progress'"
					+ " && q.stage()===0 && q.objective()==='Finish.'"
					+ " && typeof q.setStage==='function'"
					+ " && typeof q.advance==='function'"
					+ " && typeof q.complete==='function'").asBoolean());
		} finally {
			PlayerHandler.players[slot] = previous;
		}
	}

	private static QuestDefinition definition() {
		return new QuestDefinition("bridge-contract", "Bridge Contract",
				"Exercises the exact guest method shape.",
				Arrays.asList(new QuestDefinition.Stage(0, "Finish.")),
				new QuestDefinition.Requirements(0,
						Collections.<String>emptyList(),
						Collections.<QuestDefinition.SkillRequirement>emptyList(),
						Collections.<QuestDefinition.ItemAmount>emptyList()),
				new QuestDefinition.Rewards(0,
						Collections.<QuestDefinition.ItemAmount>emptyList(),
						Collections.<QuestDefinition.ExperienceReward>emptyList()));
	}

	/**
	 * Quest transitions refresh the quest-tab presentation through the same
	 * packet path real players use; a session-less test player must discard
	 * the flushed bytes instead of writing to a null session.
	 */
	private static final class TestPlayer extends Player {
		private TestPlayer(int slot) {
			super(slot);
		}

		@Override
		public void flushOutStream() {
			if (outStream != null) {
				outStream.currentOffset = 0;
			}
		}
	}
}
