package dev.andface.galaxy.mediapipe

import java.util.concurrent.Executor

/** One scheduled delivery and one replaceable value; safety failures cannot be overwritten. */
internal class LatestFrameDispatcher<T>(
    private val executor: Executor,
    private val consume: (T) -> Unit
) {
    private data class Pending<T>(val value: T, val priority: Int)
    private var pending: Pending<T>? = null
    private var scheduled = false

    @Synchronized
    fun offer(value: T, priority: Int = 0) {
        val current = pending
        if (current == null || current.priority == 0 || priority > current.priority) {
            pending = Pending(value, priority)
        }
        if (scheduled) return
        scheduled = true
        try {
            executor.execute(::deliver)
        } catch (error: RuntimeException) {
            scheduled = false
            pending = null
            throw error
        }
    }

    @Synchronized
    fun clear() {
        pending = null
    }

    private fun deliver() {
        val next = synchronized(this) {
            val value = pending
            pending = null
            scheduled = false
            value
        }
        next?.let { consume(it.value) }
    }
}
