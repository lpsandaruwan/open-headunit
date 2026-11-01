package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaAckAccumulatorTest {
    @Test fun fourCreditsBecomeOneBatch() {
        val acks = MediaAckAccumulator()
        repeat(4) { acks.credit(channel = 3, sessionId = 17, nowMs = 100) }

        assertEquals(listOf(MediaAckAccumulator.Batch(3, 17, 4)), acks.drainReady(100))
        assertNull(acks.nextDelayMs(100))
    }

    @Test fun oneCreditWaitsAtMostTwoMilliseconds() {
        val acks = MediaAckAccumulator()
        acks.credit(3, 17, 100)

        assertEquals(emptyList<MediaAckAccumulator.Batch>(), acks.drainReady(101))
        assertEquals(1L, acks.nextDelayMs(101))
        assertEquals(listOf(MediaAckAccumulator.Batch(3, 17, 1)), acks.drainReady(102))
    }

    @Test fun channelsNeverMerge() {
        val acks = MediaAckAccumulator()
        acks.credit(3, 17, 100)
        acks.credit(4, 17, 100)

        assertEquals(
            listOf(
                MediaAckAccumulator.Batch(3, 17, 1),
                MediaAckAccumulator.Batch(4, 17, 1),
            ),
            acks.drainAll(),
        )
    }

    @Test fun replacementSessionMakesOldCreditReadyWithoutMixing() {
        val acks = MediaAckAccumulator()
        acks.credit(3, 17, 100)
        acks.credit(3, 18, 101)

        assertEquals(listOf(MediaAckAccumulator.Batch(3, 17, 1)), acks.drainReady(101))
        assertEquals(listOf(MediaAckAccumulator.Batch(3, 18, 1)), acks.drainReady(103))
    }

    @Test fun sessionPublicationCanFlushOldChannelCredits() {
        val acks = MediaAckAccumulator()
        acks.credit(3, 17, 100)
        acks.markChannelReady(3, 101)

        assertEquals(listOf(MediaAckAccumulator.Batch(3, 17, 1)), acks.drainReady(101))
    }

    @Test fun shutdownAtomicallyTakesEverything() {
        val acks = MediaAckAccumulator()
        acks.credit(3, 17, 100)
        acks.credit(4, 22, 100)

        assertEquals(2, acks.drainAll().size)
        assertNull(acks.nextDelayMs(100))
    }
}
