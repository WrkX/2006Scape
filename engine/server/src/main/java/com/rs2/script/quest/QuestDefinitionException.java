package com.rs2.script.quest;

/**
 * Load-fatal validation failure for a scripted quest descriptor.
 */
public final class QuestDefinitionException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public QuestDefinitionException(String message) {
		super(message);
	}
}
