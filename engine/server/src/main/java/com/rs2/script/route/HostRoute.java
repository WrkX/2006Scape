package com.rs2.script.route;

/**
 * Java-owned executable route invoker.
 *
 * <p>A host route is owned by the candidate runtime state that registered it
 * and carries no guest value. Authority for a host route is identical to a
 * guest callback: an exact route is consumed on success, handled rejection,
 * or contained throw, and only a valid unmatched key reaches the legacy
 * path. The invocation shape is finalized by the first work package that
 * registers a production host consumer.
 */
@FunctionalInterface
public interface HostRoute {

	/**
	 * Executes the host consumer. Implementations must contain their own
	 * exceptions and must not delegate to legacy behavior.
	 *
	 * <p>The invocation shape is the exact context objects passed by the
	 * route adapter: command routes receive the {@code CommandScriptContext},
	 * object routes the {@code ScriptContext}, and later host consumers the
	 * same arguments their packet adapter passes to guest callbacks.
	 */
	void invoke(Object... arguments);

}
