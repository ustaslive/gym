package com.example.gymprogress

enum class WorkoutDayType {
    GENERAL,
    HANDS,
    LEGS
}

enum class ExerciseType {
    WEIGHTS,
    ACTIVITY,
    GUIDED,
    PLACEHOLDER
}

enum class ExerciseGroup(val order: Int) {
    WARM_UP(0),
    MAIN(1),
    CARDIO(2),
    COOLDOWN(3)
}

data class ShareContent(
    val plainText: String,
    val htmlText: String
)

data class ExerciseUiState(
    val id: String,
    val name: String,
    val type: ExerciseType,
    val group: ExerciseGroup,
    val mode: String? = null,
    val durationMinutes: Int? = null,
    val level: Int? = null,
    val weightOptions: List<Int>,
    val selectedWeight: Int,
    val defaultWeight: Int,
    val weightLabel: String? = null,
    val weightOptionLabelTemplate: String? = null,
    val restBetweenSeconds: Int,
    val restFinalSeconds: Int,
    val totalSets: Int,
    val completedSets: Int,
    val hasSettings: Boolean,
    val settingsNote: String? = null,
    val detailSections: List<String> = emptyList(),
    val personalNote: String? = null,
    val persistedWeight: Int? = null,
    val restSecondsRemaining: Int? = null,
    val isActive: Boolean = false,
    val isUnlocked: Boolean = true,
    val supportingText: String? = null,
    val recommendedNextExerciseIds: List<String> = emptyList(),
    val isRecommendedNext: Boolean = false
)

internal fun ExerciseUiState.isCompleted(): Boolean =
    totalSets > 0 && completedSets >= totalSets

internal fun ExerciseUiState.isGuided(): Boolean =
    type == ExerciseType.GUIDED

internal fun shouldActivateBeforeAdvancing(exercise: ExerciseUiState): Boolean =
    exercise.isGuided() && !exercise.isCompleted() && !exercise.isActive
