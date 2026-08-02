package com.rs2.script;

import java.util.Arrays;

import org.graalvm.polyglot.HostAccess;

/** Immutable metadata for one player command invocation. */
public final class CommandScriptContext extends ScriptContext {

	private final String name;
	private final String rawInput;
	private final String[] arguments;
	private final int rights;

	public CommandScriptContext(ScriptedPlayer player, String name, String rawInput,
			String[] arguments, int rights) {
		super(player, null, name);
		this.name = name;
		this.rawInput = rawInput;
		this.arguments = arguments == null ? new String[0]
				: Arrays.copyOf(arguments, arguments.length);
		this.rights = rights;
	}

	@HostAccess.Export
	public String getName() {
		return name;
	}

	@HostAccess.Export
	public String getRawInput() {
		return rawInput;
	}

	@HostAccess.Export
	public String[] getArguments() {
		return Arrays.copyOf(arguments, arguments.length);
	}

	@HostAccess.Export
	public int getRights() {
		return rights;
	}
}
