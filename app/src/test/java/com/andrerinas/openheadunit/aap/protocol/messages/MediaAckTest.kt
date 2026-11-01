package com.andrerinas.openheadunit.aap.protocol.messages

import com.andrerinas.openheadunit.aap.protocol.proto.Media
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MediaAckTest {
    @Test fun serializesAggregatedCreditCount() {
        val message = MediaAck(channel = 3, sessionId = 41, credits = 4)
        val ack = message.parse(Media.Ack.newBuilder()).build()

        assertEquals(41, ack.sessionId)
        assertEquals(4, ack.ack)
    }

    @Test fun refusesEmptyCreditMessage() {
        assertThrows(IllegalArgumentException::class.java) {
            MediaAck(channel = 3, sessionId = 41, credits = 0)
        }
    }
}
