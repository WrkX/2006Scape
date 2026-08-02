package com.rs2.script;

import com.rs2.game.players.Player;
import com.rs2.script.world.ScriptEncounterService;

import java.util.List;

/**
 * Drives sequential playback of buffered dialogue frames.
 *
 * <p>{@link #start(Player, List)} sends the first frame and queues the rest.
 * Each subsequent "Click to continue" advances one frame via
 * {@link #playNext(Player)}.
 */
public final class DialogueChain {

    /** Sentinel placed in {@link Player#nextChat} to signal a buffered frame. */
    public static final int CHAIN_SENTINEL = Integer.MIN_VALUE;

    /**
     * Start playing the given frames. The first frame is sent immediately;
     * remaining frames play one per continue-click.
     */
    public static void start(Player player, List<Runnable> frames,
            long generation, long facadeEpoch) {
        long token = ScriptEncounterService.getInstance().armDialogueChain(
                player, generation, facadeEpoch);
        if (token == 0L) {
            return;
        }
        player.scriptDialogueFrames = frames;
        player.scriptDialogueFrameIndex = 0;
        advance(player);
    }

    /**
     * Called from the continue packet handler when
     * {@code player.nextChat == CHAIN_SENTINEL}.
     */
    public static void playNext(Player player) {
        advance(player);
    }

    private static void advance(Player player) {
        ScriptEncounterService service = ScriptEncounterService.getInstance();
        if (!service.canAdvanceDialogueChain(player)) {
            return;
        }
        List<Runnable> frames = player.scriptDialogueFrames;
        int index = player.scriptDialogueFrameIndex;

        if (frames == null || index >= frames.size()) {
            service.invalidateDialogue(player);
            player.getDialogueHandler().endDialogue();
            player.getPacketSender().closeAllWindows();
            return;
        }

        long token = player.scriptDialogueToken;
        player.scriptDialogueFrameIndex = index + 1;
        frames.get(index).run();

        if (!service.canAdvanceDialogueChain(player)) {
            return;
        }
        if (player.scriptDialogueFrameIndex < frames.size()) {
            player.nextChat = CHAIN_SENTINEL;
        } else {
            service.completeDialogueChain(player, token);
        }
    }

    private DialogueChain() {
        throw new UnsupportedOperationException("static utility");
    }
}
