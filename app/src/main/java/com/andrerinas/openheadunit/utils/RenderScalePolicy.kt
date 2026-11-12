package com.andrerinas.openheadunit.utils

import kotlin.math.roundToInt

/**
 * A custom render size between the fixed Android Auto resolutions. The protocol only negotiates
 * 480p, 720p, 1080p and up, but the phone draws only inside the margins it is told to keep clear,
 * so a larger margin is a smaller canvas: at 85 % of 1080p the phone renders and composes about
 * 1632x918 and leaves the rest of the buffer black, and this head unit scales that canvas back up
 * to the panel. The encoded buffer stays the negotiated size, so this lightens the phone's drawing,
 * not the codec's frame size.
 *
 * The percentage scales the canvas the panel geometry already chose, so its shape - and with it
 * the pixel aspect ratio and the per-side margin split - is unchanged.
 */
object RenderScalePolicy {

    const val FULL = 100
    const val MIN = 50

    data class Margins(val width: Int, val height: Int)

    fun clamp(percent: Int): Int = percent.coerceIn(MIN, FULL)

    /**
     * The margins that leave [percent] of the canvas [autoMarginW]x[autoMarginH] would leave.
     * Canvas sides are kept even: an odd side splits into unequal margins, which shifts the picture
     * by a pixel against the touch mapping.
     */
    fun margins(videoW: Int, videoH: Int, autoMarginW: Int, autoMarginH: Int, percent: Int): Margins {
        val p = clamp(percent)
        if (p == FULL || videoW <= 0 || videoH <= 0) return Margins(autoMarginW, autoMarginH)
        val canvasW = scaledSide(videoW - autoMarginW, p)
        val canvasH = scaledSide(videoH - autoMarginH, p)
        return Margins(videoW - canvasW, videoH - canvasH)
    }

    private fun scaledSide(side: Int, percent: Int): Int {
        if (side <= 0) return side
        val even = ((side * percent / 100f) / 2f).roundToInt() * 2
        return even.coerceIn(2, side)
    }
}
