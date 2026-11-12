package com.andrerinas.openheadunit.connection.projection

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AsyncUsbReadPolicyTest {
    @Test fun `backend is disabled unless explicitly requested`() {
        assertFalse(AsyncUsbReadPolicy.isEligible(false, 29, usesLibusb = false))
    }

    @Test fun `backend requires Android O request semantics`() {
        assertFalse(AsyncUsbReadPolicy.isEligible(true, 25, usesLibusb = false))
        assertTrue(AsyncUsbReadPolicy.isEligible(true, 26, usesLibusb = false))
    }

    @Test fun `backend never wraps the native libusb transport`() {
        assertFalse(AsyncUsbReadPolicy.isEligible(true, 29, usesLibusb = true))
    }
}
