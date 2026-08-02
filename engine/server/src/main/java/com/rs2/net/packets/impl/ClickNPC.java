package com.rs2.net.packets.impl;

import com.rs2.event.CycleEvent;
import com.rs2.event.CycleEventContainer;
import com.rs2.event.CycleEventHandler;
import com.rs2.event.Event;
import com.rs2.event.impl.NpcFirstClickEvent;
import com.rs2.event.impl.NpcSecondClickEvent;
import com.rs2.event.impl.NpcThirdClickEvent;
import com.rs2.game.content.StaticNpcList;
import com.rs2.game.content.combat.CombatConstants;
import com.rs2.game.content.combat.magic.MagicData;
import com.rs2.game.content.combat.range.RangeData;
import com.rs2.game.items.DeprecatedItems;
import com.rs2.game.npcs.NpcHandler;
import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;
import com.rs2.script.registries.NpcHandlerRegistry;
import com.rs2.script.world.ScriptNpcService;

/**
 * Click NPC
 */
public class ClickNPC implements PacketType {

	public static final int ATTACK_NPC = 72, MAGE_NPC = 131, FIRST_CLICK = 155,
			SECOND_CLICK = 17, THIRD_CLICK = 21;

	static boolean postLegacyEventIfUnscripted(Player player,
			boolean handledByScript, Event event) {
		if (handledByScript) {
			return false;
		}
		player.post(event);
		return true;
	}

	static boolean isScriptedClick(int npcId, String action) {
		return NpcHandlerRegistry.get(npcId, action) != null;
	}

	@Override
	public void processPacket(final Player player, Packet packet) {
		if (ScriptInteractionGate.isActionLocked(player)) {
			return;
		}
		int attackNpcIndex;
		int clickNpcIndex;
		switch (packet.getOpcode()) {

		/**
		 * Attack npc melee or range
		 **/
		case ATTACK_NPC:
			attackNpcIndex = packet.readUnsignedWordA();
			if (!validTarget(player, attackNpcIndex)
					|| NpcHandler.npcs[attackNpcIndex].MaxHP == 0) {
				return;
			}
			prepareNpcInteraction(player);
			player.npcIndex = attackNpcIndex;
			player.npcAllocationToken = NpcHandler.npcs[attackNpcIndex]
					.allocationToken();
			if (player.tutorialProgress == 24) {
				player.getPacketSender().chatbox(6180);
				player.getDialogueHandler()
						.chatboxText(
								"While you are fighting you will see a bar over your head. The",
								"bar shows how much health you have left. Your opponent will",
								"have one too. You will continue to attack the rat until it's dead",
								"or you do something else.",
								"Sit back and watch");
				player.getPacketSender().chatbox(6179);

			}
			if (player.tutorialProgress == 33) {
				player.getPacketSender()
						.sendMessage(
								"You can't range these chickens you have to mage them!");
				return;
			}
			if (!player.mageAllowed) {
				player.mageAllowed = true;
				player.getPacketSender().sendMessage("I can't reach that.");
				break;
			}
			if (player.autocastId > 0) {
				player.autocasting = true;
			}
			if (!player.autocasting && player.spellId > 0) {
				player.spellId = 0;
			}
			player.faceUpdate(player.npcIndex);
			player.usingMagic = false;
			boolean usingBow = false;
			boolean usingOtherRangeWeapons = false;
			boolean usingArrows = false;
			boolean usingCross = player.playerEquipment[player.playerWeapon] == 9185;
			if (player.playerEquipment[player.playerWeapon] >= 4214
					&& player.playerEquipment[player.playerWeapon] <= 4223) {
				usingBow = true;
			}
			for (int bowId : RangeData.BOWS) {
				if (player.playerEquipment[player.playerWeapon] == bowId) {
					usingBow = true;
					for (int arrowId : RangeData.ARROWS) {
						if (player.playerEquipment[player.playerArrows] == arrowId) {
							usingArrows = true;
						}
					}
				}
			}
			for (int otherRangeId : RangeData.OTHER_RANGE_WEAPONS) {
				if (player.playerEquipment[player.playerWeapon] == otherRangeId) {
					usingOtherRangeWeapons = true;
				}
			}
			if ((usingBow || player.autocasting)
					&& player.goodDistance(player.getX(), player.getY(),
							NpcHandler.npcs[player.npcIndex].getX(),
							NpcHandler.npcs[player.npcIndex].getY(), 7)) {
				player.stopMovement();
			}

			if (usingOtherRangeWeapons
					&& player.goodDistance(player.getX(), player.getY(),
							NpcHandler.npcs[player.npcIndex].getX(),
							NpcHandler.npcs[player.npcIndex].getY(), 4)) {
				player.stopMovement();
			}
			if (!usingCross && !usingArrows && usingBow
					&& player.playerEquipment[player.playerWeapon] < 4212
					&& player.playerEquipment[player.playerWeapon] > 4223 && !usingCross) {
				player.getPacketSender().sendMessage(
						"You have run out of arrows!");
				break;
			}
			if (RangeData.correctBowAndArrows(player) < player.playerEquipment[player.playerArrows]
					&& CombatConstants.CORRECT_ARROWS
					&& usingBow
					&& !RangeData.usingCrystalBow(player)
					&& player.playerEquipment[player.playerWeapon] != 9185) {
				player.getPacketSender().sendMessage(
						"You can't use "
								+ DeprecatedItems.getItemName(
										player.playerEquipment[player.playerArrows])
										.toLowerCase()
								+ "s with a "
								+ DeprecatedItems.getItemName(
										player.playerEquipment[player.playerWeapon])
										.toLowerCase() + ".");
				player.stopMovement();
				player.getCombatAssistant().resetPlayerAttack();
				return;
			}
			if (player.playerEquipment[player.playerWeapon] == 9185
					&& !player.getCombatAssistant().properBolts()) {
				player.getPacketSender().sendMessage(
						"You must use bolts with a crossbow.");
				player.stopMovement();
				player.getCombatAssistant().resetPlayerAttack();
				return;
			}

			if (player.followPlayerId > 0) {
				player.getPlayerAssistant().resetFollow();
			}
			if (player.attackTimer <= 0) {
				player.getCombatAssistant().attackNpc(player.npcIndex);
				player.attackTimer++;
			}

			break;

		/**
		 * Attack npc with magic
		 **/
		case MAGE_NPC:
			int mageNpcIndex = packet.readSignedWordBigEndianA();
			int castingSpellId = packet.readSignedWordA();
			if (!validTarget(player, mageNpcIndex)
					|| NpcHandler.npcs[mageNpcIndex].MaxHP == 0
					|| NpcHandler.npcs[mageNpcIndex].npcType
							== StaticNpcList.COMBAT_INSTRUCTOR) {
				return;
			}
			prepareNpcInteraction(player);
			player.npcIndex = mageNpcIndex;
			player.npcAllocationToken = NpcHandler.npcs[mageNpcIndex]
					.allocationToken();
			if (player.tutorialProgress == 33) {
				player.getPacketSender().chatbox(6180);
				player.getDialogueHandler()
						.chatboxText(
								"",
								"All you need to do is move on to the mainland. Just speak",
								"with Terrova and he'll teleport you to Lumbridge Castle.",
								"", "You have almost completed the tutorial!");
				player.getPacketSender().chatbox(6179);
				// c.getDialogues().sendStatement4("You have almost completed the tutorial!",
				// "All you need to do is move on to the mainland. Just speak",
				// "with Terrova and he'll teleport you to Lumbridge.", "");
				player.tutorialProgress = 34;
				player.getPacketSender().createArrow(1, 9);
			}
			if (!player.mageAllowed) {
				player.mageAllowed = true;
				player.getPacketSender().sendMessage("I can't reach that.");
				break;
			}
			// c.usingSpecial = false;
			// c.getItems().updateSpecialBar();

			player.usingMagic = false;

			for (int i = 0; i < MagicData.MAGIC_SPELLS.length; i++) {
				if (castingSpellId == MagicData.MAGIC_SPELLS[i][0]) {
					player.spellId = i;
					player.usingMagic = true;
					break;
				}
			}

			if (player.autocasting) {
				player.autocasting = false;
			}

			if (player.usingMagic) {
				if (player.goodDistance(player.getX(), player.getY(),
						NpcHandler.npcs[player.npcIndex].getX(),
						NpcHandler.npcs[player.npcIndex].getY(), 6)) {
					player.stopMovement();
				}
				if (player.attackTimer <= 0) {
					player.getCombatAssistant().attackNpc(player.npcIndex);
					player.attackTimer++;
				}
			}

			break;

		case FIRST_CLICK:
			clickNpcIndex = packet.readSignedWordBigEndian();
			if (!validTarget(player, clickNpcIndex)) {
				return;
			}
			prepareNpcInteraction(player);
			player.npcClickIndex = clickNpcIndex;
			player.npcAllocationToken = NpcHandler.npcs[clickNpcIndex].allocationToken();
			player.npcType = NpcHandler.npcs[player.npcClickIndex].npcType;

			if (player.goodDistance(NpcHandler.npcs[player.npcClickIndex].getX(),
					NpcHandler.npcs[player.npcClickIndex].getY(), player.getX(),
					player.getY(), 2)) {
				player.turnPlayerTo(NpcHandler.npcs[player.npcClickIndex].getX(),
						NpcHandler.npcs[player.npcClickIndex].getY());
				NpcHandler.npcs[player.npcClickIndex].facePlayer(player);
				player.getNpcs().firstClickNpc(player.npcType);
				boolean firstHandledByScript =
						player.getNpcs().wasLastClickHandledByScript();
				postLegacyEventIfUnscripted(player, firstHandledByScript,
						new NpcFirstClickEvent(player.npcType));
			} else {
				player.clickNpcType = 1;
				   CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			            @Override
			            public void execute(CycleEventContainer container) {
						if (player.clickNpcType == 1
								&& player.npcClickIndex >= 0
								&& player.npcClickIndex < NpcHandler.npcs.length
								&& NpcHandler.npcs[player.npcClickIndex] != null
								&& (player.npcAllocationToken == 0L
										|| player.npcAllocationToken == NpcHandler.npcs[player.npcClickIndex].allocationToken())
								&& ScriptNpcService.getInstance().canAct(
										NpcHandler.npcs[player.npcClickIndex], player)) {
							if (player.goodDistance(player.getX(), player.getY(),
									NpcHandler.npcs[player.npcClickIndex].getX(),
									NpcHandler.npcs[player.npcClickIndex].getY(), 1)) {
								player.turnPlayerTo(
										NpcHandler.npcs[player.npcClickIndex].getX(),
										NpcHandler.npcs[player.npcClickIndex].getY());
								NpcHandler.npcs[player.npcClickIndex]
										.facePlayer(player);
								player.getNpcs().firstClickNpc(player.npcType);
								boolean handledByScript =
										player.getNpcs().wasLastClickHandledByScript();
								postLegacyEventIfUnscripted(player, handledByScript,
										new NpcFirstClickEvent(player.npcType));
								container.stop();
							}
						}
						if (player.clickNpcType == 0 || player.clickNpcType > 1) {
							container.stop();
						}
					}

					@Override
					public void stop() {
						player.clickNpcType = 0;
					}
				}, 1);
			}
			break;
		case SECOND_CLICK:
			clickNpcIndex = packet.readUnsignedWordBigEndianA();
			if (!validTarget(player, clickNpcIndex)) {
				return;
			}
			prepareNpcInteraction(player);
			player.npcClickIndex = clickNpcIndex;
			player.npcAllocationToken = NpcHandler.npcs[clickNpcIndex].allocationToken();
			player.npcType = NpcHandler.npcs[player.npcClickIndex].npcType;
			if (player.goodDistance(NpcHandler.npcs[player.npcClickIndex].getX(),
					NpcHandler.npcs[player.npcClickIndex].getY(), player.getX(),
					player.getY(), 2)) {
				player.turnPlayerTo(NpcHandler.npcs[player.npcClickIndex].getX(),
						NpcHandler.npcs[player.npcClickIndex].getY());
				NpcHandler.npcs[player.npcClickIndex].facePlayer(player);
				player.getNpcs().secondClickNpc(player.npcType);
				boolean secondHandledByScript =
						player.getNpcs().wasLastClickHandledByScript();
				postLegacyEventIfUnscripted(player, secondHandledByScript,
						new NpcSecondClickEvent(player.npcType));
			} else {
				player.clickNpcType = 2;
				   CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			            @Override
			            public void execute(CycleEventContainer container) {
						if (player.clickNpcType == 2
								&& player.npcClickIndex >= 0
								&& player.npcClickIndex < NpcHandler.npcs.length
								&& NpcHandler.npcs[player.npcClickIndex] != null
								&& (player.npcAllocationToken == 0L
										|| player.npcAllocationToken == NpcHandler.npcs[player.npcClickIndex].allocationToken())
								&& ScriptNpcService.getInstance().canAct(
										NpcHandler.npcs[player.npcClickIndex], player)) {
							if (player.goodDistance(player.getX(), player.getY(),
									NpcHandler.npcs[player.npcClickIndex].getX(),
									NpcHandler.npcs[player.npcClickIndex].getY(), 1)) {
								player.turnPlayerTo(
										NpcHandler.npcs[player.npcClickIndex].getX(),
										NpcHandler.npcs[player.npcClickIndex].getY());
								NpcHandler.npcs[player.npcClickIndex]
										.facePlayer(player);
								player.getNpcs().secondClickNpc(player.npcType);
								boolean handledByScript =
										player.getNpcs().wasLastClickHandledByScript();
								postLegacyEventIfUnscripted(player, handledByScript,
										new NpcSecondClickEvent(player.npcType));
								container.stop();
							}
						}
						if (player.clickNpcType < 2 || player.clickNpcType > 2) {
							container.stop();
						}
					}

					@Override
					public void stop() {
						player.clickNpcType = 0;
					}
				}, 1);
			}
			break;

		case THIRD_CLICK:
			clickNpcIndex = packet.readSignedWord();
			if (!validTarget(player, clickNpcIndex)) {
				return;
			}
 			prepareNpcInteraction(player);
			player.npcClickIndex = clickNpcIndex;
			player.npcAllocationToken = NpcHandler.npcs[clickNpcIndex].allocationToken();
			player.npcType = NpcHandler.npcs[player.npcClickIndex].npcType;
			if (player.goodDistance(NpcHandler.npcs[player.npcClickIndex].getX(),
					NpcHandler.npcs[player.npcClickIndex].getY(), player.getX(),
					player.getY(), 2)) {
				player.turnPlayerTo(NpcHandler.npcs[player.npcClickIndex].getX(),
						NpcHandler.npcs[player.npcClickIndex].getY());
				NpcHandler.npcs[player.npcClickIndex].facePlayer(player);
				player.getNpcs().thirdClickNpc(player.npcType);
				boolean thirdHandledByScript =
						player.getNpcs().wasLastClickHandledByScript();
				postLegacyEventIfUnscripted(player, thirdHandledByScript,
						new NpcThirdClickEvent(player.npcType));
			} else {
				player.clickNpcType = 3;
				   CycleEventHandler.getSingleton().addEvent(player, new CycleEvent() {
			            @Override
			            public void execute(CycleEventContainer container) {
						if (player.clickNpcType == 3
								&& player.npcClickIndex >= 0
								&& player.npcClickIndex < NpcHandler.npcs.length
								&& NpcHandler.npcs[player.npcClickIndex] != null
								&& (player.npcAllocationToken == 0L
										|| player.npcAllocationToken == NpcHandler.npcs[player.npcClickIndex].allocationToken())
								&& ScriptNpcService.getInstance().canAct(
										NpcHandler.npcs[player.npcClickIndex], player)) {
							if (player.goodDistance(player.getX(), player.getY(),
									NpcHandler.npcs[player.npcClickIndex].getX(),
									NpcHandler.npcs[player.npcClickIndex].getY(), 1)) {
								player.turnPlayerTo(
										NpcHandler.npcs[player.npcClickIndex].getX(),
										NpcHandler.npcs[player.npcClickIndex].getY());
								NpcHandler.npcs[player.npcClickIndex]
										.facePlayer(player);
								player.getNpcs().thirdClickNpc(player.npcType);
								boolean handledByScript =
										player.getNpcs().wasLastClickHandledByScript();
								postLegacyEventIfUnscripted(player, handledByScript,
										new NpcThirdClickEvent(player.npcType));
								container.stop();
							}
						}
						if (player.clickNpcType < 3) {
							container.stop();
						}
					}

					@Override
					public void stop() {
						player.clickNpcType = 0;
					}
				}, 1);
			}
			break;
		}

	}

	private static boolean validTarget(Player player, int npcIndex) {
		return player != null && npcIndex >= 0
				&& npcIndex < NpcHandler.npcs.length
				&& NpcHandler.npcs[npcIndex] != null
				&& ScriptNpcService.getInstance().canAct(
						NpcHandler.npcs[npcIndex], player);
	}

	private static void prepareNpcInteraction(Player player) {
		player.npcIndex = 0;
		player.npcClickIndex = 0;
		player.playerIndex = 0;
		player.clickNpcType = 0;
		player.getPlayerAssistant().resetFollow();
		player.getCombatAssistant().resetPlayerAttack();
		player.getPlayerAssistant().requestUpdates();
		player.endCurrentTask();
	}
}
