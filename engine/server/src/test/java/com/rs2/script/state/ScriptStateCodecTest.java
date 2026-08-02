package com.rs2.script.state;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

public class ScriptStateCodecTest {

	@Test
	public void encodingIsDeterministicSortedAndRoundTrips() {
		ScriptStateStore first = new ScriptStateStore();
		first.set("z", "two", ScriptStateValue.of("value"));
		first.set("a", "one", ScriptStateValue.of(4.5));
		first.set("a", "flag", ScriptStateValue.of(true));
		ScriptStateStore second = new ScriptStateStore();
		second.set("a", "flag", ScriptStateValue.of(true));
		second.set("a", "one", ScriptStateValue.of(4.5));
		second.set("z", "two", ScriptStateValue.of("value"));

		ScriptStateCodec codec = new ScriptStateCodec();
		String encoded = codec.encode(first.snapshot());
		assertEquals(encoded, codec.encode(second.snapshot()));
		assertFalse(encoded.contains("="));
		assertEquals(first.snapshot().getNamespaces(),
				codec.decode(encoded).getNamespaces());
	}

	@Test
	public void malformedVersionPaddingAndTrailingBytesAreRejected() {
		ScriptStateCodec codec = new ScriptStateCodec();
		assertRejected(codec, null);
		assertRejected(codec, "");
		assertRejected(codec, "v1.");
		assertRejected(codec, "v1.A=A");
		assertRejected(codec, "v1.***");
		assertRejected(codec, "v2.AAA");
		assertRejected(codec, codec.encode(new ScriptStateStore().snapshot()) + "=");
		String valid = codec.encode(new ScriptStateStore().snapshot());
		byte[] body = Base64.getUrlDecoder().decode(valid.substring(3));
		byte[] trailing = java.util.Arrays.copyOf(body, body.length + 1);
		assertRejected(codec, "v1." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(trailing));
	}

	@Test
	public void explicitMigrationRegistryDispatchesAndValidatesSnapshots() {
		ScriptStateStore migrated = new ScriptStateStore();
		migrated.set("legacy", "value", ScriptStateValue.of("migrated"));
		AtomicReference<String> body = new AtomicReference<>();
		ScriptStateCodec codec = new ScriptStateCodec(Collections.singletonMap(
				"v0", encodedBody -> {
					body.set(encodedBody);
					return migrated.snapshot();
				}));

		ScriptStateSnapshot decoded = codec.decode("v0.old-body");
		assertEquals("old-body", body.get());
		assertEquals(migrated.snapshot().getNamespaces(),
				decoded.getNamespaces());
		assertTrue(codec.encode(decoded).startsWith("v1."));
		assertRejected(codec, "v9.old-body");

		try {
			new ScriptStateCodec(Collections.singletonMap("v1",
					ignored -> migrated.snapshot()));
			fail("current decoder must not be replaceable");
		} catch (IllegalArgumentException expected) {
			// expected
		}
	}

	@Test
	public void nonFiniteNumberInBinaryPayloadIsRejected() {
		ScriptStateStore store = new ScriptStateStore();
		store.set("a", "n", ScriptStateValue.of(1.0));
		String encoded = new ScriptStateCodec().encode(store.snapshot());
		byte[] body = Base64.getUrlDecoder().decode(encoded.substring(3));
		long nan = Double.doubleToRawLongBits(Double.NaN);
		for (int i = body.length - 8; i < body.length; i++) {
			body[i] = (byte) (nan >>> (8 * (body.length - 1 - i)));
		}
		assertRejected(new ScriptStateCodec(), "v1."
				+ Base64.getUrlEncoder().withoutPadding().encodeToString(body));
	}

	@Test
	public void duplicateKeysAndInvalidUtf8AreRejected() throws Exception {
		ByteArrayOutputStream duplicateBytes = new ByteArrayOutputStream();
		DataOutputStream duplicate = new DataOutputStream(duplicateBytes);
		duplicate.writeShort(1);
		duplicate.writeShort(1);
		duplicate.writeByte('a');
		duplicate.writeShort(2);
		for (int i = 0; i < 2; i++) {
			duplicate.writeShort(1);
			duplicate.writeByte('k');
			duplicate.writeByte(1);
			duplicate.writeByte(i);
		}
		assertRejected(new ScriptStateCodec(), encode(duplicateBytes));

		ByteArrayOutputStream utf8Bytes = new ByteArrayOutputStream();
		DataOutputStream utf8 = new DataOutputStream(utf8Bytes);
		utf8.writeShort(1);
		utf8.writeShort(1);
		utf8.writeByte(0xff);
		utf8.writeShort(0);
		assertRejected(new ScriptStateCodec(), encode(utf8Bytes));
	}

	private static String encode(ByteArrayOutputStream bytes) {
		return "v1." + Base64.getUrlEncoder().withoutPadding()
				.encodeToString(bytes.toByteArray());
	}

	private static void assertRejected(ScriptStateCodec codec, String payload) {
		try {
			codec.decode(payload);
			fail("payload should be rejected: " + payload);
		} catch (ScriptStateException expected) {
			// expected
		}
	}
}
