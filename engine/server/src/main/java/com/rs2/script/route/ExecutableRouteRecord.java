package com.rs2.script.route;

import org.graalvm.polyglot.Value;

/**
 * Immutable metadata plus exactly one invoker for an executable route.
 *
 * <p>The invoker is either a generation-owned guest {@link Value} or a Java
 * {@link HostRoute} owned by the candidate runtime state. Candidate-wide
 * uniqueness rejects a second record for the same exact key, so a record can
 * never hold both invokers.
 */
public final class ExecutableRouteRecord {

	private final ExecutableRouteKey key;
	private final String source;
	private final Value guestInvoker;
	private final HostRoute hostInvoker;

	public ExecutableRouteRecord(ExecutableRouteKey key, String source,
			Value guestInvoker, HostRoute hostInvoker) {
		if (key == null) {
			throw new IllegalArgumentException("route key must not be null");
		}
		if (source == null || source.trim().isEmpty()) {
			throw new IllegalArgumentException("route source must not be empty");
		}
		if ((guestInvoker == null) == (hostInvoker == null)) {
			throw new IllegalArgumentException(
					"route must carry exactly one invoker");
		}
		this.key = key;
		this.source = source;
		this.guestInvoker = guestInvoker;
		this.hostInvoker = hostInvoker;
	}

	public static ExecutableRouteRecord guest(ExecutableRouteKey key,
			String source, Value guestInvoker) {
		return new ExecutableRouteRecord(key, source, guestInvoker, null);
	}

	public static ExecutableRouteRecord host(ExecutableRouteKey key,
			String source, HostRoute hostInvoker) {
		return new ExecutableRouteRecord(key, source, null, hostInvoker);
	}

	public ExecutableRouteKey key() {
		return key;
	}

	/** Bounded logical source module id, or the legacy-unscoped marker. */
	public String source() {
		return source;
	}

	public boolean isGuest() {
		return guestInvoker != null;
	}

	/** Generation-owned guest invoker; valid only for guest routes. */
	public Value guestInvoker() {
		if (guestInvoker == null) {
			throw new IllegalStateException(
					"route " + key + " is a host route");
		}
		return guestInvoker;
	}

	/** Java-owned invoker; valid only for host routes. */
	public HostRoute hostInvoker() {
		if (hostInvoker == null) {
			throw new IllegalStateException(
					"route " + key + " is a guest route");
		}
		return hostInvoker;
	}

	@Override
	public String toString() {
		return key + " (source: " + source + ", "
				+ (isGuest() ? "guest" : "host") + ")";
	}

}
