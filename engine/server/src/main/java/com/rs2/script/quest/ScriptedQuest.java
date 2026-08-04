package com.rs2.script.quest;

import java.util.function.BooleanSupplier;

import org.graalvm.polyglot.HostAccess;

import com.rs2.game.players.Player;
import com.rs2.script.registries.QuestRegistry;

/**
 * Fixed-player/fixed-id quest capability exposed to guest handlers.
 */
public final class ScriptedQuest {

	private final Player player;
	private final String questId;
	private final QuestService service;
	private final BooleanSupplier mutationAllowed;

	public ScriptedQuest(Player player, String questId) {
		this(player, questId, QuestService.getInstance(), () -> true);
	}

	public ScriptedQuest(Player player, String questId,
			BooleanSupplier mutationAllowed) {
		this(player, questId, QuestService.getInstance(), mutationAllowed);
	}

	ScriptedQuest(Player player, String questId, QuestService service) {
		this(player, questId, service, () -> true);
	}

	private ScriptedQuest(Player player, String questId, QuestService service,
			BooleanSupplier mutationAllowed) {
		this.player = player;
		this.questId = questId;
		this.service = service;
		this.mutationAllowed = mutationAllowed;
	}

	private QuestDefinition definition() {
		QuestDefinition definition = QuestRegistry.get(questId);
		if (definition == null) {
			throw new IllegalStateException("Quest is no longer registered: " + questId);
		}
		return definition;
	}

	@HostAccess.Export
	public String id() {
		return questId;
	}

	@HostAccess.Export
	public String name() {
		return definition().getName();
	}

	@HostAccess.Export
	public String summary() {
		return definition().getSummary();
	}

	@HostAccess.Export
	public String state() {
		return service.state(player, questId).getScriptName();
	}

	@HostAccess.Export
	public Integer stage() {
		return service.stage(player, questId);
	}

	@HostAccess.Export
	public String objective() {
		return service.objective(player, definition());
	}

	@HostAccess.Export
	public QuestResult canStart() {
		return service.canStart(player, definition());
	}

	@HostAccess.Export
	public QuestResult start() {
		return mutationAllowed.getAsBoolean()
				? service.start(player, definition()) : mutationRejected();
	}

	@HostAccess.Export
	public QuestResult setStage(int expectedCurrent, int nextStage) {
		return mutationAllowed.getAsBoolean()
				? service.setStage(player, definition(), expectedCurrent, nextStage)
				: mutationRejected();
	}

	@HostAccess.Export
	public QuestResult advance(int expectedCurrent) {
		return mutationAllowed.getAsBoolean()
				? service.advance(player, definition(), expectedCurrent)
				: mutationRejected();
	}

	@HostAccess.Export
	public QuestResult complete(int expectedFinalStage) {
		return mutationAllowed.getAsBoolean()
				? service.complete(player, definition(), expectedFinalStage)
				: mutationRejected();
	}

	private static QuestResult mutationRejected() {
		return QuestResult.unchanged(false, QuestResultCode.STATE_FAILED);
	}
}
