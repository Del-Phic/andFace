package dev.andface.galaxy.mediapipe

import org.junit.Assert.*
import org.junit.Test

class LiveFrameSessionTest {
    @Test
    fun pausedAndPreviousSessionFramesAreRejected() {
        val session = LiveFrameSession()
        session.start(1000L)
        assertTrue(session.accept(1001L, 1010L))
        session.stop()
        assertFalse(session.accept(1100L, 1110L))
        session.start(1200L)
        assertFalse(session.accept(1199L, 1210L))
        assertFalse(session.accept(1200L, 1210L))
        assertTrue(session.accept(1201L, 1210L))
    }

    @Test
    fun delayedDuplicateOutOfOrderAndFutureResultsAreRejected() {
        val session = LiveFrameSession()
        session.start(1000L)
        assertFalse(session.accept(1001L, 1752L))
        assertTrue(session.accept(1800L, 1810L))
        assertFalse(session.accept(1800L, 1820L))
        assertFalse(session.accept(1799L, 1820L))
        assertFalse(session.accept(1821L, 1820L))
    }

    @Test
    fun silenceExpiresWithoutWaitingForAnotherCameraCallback() {
        val session = LiveFrameSession()
        session.start(1000L)
        assertFalse(session.timedOut(2499L))
        assertTrue(session.timedOut(2500L))
        assertTrue(session.accept(2501L, 2501L))
        assertFalse(session.timedOut(3999L))
        assertTrue(session.timedOut(4001L))
        session.stop()
        assertFalse(session.timedOut(10000L))
    }
}
