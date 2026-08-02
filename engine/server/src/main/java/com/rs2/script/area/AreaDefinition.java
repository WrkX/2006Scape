package com.rs2.script.area;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.graalvm.polyglot.Value;

/**
 * Immutable Java-owned schema-v1 declarative area descriptor.
 *
 * <p>The descriptor carries copied canonical values only: a stable string
 * id, display name, inclusive bounds, ordered NPC spawns with exact spawn
 * keys and optional named drop bindings, ordered object projections with
 * optional per-action drop bindings, and bounded references to scripted
 * shops, quests, bosses, and raids registered in the same candidate. The
 * optional {@code onEnter}/{@code onLeave} callbacks are generation-owned
 * guest values driven by the lifecycle area-transition observer.
 */
public final class AreaDefinition {

	private final String id;
	private final String name;
	private final AreaBounds bounds;
	private final List<AreaNpcSpawn> npcs;
	private final List<AreaObjectProjection> objects;
	private final List<String> shops;
	private final List<String> quests;
	private final List<String> bosses;
	private final List<String> raids;
	private final Value onEnter;
	private final Value onLeave;
	private final String source;
	private final int schemaVersion;

	public AreaDefinition(String id, String name, AreaBounds bounds,
			List<AreaNpcSpawn> npcs, List<AreaObjectProjection> objects,
			List<String> shops, List<String> quests, List<String> bosses,
			List<String> raids, Value onEnter, Value onLeave, String source,
			int schemaVersion) {
		this.id = id;
		this.name = name;
		this.bounds = bounds;
		this.npcs = Collections.unmodifiableList(
				new ArrayList<AreaNpcSpawn>(npcs));
		this.objects = Collections.unmodifiableList(
				new ArrayList<AreaObjectProjection>(objects));
		this.shops = Collections.unmodifiableList(
				new ArrayList<String>(shops));
		this.quests = Collections.unmodifiableList(
				new ArrayList<String>(quests));
		this.bosses = Collections.unmodifiableList(
				new ArrayList<String>(bosses));
		this.raids = Collections.unmodifiableList(
				new ArrayList<String>(raids));
		this.onEnter = onEnter;
		this.onLeave = onLeave;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	public String name() {
		return name;
	}

	public AreaBounds bounds() {
		return bounds;
	}

	/** Immutable NPC spawns in registration order. */
	public List<AreaNpcSpawn> npcs() {
		return npcs;
	}

	/** Immutable object projections in registration order. */
	public List<AreaObjectProjection> objects() {
		return objects;
	}

	/** Referenced scripted shop ids, validated in the loading candidate. */
	public List<String> shops() {
		return shops;
	}

	/** Referenced quest ids, validated in the loading candidate. */
	public List<String> quests() {
		return quests;
	}

	/** Referenced boss stable ids, validated in the loading candidate. */
	public List<String> bosses() {
		return bosses;
	}

	/** Referenced raid ids; WP5 owns their strict schema validation. */
	public List<String> raids() {
		return raids;
	}

	/** Generation-owned enter observer, or {@code null}. */
	public Value onEnter() {
		return onEnter;
	}

	/** Generation-owned leave observer, or {@code null}. */
	public Value onLeave() {
		return onLeave;
	}

	/** Bounded logical source module, or the legacy-unscoped marker. */
	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "area '" + id + "' (" + bounds + ", npcs: " + npcs.size()
				+ ", objects: " + objects.size() + ", source: " + source
				+ ", schema v" + schemaVersion + ")";
	}

}
