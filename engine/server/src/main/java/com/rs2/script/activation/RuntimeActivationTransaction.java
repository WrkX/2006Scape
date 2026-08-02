package com.rs2.script.activation;

import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.graalvm.polyglot.Value;

import com.rs2.script.definition.ModuleRecord;
import com.rs2.util.LoggerUtils;

/**
 * Reusable two-phase runtime activation transaction.
 *
 * <p>The transaction installs one prepared candidate generation over its
 * predecessor:
 *
 * <ol>
 * <li>prepare and reserve candidate projection intents without touching live
 * state;</li>
 * <li>apply and verify candidate shadow state without guest visibility;</li>
 * <li>retire the predecessor into an idempotent undo ledger and verify;</li>
 * <li>pass the final injectable pre-publication checkpoint, which is the
 * last operation allowed to fail or abort;</li>
 * <li>attempt every old-generation {@code onUnload} observer as a contained,
 * non-vetoing hook with registration closed;</li>
 * <li>reach the no-throw commit: the report plus the projection selector swap
 * become visible together, and the host immediately publishes the new
 * context, generation, registries, manifest, and report;</li>
 * <li>post-commit, run new {@code onLoad} observers and dispose predecessor
 * state, quarantining any failure without reverting publication.</li>
 * </ol>
 *
 * <p>Any failure before the checkpoint aborts in reverse order and never
 * invokes an unload hook. After an unload hook is attempted, publication is
 * mandatory and neither hook effects nor publication are claimed
 * rollbackable.
 */
public final class RuntimeActivationTransaction {

	/** Thrown when the candidate aborts before the commit line. */
	public static final class Aborted extends RuntimeException {

		private static final long serialVersionUID = 1L;

		private final String quarantine;

		private Aborted(Throwable cause, String quarantine) {
			super("runtime activation aborted before publication", cause);
			this.quarantine = quarantine;
		}

		/** Bounded quarantine note, or {@code null} when none occurred. */
		public String quarantine() {
			return quarantine;
		}
	}

	private static final int UNDO_RETRY_ATTEMPTS = 3;
	private static final Logger logger =
			LoggerUtils.getLogger(RuntimeActivationTransaction.class);

	private final RuntimeSnapshot predecessor;
	private final RuntimeSnapshot candidate;
	private final ProjectionAdapter projections;

	private boolean reserved;
	private boolean shadowApplied;
	private boolean retired;
	private String quarantine;

	public RuntimeActivationTransaction(RuntimeSnapshot predecessor,
			RuntimeSnapshot candidate, ProjectionAdapter projections) {
		if (candidate == null || candidate.registry() == null) {
			throw new IllegalArgumentException(
					"candidate snapshot must not be null");
		}
		if (projections == null) {
			throw new IllegalArgumentException(
					"projection adapter must not be null");
		}
		this.predecessor = predecessor;
		this.candidate = candidate;
		this.projections = projections;
	}

	/**
	 * Executes the handoff and returns the committed snapshot. Throws
	 * {@link Aborted} when any pre-checkpoint stage fails; an abort never
	 * invokes an unload hook.
	 */
	public RuntimeSnapshot execute() {
		try {
			projections.prepare(candidate);
			reserved = true;
			projections.reserve(predecessor, candidate);
			shadowApplied = true;
			projections.applyShadow(candidate);
			projections.verifyShadow(candidate);
			retired = true;
			projections.retirePredecessor(predecessor);
			projections.verifyRetirement();
			projections.checkpoint();
		} catch (Throwable failure) {
			abort();
			throw new Aborted(failure, quarantine);
		}

		// Mandatory-commit region: no abort, no rollback, no fallible
		// injection point may intervene before the host publishes.
		HookResult unload = runUnloadObservers();
		ScriptRuntimeReport report = buildReport(unload);
		RuntimeSnapshot published = candidate.withReport(report);
		try {
			projections.commitSelection(published);
		} catch (Throwable failure) {
			quarantine("projection commit selection degraded: "
					+ bound(failure));
		}
		return published;
	}

	/**
	 * Runs every new-generation {@code onLoad} observer as a contained,
	 * non-vetoing hook. Must run after the host published this candidate.
	 * Returns the first failure result, or {@code null}.
	 */
	public HookResult runLoadObservers() {
		return runManifestObservers(candidate.registry().manifest, "load",
				"onLoad");
	}

	/**
	 * Post-commit disposal: discards predecessor undo/shadow state, releases
	 * handoff reservations, and closes the predecessor context. Failures are
	 * retried and then quarantined; publication is never reverted. Returns a
	 * bounded failure message, or {@code null} when cleanup completed.
	 */
	public String finalizeQuietly() {
		if (predecessor == null) {
			return null;
		}
		String failure = null;
		Throwable last = null;
		for (int attempt = 0; attempt < UNDO_RETRY_ATTEMPTS; attempt++) {
			try {
				projections.dispose(predecessor);
				last = null;
				break;
			} catch (Throwable t) {
				last = t;
			}
		}
		if (last != null) {
			failure = "predecessor disposal degraded after retries: "
					+ bound(last);
			logger.log(Level.SEVERE, failure, last);
		}
		closeQuietly(predecessor.context());
		return failure;
	}

	private HookResult runUnloadObservers() {
		if (predecessor == null) {
			return null;
		}
		return runManifestObservers(predecessor.registry().manifest,
				"unload", "onUnload");
	}

	private HookResult runManifestObservers(List<ModuleRecord> modules,
			String category, String member) {
		HookResult firstFailure = null;
		for (ModuleRecord module : modules) {
			Value hook = "load".equals(category)
					? module.onLoad() : module.onUnload();
			if (hook == null) {
				continue;
			}
			HookResult result = HookRunner.run(hook, category, module.id());
			if (result.threw() && firstFailure == null) {
				firstFailure = result;
			}
		}
		return firstFailure;
	}

	private ScriptRuntimeReport buildReport(HookResult unload) {
		return new ScriptRuntimeReport(ScriptRuntimeReport.Status.LOADED,
				candidate.generation(),
				candidate.registry().manifest.size(),
				candidate.registry().definitions.size(),
				candidate.registry().routes.size(), unload);
	}

	/**
	 * Abort in reverse order. Never invokes unload observers. Undo
	 * operations are idempotent and non-guest; a persistent restore failure
	 * is quarantined as fatal and never reported as a clean rollback.
	 */
	private void abort() {
		if (retired) {
			restorePredecessorWithRetry();
		}
		if (shadowApplied) {
			try {
				projections.removeShadow();
			} catch (Throwable failure) {
				quarantine("candidate shadow removal failed: " + bound(failure));
			}
		}
		if (reserved) {
			try {
				projections.releaseReservations();
			} catch (Throwable failure) {
				quarantine("handoff reservation release failed: "
						+ bound(failure));
			}
		}
	}

	private void restorePredecessorWithRetry() {
		Throwable last = null;
		for (int attempt = 0; attempt < UNDO_RETRY_ATTEMPTS; attempt++) {
			try {
				if (projections.restorePredecessor()) {
					return;
				}
				last = new IllegalStateException(
						"undo ledger reported incomplete restoration");
				break;
			} catch (Throwable failure) {
				last = failure;
			}
		}
		quarantine("FATAL: predecessor restoration failed while the handoff "
				+ "reservation was held; last-known-good restoration could "
				+ "not be verified: " + bound(last));
	}

	private void quarantine(String note) {
		if (quarantine == null) {
			quarantine = note;
		}
		logger.log(Level.SEVERE, note);
	}

	private static String bound(Throwable failure) {
		String message = failure == null ? null : failure.getMessage();
		if (message == null) {
			return failure == null ? "unknown failure"
					: failure.getClass().getSimpleName();
		}
		int limit = 512;
		String trimmed = message.trim();
		return trimmed.length() <= limit ? trimmed
				: trimmed.substring(0, limit) + "...";
	}

	private static void closeQuietly(org.graalvm.polyglot.Context context) {
		if (context == null) {
			return;
		}
		try {
			context.close();
		} catch (RuntimeException e) {
			logger.log(Level.WARNING, "Failed to close script context", e);
		}
	}

}
