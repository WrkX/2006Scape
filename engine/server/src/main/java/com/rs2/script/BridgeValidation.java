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

	private static final String GRAAL_NULL_LITERAL = "null";

	private static boolean isGraalNull(String text) {
		return text == null || GRAAL_NULL_LITERAL.equals(text);
	}

	/**
	 * Returns {@code text} unchanged, or {@code null} when the guest value is
	 * absent.
	 *
	 * <p>Graal coerces JavaScript {@code null} to the literal string
	 * {@code "null"} for {@link String} parameters; this helper maps both Java
	 * {@code null} and that literal back to {@code null}.
	 */
	public static String nonNullString(String text) {
		return isGraalNull(text) ? null : text;
	}

	/**
	 * Returns {@code true} when {@code text} may be sent as a game message.
	 *
	 * <p>Rejects Java {@code null} and Graal's coerced {@code "null"} literal.
	 * Empty strings are accepted; trimming is not applied because game messages
	 * preserve spacing.
	 */
	public static boolean hasText(String text) {
		return !isGraalNull(text);
	}

	private BridgeValidation() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
