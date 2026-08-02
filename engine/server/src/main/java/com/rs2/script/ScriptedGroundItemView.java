package com.rs2.script;

import com.rs2.game.items.GroundItem;

import org.graalvm.polyglot.HostAccess;

/** Immutable identity view of one resolved ground item. */
public final class ScriptedGroundItemView {

	private final String token;
	private final int id;
	private final int amount;
	private final ScriptedPosition position;
	private final boolean privateToPlayer;

	public ScriptedGroundItemView(String token, GroundItem item,
			boolean privateToPlayer) {
		this(token, item.getItemId(), item.getItemAmount(),
				new ScriptedPosition(item.getItemX(), item.getItemY(), item.getItemH()),
				privateToPlayer);
	}

	public ScriptedGroundItemView(String token, int id, int amount,
			ScriptedPosition position, boolean privateToPlayer) {
		this.token = token;
		this.id = id;
		this.amount = amount;
		this.position = position;
		this.privateToPlayer = privateToPlayer;
	}

	@HostAccess.Export
	public String token() {
		return token;
	}

	@HostAccess.Export
	public int id() {
		return id;
	}

	@HostAccess.Export
	public int amount() {
		return amount;
	}

	@HostAccess.Export
	public ScriptedPosition position() {
		return position;
	}

	@HostAccess.Export
	public boolean isPrivateToPlayer() {
		return privateToPlayer;
	}
}
