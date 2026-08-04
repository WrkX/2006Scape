package com.rs2.script.raid;

import org.graalvm.polyglot.Value;

import com.rs2.script.boss.BossDefinition;

/**
 * Immutable Java-owned schema-v1 raid room descriptor.
 *
 * <p>The room carries copied canonical values only: a stable id, name,
 * bounded rectangle inside the raid bounds, the generation-owned
 * {@code onEnter}/{@code onTick}/{@code onComplete} callbacks, and an
 * optional boss reference resolved at candidate validation to the
 * {@link BossDefinition} registered earlier in the same candidate. Room
 * callbacks are valid only while the registering context is active.
 */
public final class RaidRoomDefinition {

	private final String id;
	private final String name;
	private final RaidBounds bounds;
	private final Value onEnter;
	private final Value onTick;
	private final Value onComplete;
	private final BossDefinition boss;

	public RaidRoomDefinition(String id, String name, RaidBounds bounds,
			Value onEnter, Value onTick, Value onComplete,
			BossDefinition boss) {
		this.id = id;
		this.name = name;
		this.bounds = bounds;
		this.onEnter = onEnter;
		this.onTick = onTick;
		this.onComplete = onComplete;
		this.boss = boss;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public RaidBounds bounds() {
		return bounds;
	}

	public Value onEnter() {
		return onEnter;
	}

	public Value onTick() {
		return onTick;
	}

	public Value onComplete() {
		return onComplete;
	}

	/** Resolved declarative boss of this room, or {@code null}. */
	public BossDefinition boss() {
		return boss;
	}

	@Override
	public String toString() {
		return "room '" + id + "' " + bounds
				+ (boss == null ? "" : " (boss: " + boss.id() + ")");
	}

}
