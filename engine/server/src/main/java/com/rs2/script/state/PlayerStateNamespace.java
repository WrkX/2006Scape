package com.rs2.script.state;

import java.util.function.BooleanSupplier;

import org.graalvm.polyglot.HostAccess;

/**
 * Fixed-namespace guest capability for one player's primitive state.
 */
public final class PlayerStateNamespace {

	private final ScriptStateStore store;
	private final String namespace;
	private final BooleanSupplier mutationAllowed;

	public PlayerStateNamespace(ScriptStateStore store, String namespace) {
		this(store, namespace, new BooleanSupplier() {
			@Override
			public boolean getAsBoolean() {
				return true;
			}
		});
	}

	public PlayerStateNamespace(ScriptStateStore store, String namespace,
			BooleanSupplier mutationAllowed) {
		ScriptStateLimits.validatePublicNamespace(namespace);
		this.store = store;
		this.namespace = namespace;
		this.mutationAllowed = mutationAllowed;
	}

	@HostAccess.Export
	public boolean has(String key) {
		return store.has(namespace, key);
	}

	@HostAccess.Export
	public Boolean getBoolean(String key) {
		ScriptStateValue value = store.get(namespace, key);
		return value == null ? null : Boolean.valueOf(value.asBoolean());
	}

	@HostAccess.Export
	public boolean getBooleanOr(String key, boolean fallback) {
		Boolean value = getBoolean(key);
		return value == null ? fallback : value.booleanValue();
	}

	@HostAccess.Export
	public boolean setBoolean(String key, boolean value) {
		return mutationAllowed.getAsBoolean()
				&& store.set(namespace, key, ScriptStateValue.of(value));
	}

	@HostAccess.Export
	public Double getNumber(String key) {
		ScriptStateValue value = store.get(namespace, key);
		return value == null ? null : Double.valueOf(value.asNumber());
	}

	@HostAccess.Export
	public double getNumberOr(String key, double fallback) {
		ScriptStateValue.of(fallback);
		Double value = getNumber(key);
		return value == null ? fallback : value.doubleValue();
	}

	@HostAccess.Export
	public boolean setNumber(String key, double value) {
		return mutationAllowed.getAsBoolean()
				&& store.set(namespace, key, ScriptStateValue.of(value));
	}

	@HostAccess.Export
	public String getString(String key) {
		ScriptStateValue value = store.get(namespace, key);
		return value == null ? null : value.asString();
	}

	@HostAccess.Export
	public String getStringOr(String key, String fallback) {
		ScriptStateValue.of(fallback);
		String value = getString(key);
		return value == null ? fallback : value;
	}

	@HostAccess.Export
	public boolean setString(String key, String value) {
		return mutationAllowed.getAsBoolean()
				&& store.set(namespace, key, ScriptStateValue.of(value));
	}

	@HostAccess.Export
	public boolean remove(String key) {
		return mutationAllowed.getAsBoolean()
				&& store.remove(namespace, key);
	}
}
