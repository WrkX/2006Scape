package com.rs2.game.content.skills.woodcutting;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WoodcuttingTest {

	@Test
	public void playerTreesRecognizesHollowTrees() {
		assertTrue(Woodcutting.playerTrees(null, 2289));
		assertTrue(Woodcutting.playerTrees(null, 4060));
	}

	@Test
	public void playerTreesStillRecognizesNormalTreesBeyondFirstSixIds() {
		assertTrue(Woodcutting.playerTrees(null, 4060));
		assertTrue(Woodcutting.playerTrees(null, 1309));
	}

	@Test
	public void playerTreesRejectsNonTrees() {
		assertFalse(Woodcutting.playerTrees(null, 1));
		assertFalse(Woodcutting.playerTrees(null, 995));
	}

}
