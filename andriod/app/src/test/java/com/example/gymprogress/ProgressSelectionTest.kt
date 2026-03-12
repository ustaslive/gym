package com.example.gymprogress

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressSelectionTest {
    @Test
    fun cooldownExerciseAutoActivatesBeforeBeingCompleted() {
        val exercise = sampleExercise(
            id = "cooldown",
            type = ExerciseType.COOLDOWN,
            totalSets = 1,
            completedSets = 0,
            isActive = false
        )

        assertTrue(shouldActivateBeforeAdvancing(exercise))
    }

    @Test
    fun completedCooldownDoesNotAutoActivateAgain() {
        val exercise = sampleExercise(
            id = "cooldown_done",
            type = ExerciseType.COOLDOWN,
            totalSets = 1,
            completedSets = 1,
            isActive = false
        )

        assertFalse(shouldActivateBeforeAdvancing(exercise))
    }

    @Test
    fun alreadyActiveCooldownDoesNotNeedExtraActivation() {
        val exercise = sampleExercise(
            id = "cooldown_active",
            type = ExerciseType.COOLDOWN,
            totalSets = 1,
            completedSets = 0,
            isActive = true
        )

        assertFalse(shouldActivateBeforeAdvancing(exercise))
    }

    @Test
    fun nonCooldownExerciseDoesNotAutoActivateOnProgressTap() {
        val exercise = sampleExercise(
            id = "weights",
            type = ExerciseType.WEIGHTS,
            totalSets = 3,
            completedSets = 0,
            isActive = false
        )

        assertFalse(shouldActivateBeforeAdvancing(exercise))
    }

    private fun sampleExercise(
        id: String,
        type: ExerciseType,
        totalSets: Int,
        completedSets: Int,
        isActive: Boolean
    ): ExerciseUiState = ExerciseUiState(
        id = id,
        name = id.uppercase(),
        type = type,
        group = ExerciseGroup.COOLDOWN,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = 0,
        restFinalSeconds = 0,
        totalSets = totalSets,
        completedSets = completedSets,
        hasSettings = false,
        isActive = isActive
    )
}
