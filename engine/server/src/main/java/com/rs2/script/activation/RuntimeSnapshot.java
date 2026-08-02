package com.rs2.script.activation;

import org.graalvm.polyglot.Context;

import com.rs2.script.registries.RegistryStore;

/**
 * Immutable view of one runtime generation handed to the activation
 * transaction and its projection adapter.
 *
 * <p>The predecessor snapshot is the generation being replaced and always
 * carries its committed report. The candidate snapshot is the prepared
 * generation being installed; its report is assigned only at the no-throw
 * commit line.
 */
public final class RuntimeSnapshot {

	private final Context context;
	private final RegistryStore.State registry;
	private final long generation;
	private final ScriptRuntimeReport report;

	public RuntimeSnapshot(Context context, RegistryStore.State registry,
			long generation, ScriptRuntimeReport report) {
		this.context = context;
		this.registry = registry;
		this.generation = generation;
		this.report = report;
	}

	public static RuntimeSnapshot candidate(Context context,
			RegistryStore.State registry, long generation) {
		return new RuntimeSnapshot(context, registry, generation, null);
	}

	public static RuntimeSnapshot committed(Context context,
			RegistryStore.State registry, long generation,
			ScriptRuntimeReport report) {
		return new RuntimeSnapshot(context, registry, generation, report);
	}

	/** Graal context; {@code null} for an empty generation. */
	public Context context() {
		return context;
	}

	/** Frozen candidate-wide registries, routes, and manifest. */
	public RegistryStore.State registry() {
		return registry;
	}

	public long generation() {
		return generation;
	}

	/** Committed report of this generation, or {@code null} pre-commit. */
	public ScriptRuntimeReport report() {
		return report;
	}

	public RuntimeSnapshot withReport(ScriptRuntimeReport newReport) {
		return new RuntimeSnapshot(context, registry, generation, newReport);
	}

}
