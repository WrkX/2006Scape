package com.rs2.script.minigame;

/** Per-player score storage namespace for one minigame. */
public final class MinigameScoreDefinition {

	private final String namespace;
	private final String key;

	public MinigameScoreDefinition(String namespace, String key) {
		this.namespace = namespace;
		this.key = key;
	}

	public String namespace() {
		return namespace;
	}

	public String key() {
		return key;
	}
}
