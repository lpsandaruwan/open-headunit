package com.andrerinas.openheadunit.connection.projection

import org.junit.Assert.assertEquals
import org.junit.Test

class UsbReadBufferPolicyTest {
    @Test fun legacyAndroidRetainsFrameworkSafeLimit() {
        assertEquals(16 * 1024, UsbReadBufferPolicy.capacity(27))
    }

    @Test fun androidNineAndNewerUseLargeTransfers() {
        assertEquals(64 * 1024, UsbReadBufferPolicy.capacity(28))
        assertEquals(64 * 1024, UsbReadBufferPolicy.capacity(29)) // Android 10 head unit
        assertEquals(64 * 1024, UsbReadBufferPolicy.capacity(33)) // Android 13 peer generation
    }
}
