package com.rs2.script.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.Test;

/**
 * Table-driven {@code -1/exact/+1} byte/count/value matrix for every v1 and
 * built-in v0 codec bound plus unknown types, duplicates, truncation,
 * trailing data, malformed UTF-8/Base64URL, and overflow.
 */
public class ScriptStateCodecBoundaryTest {

	@Test
	public void v1NamespaceCountBoundary() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertDecoded(codec, v1().namespaces(31, 1).payload());
		assertDecoded(codec, v1().namespaces(32, 1).payload());
		assertRejected(codec, v1().namespaces(33, 1).payload());
	}

	@Test
	public void v1EntriesPerNamespaceBoundary() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertDecoded(codec, v1().namespace("n", 255).payload());
		assertDecoded(codec, v1().namespace("n", 256).payload());
		assertRejected(codec, v1().namespace("n", 257).payload());
	}

	@Test
	public void v1TotalEntriesBoundary() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertDecoded(codec, v1().namespaces(3, 256).namespace("extra", 255)
				.payload());
		assertDecoded(codec, v1().namespaces(4, 256).payload());
		assertRejected(codec, v1().namespaces(4, 256).namespace("extra", 1)
				.payload());
	}

	@Test
	public void v1NamespaceBytesBoundary() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertDecoded(codec, v1().namespace("n" + repeat("a", 46), 1).payload());
		assertDecoded(codec, v1().namespace("n" + repeat("a", 47), 1).payload());
		assertRejected(codec, v1().namespace("n" + repeat("a", 48), 1).payload());
	}

	@Test
	public void v1KeyBytesBoundary() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertDecoded(codec, v1().namespace("n", 1, "k" + repeat("a", 94))
				.payload());
		assertDecoded(codec, v1().namespace("n", 1, "k" + repeat("a", 95))
				.payload());
		assertRejected(codec, v1().namespace("n", 1, "k" + repeat("a", 96))
				.payload());
	}

	@Test
	public void v1StringBytesBoundary() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertDecoded(codec, v1().header("n", 1).string("s",
				"s" + repeat("a", 4094)).payload());
		assertDecoded(codec, v1().header("n", 1).string("s",
				"s" + repeat("a", 4095)).payload());
		assertRejected(codec, v1().header("n", 1).string("s",
				"s" + repeat("a", 4096)).payload());
	}

	@Test
	public void v1EncodedPayloadBoundary() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		V1 exact = v1();
		exact.namespace("n" + repeat("a", 47), 200, 1.0);
		exact.namespace("p" + repeat("b", 32), 200, 1.0);
		exact.namespace("q" + repeat("c", 16), 200, 1.0);
		exact.namespace("r" + repeat("d", 14), 200, 1.0);
		exact.namespace("s" + repeat("e", 13), 200, 1.0);
		String payload = exact.payload();
		// The "v1." prefix counts against the 65536-byte encoded-payload
		// limit, and a canonical unpadded body is a multiple of four, so the
		// largest accepted payload is exactly 65535 characters.
		assertEquals(65535, payload.getBytes(StandardCharsets.US_ASCII).length);
		ScriptStateSnapshot decoded = codec.decode(payload);
		assertEquals(1000, decoded.entryCount());
		assertEquals(payload, codec.encode(decoded));
		assertRejected(codec, payload + "AA");
		assertRejected(codec, "v1." + repeat("A", 65534));
	}

	@Test
	public void v1MalformedAndOverflowInputsAreRejected() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertRejected(codec, "v1.###");
		assertRejected(codec, "v1." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(new byte[] { 0 }) + "=");
		assertRejected(codec, v1().namespace("Bad_NS", 1).payload());
		assertRejected(codec, v1().namespace("sys.reserved", 1).payload());
		assertRejected(codec, v1().namespace("n", 1, "Bad_Key").payload());
		assertRejected(codec, v1().namespace("n", 1, "sys.reserved").payload());
		assertRejected(codec, v1().header("n", 1).bool("k", 2).payload());
		assertRejected(codec, v1().header("n", 2).bool("k", 1)
				.number("k", 2.0).payload());
		assertRejected(codec, truncated(v1().namespace("n", 2).payload()));
		assertRejected(codec, trailing(v1().namespace("n", 1).payload()));
	}

	@Test
	public void v0BuiltInDecoderGroupsEntriesAndRoundTripsThroughV1()
			throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		V0 v0 = v0();
		v0.entry("__quest", "stage.dragon-awakens", 2, 3.0);
		v0.entry("__quest", "state.dragon-awakens", 3, "in_progress");
		v0.entry("dragon-awakens", "bones-recovered", 1, 1);
		String payload = v0.payload();
		ScriptStateSnapshot decoded = codec.decode(payload);
		assertEquals(3, decoded.entryCount());
		assertEquals(ScriptStateValue.of("in_progress"), decoded
				.getNamespaces().get("__quest")
				.get("state.dragon-awakens"));
		assertEquals(ScriptStateValue.of(3.0), decoded.getNamespaces()
				.get("__quest").get("stage.dragon-awakens"));
		assertEquals(ScriptStateValue.of(true), decoded.getNamespaces()
				.get("dragon-awakens").get("bones-recovered"));
		assertEquals(decoded.getNamespaces(), codec.decode(
				codec.encode(decoded)).getNamespaces());
	}

	@Test
	public void v0CountAndValueBoundaries() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertDecoded(codec, v0().namespaces(32, 1).payload());
		assertRejected(codec, v0().namespaces(33, 1).payload());
		assertDecoded(codec, v0().namespace("n", 256).payload());
		assertRejected(codec, v0().namespace("n", 257).payload());
		assertDecoded(codec, v0().namespaces(4, 256).payload());
		assertRejected(codec, v0().namespaces(4, 256).namespace("extra", 1)
				.payload());
		assertDecoded(codec, v0().namespace("n" + repeat("a", 47), 1).payload());
		assertRejected(codec, v0().namespace("n" + repeat("a", 48), 1).payload());
		assertDecoded(codec, v0().namespace("n", 1, "k" + repeat("a", 95))
				.payload());
		assertRejected(codec, v0().namespace("n", 1, "k" + repeat("a", 96))
				.payload());
		assertDecoded(codec, v0().namespace("n", 1).string("s",
				"s" + repeat("a", 4095)).payload());
		assertRejected(codec, v0().namespace("n", 1).string("s",
				"s" + repeat("a", 4096)).payload());
	}

	@Test
	public void v0MalformedInputsAreRejected() throws Exception {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertRejected(codec, "v0.###");
		assertRejected(codec, v0().entry("n", "k", 9, 0).payload());
		assertRejected(codec, v0().entry("n", "k", 1, 2).payload());
		assertRejected(codec, v0().entry("n", "k", 2, 1.0)
				.entry("n", "k", 2, 2.0).payload());
		assertRejected(codec, v0().entry("Bad_NS", "k", 1, 0).payload());
		assertRejected(codec, v0().entry("n", "Bad_Key", 1, 0).payload());
		assertRejected(codec, v0().entry("sys.reserved", "k", 1, 0).payload());
		assertRejected(codec, v0().entry("n", "k", 2, Double.NaN).payload());
		assertRejected(codec, v0().entry("n", "k", 2,
				Double.POSITIVE_INFINITY).payload());
		assertRejected(codec, truncated(v0().entry("n", "k", 1, 0).payload()));
		assertRejected(codec, trailing(v0().entry("n", "k", 1, 0).payload()));
		ByteArrayOutputStream invalidUtf8 = new ByteArrayOutputStream();
		DataOutputStream utf8Out = new DataOutputStream(invalidUtf8);
		utf8Out.writeShort(1);
		writeString(utf8Out, "n");
		writeString(utf8Out, "k");
		utf8Out.writeByte(3);
		utf8Out.writeInt(1);
		utf8Out.writeByte(0xff);
		assertRejected(codec, v0Raw(invalidUtf8));
	}

	private static String v0Raw(ByteArrayOutputStream bytes) {
		return "v0." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(bytes.toByteArray());
	}

	private static String truncated(String payload) throws Exception {
		byte[] bytes = Base64.getUrlDecoder().decode(payload.substring(3));
		byte[] cut = java.util.Arrays.copyOf(bytes, bytes.length - 1);
		return payload.substring(0, 3) + Base64.getUrlEncoder()
				.withoutPadding().encodeToString(cut);
	}

	private static String trailing(String payload) throws Exception {
		byte[] bytes = Base64.getUrlDecoder().decode(payload.substring(3));
		byte[] extended = java.util.Arrays.copyOf(bytes, bytes.length + 1);
		return payload.substring(0, 3) + Base64.getUrlEncoder()
				.withoutPadding().encodeToString(extended);
	}

	private static V1 v1() throws Exception {
		return new V1();
	}

	private static V0 v0() throws Exception {
		return new V0();
	}

	private static String repeat(String text, int count) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < count; i++) {
			builder.append(text);
		}
		return builder.toString();
	}

	private static void assertDecoded(ScriptStateCodec codec, String payload) {
		try {
			codec.decode(payload);
		} catch (ScriptStateException failure) {
			fail("payload should decode: " + payload + " ("
					+ failure.getMessage() + ")");
		}
	}

	private static void assertRejected(ScriptStateCodec codec, String payload) {
		try {
			codec.decode(payload);
			fail("payload should be rejected: " + payload);
		} catch (ScriptStateException expected) {
			// expected
		}
	}

	/**
	 * Writes the v1 layout: {@code u16 namespaceCount}, then per namespace
	 * {@code u16 name, name, u16 entryCount} and its entries. The namespace
	 * count is patched once at {@link #payload()}.
	 */
	private static final class V1 {
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private final DataOutputStream out = new DataOutputStream(bytes);
		private int namespaceCount;

		private V1() throws Exception {
			out.writeShort(0);
		}

		V1 namespaces(int count, int entriesPerNamespace) throws Exception {
			for (int i = 0; i < count; i++) {
				namespace("n" + i, entriesPerNamespace);
			}
			return this;
		}

		V1 namespace(String name, int entryCount) throws Exception {
			namespaceHeader(name, entryCount);
			for (int i = 0; i < entryCount; i++) {
				writeString(out, "k" + i);
				out.writeByte(1);
				out.writeByte(1);
			}
			return this;
		}

		V1 header(String name, int entryCount) throws Exception {
			namespaceHeader(name, entryCount);
			return this;
		}

		V1 bool(String key, int value) throws Exception {
			writeString(out, key);
			out.writeByte(1);
			out.writeByte(value);
			return this;
		}

		V1 number(String key, double value) throws Exception {
			writeString(out, key);
			out.writeByte(2);
			out.writeDouble(value);
			return this;
		}

		V1 string(String key, String value) throws Exception {
			writeString(out, key);
			out.writeByte(3);
			byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
			out.writeInt(valueBytes.length);
			out.write(valueBytes);
			return this;
		}

		V1 namespace(String name, int entryCount, String key)
				throws Exception {
			namespaceHeader(name, entryCount);
			for (int i = 0; i < entryCount; i++) {
				writeString(out, key);
				out.writeByte(1);
				out.writeByte(1);
			}
			return this;
		}

		V1 namespace(String name, int entryCount, double value)
				throws Exception {
			namespaceHeader(name, entryCount);
			for (int i = 0; i < entryCount; i++) {
				writeString(out, "k" + String.format("%04d", i)
						+ repeat("a", 33));
				out.writeByte(2);
				out.writeDouble(value);
			}
			return this;
		}

		private void namespaceHeader(String name, int entryCount)
				throws Exception {
			namespaceCount++;
			writeString(out, name);
			out.writeShort(entryCount);
		}

		String payload() throws Exception {
			byte[] data = bytes.toByteArray();
			data[0] = (byte) (namespaceCount >>> 8);
			data[1] = (byte) namespaceCount;
			return "v1." + Base64.getUrlEncoder().withoutPadding()
					.encodeToString(data);
		}
	}

	/**
	 * Writes the historical v0 layout: {@code u16 entryCount}, then per entry
	 * {@code u16 name, name, u16 key, key, u8 type} and the v1 type payload.
	 * The entry count is patched once at {@link #payload()}.
	 */
	private static final class V0 {
		private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		private final DataOutputStream out = new DataOutputStream(bytes);
		private int entryCount;

		private V0() throws Exception {
			out.writeShort(0);
		}

		V0 namespaces(int count, int entriesPerNamespace) throws Exception {
			for (int i = 0; i < count; i++) {
				for (int j = 0; j < entriesPerNamespace; j++) {
					entry("n" + i, "k" + j, 1, 1);
				}
			}
			return this;
		}

		V0 namespace(String name, int entryCount) throws Exception {
			for (int i = 0; i < entryCount; i++) {
				entry(name, "k" + i, 1, 1);
			}
			return this;
		}

		V0 namespace(String name, int entryCount, String key)
				throws Exception {
			for (int i = 0; i < entryCount; i++) {
				entry(name, key, 1, 1);
			}
			return this;
		}

		V0 string(String key, String value) throws Exception {
			return entry("n", key, 3, value);
		}

		V0 entry(String name, String key, int type, Object value)
				throws Exception {
			entryCount++;
			writeString(out, name);
			writeString(out, key);
			out.writeByte(type);
			if (type == 1) {
				out.writeByte(((Integer) value).intValue());
			} else if (type == 2) {
				out.writeDouble(((Double) value).doubleValue());
			} else if (type == 3) {
				byte[] valueBytes = ((String) value)
						.getBytes(StandardCharsets.UTF_8);
				out.writeInt(valueBytes.length);
				out.write(valueBytes);
			} else {
				out.writeByte(0);
			}
			return this;
		}

		String payload() throws Exception {
			byte[] data = bytes.toByteArray();
			data[0] = (byte) (entryCount >>> 8);
			data[1] = (byte) entryCount;
			return "v0." + Base64.getUrlEncoder().withoutPadding()
					.encodeToString(data);
		}
	}

	private static void writeString(DataOutputStream out, String value)
			throws Exception {
		byte[] valueBytes = value.getBytes(StandardCharsets.UTF_8);
		out.writeShort(valueBytes.length);
		out.write(valueBytes);
	}
}
