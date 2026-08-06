package com.rs2.script;

/**
 * Shared validation helpers for Graal guest values at the JS-Java boundary.
 *
 * <p>Facade methods should use these helpers instead of duplicating coercion
 * rules. Invalid guest input is rejected by returning {@code null} or
 * {@code false} rather than throwing into script code.
 */
public final class BridgeValidation {

	/**
	 * Coerces one guest numeric value to a whole-number {@code int} in range.
	 *
	 * @return the integral value, or {@code null} when the input is not a finite
	 *         whole number within {@code [min, max]}
	 */
	public static Integer integral(double value, int min, int max) {
		if (!Double.isFinite(value) || value != Math.rint(value)
				|| value < min || value > max) {
			return null;
		}
		return Integer.valueOf((int) value);
	}

	/**
	 * Returns {@code text} when non-null; otherwise {@code null}.
	 *
	 * <p>Graal coerces JavaScript {@code null} to the literal string
	 * {@code "null"} for {@link String} parameters unless the host rejects it
	 * first.
	 */
	public static String nonNullString(String text) {
		return text;
	}

	/**
	 * Returns {@code true} when {@code text} is non-null and non-empty after
	 * trimming is not required (game messages preserve spacing).
	 */
	public static boolean hasText(String text) {
		return text != null;
	}

	private BridgeValidation() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
