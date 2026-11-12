package com.andrerinas.openheadunit.aap

import java.util.LinkedHashMap

/**
 * Batches media credits without weakening their channel/session ownership.
 *
 * Credits are recorded by the reader and video workers, while draining belongs to the single TLS
 * writer. All methods are synchronized so shutdown can atomically take the remaining credits even
 * when it jumps to the front of the writer queue.
 */
internal class MediaAckAccumulator(
    private val threshold: Int = 4,
    private val maxDelayMs: Long = 2,
) {
    init {
        require(threshold > 0)
        require(maxDelayMs >= 0)
    }

    data class Batch(val channel: Int, val sessionId: Int, val credits: Int)

    private data class Key(val channel: Int, val sessionId: Int)
    private data class Pending(var credits: Int, var dueMs: Long)
    private val pending = LinkedHashMap<Key, Pending>()

    @Synchronized fun credit(channel: Int, sessionId: Int, nowMs: Long) {
        // A replacement media session must not wait behind the new session's batching window.
        pending.forEach { (key, value) ->
            if (key.channel == channel && key.sessionId != sessionId) {
                value.dueMs = minOf(value.dueMs, nowMs)
            }
        }

        val key = Key(channel, sessionId)
        val value = pending[key]
        if (value == null) {
            pending[key] = Pending(1, nowMs + maxDelayMs)
            if (threshold == 1) pending[key]!!.dueMs = nowMs
            return
        }

        // The normal threshold keeps this far from overflow. Saturate defensively so malformed
        // or synthetic traffic can never wrap a positive acknowledgement into zero/negative.
        value.credits = if (value.credits == Int.MAX_VALUE) Int.MAX_VALUE else value.credits + 1
        if (value.credits >= threshold) value.dueMs = nowMs
    }

    /** Make every old credit on this channel eligible before publishing a replacement session. */
    @Synchronized fun markChannelReady(channel: Int, nowMs: Long) {
        pending.forEach { (key, value) ->
            if (key.channel == channel) value.dueMs = minOf(value.dueMs, nowMs)
        }
    }

    @Synchronized fun drainReady(nowMs: Long): List<Batch> = drain { it.dueMs <= nowMs }

    @Synchronized fun drainAll(): List<Batch> = drain { true }

    @Synchronized fun nextDelayMs(nowMs: Long): Long? =
        pending.values.minOfOrNull { (it.dueMs - nowMs).coerceAtLeast(0) }

    @Synchronized fun clear() = pending.clear()

    private fun drain(ready: (Pending) -> Boolean): List<Batch> {
        if (pending.isEmpty()) return emptyList()
        val batches = ArrayList<Batch>()
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val (key, value) = iterator.next()
            if (!ready(value)) continue
            if (value.credits > 0) batches.add(Batch(key.channel, key.sessionId, value.credits))
            iterator.remove()
        }
        return batches
    }
}
