package com.rs2.script.state;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deterministic, versioned single-line codec for player script state.
 */
public final class ScriptStateCodec {

	public static final String PREFIX = "v1.";
	private static final String BASE64_URL = "^[A-Za-z0-9_-]+$";
	private final Map<String, ScriptStateVersionDecoder> decoders;

	public ScriptStateCodec() {
		this(Collections.<String, ScriptStateVersionDecoder>emptyMap());
	}

	ScriptStateCodec(Map<String, ScriptStateVersionDecoder> migrations) {
		Map<String, ScriptStateVersionDecoder> configured = new LinkedHashMap<>();
		configured.put("v1", this::decodeV1);
		for (Map.Entry<String, ScriptStateVersionDecoder> migration
				: migrations.entrySet()) {
			String version = migration.getKey();
			if (version == null || version.isEmpty() || version.indexOf('.') >= 0
					|| "v1".equals(version) || migration.getValue() == null) {
				throw new IllegalArgumentException(
						"Invalid script-state migration registration: " + version);
			}
			configured.put(version, migration.getValue());
		}
		decoders = Collections.unmodifiableMap(configured);
	}

	public String encode(ScriptStateSnapshot snapshot) {
		try {
			ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			DataOutputStream output = new DataOutputStream(bytes);
			Map<String, Map<String, ScriptStateValue>> namespaces =
					snapshot.getNamespaces();
			output.writeShort(namespaces.size());
			for (Map.Entry<String, Map<String, ScriptStateValue>> namespace
					: namespaces.entrySet()) {
				writeString(output, namespace.getKey(), 2);
				output.writeShort(namespace.getValue().size());
				for (Map.Entry<String, ScriptStateValue> entry
						: namespace.getValue().entrySet()) {
					writeString(output, entry.getKey(), 2);
					ScriptStateValue value = entry.getValue();
					switch (value.getType()) {
						case BOOLEAN:
							output.writeByte(1);
							output.writeByte(value.asBoolean() ? 1 : 0);
							break;
						case NUMBER:
							output.writeByte(2);
							output.writeDouble(value.asNumber());
							break;
						case STRING:
							output.writeByte(3);
							writeString(output, value.asString(), 4);
							break;
						default:
							throw new ScriptStateException("Unsupported state type");
					}
				}
			}
			output.flush();
			String encoded = PREFIX + Base64.getUrlEncoder().withoutPadding()
					.encodeToString(bytes.toByteArray());
			if (encoded.getBytes(StandardCharsets.US_ASCII).length
					> ScriptStateLimits.MAX_ENCODED_PAYLOAD_BYTES) {
				throw new ScriptStateException("Encoded script state exceeds "
						+ ScriptStateLimits.MAX_ENCODED_PAYLOAD_BYTES + " bytes");
			}
			return encoded;
		} catch (IOException e) {
			throw new ScriptStateException("Unable to encode script state", e);
		}
	}

	public ScriptStateSnapshot decode(String payload) {
		if (payload == null) {
			throw new ScriptStateException("Unknown script-state version");
		}
		if (payload.getBytes(StandardCharsets.US_ASCII).length
				> ScriptStateLimits.MAX_ENCODED_PAYLOAD_BYTES) {
			throw new ScriptStateException("Encoded script state is oversized");
		}
		int separator = payload.indexOf('.');
		if (separator < 1) {
			throw new ScriptStateException("Unknown script-state version");
		}
		String version = payload.substring(0, separator);
		ScriptStateVersionDecoder decoder = decoders.get(version);
		if (decoder == null) {
			throw new ScriptStateException("Unknown script-state version: " + version);
		}
		ScriptStateSnapshot decoded = decoder.decode(payload.substring(separator + 1));
		if (decoded == null) {
			throw new ScriptStateException(
					"Script-state decoder returned no snapshot: " + version);
		}
		ScriptStateStore validator = new ScriptStateStore();
		validator.replace(decoded);
		return validator.snapshot();
	}

	private ScriptStateSnapshot decodeV1(String encodedBody) {
		if (!encodedBody.matches(BASE64_URL)) {
			throw new ScriptStateException("Malformed script-state Base64URL");
		}
		try {
			byte[] bytes = Base64.getUrlDecoder().decode(encodedBody);
			String canonical = Base64.getUrlEncoder().withoutPadding()
					.encodeToString(bytes);
			if (!canonical.equals(encodedBody)) {
				throw new ScriptStateException(
						"Non-canonical script-state Base64URL");
			}
			DataInputStream input = new DataInputStream(new ByteArrayInputStream(bytes));
			int namespaceCount = input.readUnsignedShort();
			if (namespaceCount > ScriptStateLimits.MAX_NAMESPACES) {
				throw new ScriptStateException("Too many script-state namespaces");
			}
			Map<String, Map<String, ScriptStateValue>> namespaces =
					new LinkedHashMap<>();
			int totalEntries = 0;
			for (int i = 0; i < namespaceCount; i++) {
				String namespace = readString(input, 2,
						ScriptStateLimits.MAX_NAMESPACE_BYTES);
				ScriptStateLimits.validateStoredNamespace(namespace);
				if (namespaces.containsKey(namespace)) {
					throw new ScriptStateException(
							"Duplicate script-state namespace: " + namespace);
				}
				int entryCount = input.readUnsignedShort();
				if (entryCount > ScriptStateLimits.MAX_ENTRIES_PER_NAMESPACE) {
					throw new ScriptStateException(
							"Too many entries in namespace: " + namespace);
				}
				totalEntries += entryCount;
				if (totalEntries > ScriptStateLimits.MAX_TOTAL_ENTRIES) {
					throw new ScriptStateException("Too many script-state entries");
				}
				Map<String, ScriptStateValue> entries = new LinkedHashMap<>();
				for (int j = 0; j < entryCount; j++) {
					String key = readString(input, 2, ScriptStateLimits.MAX_KEY_BYTES);
					ScriptStateLimits.validateStoredKey(namespace, key);
					if (entries.containsKey(key)) {
						throw new ScriptStateException(
								"Duplicate script-state key: " + key);
					}
					int type = input.readUnsignedByte();
					ScriptStateValue value;
					if (type == 1) {
						int bool = input.readUnsignedByte();
						if (bool != 0 && bool != 1) {
							throw new ScriptStateException("Invalid boolean state value");
						}
						value = ScriptStateValue.of(bool == 1);
					} else if (type == 2) {
						value = ScriptStateValue.of(input.readDouble());
					} else if (type == 3) {
						value = ScriptStateValue.of(readString(input, 4,
								ScriptStateLimits.MAX_STRING_BYTES));
					} else {
						throw new ScriptStateException("Invalid script-state type tag");
					}
					entries.put(key, value);
				}
				namespaces.put(namespace, entries);
			}
			if (input.read() != -1) {
				throw new ScriptStateException("Trailing bytes in script-state payload");
			}
			return new ScriptStateSnapshot(namespaces);
		} catch (IllegalArgumentException e) {
			throw new ScriptStateException("Malformed script-state Base64", e);
		} catch (EOFException e) {
			throw new ScriptStateException("Truncated script-state payload", e);
		} catch (IOException e) {
			throw new ScriptStateException("Unable to decode script state", e);
		}
	}

	private static void writeString(DataOutputStream output, String value,
			int lengthBytes) throws IOException {
		byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
		if (lengthBytes == 2) {
			output.writeShort(bytes.length);
		} else {
			output.writeInt(bytes.length);
		}
		output.write(bytes);
	}

	private static String readString(DataInputStream input, int lengthBytes,
			int maxBytes) throws IOException {
		int length = lengthBytes == 2 ? input.readUnsignedShort() : input.readInt();
		if (length < 0 || length > maxBytes) {
			throw new ScriptStateException("Invalid script-state string length");
		}
		byte[] bytes = new byte[length];
		input.readFully(bytes);
		try {
			return StandardCharsets.UTF_8.newDecoder()
					.onMalformedInput(CodingErrorAction.REPORT)
					.onUnmappableCharacter(CodingErrorAction.REPORT)
					.decode(ByteBuffer.wrap(bytes)).toString();
		} catch (CharacterCodingException e) {
			throw new ScriptStateException("Invalid UTF-8 in script-state payload", e);
		}
	}
}
