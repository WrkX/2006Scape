package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.graalvm.polyglot.proxy.ProxyExecutable;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.rs2.Constants;
import com.rs2.game.players.PlayerHandler;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketHandler;
import com.rs2.script.context.PlayerDeathScriptContext;
import com.rs2.script.quest.QuestDefinition;
import com.rs2.script.quest.ScriptedQuest;
import com.rs2.script.state.PlayerStateNamespace;
import com.rs2.script.world.ScriptEncounterHandle;
import com.rs2.script.world.ScriptEncounterService;
import com.rs2.script.world.ScriptPlayerDeathTicket;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

public class PlayerDeathLifecycleIntegrationTest {

	private ScriptEncounterTestSupport support;

	@Before
	public void setUp() throws Exception {
		support = new ScriptEncounterTestSupport();
	}

	@After
	public void tearDown() throws Exception {
		support.close();
	}

	@Test
	public void realPlayerProcessCompletesCoreDeathBeforeImmutableCallback()
			throws Exception {
		ScriptEncounterTestSupport.TestClient victim =
				support.player(5, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient lowIndex =
				support.player(2, 3201, 3200, 0);
		ScriptEncounterTestSupport.TestClient highIndex =
				support.player(3, 3201, 3201, 0);
		AtomicInteger calls = new AtomicInteger();
		AtomicInteger respawnAtCallback = new AtomicInteger();
		AtomicReference<String> killerName = new AtomicReference<String>();
		AtomicReference<PlayerDeathScriptContext> observed =
				new AtomicReference<PlayerDeathScriptContext>();
		support.publishPlayerDeathHandler((ProxyExecutable) arguments -> {
			PlayerDeathScriptContext context = arguments[0].asHostObject();
			observed.set(context);
			calls.incrementAndGet();
			respawnAtCallback.set(victim.respawnTimer);
			killerName.set(context.killer == null
					? null : context.killer.username());
			return null;
		});
		ScriptEncounterHandle encounter = support.encounter(
				victim, "death", 3200, 3200, 3204, 3204, 0);
		assertNotNull(encounter);
		victim.damageTaken[lowIndex.playerId] = 10;
		victim.damageTaken[highIndex.playerId] = 10;
		victim.isDead = true;
		victim.respawnTimer = -6;

		victim.process();

		assertEquals(1, calls.get());
		assertEquals(15, respawnAtCallback.get());
		assertEquals(lowIndex.playerName, killerName.get());
		assertNotNull(observed.get());
		assertEquals(victim.playerName, observed.get().player.username());
		assertEquals(3200, observed.get().position.x);
		assertFalse(encounter.isOpen());
		assertEquals(14, victim.respawnTimer);
	}

	@Test
	public void killerResolutionRejectsSelfStaleDeadAndNoDamage() {
		ScriptEncounterTestSupport.TestClient victim =
				support.player(5, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient stale =
				support.player(2, 3201, 3200, 0);
		ScriptEncounterTestSupport.TestClient dead =
				support.player(3, 3201, 3201, 0);
		stale.playerId = 8;
		dead.isDead = true;
		victim.damageTaken[5] = 100;
		victim.damageTaken[2] = 90;
		victim.damageTaken[3] = 80;
		victim.isDead = true;
		victim.respawnTimer = -6;

		ScriptPlayerDeathTicket ticket =
				ScriptEncounterService.getInstance().beginPlayerDeath(victim);
		assertNotNull(ticket);
		assertNull(ticket.killer());
		assertTrue(ScriptEncounterService.getInstance()
				.completePlayerDeath(ticket));
		assertFalse(ScriptEncounterService.getInstance()
				.completePlayerDeath(ticket));
	}

	@Test
	public void deathRespawnUsesTypedPrivilegedRelocation() throws Exception {
		ScriptEncounterTestSupport.TestClient blocker =
				support.player(1, Constants.RESPAWN_X - 1,
						Constants.RESPAWN_Y, 0);
		ScriptEncounterTestSupport.TestClient victim =
				support.player(5, 3200, 3200, 0);
		victim.playerRights = 3;
		ScriptEncounterHandle reservedRespawn = support.encounter(
				blocker, "reserved-respawn", Constants.RESPAWN_X,
				Constants.RESPAWN_Y, Constants.RESPAWN_X,
				Constants.RESPAWN_Y, 0);
		assertNotNull(reservedRespawn);
		assertFalse(ScriptEncounterService.getInstance().canDestination(
				victim, Constants.RESPAWN_X, Constants.RESPAWN_Y, 0));

		String previousUserDir = System.getProperty("user.dir");
		System.setProperty("user.dir",
				Files.createTempDirectory("wp3-death-save").toString());
		try {
			victim.isDead = true;
			victim.respawnTimer = 7;
			victim.process();
		} finally {
			System.setProperty("user.dir", previousUserDir);
		}

		assertEquals(Constants.RESPAWN_X, victim.teleportToX);
		assertEquals(Constants.RESPAWN_Y, victim.teleportToY);
		assertFalse(victim.isDead);
		assertTrue(reservedRespawn.isOpen());
	}

	@Test
	public void deathAdmissionAndCompletionRequireExactTransitionAndTicket() {
		ScriptEncounterTestSupport.TestClient victim =
				support.player(5, 3200, 3200, 0);
		ScriptEncounterService service = ScriptEncounterService.getInstance();

		assertNull(service.beginPlayerDeath(victim));
		victim.isDead = true;
		victim.respawnTimer = -5;
		assertNull(service.beginPlayerDeath(victim));
		victim.respawnTimer = -6;

		ScriptEncounterTestSupport.TestClient replacement =
				support.player(5, 3201, 3201, 0);
		assertNull(service.beginPlayerDeath(victim));
		PlayerHandler.players[5] = victim;

		ScriptPlayerDeathTicket ticket = service.beginPlayerDeath(victim);
		assertNotNull(ticket);
		assertNull(service.beginPlayerDeath(victim));
		assertTrue(service.completePlayerDeath(ticket));
		assertFalse(service.completePlayerDeath(ticket));
		assertFalse(service.completePlayerDeath(null));
		assertNull(service.beginPlayerDeath(replacement));
	}

	@Test
	public void realPlayerProcessContainsDeathCallbackFailure() throws Exception {
		ScriptEncounterTestSupport.TestClient victim =
				support.player(5, 3200, 3200, 0);
		support.publishPlayerDeathHandler((ProxyExecutable) arguments -> {
			throw new IllegalStateException("expected death callback failure");
		});
		ScriptEncounterHandle encounter = support.encounter(
				victim, "death-failure", 3200, 3200, 3204, 3204, 0);
		assertNotNull(encounter);
		victim.isDead = true;
		victim.respawnTimer = -6;

		victim.process();

		assertEquals(14, victim.respawnTimer);
		assertFalse(encounter.isOpen());
	}

	@Test
	public void preDeathFacadeStaysInvalidButFreshPostRespawnFacadeWorks()
			throws Exception {
		ScriptEncounterTestSupport.TestClient victim =
				support.player(5, 3200, 3200, 0);
		ScriptedPlayer beforeDeath = new ScriptedPlayer(victim);
		victim.isDead = true;
		victim.respawnTimer = -6;
		victim.process();

		String previousUserDir = System.getProperty("user.dir");
		System.setProperty("user.dir",
				Files.createTempDirectory("wp3-epoch-respawn").toString());
		try {
			victim.playerRights = 3;
			victim.respawnTimer = 7;
			victim.process();
		} finally {
			System.setProperty("user.dir", previousUserDir);
		}

		assertFalse(beforeDeath.state("epoch").setBoolean("old", true));
		ScriptedPlayer afterRespawn = new ScriptedPlayer(victim);
		assertTrue(afterRespawn.state("epoch").setBoolean("fresh", true));
		assertTrue(afterRespawn.state("epoch").getBoolean("fresh"));
	}

	@Test
	public void deathPurgesBufferedFramesOptionsAndCapturedSubfacades()
			throws Exception {
		support.publishQuest(epochQuest());
		ScriptEncounterTestSupport.TestClient buffered =
				support.player(5, 3200, 3200, 0);
		ScriptEncounterTestSupport.TestClient armed =
				support.player(6, 3201, 3200, 0);
		AtomicInteger oldBufferedChoice = new AtomicInteger();
		AtomicInteger oldArmedChoice = new AtomicInteger();
		AtomicInteger freshChoice = new AtomicInteger();
		org.graalvm.polyglot.Context context =
				ScriptHost.getInstance().getContext();
		ScriptedPlayer bufferedFacade = new ScriptedPlayer(buffered);
		ScriptedPlayer armedFacade = new ScriptedPlayer(armed);
		PlayerStateNamespace oldState = armedFacade.state("captured");
		ScriptedQuest oldQuest = armedFacade.quest("epoch-quest");
		ScriptedDialogue oldDialogue = armedFacade.getDialogue();

		bufferedFacade.getDialogue().statement("first").options(
				new String[] {"Continue", "Stop"},
				context.asValue((ProxyExecutable) arguments -> {
					oldBufferedChoice.incrementAndGet();
					return null;
				}));
		oldDialogue.options(new String[] {"Yes", "No"},
				context.asValue((ProxyExecutable) arguments -> {
					oldArmedChoice.incrementAndGet();
					return null;
				}));
		assertEquals(DialogueChain.CHAIN_SENTINEL, buffered.nextChat);
		assertNull(buffered.pendingScriptOption);
		assertNotNull(armed.pendingScriptOption);

		deathAndRespawn(buffered);
		deathAndRespawn(armed);
		PacketHandler.processPacket(buffered, emptyPacket(40));
		PacketHandler.processPacket(armed, actionButton(9157));

		assertEquals(0, oldBufferedChoice.get());
		assertEquals(0, oldArmedChoice.get());
		assertNull(buffered.pendingScriptOption);
		assertNull(armed.pendingScriptOption);
		assertNull(buffered.scriptDialogueFrames);
		assertFalse(oldState.setBoolean("stale", true));
		assertEquals("state_failed", oldQuest.start().code());
		oldDialogue.options(new String[] {"Stale", "No"},
				context.asValue((ProxyExecutable) arguments -> null));
		assertNull(armed.pendingScriptOption);

		ScriptedPlayer fresh = new ScriptedPlayer(armed);
		assertTrue(fresh.state("captured").setBoolean("fresh", true));
		assertTrue(fresh.quest("epoch-quest").start().changed());
		fresh.getDialogue().options(new String[] {"Fresh", "No"},
				context.asValue((ProxyExecutable) arguments -> {
					freshChoice.incrementAndGet();
					return null;
				}));
		assertNotNull(armed.pendingScriptOption);
		PacketHandler.processPacket(armed, actionButton(9157));
		assertEquals(1, freshChoice.get());
	}

	private static QuestDefinition epochQuest() {
		return new QuestDefinition("epoch-quest", "Epoch Quest",
				"Tests retained quest authority.",
				Arrays.asList(new QuestDefinition.Stage(0, "Begin.")),
				new QuestDefinition.Requirements(0,
						Collections.<String>emptyList(),
						Collections.<QuestDefinition.SkillRequirement>emptyList(),
						Collections.<QuestDefinition.ItemAmount>emptyList()),
				new QuestDefinition.Rewards(0,
						Collections.<QuestDefinition.ItemAmount>emptyList(),
						Collections.<QuestDefinition.ExperienceReward>emptyList()));
	}

	private static void deathAndRespawn(
			ScriptEncounterTestSupport.TestClient player) throws Exception {
		player.isDead = true;
		player.respawnTimer = -6;
		player.process();
		String previousUserDir = System.getProperty("user.dir");
		System.setProperty("user.dir",
				Files.createTempDirectory("wp3-dialogue-respawn").toString());
		try {
			player.playerRights = 3;
			player.respawnTimer = 7;
			player.process();
		} finally {
			System.setProperty("user.dir", previousUserDir);
		}
	}

	private static Packet actionButton(int actionButtonId) {
		ByteBuf payload = Unpooled.buffer(2);
		payload.writeByte(actionButtonId / 1000);
		payload.writeByte(actionButtonId % 1000);
		return new Packet(185, Packet.Type.FIXED, payload);
	}

	private static Packet emptyPacket(int opcode) {
		return new Packet(opcode, Packet.Type.FIXED, Unpooled.buffer(0));
	}
}
