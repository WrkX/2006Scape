package com.rs2.script.state;

/**
 * Raised when a stored value exists but is read through the wrong typed API.
 */
public final class ScriptStateTypeException extends ScriptStateException {

	private static final long serialVersionUID = 1L;

	public ScriptStateTypeException(String message) {
		super(message);
	}
}
