package com.rs2.script.boss;

import org.graalvm.polyglot.Value;

/**
 * Immutable Java-owned phase descriptor of one declarative boss.
 *
 * <p>The phase's {@code onEnter} callback is a generation-owned guest
 * {@link Value}; the controller runs it exactly once, in strictly descending
 * {@code hpPercentThreshold} order, when the boss HP crosses the threshold.
 * A throwing callback fails the controller.
 */
public final class BossPhaseDefinition {

	private final String name;
	private final int hpPercentThreshold;
	private final Value onEnter;

	public BossPhaseDefinition(String name, int hpPercentThreshold,
			Value onEnter) {
		this.name = name;
		this.hpPercentThreshold = hpPercentThreshold;
		this.onEnter = onEnter;
	}

	public String name() {
		return name;
	}

	/** HP threshold in percent (0..100); phases run in descending order. */
	public int hpPercentThreshold() {
		return hpPercentThreshold;
	}

	public Value onEnter() {
		return onEnter;
	}

}
