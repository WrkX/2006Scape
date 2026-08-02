package com.rs2.script.activation;

/**
 * Default projection adapter for phases that install no world projections.
 *
 * <p>Every stage succeeds trivially, the selector swap is a no-op, and the
 * undo ledger has nothing to restore. The work package that activates real
 * world state replaces this adapter; WP1 proves the protocol with a
 * synthetic adapter that injects failures at every stage.
 */
public final class NoOpProjectionAdapter implements ProjectionAdapter {

	private static final NoOpProjectionAdapter INSTANCE =
			new NoOpProjectionAdapter();

	private NoOpProjectionAdapter() {
	}

	public static NoOpProjectionAdapter getInstance() {
		return INSTANCE;
	}

	@Override
	public void prepare(RuntimeSnapshot candidate) {
	}

	@Override
	public void reserve(RuntimeSnapshot predecessor, RuntimeSnapshot candidate) {
	}

	@Override
	public void applyShadow(RuntimeSnapshot candidate) {
	}

	@Override
	public void verifyShadow(RuntimeSnapshot candidate) {
	}

	@Override
	public void retirePredecessor(RuntimeSnapshot predecessor) {
	}

	@Override
	public void verifyRetirement() {
	}

	@Override
	public void checkpoint() {
	}

	@Override
	public void commitSelection(RuntimeSnapshot candidate) {
	}

	@Override
	public boolean restorePredecessor() {
		return true;
	}

	@Override
	public void removeShadow() {
	}

	@Override
	public void releaseReservations() {
	}

	@Override
	public void dispose(RuntimeSnapshot predecessor) {
	}

}
