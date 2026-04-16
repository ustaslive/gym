package com.example.gymprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WorkoutRestTimerStateReducerTest {
    private val reducer = WorkoutRestTimerStateReducer()

    @Test
    fun restoreBuildsStateFromRemainingSeconds() {
        val state = reducer.restore(
            activeRestTimerExerciseId = "row",
            restTimerRemainingSeconds = 75,
            nowEpochMillis = 1_000L
        )

        assertEquals("row", state.activeExerciseId)
        assertEquals(76_000L, state.endEpochMillis)
        assertEquals("1:15", state.statusText)
    }

    @Test
    fun onTimerUpdatedRefreshesStatusTextForActiveExercise() {
        val currentState = RestTimerUiState(
            activeExerciseId = "row",
            endEpochMillis = 10_000L,
            statusText = "0:10"
        )

        val result = reducer.onTimerUpdated(
            currentState = currentState,
            exerciseId = "row",
            secondsRemaining = 9
        )

        assertEquals("0:09", result.statusText)
        assertEquals("row", result.activeExerciseId)
    }

    @Test
    fun onTimerStateChangedClearsVisibleStatusWhenTimerStops() {
        val currentState = RestTimerUiState(
            activeExerciseId = "row",
            endEpochMillis = 10_000L,
            statusText = "0:09"
        )

        val result = reducer.onTimerStateChanged(
            currentState = currentState,
            exerciseId = null,
            endEpochMillis = null
        )

        assertNull(result.activeExerciseId)
        assertNull(result.endEpochMillis)
        assertNull(result.statusText)
    }
}
