package com.andrerinas.openheadunit.utils

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertEquals
import org.junit.Test

class UsbProjectionHealthPolicyTest {
    @Test fun threeUnexpectedStarvedSessionsLatchTheCap() {
        var state = UsbProjectionHealthPolicy.State()
        repeat(3) {
            state = UsbProjectionHealthPolicy.next(state, true, false, false)
        }
        assertEquals(3, state.starvedStreak)
        assertTrue(state.profileCapped)
    }

    @Test fun threeHealthyCappedSessionsReleaseWithHysteresis() {
        var state = UsbProjectionHealthPolicy.State(3, true, 0)
        repeat(2) {
            state = UsbProjectionHealthPolicy.next(state, true, true, false)
            assertTrue(state.profileCapped)
        }
        state = UsbProjectionHealthPolicy.next(state, true, true, false)
        assertFalse(state.profileCapped)
        assertEquals(0, state.healthySinceCap)
    }

    @Test fun deliberateAndPreHandshakeEndsDoNotChangeEvidence() {
        val state = UsbProjectionHealthPolicy.State(2, false, 0)
        assertEquals(state, UsbProjectionHealthPolicy.next(state, true, false, true))
        assertEquals(state, UsbProjectionHealthPolicy.next(state, false, false, false))
    }

    @Test fun aNewStarvationCancelsRecoveryProgress() {
        val recovering = UsbProjectionHealthPolicy.State(0, true, 2)
        val next = UsbProjectionHealthPolicy.next(recovering, true, false, false)
        assertTrue(next.profileCapped)
        assertEquals(0, next.healthySinceCap)
        assertEquals(1, next.starvedStreak)
    }
}
