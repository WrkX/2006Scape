package com.rs2.script.raid;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.polyglot.HostAccess;

import com.rs2.script.ScriptArray;
import com.rs2.script.ScriptedPlayer;
import com.rs2.script.ScriptedPosition;
import com.rs2.script.world.ScriptEncounterHandle;

/**
 * Narrow runtime context passed to every executable raid callback.
 *
 * <p>The context is composed only of accepted wrappers and handles: the
 * borrowed encounter handle, the raid owner, the active participant view,
 * the room identity, the room-relative elapsed ticks, the room center, and
 * a bounded announce broadcast. There is deliberately no rich domain
 * {@code Player}, no registry access, and no raw engine object.
 *
 * <p>{@code encounter} and {@code owner} are public final fields: GraalJS
 * exposes a zero-argument Java method member as the method object rather
 * than its result, so guest code reads them as plain properties (the same
 * pattern as {@code ScriptContext.player} and the boss runtime context).
 * Every behavior is a method.
 */
public final class RaidRoomContext {

	private final String raidId;
	private final String roomId;
	private final int roomIndex;
	private final int elapsedTicks;
	private final ScriptedPosition position;
	private final List<ScriptedPlayer> participants;

	/** The raid's sole borrowed encounter handle. */
	@HostAccess.Export
	public final ScriptEncounterHandle encounter;

	/** The raid owner. */
	@HostAccess.Export
	public final ScriptedPlayer owner;

	public RaidRoomContext(ScriptEncounterHandle encounter,
			ScriptedPlayer owner, String raidId, String roomId, int roomIndex,
			int elapsedTicks, ScriptedPosition position,
			List<ScriptedPlayer> participants) {
		this.encounter = encounter;
		this.owner = owner;
		this.raidId = raidId;
		this.roomId = roomId;
		this.roomIndex = roomIndex;
		this.elapsedTicks = elapsedTicks;
		this.position = position;
		this.participants = new ArrayList<ScriptedPlayer>(participants);
	}

	@HostAccess.Export
	public String id() {
		return raidId;
	}

	/** The active room id; {@code null} for raid-level callbacks. */
	@HostAccess.Export
	public String roomId() {
		return roomId;
	}

	/** Zero-based room index; {@code -1} for raid-level callbacks. */
	@HostAccess.Export
	public int roomIndex() {
		return roomIndex;
	}

	/** Immutable active participant view supplied by the raid session. */
	@HostAccess.Export
	public ScriptArray participants() {
		return new ScriptArray(participants.toArray());
	}

	/** Game cycles since this room was entered (0 on entry). */
	@HostAccess.Export
	public int elapsedTicks() {
		return elapsedTicks;
	}

	/** The room center, or the raid entrance for raid-level callbacks. */
	@HostAccess.Export
	public ScriptedPosition position() {
		return position;
	}

	/** Broadcasts a message to every active live member. */
	@HostAccess.Export
	public boolean announce(String text) {
		if (text == null) {
			return false;
		}
		for (ScriptedPlayer member : participants) {
			member.message(text);
		}
		return !participants.isEmpty();
	}

}
