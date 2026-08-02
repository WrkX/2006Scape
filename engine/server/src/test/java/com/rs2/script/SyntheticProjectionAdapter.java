package com.rs2.script;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.rs2.script.activation.ProjectionAdapter;
import com.rs2.script.activation.RuntimeSnapshot;

/**
 * Test projection adapter that models a small world of generation-owned
 * footprint keys with an undo ledger, a visible selector, and injectable
 * failures at every transaction stage.
 *
 * <p>The adapter proves the activation protocol before a real world adapter
 * exists: old/new same-key handoff, LIFO shadow reversal, idempotent undo
 * retry, third-party writer blocking while the handoff reservation is held,
 * and the atomic selector swap at commit.
 */
final class SyntheticProjectionAdapter implements ProjectionAdapter {

	enum Stage {
		PREPARE, RESERVE, APPLY_SHADOW, VERIFY_SHADOW, RETIRE,
		VERIFY_RETIREMENT, CHECKPOINT, DISPOSE
	}

	final List<String> log = new ArrayList<>();
	final Map<String, Long> footprints = new LinkedHashMap<>();
	final List<String> shadowKeys = new ArrayList<>();
	final List<String> retiredKeys = new ArrayList<>();

	Stage failAt;
	boolean failFirstUndo;
	boolean failAllUndo;
	boolean failRemoveShadow;
	boolean failReleaseReservations;
	boolean failDispose;
	Runnable onReserved;

	Long selectedGeneration;
	Long predecessorGeneration;
	boolean reservationHeld;

	private final List<String> candidateFootprint;
	private final List<String> predecessorFootprint;

	SyntheticProjectionAdapter(List<String> candidateFootprint,
			List<String> predecessorFootprint) {
		this.candidateFootprint = candidateFootprint;
		this.predecessorFootprint = predecessorFootprint;
	}

	/** Clears transient run state; the modeled world persists. */
	void resetRunState() {
		log.clear();
		shadowKeys.clear();
		retiredKeys.clear();
		failAt = null;
		failFirstUndo = false;
		failAllUndo = false;
		failRemoveShadow = false;
		failReleaseReservations = false;
		failDispose = false;
		onReserved = null;
	}

	/**
	 * Simulates a third-party world writer. Writers are rejected while the
	 * handoff reservation is held and revalidated after commit or abort.
	 */
	void thirdPartyWrite(String key, long generation) {
		if (reservationHeld) {
			throw new IllegalStateException(
					"third-party write blocked by handoff reservation");
		}
		footprints.put(key, generation);
	}

	@Override
	public void prepare(RuntimeSnapshot candidate) {
		log.add("prepare:" + candidate.generation());
		if (failAt == Stage.PREPARE) {
			throw new IllegalStateException("injected prepare failure");
		}
	}

	@Override
	public void reserve(RuntimeSnapshot predecessor, RuntimeSnapshot candidate) {
		log.add("reserve:" + candidate.generation());
		for (String key : candidateFootprint) {
			Long owner = footprints.get(key);
			if (owner != null && (predecessor == null
					|| owner.longValue() != predecessor.generation())) {
				throw new IllegalStateException(
						"third-party owner " + owner + " holds " + key);
			}
		}
		if (predecessor != null) {
			predecessorGeneration = predecessor.generation();
		}
		reservationHeld = true;
		if (failAt == Stage.RESERVE) {
			reservationHeld = false;
			throw new IllegalStateException("injected reserve failure");
		}
		if (onReserved != null) {
			onReserved.run();
		}
	}

	@Override
	public void applyShadow(RuntimeSnapshot candidate) {
		log.add("apply-shadow:" + candidate.generation());
		for (String key : candidateFootprint) {
			shadowKeys.add(key);
			log.add("shadow:" + key);
		}
		if (failAt == Stage.APPLY_SHADOW) {
			throw new IllegalStateException("injected shadow failure");
		}
	}

	@Override
	public void verifyShadow(RuntimeSnapshot candidate) {
		log.add("verify-shadow:" + candidate.generation());
		if (failAt == Stage.VERIFY_SHADOW) {
			throw new IllegalStateException(
					"injected shadow verification failure");
		}
	}

	@Override
	public void retirePredecessor(RuntimeSnapshot predecessor) {
		log.add("retire:" + (predecessor == null
				? "none" : predecessor.generation()));
		for (String key : predecessorFootprint) {
			retiredKeys.add(key);
			log.add("retire-key:" + key);
			if (failAt == Stage.RETIRE) {
				throw new IllegalStateException(
						"injected retire failure midway");
			}
		}
	}

	@Override
	public void verifyRetirement() {
		log.add("verify-retirement");
		if (failAt == Stage.VERIFY_RETIREMENT) {
			throw new IllegalStateException(
					"injected retirement verification failure");
		}
	}

	@Override
	public void checkpoint() {
		log.add("checkpoint");
		if (failAt == Stage.CHECKPOINT) {
			throw new IllegalStateException("injected checkpoint failure");
		}
	}

	@Override
	public void commitSelection(RuntimeSnapshot candidate) {
		log.add("commit:" + candidate.generation());
		selectedGeneration = candidate.generation();
		for (String key : candidateFootprint) {
			footprints.put(key, candidate.generation());
		}
		for (String key : retiredKeys) {
			footprints.put(key, candidate.generation());
		}
		retiredKeys.clear();
		reservationHeld = false;
	}

	@Override
	public boolean restorePredecessor() {
		log.add("undo");
		if (failAllUndo) {
			throw new IllegalStateException("injected persistent undo failure");
		}
		if (failFirstUndo) {
			failFirstUndo = false;
			throw new IllegalStateException("injected first undo failure");
		}
		for (String key : retiredKeys) {
			footprints.put(key, predecessorGeneration);
		}
		retiredKeys.clear();
		return true;
	}

	@Override
	public void removeShadow() {
		log.add("remove-shadow");
		for (int i = shadowKeys.size() - 1; i >= 0; i--) {
			log.add("unshadow:" + shadowKeys.get(i));
		}
		shadowKeys.clear();
		if (failRemoveShadow) {
			throw new IllegalStateException(
					"injected shadow removal failure");
		}
	}

	@Override
	public void releaseReservations() {
		log.add("release-reservations");
		reservationHeld = false;
		if (failReleaseReservations) {
			throw new IllegalStateException(
					"injected reservation release failure");
		}
	}

	@Override
	public void dispose(RuntimeSnapshot predecessor) {
		log.add("dispose");
		reservationHeld = false;
		if (failDispose) {
			throw new IllegalStateException("injected dispose failure");
		}
	}

}
