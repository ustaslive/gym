package com.example.gymprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutSessionStateTest {
    @Test
    fun serializeWorkoutSessionSnapshotRoundTripsAllFields() {
        val snapshot = WorkoutSessionSnapshot(
            exerciseStates = listOf(
                WorkoutSessionExerciseState(id = "a", completedSets = 1, isUnlocked = true),
                WorkoutSessionExerciseState(id = "b", completedSets = 3, isUnlocked = false)
            ),
            exerciseOrder = listOf("b", "a"),
            activeExerciseId = "a",
            activeRestTimerExerciseId = "b",
            restTimerEndEpochMillis = 12_345L
        )

        val restored = deserializeWorkoutSessionSnapshot(serializeWorkoutSessionSnapshot(snapshot))

        assertEquals(snapshot, restored)
    }

    @Test
    fun restoreWorkoutSessionAppliesSavedProgressOrderAndTimer() {
        val baseExercises = listOf(
            sampleExercise(id = "a", totalSets = 3, completedSets = 0, isUnlocked = true),
            sampleExercise(id = "b", totalSets = 4, completedSets = 0, isUnlocked = true),
            sampleExercise(id = "c", totalSets = 2, completedSets = 0, isUnlocked = true)
        )
        val snapshot = WorkoutSessionSnapshot(
            exerciseStates = listOf(
                WorkoutSessionExerciseState(id = "a", completedSets = 1, isUnlocked = true),
                WorkoutSessionExerciseState(id = "b", completedSets = 4, isUnlocked = true),
                WorkoutSessionExerciseState(id = "c", completedSets = 0, isUnlocked = false)
            ),
            exerciseOrder = listOf("b", "a", "c"),
            activeExerciseId = "b",
            activeRestTimerExerciseId = "b",
            restTimerEndEpochMillis = 11_500L
        )

        val restored = restoreWorkoutSession(
            baseExercises = baseExercises,
            snapshot = snapshot,
            defaultOrder = listOf("a", "b", "c"),
            nowEpochMillis = 10_001L
        )

        assertEquals(listOf("b", "a", "c"), restored.exercises.map { exercise -> exercise.id })
        assertEquals(4, restored.exercises.first().completedSets)
        assertFalse(restored.exercises.last().isUnlocked)
        assertTrue(restored.exercises.first().isActive)
        assertEquals("b", restored.activeExerciseId)
        assertEquals("b", restored.activeRestTimerExerciseId)
        assertEquals(2, restored.restTimerRemainingSeconds)
        assertEquals(2, restored.exercises.first().restSecondsRemaining)
    }

    @Test
    fun restoreWorkoutSessionDropsExpiredTimer() {
        val baseExercises = listOf(sampleExercise(id = "a", totalSets = 3, completedSets = 0, isUnlocked = true))
        val snapshot = WorkoutSessionSnapshot(
            exerciseStates = listOf(
                WorkoutSessionExerciseState(id = "a", completedSets = 2, isUnlocked = true)
            ),
            exerciseOrder = listOf("a"),
            activeExerciseId = "a",
            activeRestTimerExerciseId = "a",
            restTimerEndEpochMillis = 10_000L
        )

        val restored = restoreWorkoutSession(
            baseExercises = baseExercises,
            snapshot = snapshot,
            defaultOrder = listOf("a"),
            nowEpochMillis = 10_000L
        )

        assertNull(restored.activeRestTimerExerciseId)
        assertNull(restored.restTimerRemainingSeconds)
        assertNull(restored.exercises.first().restSecondsRemaining)
    }

    private fun sampleExercise(
        id: String,
        totalSets: Int,
        completedSets: Int,
        isUnlocked: Boolean
    ): ExerciseUiState = ExerciseUiState(
        id = id,
        name = id.uppercase(),
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = listOf(10),
        selectedWeight = 10,
        defaultWeight = 10,
        restBetweenSeconds = 45,
        restFinalSeconds = 120,
        totalSets = totalSets,
        completedSets = completedSets,
        hasSettings = false,
        isUnlocked = isUnlocked
    )
}
