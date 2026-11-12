package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbProjectionProfilePolicyTest {
    private fun input(
        wired: Boolean = true,
        packet: Int = 512,
        software: Boolean = false,
        starvation: Boolean = false,
        thermal: Int = 0,
        fps: Int = 60,
        sessions: List<UsbProjectionProfilePolicy.Session> = emptyList(),
    ) = UsbProjectionProfilePolicy.Input(wired, packet, software, starvation, thermal, fps, sessions)

    private val healthy = UsbProjectionProfilePolicy.Session(1_000, 990, 2, 2)
    private val stuttered = UsbProjectionProfilePolicy.Session(1_000, 700, 120, 20)

    @Test fun healthyHighSpeedHardwareRouteKeepsUserProfile() {
        val decision = UsbProjectionProfilePolicy.decide(input())
        assertEquals(60, decision.frameRate)
        assertFalse(decision.capResolutionTo720)
        assertTrue(decision.reasons.isEmpty())
    }

    @Test fun fullSpeedEndpointCapsWiredProfile() {
        val decision = UsbProjectionProfilePolicy.decide(input(packet = 64))
        assertEquals(30, decision.frameRate)
        assertTrue(decision.capResolutionTo720)
        assertTrue(UsbProjectionProfilePolicy.Reason.FULL_SPEED_ENDPOINT in decision.reasons)
    }

    @Test fun softwareDecoderCapsProfile() {
        assertTrue(UsbProjectionProfilePolicy.decide(input(software = true)).capResolutionTo720)
    }

    @Test fun repeatedStarvationCapsProfile() {
        assertTrue(UsbProjectionProfilePolicy.decide(input(starvation = true)).capResolutionTo720)
    }

    @Test fun moderateThermalPressureCapsProfile() {
        assertTrue(UsbProjectionProfilePolicy.decide(input(thermal = 2)).capResolutionTo720)
    }

    @Test fun twoRecentStuttersHoldCapUntilSeveralHealthySessionsReplaceThem() {
        val held = UsbProjectionProfilePolicy.decide(
            input(sessions = listOf(stuttered, stuttered, healthy, healthy, healthy))
        )
        assertTrue(held.capResolutionTo720)

        val recovered = UsbProjectionProfilePolicy.decide(
            input(sessions = listOf(stuttered, healthy, healthy, healthy, healthy))
        )
        assertFalse(recovered.capResolutionTo720)
    }

    @Test fun explicitThirtyFpsDoesNotSilentlyLowerResolution() {
        val decision = UsbProjectionProfilePolicy.decide(input(fps = 30))
        assertEquals(30, decision.frameRate)
        assertFalse(decision.capResolutionTo720)
        assertEquals(setOf(UsbProjectionProfilePolicy.Reason.USER_FPS_30), decision.reasons)
    }

    @Test fun wirelessRouteIsLeftToWirelessPolicies() {
        val decision = UsbProjectionProfilePolicy.decide(
            input(wired = false, packet = 64, software = true, starvation = true, thermal = 4,
                sessions = listOf(stuttered, stuttered))
        )
        assertEquals(60, decision.frameRate)
        assertFalse(decision.capResolutionTo720)
    }

    @Test fun unknownEndpointDoesNotPretendToBeFullSpeed() {
        assertFalse(UsbProjectionProfilePolicy.decide(input(packet = 0)).capResolutionTo720)
    }
}
