package com.rs2.script.interfacehook;

import java.util.Collections;
import java.util.Map;

import org.graalvm.polyglot.Value;

/**
 * Immutable Java-owned schema-v1 interface hook descriptor.
 *
 * <p>Groups button handlers and optional open/close lifecycle callbacks for
 * one cache interface id. Button dispatch is scoped to
 * {@code player.lastMainFrameInterface}; unregistered interfaces keep legacy
 * behavior.
 */
public final class InterfaceHookDefinition {

	private final String id;
	private final int interfaceId;
	private final Map<Integer, Value> buttons;
	private final Value onOpen;
	private final Value onClose;
	private final String source;
	private final int schemaVersion;

	public InterfaceHookDefinition(String id, int interfaceId,
			Map<Integer, Value> buttons, Value onOpen, Value onClose,
			String source, int schemaVersion) {
		this.id = id;
		this.interfaceId = interfaceId;
		this.buttons = Collections.unmodifiableMap(buttons);
		this.onOpen = onOpen;
		this.onClose = onClose;
		this.source = source;
		this.schemaVersion = schemaVersion;
	}

	public String id() {
		return id;
	}

	public int interfaceId() {
		return interfaceId;
	}

	public Map<Integer, Value> buttons() {
		return buttons;
	}

	public Value onOpen() {
		return onOpen;
	}

	public Value onClose() {
		return onClose;
	}

	public String source() {
		return source;
	}

	public int schemaVersion() {
		return schemaVersion;
	}

	@Override
	public String toString() {
		return "interfaceHook '" + id + "' (interfaceId: " + interfaceId
				+ ", source: " + source + ", schema v" + schemaVersion + ")";
	}
}
