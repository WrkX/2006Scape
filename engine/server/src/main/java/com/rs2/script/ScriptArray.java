package com.rs2.script;

import java.util.Collection;

import org.graalvm.polyglot.HostAccess;

/**
 * Immutable copied array surface for guest code.
 */
public final class ScriptArray {

	private final Object[] values;

	public ScriptArray(Collection<?> source) {
		this.values = source == null ? new Object[0] : source.toArray();
	}

	public ScriptArray(Object[] source) {
		this.values = source == null ? new Object[0]
				: java.util.Arrays.copyOf(source, source.length);
	}

	@HostAccess.Export
	public int length() {
		return values.length;
	}

	@HostAccess.Export
	public Object get(double index) {
		if (!Double.isFinite(index) || index != Math.rint(index)
				|| index < 0 || index >= values.length) {
			return null;
		}
		return values[(int) index];
	}
}
