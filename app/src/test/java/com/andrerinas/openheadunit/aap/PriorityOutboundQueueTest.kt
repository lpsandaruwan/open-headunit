package com.andrerinas.openheadunit.aap

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PriorityOutboundQueueTest {
    private fun entry(
        value: String,
        priority: PriorityOutboundQueue.Priority,
        channel: Int? = null,
        replaceKey: Any? = null,
        time: Long = 0,
    ) = PriorityOutboundQueue.Entry(value, priority, channel, replaceKey, time)

    @Test fun controlAndAckOvertakeRoutineState() {
        val queue = PriorityOutboundQueue<String>()
        queue.offer(entry("sensor", PriorityOutboundQueue.Priority.ROUTINE_STATE))
        queue.offer(entry("input", PriorityOutboundQueue.Priority.INPUT))
        queue.offer(entry("ack", PriorityOutboundQueue.Priority.ACK))
        queue.offer(entry("control", PriorityOutboundQueue.Priority.CONTROL))

        assertEquals("control", queue.poll()!!.value)
        assertEquals("ack", queue.poll()!!.value)
        assertEquals("input", queue.poll()!!.value)
        assertEquals("sensor", queue.poll()!!.value)
    }

    @Test fun fifoIsStableInsideOnePriorityAndChannel() {
        val queue = PriorityOutboundQueue<String>()
        queue.offer(entry("down", PriorityOutboundQueue.Priority.INPUT, channel = 3))
        queue.offer(entry("move", PriorityOutboundQueue.Priority.INPUT, channel = 3))
        queue.offer(entry("up", PriorityOutboundQueue.Priority.INPUT, channel = 3))

        assertEquals(listOf("down", "move", "up"), List(3) { queue.poll()!!.value })
    }

    @Test fun latestReplaceableStateCoalescesWithoutTouchingTransitions() {
        val queue = PriorityOutboundQueue<String>()
        assertEquals(
            PriorityOutboundQueue.Offer.ADDED,
            queue.offer(entry("speed=1", PriorityOutboundQueue.Priority.ROUTINE_STATE, replaceKey = "speed")),
        )
        assertEquals(
            PriorityOutboundQueue.Offer.REPLACED,
            queue.offer(entry("speed=2", PriorityOutboundQueue.Priority.ROUTINE_STATE, replaceKey = "speed")),
        )

        assertEquals(1, queue.size())
        assertEquals("speed=2", queue.poll()!!.value)
    }

    @Test fun criticalReserveRetiresReplaceableStateBeforeRejectingControl() {
        val queue = PriorityOutboundQueue<String>(normalCapacity = 2, criticalReserve = 1)
        queue.offer(entry("sensor-a", PriorityOutboundQueue.Priority.ROUTINE_STATE, replaceKey = "a"))
        queue.offer(entry("sensor-b", PriorityOutboundQueue.Priority.ROUTINE_STATE, replaceKey = "b"))

        assertEquals(
            PriorityOutboundQueue.Offer.ADDED,
            queue.offer(entry("control", PriorityOutboundQueue.Priority.CONTROL)),
        )
        assertEquals(2, queue.size())
        assertEquals("control", queue.poll()!!.value)
    }

    @Test fun routineStateIsRejectedAtCapacityButCriticalUsesReserve() {
        val queue = PriorityOutboundQueue<String>(normalCapacity = 1, criticalReserve = 1)
        queue.offer(entry("default", PriorityOutboundQueue.Priority.DEFAULT))
        assertEquals(
            PriorityOutboundQueue.Offer.REJECTED,
            queue.offer(entry("sensor", PriorityOutboundQueue.Priority.ROUTINE_STATE, replaceKey = "speed")),
        )
        assertEquals(
            PriorityOutboundQueue.Offer.ADDED,
            queue.offer(entry("ack", PriorityOutboundQueue.Priority.ACK)),
        )
    }

    @Test fun fairnessEventuallyServesLowestPendingClass() {
        val queue = PriorityOutboundQueue<String>(fairnessBurst = 3)
        repeat(8) { queue.offer(entry("control-$it", PriorityOutboundQueue.Priority.CONTROL)) }
        queue.offer(entry("sensor", PriorityOutboundQueue.Priority.ROUTINE_STATE))

        assertEquals("control-0", queue.poll()!!.value)
        assertEquals("control-1", queue.poll()!!.value)
        assertEquals("control-2", queue.poll()!!.value)
        assertEquals("sensor", queue.poll()!!.value)
    }

    @Test fun priorityDrainPreservesOtherWork() {
        val queue = PriorityOutboundQueue<String>()
        queue.offer(entry("ack-1", PriorityOutboundQueue.Priority.ACK))
        queue.offer(entry("input", PriorityOutboundQueue.Priority.INPUT))
        queue.offer(entry("ack-2", PriorityOutboundQueue.Priority.ACK))

        assertEquals(listOf("ack-1", "ack-2"), queue.drain(PriorityOutboundQueue.Priority.ACK).map { it.value })
        assertFalse(queue.isEmpty())
        assertEquals("input", queue.poll()!!.value)
        assertTrue(queue.isEmpty())
    }
}
