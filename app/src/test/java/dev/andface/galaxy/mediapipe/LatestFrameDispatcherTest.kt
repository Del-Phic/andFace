package dev.andface.galaxy.mediapipe

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.Executor

class LatestFrameDispatcherTest {
    private val tasks = ArrayDeque<Runnable>()
    private val delivered = mutableListOf<Int>()
    private val dispatcher = LatestFrameDispatcher<Int>(Executor { tasks.addLast(it) }, delivered::add)

    @Test
    fun slowConsumerKeepsOnlyTheLatestFrameAndOneScheduledTask() {
        repeat(1000) { dispatcher.offer(it) }
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals(listOf(999), delivered)
    }

    @Test
    fun multipleFaceOrErrorEventSurvivesLaterNormalFrames() {
        dispatcher.offer(1)
        dispatcher.offer(2, priority = 2)
        repeat(100) { dispatcher.offer(3) }
        tasks.removeFirst().run()
        assertEquals(listOf(2), delivered)
        dispatcher.offer(4)
        tasks.removeFirst().run()
        assertEquals(listOf(2, 4), delivered)
    }

    @Test
    fun clearingSessionDiscardsPendingResults() {
        dispatcher.offer(1, priority = 2)
        dispatcher.clear()
        dispatcher.offer(2)
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals(listOf(2), delivered)
    }

    @Test
    fun multipleFacesSupersedeMissingFaceAndCannotBeHiddenByAnotherMissingFace() {
        dispatcher.offer(1, priority = 1)
        dispatcher.offer(2, priority = 2)
        dispatcher.offer(3, priority = 1)
        dispatcher.offer(4)
        tasks.removeFirst().run()
        assertEquals(listOf(2), delivered)
    }

    @Test
    fun arrivalDuringDeliverySchedulesOneNewDelivery() {
        val target = LatestFrameDispatcher<Int>(Executor { tasks.addLast(it) }) {
            delivered.add(it)
            dispatcher.offer(2)
            dispatcher.offer(3)
        }
        target.offer(1)
        tasks.removeFirst().run()
        assertEquals(1, tasks.size)
        tasks.removeFirst().run()
        assertEquals(listOf(1, 3), delivered)
    }
}
