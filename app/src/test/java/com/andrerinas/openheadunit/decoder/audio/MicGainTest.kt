package com.andrerinas.openheadunit.decoder.audio

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MicGainTest {

    private fun pcm(vararg samples: Int): ByteArray {
        val out = ByteArray(samples.size * 2)
        samples.forEachIndexed { i, s ->
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun samples(buf: ByteArray): List<Int> =
        (0 until buf.size step 2).map {
            ((buf[it + 1].toInt() shl 8) or (buf[it].toInt() and 0xFF)).toShort().toInt()
        }

    @Test
    fun `zero dB leaves the capture bit-exact`() {
        val src = pcm(0, 1, -1, 12000, -32768, 32767)
        val copy = src.copyOf()
        MicGain.apply(copy, copy.size, 0)
        assertArrayEquals(src, copy)
    }

    @Test
    fun `six dB roughly doubles a quiet signal`() {
        val buf = pcm(1000, -1000, 2000)
        MicGain.apply(buf, buf.size, 6)
        val out = samples(buf)
        assertEquals(1995.0, out[0].toDouble(), 3.0)
        assertEquals(-1995.0, out[1].toDouble(), 3.0)
        assertEquals(3990.0, out[2].toDouble(), 5.0)
    }

    @Test
    fun `a boosted loud signal is compressed and never wraps`() {
        val buf = pcm(30000, -30000, 32767, -32768)
        MicGain.apply(buf, buf.size, 12)
        val out = samples(buf)
        assertTrue(out[0] > 0 && out[0] <= 32767)
        assertTrue(out[1] < 0 && out[1] >= -32768)
        assertTrue(out[2] > 0)
        assertTrue(out[3] < 0)
        assertEquals(-out[0], out[1])
    }

    @Test
    fun `the shaping curve is monotonic and below full scale`() {
        var last = -1.0
        var x = 0.0
        while (x <= 4.0) {
            val y = MicGain.shape(x)
            assertTrue(y >= last)
            assertTrue(y < 1.0)
            last = y
            x += 0.01
        }
    }

    @Test
    fun `gain is clamped to the supported range`() {
        assertEquals(0, MicGain.clamp(-5))
        assertEquals(12, MicGain.clamp(40))
    }

    @Test
    fun `the meter reads peak, rms and clipping`() {
        val meter = MicLevelMeter()
        meter.add(pcm(0, 0, 0, 0), 8)
        assertEquals(0, meter.peak)
        assertEquals(0, meter.rms())
        meter.add(pcm(3000, -3000, 32500, -100), 8)
        assertEquals(32500, meter.peak)
        assertEquals(1L, meter.clipped)
        assertEquals(8L, meter.samples)
        meter.reset()
        assertEquals(0L, meter.samples)
        assertEquals(0, meter.peak)
    }
}
