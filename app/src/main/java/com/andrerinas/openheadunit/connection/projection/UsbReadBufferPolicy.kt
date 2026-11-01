package com.andrerinas.openheadunit.connection.projection

/**
 * Receive size shared by both USB transports.
 *
 * Android limited bulk transfers to 16 KiB before API 28. The Android 10 head unit is past that
 * limit, so one fixed 64 KiB buffer lets a large AAP fragment arrive with fewer framework/JNI
 * calls. Older units retain the safe legacy size. Buffers are allocated once per connection.
 */
object UsbReadBufferPolicy {
    const val LEGACY_BYTES = 16 * 1024
    const val MODERN_BYTES = 64 * 1024
    const val LARGE_BULK_API = 28

    fun capacity(sdkInt: Int): Int =
        if (sdkInt >= LARGE_BULK_API) MODERN_BYTES else LEGACY_BYTES
}
