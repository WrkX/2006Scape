package com.rs2.script.minigame;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One ordered wave of NPC spawns inside a minigame session. */
public final class MinigameWaveDefinition {

	private final String id;
	private final List<MinigameWaveSpawn> spawns;

	public MinigameWaveDefinition(String id, List<MinigameWaveSpawn> spawns) {
		this.id = id;
		this.spawns = Collections.unmodifiableList(
				new ArrayList<MinigameWaveSpawn>(spawns));
	}

	public String id() {
		return id;
	}

	public List<MinigameWaveSpawn> spawns() {
		return spawns;
	}
}
