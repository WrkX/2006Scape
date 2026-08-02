package com.rs2.script.state;

import java.util.Objects;

/**
 * One immutable primitive script-state value.
 */
public final class ScriptStateValue {

	public enum Type {
		BOOLEAN,
		NUMBER,
		STRING
	}

	private final Type type;
	private final Object value;

	private ScriptStateValue(Type type, Object value) {
		this.type = type;
		this.value = value;
	}

	public static ScriptStateValue of(boolean value) {
		return new ScriptStateValue(Type.BOOLEAN, Boolean.valueOf(value));
	}

	public static ScriptStateValue of(double value) {
		if (Double.isNaN(value) || Double.isInfinite(value)) {
			throw new ScriptStateException("State numbers must be finite");
		}
		return new ScriptStateValue(Type.NUMBER,
				Double.valueOf(value == 0.0d ? 0.0d : value));
	}

	public static ScriptStateValue of(String value) {
		if (value == null) {
			throw new ScriptStateException("State strings must not be null");
		}
		ScriptStateLimits.validateString(value);
		return new ScriptStateValue(Type.STRING, value);
	}

	public Type getType() {
		return type;
	}

	public boolean asBoolean() {
		require(Type.BOOLEAN);
		return ((Boolean) value).booleanValue();
	}

	public double asNumber() {
		require(Type.NUMBER);
		return ((Double) value).doubleValue();
	}

	public String asString() {
		require(Type.STRING);
		return (String) value;
	}

	private void require(Type expected) {
		if (type != expected) {
			throw new ScriptStateTypeException(
					"State value is " + type.name().toLowerCase()
					+ ", not " + expected.name().toLowerCase());
		}
	}

	@Override
	public boolean equals(Object other) {
		if (!(other instanceof ScriptStateValue)) {
			return false;
		}
		ScriptStateValue that = (ScriptStateValue) other;
		return type == that.type && Objects.equals(value, that.value);
	}

	@Override
	public int hashCode() {
		return Objects.hash(type, value);
	}
}
