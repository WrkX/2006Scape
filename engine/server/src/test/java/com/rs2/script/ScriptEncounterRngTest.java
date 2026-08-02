package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import com.rs2.script.world.ScriptEncounterRng;

/**
 * Literal SplitMix64 vectors and the no-advance bound/chance contracts.
 * Every expected value below is an independent literal from the Phase 4
 * implementation plan, never a value produced by this class under test.
 */
public class ScriptEncounterRngTest {

	@Test
	public void nextLongLiteralVectorsMatchSplitMix64() {
		ScriptEncounterRng rng = new ScriptEncounterRng(0L);
		assertEquals(0xe220a8397b1dcdafL, rng.nextLong());
		assertEquals(0x6e789e6aa1b965f4L, rng.nextLong());
		assertEquals(0x06c45d188009454fL, rng.nextLong());
		assertEquals(0xf88bb8a8724c81ecL, rng.nextLong());
		assertEquals(0x1b39896a51a8749bL, rng.nextLong());
	}

	@Test
	public void derivedSeedMaterialInitialStateAndFirstOutputAreLiteral() {
		long material = ScriptEncounterRng.material(0x0123456789abcdefL,
				7L, 11L, 13L);
		assertEquals(0xfbbfc53b1d71fcf4L, material);
		long initialState = ScriptEncounterRng.mix64(material);
		assertEquals(0x84451a083e76124dL, initialState);
		ScriptEncounterRng rng = new ScriptEncounterRng(initialState);
		assertEquals(0xbcc864c2a4d2c848L, rng.nextLong());
	}

	@Test
	public void nextIntMixedBoundsMatchLiteralVectors() {
		ScriptEncounterRng rng = new ScriptEncounterRng(0L);
		assertEquals(6, rng.nextInt(10));
		assertEquals(699317, rng.nextInt(1000000));
		assertEquals(2, rng.nextInt(3));
	}

	@Test
	public void invalidBoundsReturnMinusOneWithoutStateAdvance() {
		ScriptEncounterRng rng = new ScriptEncounterRng(0L);
		assertEquals(-1, rng.nextInt(0));
		assertEquals(-1, rng.nextInt(-7));
		assertEquals(-1, rng.nextInt(1000001));
		assertEquals(0L, rng.state());
		assertEquals(0xe220a8397b1dcdafL, rng.nextLong());
	}

	@Test
	public void chanceConsumesExactlyOneAdvanceForRealRolls() {
		ScriptEncounterRng below = new ScriptEncounterRng(0L);
		assertTrue(below.chance(7, 10));
		assertEquals(0x9e3779b97f4a7c15L, below.state());
		ScriptEncounterRng above = new ScriptEncounterRng(0L);
		assertFalse(above.chance(6, 10));
		assertEquals(0x9e3779b97f4a7c15L, above.state());
	}

	@Test
	public void invalidZeroAndEqualChanceNeverAdvance() {
		ScriptEncounterRng rng = new ScriptEncounterRng(0L);
		assertFalse(rng.chance(6, 5));
		assertFalse(rng.chance(7, 1000001));
		assertFalse(rng.chance(-1, 10));
		assertFalse(rng.chance(0, 10));
		assertTrue(rng.chance(10, 10));
		assertEquals(0L, rng.state());
		assertEquals(0xe220a8397b1dcdafL, rng.nextLong());
	}

	@Test
	public void copyIsIndependentAndRestoreIsExact() {
		ScriptEncounterRng original = new ScriptEncounterRng(0L);
		ScriptEncounterRng copy = original.copy();
		assertEquals(0L, copy.state());
		original.nextInt(10);
		assertEquals(0L, copy.state());
		copy.restore(original.state());
		assertEquals(original.state(), copy.state());
		assertEquals(original.nextInt(10), copy.nextInt(10));
	}
}
