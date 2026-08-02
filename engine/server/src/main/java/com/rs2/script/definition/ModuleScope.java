package com.rs2.script.definition;

import java.util.regex.Pattern;

import com.rs2.script.registries.RegistryStore;

/**
 * Synchronous per-candidate module registration scope.
 *
 * <p>Every definition and executable route registered while a scope is active
 * carries that module's logical id and declared schema version. Registrations
 * outside any scope are recorded with {@link #LEGACY_SOURCE} and the
 * generated compatibility schema version {@code 0}, keeping the existing
 * direct-import loader working unchanged.
 */
public final class ModuleScope {

	/** Source identity of registrations made outside any module scope. */
	public static final String LEGACY_SOURCE = "legacy-unscoped";

	/** Compatibility schema version of legacy-unscoped registrations. */
	public static final int LEGACY_SCHEMA_VERSION = 0;

	private static final int MAX_MODULE_ID_LENGTH = 64;
	private static final int MAX_SCHEMA_VERSION = 255;
	private static final Pattern MODULE_ID_PATTERN = Pattern.compile(
			"[a-zA-Z0-9][a-zA-Z0-9._-]*");

	private static final ThreadLocal<String> ACTIVE = new ThreadLocal<>();
	private static final ThreadLocal<Integer> VERSION = new ThreadLocal<>();

	private ModuleScope() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}

	/**
	 * Opens a module scope on the current thread. Rejects nested scopes,
	 * duplicate module ids, invalid ids, and invalid declared versions.
	 */
	public static void begin(String id, int schemaVersion) {
		if (!RegistryStore.isStagingActive()) {
			throw new IllegalStateException(
					"registerContentModule is allowed only while a candidate is loading");
		}
		if (ACTIVE.get() != null) {
			throw new IllegalStateException(
					"nested registerContentModule scope; module '"
							+ ACTIVE.get() + "' is still active");
		}
		if (id == null || !MODULE_ID_PATTERN.matcher(id).matches()
				|| id.length() > MAX_MODULE_ID_LENGTH) {
			throw new IllegalArgumentException(
					"invalid content module id: " + id);
		}
		if (schemaVersion < 1 || schemaVersion > MAX_SCHEMA_VERSION) {
			throw new IllegalArgumentException(
					"content module schema version must be between 1 and "
							+ MAX_SCHEMA_VERSION);
		}
		if (RegistryStore.hasModuleRecord(id)) {
			throw new IllegalArgumentException(
					"duplicate content module scope: " + id);
		}
		ACTIVE.set(id);
		VERSION.set(Integer.valueOf(schemaVersion));
	}

	/** Closes the current scope. Must be called in a finally block. */
	public static void end() {
		ACTIVE.remove();
		VERSION.remove();
	}

	/** Returns the active module id or {@link #LEGACY_SOURCE}. */
	public static String currentSource() {
		String active = ACTIVE.get();
		return active == null ? LEGACY_SOURCE : active;
	}

	/** Returns the active declared schema version or {@code 0}. */
	public static int currentSchemaVersion() {
		Integer version = VERSION.get();
		return version == null ? LEGACY_SCHEMA_VERSION : version.intValue();
	}

}
