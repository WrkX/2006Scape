package com.rs2.script.world;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rs2.game.players.Player;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.scheduler.ScriptTaskHandle;

/** Package-private mutable state owned exclusively by ScriptEncounterService. */
final class ScriptEncounter {

	final long token;
	final long generation;
	final String id;
	final Player owner;
	final ScriptedPlayer scriptedOwner;
	final ScriptEncounterReservation reservation;
	final ScriptEncounterRng rng;
	final long ownerToken;
	final long ordinal;
	/** Monotonic drop-commit version owned by the encounter RNG adapter. */
	long dropVersion;
	final LinkedHashMap<Player, ScriptedPlayer> participants =
			new LinkedHashMap<Player, ScriptedPlayer>();
	final List<ScriptTaskHandle> tasks = new ArrayList<ScriptTaskHandle>();
	final Map<Player, List<ScriptTaskHandle>> participantTasks =
			new IdentityHashMap<Player, List<ScriptTaskHandle>>();
	final Map<Player, List<Long>> actionLocks =
			new IdentityHashMap<Player, List<Long>>();
	final Map<Player, List<Long>> movementLocks =
			new IdentityHashMap<Player, List<Long>>();
	boolean open = true;
	int npcCount;
	int deathCallbackCount;
	int groundIdentityCount;
	int objectMutationCount;

	ScriptEncounter(long token, long generation, String id, Player owner,
			ScriptedPlayer scriptedOwner,
			ScriptEncounterReservation reservation, ScriptEncounterRng rng,
			long ownerToken, long ordinal) {
		this.token = token;
		this.generation = generation;
		this.id = id;
		this.owner = owner;
		this.scriptedOwner = scriptedOwner;
		this.reservation = reservation;
		this.rng = rng;
		this.ownerToken = ownerToken;
		this.ordinal = ordinal;
		participants.put(owner, scriptedOwner);
	}
}
