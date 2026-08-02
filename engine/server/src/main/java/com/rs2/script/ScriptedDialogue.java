package com.rs2.script;

import com.rs2.game.dialogues.DialogueHandler;
import com.rs2.game.npcs.NPCDefinition;
import com.rs2.game.players.Player;
import com.rs2.script.world.ScriptEncounterService;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;

/**
 * Fluent script dialogue builder.
 *
 * <p>Every frame ({@link #npc(int, String)}, {@link #player(String)},
 * {@link #statement(String)}) is silently buffered. The chain starts
 * playing only when a terminal frame ({@link #options(String[], Value)},
 * {@link #itemDialogue(int, String, String[])}) is added, or when
 * {@link #end()} is called explicitly.
 *
 * <p>A single instance is cached on {@link ScriptedPlayer} and reused.
 * After each flush the buffer is replaced so callback-driven follow-ups
 * (inside {@code options(...)}) build a fresh chain.
 */
public class ScriptedDialogue {

    private final Player player;
    private final DialogueHandler dialogueHandler;
    private final long generation;
    private final long facadeEpoch;
    private final BooleanSupplier mutationAllowed;
    private List<Runnable> frames = new ArrayList<>();

    ScriptedDialogue(Player p, long generation, long facadeEpoch,
            BooleanSupplier mutationAllowed) {
        this.player = p;
        this.dialogueHandler = p.getDialogueHandler();
        this.generation = generation;
        this.facadeEpoch = facadeEpoch;
        this.mutationAllowed = mutationAllowed;
    }

    // ── NPC chat frames ──────────────────────────────────────────────────────

    @HostAccess.Export
    public ScriptedDialogue npc(int npcId, String line) {
        if (!canMutate()) return this;
        String name = resolveNpcName(npcId);
        addFrame(() -> dialogueHandler.sendNpcChat1(line, npcId, name));
        return this;
    }

    @HostAccess.Export
    public ScriptedDialogue npc(int npcId, String line, String line2) {
        if (!canMutate()) return this;
        String name = resolveNpcName(npcId);
        addFrame(() -> dialogueHandler.sendNpcChat2(line, line2, npcId, name));
        return this;
    }

    @HostAccess.Export
    public ScriptedDialogue npc(int npcId, String line, String line2, String line3) {
        if (!canMutate()) return this;
        String name = resolveNpcName(npcId);
        addFrame(() -> dialogueHandler.sendNpcChat3(line, line2, line3, npcId, name));
        return this;
    }

    @HostAccess.Export
    public ScriptedDialogue npc(int npcId, String line, String line2, String line3, String line4) {
        if (!canMutate()) return this;
        String name = resolveNpcName(npcId);
        addFrame(() -> dialogueHandler.sendNpcChat4(line, line2, line3, line4, npcId, name));
        return this;
    }

    // ── Player chat frames ───────────────────────────────────────────────────

    @HostAccess.Export
    public ScriptedDialogue player(String line) {
        if (!canMutate()) return this;
        addFrame(() -> dialogueHandler.sendPlayerChat(line));
        return this;
    }

    @HostAccess.Export
    public ScriptedDialogue player(String line, String line2) {
        if (!canMutate()) return this;
        addFrame(() -> dialogueHandler.sendPlayerChat(line, line2));
        return this;
    }

    @HostAccess.Export
    public ScriptedDialogue player(String line, String line2, String line3) {
        if (!canMutate()) return this;
        addFrame(() -> dialogueHandler.sendPlayerChat(line, line2, line3));
        return this;
    }

    @HostAccess.Export
    public ScriptedDialogue player(String line, String line2, String line3, String line4) {
        if (!canMutate()) return this;
        addFrame(() -> dialogueHandler.sendPlayerChat(line, line2, line3, line4));
        return this;
    }

    // ── Statement frames ─────────────────────────────────────────────────────

    @HostAccess.Export
    public ScriptedDialogue statement(String line) {
        if (!canMutate()) return this;
        addFrame(() -> dialogueHandler.sendStatement(line));
        return this;
    }

    @HostAccess.Export
    public ScriptedDialogue statement(String line, String line2) {
        if (!canMutate()) return this;
        addFrame(() -> dialogueHandler.sendStatement(line, line2));
        return this;
    }

    @HostAccess.Export
    public ScriptedDialogue statement(String line, String line2, String line3) {
        if (!canMutate()) return this;
        addFrame(() -> dialogueHandler.sendStatement(line, line2, line3));
        return this;
    }

    @HostAccess.Export
    public ScriptedDialogue statement(String line, String line2, String line3, String line4) {
        if (!canMutate()) return this;
        addFrame(() -> dialogueHandler.sendStatement(line, line2, line3, line4));
        return this;
    }

    // ── Options (terminal — flushes) ─────────────────────────────────────────

    @HostAccess.Export
    public ScriptedDialogue options(String[] lines, Value callback) {
        if (!canMutate()) return this;
        if (lines == null || lines.length < 2 || lines.length > 5) {
            throw new IllegalArgumentException(
                    "options() requires between 2 and 5 lines, got "
                            + (lines == null ? "null" : lines.length));
        }
        if (callback == null || !callback.canExecute()) {
            throw new IllegalArgumentException("options() callback must be executable");
        }
        final Value cb = callback;
        addFrame(() -> {
            dialogueHandler.sendOption(lines);
            ScriptEncounterService.getInstance().armDialogueOption(
                    player, generation, facadeEpoch, lines.length,
                    choice -> ScriptExecutor.execute(cb, "dialogue", "options",
                            String.valueOf(choice), choice));
        });
        flush();
        return this;
    }

    // ── Item dialogue (terminal — flushes) ───────────────────────────────────

    @HostAccess.Export
    public ScriptedDialogue itemDialogue(int itemId, String header, String[] lines) {
        if (!canMutate()) return this;
        if (lines == null) {
            throw new IllegalArgumentException("itemDialogue() lines must not be null");
        }
        addFrame(() -> dialogueHandler.sendItemChat(itemId, 200, header, lines));
        flush();
        return this;
    }

    // ── Finalisation (flush) ─────────────────────────────────────────────────

    @HostAccess.Export
    public void end() {
        if (!canMutate()) return;
        if (frames.isEmpty()) {
            player.pendingScriptOption = null;
            player.pendingOptionCount = 0;
            player.pendingScriptOptionGeneration = 0L;
            player.pendingScriptOptionFacadeEpoch = 0L;
            player.pendingScriptOptionToken = 0L;
            dialogueHandler.endDialogue();
            player.getPacketSender().closeAllWindows();
        } else {
            flush();
        }
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private void flush() {
        if (!canMutate() || frames.isEmpty()) {
            return;
        }
        List<Runnable> snapshot = frames;
        frames = new ArrayList<>();
        DialogueChain.start(player, snapshot, generation, facadeEpoch);
    }

    private boolean canMutate() {
        return mutationAllowed != null && mutationAllowed.getAsBoolean();
    }

    private void addFrame(Runnable frame) {
        frames.add(() -> {
            if (ScriptEncounterService.getInstance()
                    .canAdvanceDialogueChain(player)) {
                frame.run();
            }
        });
    }

    private static String resolveNpcName(int npcId) {
        NPCDefinition def = NPCDefinition.forId(npcId);
        String name = def == null ? null : def.getName();
        return name == null ? "NPC" : name;
    }
}
