package com.rs2.script;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class BridgeValidationTest {

	@Test
	public void integralAcceptsWholeNumbersInRange() {
		assertEquals(Integer.valueOf(42), BridgeValidation.integral(42.0, 0, 100));
		assertEquals(Integer.valueOf(0), BridgeValidation.integral(0.0, 0, 100));
		assertEquals(Integer.valueOf(100), BridgeValidation.integral(100.0, 0, 100));
	}

	@Test
	public void integralRejectsFractionalOutOfRangeAndNonFiniteValues() {
		assertNull(BridgeValidation.integral(1.5, 0, 10));
		assertNull(BridgeValidation.integral(-1.0, 0, 10));
		assertNull(BridgeValidation.integral(11.0, 0, 10));
		assertNull(BridgeValidation.integral(Double.NaN, 0, 10));
		assertNull(BridgeValidation.integral(Double.POSITIVE_INFINITY, 0, 10));
	}

	@Test
	public void hasTextRejectsNullOnly() {
		assertFalse(BridgeValidation.hasText(null));
		assertFalse(BridgeValidation.hasText("null"));
		assertTrue(BridgeValidation.hasText(""));
		assertTrue(BridgeValidation.hasText("hello"));
		assertTrue(BridgeValidation.hasText("nullish"));
	}

	@Test
	public void nonNullStringMapsAbsentGuestValuesToNull() {
		assertNull(BridgeValidation.nonNullString(null));
		assertNull(BridgeValidation.nonNullString("null"));
		assertEquals("", BridgeValidation.nonNullString(""));
		assertEquals("hello", BridgeValidation.nonNullString("hello"));
	}
}
