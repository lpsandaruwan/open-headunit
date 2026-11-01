package com.andrerinas.openheadunit.input

import android.view.KeyEvent

/**
 * Decodes the steering-wheel media keys of a Dongfeng head unit from the log lines its input
 * service writes.
 *
 * On these units the wheel buttons are not Android `KeyEvent`s and not broadcasts. The MCU hands
 * them to `com.dftc.carinput` (a system service), which passes them to clients that registered for a
 * key — `com.dftc.media` is the only one, and it drops them unless the stock CarPlay or radio source
 * is on its media card. Registering needs `df.carinput.permission.MONITOR_INPUT`, which only system
 * apps can hold, so the one thing an ordinary app can see is the line the service logs for every
 * key it dispatches:
 * ```
 *   I/DF_CARINPUT( 2140): [Capture]dispatchHWKeyEvent: inputKey=12,inputAction=0
 * ```
 * Reading it needs `READ_LOGS`, granted once with `pm grant`. Measured on a Dongfeng "BOX" unit
 * (Android 10): each press logs one `inputAction=0` and, on release, one `2` (short) or `3` (long).
 */
object DongfengWheelKey {

    /** The service whose log tag carries the key lines, and the package that identifies the unit. */
    const val LOG_TAG = "DF_CARINPUT"
    const val INPUT_PACKAGE = "com.dftc.carinput"

    private const val DOWN = 0
    private const val SHORT_UP = 2
    private const val LONG_UP = 3

    private val LINE = Regex("""dispatchHWKeyEvent: inputKey=(\d+),inputAction=(\d+)""")

    /** Wheel key numbers seen in the logs, mapped to the Android key the phone should receive. */
    private val KEYS = mapOf(
        11 to KeyEvent.KEYCODE_MEDIA_PREVIOUS,
        12 to KeyEvent.KEYCODE_MEDIA_NEXT,
        20 to KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE,
    )

    data class Event(val keyCode: Int, val isDown: Boolean)

    /** The key event a log line describes, or null for any other line or an unknown key. */
    fun parse(line: String): Event? {
        val match = LINE.find(line) ?: return null
        val key = match.groupValues[1].toIntOrNull() ?: return null
        val action = match.groupValues[2].toIntOrNull() ?: return null
        val keyCode = KEYS[key] ?: return null
        return when (action) {
            DOWN -> Event(keyCode, true)
            SHORT_UP, LONG_UP -> Event(keyCode, false)
            else -> null
        }
    }
}
