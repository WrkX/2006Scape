package com.rs2.net.packets.impl;

import com.rs2.net.Packet;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;

/** Packet payloads encoded as the production item packet decoders expect. */
final class PacketFixtures {

	static Packet firstItemClick(int itemId, int slot) {
		ByteBuf payload = Unpooled.buffer(6);
		writeLittleEndianA(payload, 3214);
		writeBigEndianA(payload, slot);
		writeLittleEndian(payload, itemId);
		return packet(122, payload);
	}

	static Packet secondItemClick(int itemId, int slot) {
		ByteBuf payload = Unpooled.buffer(6);
		writeBigEndianA(payload, itemId);
		writeLittleEndianA(payload, slot);
		writeLittleEndianA(payload, 3214);
		return packet(16, payload);
	}

	static Packet thirdItemClick(int itemId, int slot) {
		ByteBuf payload = Unpooled.buffer(6);
		writeLittleEndianA(payload, 3214);
		writeBigEndianA(payload, slot);
		writeBigEndianA(payload, itemId);
		return packet(75, payload);
	}

	static Packet itemOnItem(int usedSlot, int targetSlot) {
		ByteBuf payload = Unpooled.buffer(4);
		writeBigEndian(payload, targetSlot);
		writeBigEndianA(payload, usedSlot);
		return packet(53, payload);
	}

	static Packet itemOnObject(int itemId, int slot, int objectId,
			int objectX, int objectY) {
		ByteBuf payload = Unpooled.buffer(12);
		writeBigEndian(payload, 3214);
		writeLittleEndian(payload, objectId);
		writeLittleEndianA(payload, objectY);
		writeLittleEndian(payload, slot);
		writeLittleEndianA(payload, objectX);
		writeBigEndian(payload, itemId);
		return packet(192, payload);
	}

	static Packet firstObjectClick(int objectId, int objectX, int objectY) {
		ByteBuf payload = Unpooled.buffer(6);
		writeLittleEndianA(payload, objectX);
		writeBigEndian(payload, objectId);
		writeBigEndianA(payload, objectY);
		return packet(ClickObject.FIRST_CLICK, payload);
	}

	static Packet itemOnNpc(int itemId, int slot, int npcIndex) {
		ByteBuf payload = Unpooled.buffer(6);
		writeBigEndianA(payload, itemId);
		writeBigEndianA(payload, npcIndex);
		writeLittleEndian(payload, slot);
		return packet(57, payload);
	}

	static Packet pickup(int itemId, int itemX, int itemY) {
		ByteBuf payload = Unpooled.buffer(6);
		writeLittleEndian(payload, itemY);
		writeBigEndian(payload, itemId);
		writeLittleEndian(payload, itemX);
		return packet(236, payload);
	}

	static Packet npcFirstClick(int npcIndex) {
		ByteBuf payload = Unpooled.buffer(2);
		writeLittleEndian(payload, npcIndex);
		return packet(155, payload);
	}

	static Packet actionButton(int actionButtonId) {
		ByteBuf payload = Unpooled.buffer(2);
		payload.writeByte(actionButtonId / 1000);
		payload.writeByte(actionButtonId % 1000);
		return packet(185, payload);
	}

	static Packet itemOnGroundItem(int usedItemId, int groundItemId, int slot,
			int x, int y) {
		ByteBuf payload = Unpooled.buffer(12);
		writeLittleEndian(payload, 3214);
		writeBigEndianA(payload, usedItemId);
		writeBigEndian(payload, groundItemId);
		writeBigEndianA(payload, y);
		writeLittleEndianA(payload, slot);
		writeBigEndian(payload, x);
		return packet(25, payload);
	}

	static Packet itemOnPlayer(int targetIndex, int slot) {
		ByteBuf payload = Unpooled.buffer(4);
		writeBigEndian(payload, targetIndex);
		writeLittleEndian(payload, slot);
		return packet(14, payload);
	}

	static Packet magicOnItem(int spellId, int itemId, int slot) {
		ByteBuf payload = Unpooled.buffer(8);
		writeBigEndian(payload, slot);
		writeBigEndianA(payload, itemId);
		writeBigEndian(payload, 3214);
		writeBigEndianA(payload, spellId);
		return packet(237, payload);
	}

	static Packet magicOnObject(int spellId, int objectId, int x, int y) {
		ByteBuf payload = Unpooled.buffer(8);
		writeLittleEndian(payload, x);
		writeBigEndian(payload, spellId);
		writeBigEndianA(payload, y);
		writeLittleEndian(payload, objectId);
		return packet(35, payload);
	}

	static Packet secondGroundItemClick(int itemId, int x, int y) {
		ByteBuf payload = Unpooled.buffer(6);
		writeLittleEndian(payload, x);
		writeLittleEndianA(payload, y);
		writeBigEndianA(payload, itemId);
		return packet(253, payload);
	}

	static Packet rawPacket(int opcode, int... bytes) {
		ByteBuf payload = Unpooled.buffer(bytes.length);
		for (int value : bytes) {
			payload.writeByte(value);
		}
		return packet(opcode, payload);
	}

	static Packet dialogueContinue() {
		return packet(40, Unpooled.buffer(0));
	}

	private static Packet packet(int opcode, ByteBuf payload) {
		return new Packet(opcode, Packet.Type.FIXED, payload);
	}

	private static void writeBigEndian(ByteBuf payload, int value) {
		payload.writeByte(value >> 8);
		payload.writeByte(value);
	}

	private static void writeBigEndianA(ByteBuf payload, int value) {
		payload.writeByte(value >> 8);
		payload.writeByte(value + 128);
	}

	private static void writeLittleEndian(ByteBuf payload, int value) {
		payload.writeByte(value);
		payload.writeByte(value >> 8);
	}

	private static void writeLittleEndianA(ByteBuf payload, int value) {
		payload.writeByte(value + 128);
		payload.writeByte(value >> 8);
	}

	private PacketFixtures() {
	}
}
