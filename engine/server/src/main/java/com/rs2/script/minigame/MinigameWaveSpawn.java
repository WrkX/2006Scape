package com.rs2.script.minigame;

/** One NPC spawn entry in a minigame wave. */
public final class MinigameWaveSpawn {

	private final int npcId;
	private final int x;
	private final int y;
	private final int plane;

	public MinigameWaveSpawn(int npcId, int x, int y, int plane) {
		this.npcId = npcId;
		this.x = x;
		this.y = y;
		this.plane = plane;
	}

	public int npcId() {
		return npcId;
	}

	public int x() {
		return x;
	}

	public int y() {
		return y;
	}

	public int plane() {
		return plane;
	}
}
