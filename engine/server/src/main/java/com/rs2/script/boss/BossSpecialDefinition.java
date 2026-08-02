package com.rs2.script.boss;

import org.graalvm.polyglot.Value;

/**
 * Immutable Java-owned named special descriptor of one declarative boss.
 *
 * <p>The {@code handler} is a generation-owned guest {@link Value}. Once
 * armed through {@link BossRuntimeContext#useSpecial(String)}, the special
 * fires first after {@code cooldownTicks} game cycles and then every
 * {@code cooldownTicks} cycles while the boss is alive and the controller
 * runs. A throwing handler fails the controller.
 */
public final class BossSpecialDefinition {

	private final String name;
	private final int cooldownTicks;
	private final Value handler;

	public BossSpecialDefinition(String name, int cooldownTicks, Value handler) {
		this.name = name;
		this.cooldownTicks = cooldownTicks;
		this.handler = handler;
	}

	public String name() {
		return name;
	}

	/** Repeat interval in game cycles (1..100000). */
	public int cooldownTicks() {
		return cooldownTicks;
	}

	public Value handler() {
		return handler;
	}

}
