package com.rs2.world;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Allocates one ordering/identity token across every visible ground source.
 */
public final class GroundIdentityTokens {

	private static final AtomicLong NEXT = new AtomicLong(1L);

	public static long next() {
		return NEXT.getAndIncrement();
	}

	private GroundIdentityTokens() {
		throw new UnsupportedOperationException(
				"static-utility classes may not be instantiated.");
	}
}
