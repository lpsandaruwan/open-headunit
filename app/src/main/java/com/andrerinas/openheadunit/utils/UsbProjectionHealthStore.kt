package com.andrerinas.openheadunit.utils

import android.content.Context

/** Small wired-only history used before the next USB service discovery. */
class UsbProjectionHealthStore(context: Context) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun state(): UsbProjectionHealthPolicy.State = UsbProjectionHealthPolicy.State(
        starvedStreak = prefs.getInt(KEY_STARVED, 0),
        profileCapped = prefs.getBoolean(KEY_CAPPED, false),
        healthySinceCap = prefs.getInt(KEY_HEALTHY, 0),
    )

    fun record(reachedHandshake: Boolean, renderedAnyFrame: Boolean, deliberateEnd: Boolean) {
        val next = UsbProjectionHealthPolicy.next(
            state(), reachedHandshake, renderedAnyFrame, deliberateEnd
        )
        prefs.edit()
            .putInt(KEY_STARVED, next.starvedStreak)
            .putBoolean(KEY_CAPPED, next.profileCapped)
            .putInt(KEY_HEALTHY, next.healthySinceCap)
            .apply()
    }

    private companion object {
        const val PREFS = "usb-projection-health"
        const val KEY_STARVED = "starved-streak"
        const val KEY_CAPPED = "profile-capped"
        const val KEY_HEALTHY = "healthy-since-cap"
    }
}

object UsbProjectionHealthPolicy {
    const val STARVED_TO_CAP = 3
    const val HEALTHY_TO_RECOVER = 3

    data class State(
        val starvedStreak: Int = 0,
        val profileCapped: Boolean = false,
        val healthySinceCap: Int = 0,
    )

    fun next(
        state: State,
        reachedHandshake: Boolean,
        renderedAnyFrame: Boolean,
        deliberateEnd: Boolean,
    ): State {
        if (deliberateEnd || !reachedHandshake) return state
        if (!renderedAnyFrame) {
            val streak = (state.starvedStreak + 1).coerceAtMost(Int.MAX_VALUE)
            return State(
                starvedStreak = streak,
                profileCapped = state.profileCapped || streak >= STARVED_TO_CAP,
                healthySinceCap = 0,
            )
        }

        if (!state.profileCapped) return State()
        val healthy = state.healthySinceCap + 1
        return if (healthy >= HEALTHY_TO_RECOVER) State()
        else State(starvedStreak = 0, profileCapped = true, healthySinceCap = healthy)
    }
}
