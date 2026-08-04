package com.rs2.game.objects.impl;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class KeldagrimStairsTest {

	@Test
	public void palaceStairIsRecognized() {
		assertTrue(KeldagrimStairs.isStaircase(6100, 2872, 10195, 0));
		assertTrue(KeldagrimStairs.isStaircase(6101, 2866, 10199, 0));
	}

	@Test
	public void blastFurnaceDoorIsNotTreatedAsStair() {
		assertFalse(KeldagrimStairs.isStaircase(6106, 2913, 10180, 0));
		assertFalse(KeldagrimStairs.isStaircase(6109, 2914, 10196, 0));
		assertFalse(KeldagrimStairs.isStaircase(6975, 2930, 10195, 0));
	}

	@Test
	public void blastFurnaceStairIsRecognized() {
		assertTrue(KeldagrimStairs.isStaircase(6108, 2913, 10167, 0));
		assertTrue(KeldagrimStairs.isStaircase(9138, 2913, 10167, 0));
	}

	@Test
	public void blastFurnaceBasementStairIsRecognized() {
		assertTrue(KeldagrimStairs.isStaircase(6108, 1939, 4956, 0));
		assertTrue(KeldagrimStairs.isStaircase(9138, 1945, 4950, 0));
	}

	@Test
	public void blastFurnaceStairOutsideBuildingIsIgnored() {
		// (2872, 10167) is outside the palace and blast-furnace rectangles.
		assertFalse(KeldagrimStairs.isStaircase(6108, 2872, 10167, 0));
	}
}
