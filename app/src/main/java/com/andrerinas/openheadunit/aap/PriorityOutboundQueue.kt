package com.andrerinas.openheadunit.aap

import java.util.ArrayDeque

/** A bounded, stable-priority queue with a reserve for protocol-critical work. */
internal class PriorityOutboundQueue<T>(
    private val normalCapacity: Int = 256,
    private val criticalReserve: Int = 64,
    private val fairnessBurst: Int = 8,
) {
    enum class Priority {
        CONTROL,
        ACK,
        MICROPHONE,
        INPUT,
        AUDIO_NOTICE,
        DEFAULT,
        ROUTINE_STATE;

        val isCritical: Boolean get() = this != DEFAULT && this != ROUTINE_STATE
    }

    enum class Offer { ADDED, REPLACED, REJECTED }

    data class Entry<T>(
        val value: T,
        val priority: Priority,
        val channel: Int?,
        val replaceKey: Any?,
        val enqueuedMs: Long,
    )

    private val queues = Array(Priority.entries.size) { ArrayDeque<Entry<T>>() }
    private var count = 0
    private var urgentRun = 0

    init {
        require(normalCapacity > 0)
        require(criticalReserve >= 0)
        require(fairnessBurst > 0)
    }

    @Synchronized fun offer(entry: Entry<T>): Offer {
        if (entry.replaceKey != null && removeReplacement(entry.replaceKey)) {
            add(entry)
            return Offer.REPLACED
        }

        if (count >= normalCapacity) {
            if (!entry.priority.isCritical) return Offer.REJECTED
            // Protocol work may consume the reserve. Prefer retiring stale replaceable state first.
            removeOldestReplaceable()
            if (count >= normalCapacity + criticalReserve) return Offer.REJECTED
        }
        add(entry)
        return Offer.ADDED
    }

    @Synchronized fun poll(): Entry<T>? {
        val highest = queues.indexOfFirst { it.isNotEmpty() }
        if (highest < 0) {
            urgentRun = 0
            return null
        }

        val lowerPending = (highest + 1 until queues.size).any { queues[it].isNotEmpty() }
        val selected = if (lowerPending && urgentRun >= fairnessBurst) {
            // One oldest item from the lowest pending class bounds starvation without disturbing
            // FIFO order inside any class/channel.
            (queues.lastIndex downTo highest + 1).first { queues[it].isNotEmpty() }
        } else {
            highest
        }
        if (selected == highest && lowerPending) urgentRun++ else urgentRun = 0
        count--
        return queues[selected].removeFirst()
    }

    @Synchronized fun drain(priority: Priority): List<Entry<T>> {
        val queue = queues[priority.ordinal]
        if (queue.isEmpty()) return emptyList()
        val result = ArrayList<Entry<T>>(queue.size)
        while (queue.isNotEmpty()) {
            result.add(queue.removeFirst())
            count--
        }
        return result
    }

    /** Removes the exact work item when its Handler refuses the drain callback. */
    @Synchronized fun remove(entry: Entry<T>): Boolean {
        val removed = queues[entry.priority.ordinal].remove(entry)
        if (removed) count--
        return removed
    }

    @Synchronized fun isEmpty(): Boolean = count == 0
    @Synchronized fun size(): Int = count

    @Synchronized fun clear() {
        queues.forEach { it.clear() }
        count = 0
        urgentRun = 0
    }

    private fun add(entry: Entry<T>) {
        queues[entry.priority.ordinal].addLast(entry)
        count++
    }

    private fun removeReplacement(key: Any): Boolean {
        for (queue in queues) {
            val iterator = queue.iterator()
            while (iterator.hasNext()) {
                if (iterator.next().replaceKey == key) {
                    iterator.remove()
                    count--
                    return true
                }
            }
        }
        return false
    }

    private fun removeOldestReplaceable(): Boolean {
        for (index in queues.lastIndex downTo 0) {
            val iterator = queues[index].iterator()
            while (iterator.hasNext()) {
                if (iterator.next().replaceKey != null) {
                    iterator.remove()
                    count--
                    return true
                }
            }
        }
        return false
    }
}
