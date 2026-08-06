package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** Range and postcondition checks for the WP5 player capabilities. */
public class ScriptedPlayerCapabilityTest {
    @Test public void equipmentCombatMovementAndPresentationAreTruthful() throws Exception {
        com.rs2.game.players.Player player = Wp5PlayerSupport.player(92);
        try {
            ScriptedPlayer scripted = Wp5PlayerSupport.scripted(player);
            player.playerEquipment[player.playerWeapon] = 4151;
            player.playerEquipmentN[player.playerWeapon] = 1;
            assertEquals(4151, scripted.getEquipment().get("weapon").intValue());
            assertEquals(1, scripted.getEquipment().amount("weapon"));
            assertEquals(null, scripted.getEquipment().get("unknown"));
            assertEquals(0, scripted.getCombat().damage(Double.NaN));
            assertFalse(scripted.getMovement().setRunEnergy(101));
            assertTrue(scripted.getMovement().setRunEnergy(50));
            assertEquals(50, scripted.getMovement().runEnergy());
			assertTrue(scripted.getMovement().teleport(Wp5PlayerSupport.X + 1,
					Wp5PlayerSupport.Y + 1, 0));
			assertEquals(Wp5PlayerSupport.X + 1, player.absX);
			assertEquals(Wp5PlayerSupport.Y + 1, player.absY);
			assertEquals(0, player.heightLevel);
			assertTrue(player.didTeleport);
			assertTrue(player.preserveScriptTeleportUpdate);
			assertFalse(scripted.getMovement().teleport(Wp5PlayerSupport.X + 2,
					Wp5PlayerSupport.Y + 2, 0));
			assertEquals(Wp5PlayerSupport.X + 1, player.absX);
			assertEquals(Wp5PlayerSupport.Y + 1, player.absY);
			player.getNextPlayerMovement();
			assertTrue(player.didTeleport);
			assertFalse(player.preserveScriptTeleportUpdate);
			assertEquals(-1, player.teleportToX);
			assertEquals(-1, player.teleportToY);
            assertFalse(scripted.getPresentation().animate(65536, 0));
        } finally {
            Wp5PlayerSupport.cleanup(player);
        }
    }

    @Test public void magicEquipmentBonusesAndCombatStateAreTruthful() throws Exception {
        com.rs2.game.players.Player player = Wp5PlayerSupport.player(93);
        try {
            ScriptedPlayer scripted = Wp5PlayerSupport.scripted(player);
            assertEquals(0, scripted.getMagic().findIndex(1152));
            assertEquals(1, scripted.getMagic().requiredLevel(1152));
            assertTrue(scripted.getMagic().hasLevel(1152));
            assertFalse(scripted.getMagic().hasRunes(1152));
            player.playerItems[0] = 557;
            player.playerItemsN[0] = 1;
            player.playerItems[1] = 559;
            player.playerItemsN[1] = 1;
            assertTrue(scripted.getMagic().hasRunes(1152));
            assertTrue(scripted.getMagic().consumeRunes(1152));
            assertFalse(scripted.getMagic().hasRunes(1152));

            player.playerEquipment[player.playerWeapon] = 4151;
            player.playerBonus[10] = 999;
            assertEquals(0, scripted.getEquipment().bonus(10));
            assertEquals("Strength", scripted.getEquipment().bonusName(10));

            player.underAttackBy = 1;
            assertTrue(scripted.getCombat().underAttack());
            player.poisonDamage = 4;
            assertTrue(scripted.getCombat().poisoned());
        } finally {
            Wp5PlayerSupport.cleanup(player);
        }
    }
}
