package com.andrerinas.openheadunit.connection.projection

/** Pure feature gates for the experimental framework USB request backend. */
object AsyncUsbReadPolicy {
    const val MIN_API = 26

    fun isEligible(requested: Boolean, apiLevel: Int, usesLibusb: Boolean): Boolean =
        requested && apiLevel >= MIN_API && !usesLibusb
}
