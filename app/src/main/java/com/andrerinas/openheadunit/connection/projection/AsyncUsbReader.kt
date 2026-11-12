package com.andrerinas.openheadunit.connection.projection

import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbEndpoint
import android.hardware.usb.UsbRequest
import android.os.Build
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer
import java.util.TreeMap

/**
 * A two-request receive pipeline for Android's framework USB backend.
 *
 * Only the projection poll thread calls [read]. [close] may be called concurrently by disconnect.
 * Completed requests are consumed by queue sequence, even if a framework happens to report their
 * callbacks out of order. A slot is not requeued until every byte in it has been delivered.
 */
@RequiresApi(Build.VERSION_CODES.O)
internal class AsyncUsbReader private constructor(
    private val connection: UsbDeviceConnection,
    private val endpoint: UsbEndpoint,
    private val capacity: Int
) {
    private class Slot(
        val request: UsbRequest,
        val buffer: ByteBuffer
    ) {
        var sequence = -1L
        var length = 0
        var consumed = 0
    }

    private val slots = ArrayList<Slot>(PIPELINE_DEPTH)
    private val completed = TreeMap<Long, Slot>()
    private var nextQueueSequence = 0L
    private var nextConsumeSequence = 0L
    private var current: Slot? = null

    @Volatile private var closed = false
    @Volatile var terminalFailure = false
        private set

    /** Number of bytes already exposed to AAP; useful when diagnosing an unsafe mid-stream fault. */
    var deliveredBytes = 0L
        private set

    /**
     * Queues both requests. A false result means this connection must be retired: once the first
     * request was queued, switching to bulkTransfer on the same handle could skip received bytes.
     */
    private fun start(): Boolean {
        repeat(PIPELINE_DEPTH) {
            val request = UsbRequest()
            if (!request.initialize(connection, endpoint)) {
                request.close()
                close()
                return false
            }
            val slot = Slot(request, ByteBuffer.allocateDirect(capacity))
            request.clientData = slot
            slots.add(slot)
        }
        for (slot in slots) {
            if (!queue(slot)) {
                terminalFailure = true
                close()
                return false
            }
        }
        return true
    }

    fun read(target: ByteArray, length: Int, timeoutMs: Int, readFully: Boolean): Int {
        if (closed || terminalFailure) return -1
        if (length <= 0) return 0
        val deadline = if (timeoutMs > 0) SystemClock.elapsedRealtime() + timeoutMs else Long.MAX_VALUE
        var total = 0

        try {
            while (total < length && !closed) {
                var slot = current
                if (slot == null) {
                    slot = awaitNext(deadline) ?: return total
                    if (slot.length == 0) {
                        finish(slot)
                        continue
                    }
                    current = slot
                }

                val count = minOf(length - total, slot.length - slot.consumed)
                slot.buffer.position(slot.consumed)
                slot.buffer.get(target, total, count)
                slot.consumed += count
                total += count
                deliveredBytes += count

                if (slot.consumed == slot.length) {
                    current = null
                    finish(slot)
                }
                if (!readFully && total > 0) break
            }
            return if (closed && total == 0) -1 else total
        } catch (_: Throwable) {
            if (!closed) terminalFailure = true
            return if (total > 0) total else -1
        }
    }

    private fun awaitNext(deadline: Long): Slot? {
        completed.remove(nextConsumeSequence)?.let { return prepare(it) }
        while (!closed) {
            val request = if (deadline == Long.MAX_VALUE) {
                connection.requestWait()
            } else {
                val remaining = deadline - SystemClock.elapsedRealtime()
                if (remaining <= 0) return null
                connection.requestWait(remaining)
            } ?: return null

            val slot = request.clientData as? Slot
            if (slot == null || slot.request !== request || slot.sequence < nextConsumeSequence) {
                terminalFailure = true
                return null
            }
            slot.length = slot.buffer.position()
            slot.consumed = 0
            slot.buffer.flip()
            completed[slot.sequence] = slot
            completed.remove(nextConsumeSequence)?.let { return prepare(it) }
        }
        return null
    }

    private fun prepare(slot: Slot): Slot {
        nextConsumeSequence++
        return slot
    }

    private fun finish(slot: Slot) {
        if (!closed && !queue(slot)) terminalFailure = true
    }

    private fun queue(slot: Slot): Boolean {
        slot.buffer.clear()
        slot.length = 0
        slot.consumed = 0
        slot.sequence = nextQueueSequence++
        return try {
            slot.request.queue(slot.buffer)
        } catch (_: Throwable) {
            false
        }
    }

    fun close() {
        if (closed) return
        closed = true
        for (slot in slots) {
            try { slot.request.cancel() } catch (_: Throwable) { }
            try { slot.request.close() } catch (_: Throwable) { }
        }
        completed.clear()
        current = null
    }

    companion object {
        private const val PIPELINE_DEPTH = 2

        /** Returns null when requests cannot be initialized before the session starts. */
        fun create(
            connection: UsbDeviceConnection,
            endpoint: UsbEndpoint,
            capacity: Int
        ): AsyncUsbReader? = AsyncUsbReader(connection, endpoint, capacity).takeIf { it.start() }
    }
}
