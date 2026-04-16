package com.example.gymprogress

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutExerciseStateReducerTest {
    private val reducer = WorkoutExerciseStateReducer(
        groupSequence = listOf(
            ExerciseGroup.WARM_UP,
            ExerciseGroup.MAIN,
            ExerciseGroup.CARDIO,
            ExerciseGroup.COOLDOWN
        )
    )

    @Test
    fun advanceProgressUnlocksNextGroupAfterCompletingPreviousGroup() {
        val exercises = listOf(
            sampleExercise(
                id = "warmup",
                group = ExerciseGroup.WARM_UP,
                totalSets = 1,
                completedSets = 0,
                restFinalSeconds = 30,
                isUnlocked = true,
                isActive = true
            ),
            sampleExercise(
                id = "main",
                group = ExerciseGroup.MAIN,
                totalSets = 3,
                completedSets = 0,
                isUnlocked = false
            )
        )

        val result = reducer.advanceProgress(
            exercises = exercises,
            exerciseId = "warmup",
            currentActiveExerciseId = "warmup",
            activeRestTimerExerciseId = "warmup"
        )

        assertTrue(result.changed)
        assertEquals("warmup", result.activeExerciseId)
        assertEquals(30, result.restDurationSeconds)
        assertEquals(setOf("warmup"), result.cancelledRestTimerExerciseIds)
        assertEquals("main", result.newlyUnlockedGroupAnchorId)
        assertTrue(result.exercises.first { exercise -> exercise.id == "warmup" }.isCompleted())
        assertTrue(result.exercises.first { exercise -> exercise.id == "main" }.isUnlocked)
    }

    @Test
    fun advanceProgressResetsCompletedExerciseAndMovesItAheadOfCompletedItems() {
        val exercises = listOf(
            sampleExercise(
                id = "completed_first",
                totalSets = 1,
                completedSets = 1,
                isUnlocked = true,
                isActive = false
            ),
            sampleExercise(
                id = "completed_second",
                totalSets = 1,
                completedSets = 1,
                isUnlocked = true,
                isActive = true
            )
        )

        val result = reducer.advanceProgress(
            exercises = exercises,
            exerciseId = "completed_second",
            currentActiveExerciseId = "completed_second",
            activeRestTimerExerciseId = null
        )

        assertTrue(result.changed)
        assertEquals(listOf("completed_second", "completed_first"), result.exercises.map { exercise -> exercise.id })
        assertFalse(result.exercises.first().isCompleted())
        assertTrue(result.exercises.first().isActive)
        assertEquals("completed_second", result.activeExerciseId)
        assertNull(result.restDurationSeconds)
        assertNull(result.newlyUnlockedGroupAnchorId)
    }

    @Test
    fun advanceProgressCompletingExerciseReplacesRecommendationsWithRemainingMatches() {
        val exercises = listOf(
            sampleExercise(
                id = "source",
                totalSets = 1,
                completedSets = 0,
                isUnlocked = true,
                isActive = true,
                recommendedNextExerciseIds = listOf("next", "missing", "done")
            ),
            sampleExercise(
                id = "old_recommended",
                totalSets = 3,
                completedSets = 0,
                isUnlocked = true,
                isRecommendedNext = true
            ),
            sampleExercise(
                id = "next",
                totalSets = 3,
                completedSets = 0,
                isUnlocked = true
            ),
            sampleExercise(
                id = "done",
                totalSets = 1,
                completedSets = 1,
                isUnlocked = true
            )
        )

        val result = reducer.advanceProgress(
            exercises = exercises,
            exerciseId = "source",
            currentActiveExerciseId = "source",
            activeRestTimerExerciseId = null
        )

        assertTrue(result.exercises.first { exercise -> exercise.id == "source" }.isCompleted())
        assertFalse(result.exercises.first { exercise -> exercise.id == "old_recommended" }.isRecommendedNext)
        assertTrue(result.exercises.first { exercise -> exercise.id == "next" }.isRecommendedNext)
        assertFalse(result.exercises.first { exercise -> exercise.id == "done" }.isRecommendedNext)
    }

    @Test
    fun advanceProgressResettingCompletedExerciseClearsPreviousRecommendations() {
        val exercises = listOf(
            sampleExercise(
                id = "source",
                totalSets = 1,
                completedSets = 1,
                isUnlocked = true,
                isActive = true,
                recommendedNextExerciseIds = listOf("next")
            ),
            sampleExercise(
                id = "next",
                totalSets = 3,
                completedSets = 0,
                isUnlocked = true,
                isRecommendedNext = true
            )
        )

        val result = reducer.advanceProgress(
            exercises = exercises,
            exerciseId = "source",
            currentActiveExerciseId = "source",
            activeRestTimerExerciseId = null
        )

        assertFalse(result.exercises.first { exercise -> exercise.id == "source" }.isCompleted())
        assertFalse(result.exercises.first { exercise -> exercise.id == "next" }.isRecommendedNext)
    }

    private fun sampleExercise(
        id: String,
        group: ExerciseGroup = ExerciseGroup.MAIN,
        totalSets: Int,
        completedSets: Int,
        restFinalSeconds: Int = 120,
        isUnlocked: Boolean,
        isActive: Boolean = false,
        recommendedNextExerciseIds: List<String> = emptyList(),
        isRecommendedNext: Boolean = false
    ): ExerciseUiState = ExerciseUiState(
        id = id,
        name = id.uppercase(),
        type = ExerciseType.WEIGHTS,
        group = group,
        weightOptions = listOf(10),
        selectedWeight = 10,
        defaultWeight = 10,
        restBetweenSeconds = 45,
        restFinalSeconds = restFinalSeconds,
        totalSets = totalSets,
        completedSets = completedSets,
        hasSettings = false,
        isActive = isActive,
        isUnlocked = isUnlocked,
        recommendedNextExerciseIds = recommendedNextExerciseIds,
        isRecommendedNext = isRecommendedNext
    )
}
