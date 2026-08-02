package com.rs2.script.world;

/**
 * Deterministic game-cycle-owned encounter RNG (SplitMix64).
 *
 * <p>The class is intentionally public only so focused tests in the
 * {@code com.rs2.script} package can pin the normative vectors; it is never
 * exported to guest code and no member carries {@code @HostAccess.Export}.
 *
 * <p>Every add/multiply uses Java {@code long} two's-complement overflow and
 * every shift is unsigned ({@code >>>}).
 */
public final class ScriptEncounterRng {

	private static final long GAMMA = 0x9E3779B97F4A7C15L;
	private static final long MATERIAL_GENERATION = 0xD1342543DE82EF95L;
	private static final long MATERIAL_OWNER = 0x9E3779B97F4A7C15L;
	private static final long MATERIAL_ORDINAL = 0x94D049BB133111EBL;
	private static final int MAX_BOUND = 1_000_000;

	private long state;

	public ScriptEncounterRng(long state) {
		this.state = state;
	}

	/**
	 * Production initial-state derivation: material mix over the process seed,
	 * generation, owner token, and encounter ordinal, then {@link #mix64}.
	 */
	public static long material(long processSeed, long generation,
			long ownerToken, long encounterOrdinal) {
		long material = processSeed;
		material ^= generation * MATERIAL_GENERATION;
		material ^= ownerToken * MATERIAL_OWNER;
		material ^= encounterOrdinal * MATERIAL_ORDINAL;
		return material;
	}

	/** The three xor/multiply steps without the initial gamma add. */
	public static long mix64(long z) {
		z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
		z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
		return z ^ (z >>> 31);
	}

	/** Derives the encounter RNG from the production seed material. */
	public static ScriptEncounterRng derive(long processSeed, long generation,
			long ownerToken, long encounterOrdinal) {
		return new ScriptEncounterRng(mix64(material(processSeed, generation,
				ownerToken, encounterOrdinal)));
	}

	public long nextLong() {
		state += GAMMA;
		return mix64(state);
	}

	/**
	 * Bounded {@code 0..bound-1} using the upper 31 output bits and Java-style
	 * rejection. Invalid bounds return {@code -1} without advancing state.
	 */
	public int nextInt(int bound) {
		if (bound < 1 || bound > MAX_BOUND) {
			return -1;
		}
		int bits = (int) (nextLong() >>> 33);
		int value = bits % bound;
		while (bits - value + (bound - 1) < 0) {
			bits = (int) (nextLong() >>> 33);
			value = bits % bound;
		}
		return value;
	}

	/**
	 * Rational chance {@code numerator/denominator}. Invalid values and a zero
	 * numerator return {@code false} without advancing state; equality returns
	 * {@code true} without advancing state; otherwise
	 * {@code nextInt(denominator) < numerator}.
	 */
	public boolean chance(int numerator, int denominator) {
		if (denominator < 1 || denominator > MAX_BOUND
				|| numerator < 0 || numerator > denominator) {
			return false;
		}
		if (numerator == 0) {
			return false;
		}
		if (numerator == denominator) {
			return true;
		}
		return nextInt(denominator) < numerator;
	}

	public long state() {
		return state;
	}

	/** Commits a transaction RNG state after a successful drop roll. */
	public void restore(long state) {
		this.state = state;
	}

	/** Independent state copy used by the drop transaction. */
	public ScriptEncounterRng copy() {
		return new ScriptEncounterRng(state);
	}
}
