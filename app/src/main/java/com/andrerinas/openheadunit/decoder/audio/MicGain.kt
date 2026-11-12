package com.andrerinas.openheadunit.decoder.audio

import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.tanh

/**
 * Software gain for the head unit's microphone, with a soft knee so a raised level never hard-clips.
 *
 * Some head-unit microphones are quiet enough that the person on the other end of a call asks to be
 * spoken up to, and the platform's own gain control is unavailable or off on those units. This lifts
 * the level on the PCM offered to the phone. At 0 dB nothing is touched, so the default is bit-exact
 * with the stream that has always been sent.
 *
 * Mono 16-bit little-endian PCM, in place. Pure: no Android, no logging.
 */
object MicGain {

    const val MIN_DB = 0
    const val MAX_DB = 12

    /** Below this fraction of full scale the signal passes through linearly. */
    private const val KNEE = 0.7

    fun clamp(db: Int): Int = db.coerceIn(MIN_DB, MAX_DB)

    fun linear(db: Int): Double = 10.0.pow(clamp(db) / 20.0)

    /** One sample, -1..1 in and out; linear to [KNEE], then compressed toward but never reaching 1. */
    fun shape(x: Double): Double {
        val a = abs(x)
        if (a <= KNEE) return x
        val shaped = KNEE + (1 - KNEE) * tanh((a - KNEE) / (1 - KNEE))
        return if (x < 0) -shaped else shaped
    }

    fun apply(buf: ByteArray, len: Int, gainDb: Int) {
        val db = clamp(gainDb)
        if (db == 0) return
        val gain = linear(db)
        var i = 0
        while (i + 1 < len) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
            val out = (shape(s / 32768.0 * gain) * 32767.0).roundToInt().coerceIn(-32768, 32767)
            buf[i] = (out and 0xFF).toByte()
            buf[i + 1] = ((out shr 8) and 0xFF).toByte()
            i += 2
        }
    }
}

/**
 * Level over a window of microphone reads, for a line in the log every few seconds.
 *
 * The capture summary only prints when capture stops, so a call where the other side heard nothing
 * leaves one line for the whole call. This gives a timeline: a dead input shows peak 0, a quiet one
 * a low rms, an over-driven one clipped samples.
 */
class MicLevelMeter {
    var peak = 0; private set
    var clipped = 0L; private set
    var samples = 0L; private set
    private var sumSquares = 0.0

    fun add(buf: ByteArray, len: Int) {
        var i = 0
        while (i + 1 < len) {
            val s = ((buf[i + 1].toInt() shl 8) or (buf[i].toInt() and 0xFF)).toShort().toInt()
            val m = if (s == Short.MIN_VALUE.toInt()) 32767 else abs(s)
            if (m > peak) peak = m
            if (m >= CLIP_LEVEL) clipped++
            sumSquares += m.toDouble() * m
            samples++
            i += 2
        }
    }

    /** Root mean square on the 16-bit scale, or 0 with nothing measured. */
    fun rms(): Int = if (samples == 0L) 0 else kotlin.math.sqrt(sumSquares / samples).roundToInt()

    fun reset() {
        peak = 0; clipped = 0; samples = 0; sumSquares = 0.0
    }

    companion object {
        const val CLIP_LEVEL = 32000
        /** Ten seconds at the 16 kHz wire rate. */
        const val WINDOW_SAMPLES = 160_000L
    }
}
