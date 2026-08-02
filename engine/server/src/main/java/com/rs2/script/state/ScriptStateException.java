package com.rs2.script.state;

/**
 * Raised when script-owned player state violates its public contract.
 */
public class ScriptStateException extends IllegalArgumentException {

	private static final long serialVersionUID = 1L;

	public ScriptStateException(String message) {
		super(message);
	}

	public ScriptStateException(String message, Throwable cause) {
		super(message, cause);
	}
}
