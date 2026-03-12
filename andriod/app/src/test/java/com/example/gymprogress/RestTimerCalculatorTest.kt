package com.example.gymprogress

import org.junit.Assert.assertEquals
import org.junit.Test

class RestTimerCalculatorTest {
    @Test
    fun computeRemainingSecondsRoundsUpPartialSeconds() {
        assertEquals(45, computeRemainingSeconds(endElapsedRealtimeMs = 45_000L, nowElapsedRealtimeMs = 1L))
        assertEquals(1, computeRemainingSeconds(endElapsedRealtimeMs = 45_000L, nowElapsedRealtimeMs = 44_001L))
    }

    @Test
    fun computeRemainingSecondsClampsToZeroAfterDeadline() {
        assertEquals(0, computeRemainingSeconds(endElapsedRealtimeMs = 45_000L, nowElapsedRealtimeMs = 45_000L))
        assertEquals(0, computeRemainingSeconds(endElapsedRealtimeMs = 45_000L, nowElapsedRealtimeMs = 46_500L))
    }

    @Test
    fun computeDelayUntilNextTickUsesNextSecondBoundary() {
        assertEquals(999L, computeDelayUntilNextTick(endElapsedRealtimeMs = 45_000L, nowElapsedRealtimeMs = 1L))
        assertEquals(1L, computeDelayUntilNextTick(endElapsedRealtimeMs = 45_000L, nowElapsedRealtimeMs = 44_999L))
    }

    @Test
    fun computeDelayUntilNextTickReturnsZeroWhenFinished() {
        assertEquals(0L, computeDelayUntilNextTick(endElapsedRealtimeMs = 45_000L, nowElapsedRealtimeMs = 45_000L))
        assertEquals(0L, computeDelayUntilNextTick(endElapsedRealtimeMs = 45_000L, nowElapsedRealtimeMs = 50_000L))
    }
}
