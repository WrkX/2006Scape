package com.rs2.game.dialogues;

import com.rs2.game.players.Player;
import com.rs2.net.Packet;
import com.rs2.net.packets.PacketType;
import com.rs2.script.DialogueChain;

/**
 * Dialogue continue handler.
 *
 * <p>When the player clicks "Click here to continue" the client sends this
 * packet. If {@link Player#nextChat} is set to the script-chain sentinel the
 * next buffered script dialogue frame is played. Otherwise the normal
 * {@link DialogueHandler#sendDialogues} dispatch runs.
 */
public class Dialogue implements PacketType {

    @Override
    public void processPacket(Player c, Packet packet) {
        if (c.nextChat == DialogueChain.CHAIN_SENTINEL) {
            DialogueChain.playNext(c);
        } else if (c.nextChat > 0) {
            c.getDialogueHandler().sendDialogues(c.nextChat, c.talkingNpc);
        } else {
            c.getDialogueHandler().sendDialogues(0, -1);
        }
    }
}
