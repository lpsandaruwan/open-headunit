package com.andrerinas.openheadunit.aap

/** Chooses a session-only wired projection ceiling; it never edits the user's settings. */
object UsbProjectionProfilePolicy {
    const val HIGH_SPEED_BULK_PACKET_BYTES = 512
    const val THERMAL_MODERATE = 2
    const val HISTORY_WINDOW = 5
    const val BAD_SESSIONS_TO_CAP = 2

    enum class Reason {
        FULL_SPEED_ENDPOINT,
        SOFTWARE_DECODER,
        REPEATED_STARVATION,
        THERMAL_PRESSURE,
        RECENT_STUTTER,
        USER_FPS_30,
    }

    data class Session(
        val framesFed: Long,
        val framesRendered: Long,
        val framesDropped: Long,
        val framesSkipped: Long,
    )

    data class Input(
        val wired: Boolean,
        val endpointMaxPacketBytes: Int,
        val forcedSoftwareDecoder: Boolean,
        val repeatedStarvation: Boolean,
        val thermalStatus: Int,
        val requestedFps: Int,
        val recentSessions: List<Session>,
    )

    data class Decision(
        val frameRate: Int,
        val capResolutionTo720: Boolean,
        val reasons: Set<Reason>,
    )

    fun decide(input: Input): Decision {
        if (!input.wired) return Decision(input.requestedFps, false, emptySet())

        val reasons = linkedSetOf<Reason>()
        if (input.endpointMaxPacketBytes in 1 until HIGH_SPEED_BULK_PACKET_BYTES) {
            reasons += Reason.FULL_SPEED_ENDPOINT
        }
        if (input.forcedSoftwareDecoder) reasons += Reason.SOFTWARE_DECODER
        if (input.repeatedStarvation) reasons += Reason.REPEATED_STARVATION
        if (input.thermalStatus >= THERMAL_MODERATE) reasons += Reason.THERMAL_PRESSURE
        if (input.recentSessions.takeLast(HISTORY_WINDOW).count(::stuttered) >= BAD_SESSIONS_TO_CAP) {
            reasons += Reason.RECENT_STUTTER
        }
        if (input.requestedFps <= 30) reasons += Reason.USER_FPS_30

        // A user's explicit 30 fps choice is already honoured, but it does not silently lower
        // resolution. Every measured/capability risk lowers both dimensions of the stream.
        val capabilityReasons = reasons - Reason.USER_FPS_30
        val cap = capabilityReasons.isNotEmpty()
        return Decision(
            frameRate = if (cap) minOf(input.requestedFps, 30) else input.requestedFps,
            capResolutionTo720 = cap,
            reasons = reasons,
        )
    }

    private fun stuttered(session: Session): Boolean {
        if (session.framesFed < 150) return false
        val fed = session.framesFed.toDouble()
        return session.framesRendered < fed * 0.85 ||
            session.framesDropped > fed * 0.05 ||
            session.framesSkipped > fed * 0.05
    }
}
