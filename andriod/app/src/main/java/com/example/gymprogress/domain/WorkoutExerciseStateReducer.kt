package com.example.gymprogress

internal data class ExerciseSelectionResult(
    val exercises: List<ExerciseUiState>,
    val activeExerciseId: String?
)

internal data class AdvanceProgressResult(
    val changed: Boolean,
    val exercises: List<ExerciseUiState>,
    val activeExerciseId: String?,
    val restDurationSeconds: Int?,
    val cancelledRestTimerExerciseIds: Set<String>,
    val newlyUnlockedGroupAnchorId: String?
)

internal class WorkoutExerciseStateReducer(
    private val groupSequence: List<ExerciseGroup>
) {
    fun selectActive(
        exercises: List<ExerciseUiState>,
        requestedExerciseId: String?
    ): ExerciseSelectionResult {
        val sanitizedId = requestedExerciseId?.takeIf { id ->
            exercises.any { exercise ->
                exercise.id == id &&
                    exercise.type != ExerciseType.PLACEHOLDER &&
                    !exercise.isCompleted()
            }
        }
        val selectionAlreadyApplied = exercises.all { exercise ->
            val shouldBeActive = sanitizedId != null && exercise.id == sanitizedId
            exercise.isActive == shouldBeActive
        }
        if (selectionAlreadyApplied) {
            return ExerciseSelectionResult(
                exercises = exercises,
                activeExerciseId = sanitizedId
            )
        }
        return ExerciseSelectionResult(
            exercises = exercises.map { exercise ->
                val shouldBeActive = sanitizedId != null && exercise.id == sanitizedId
                if (exercise.isActive == shouldBeActive) {
                    exercise
                } else {
                    exercise.copy(isActive = shouldBeActive)
                }
            },
            activeExerciseId = sanitizedId
        )
    }

    fun advanceProgress(
        exercises: List<ExerciseUiState>,
        exerciseId: String,
        currentActiveExerciseId: String?,
        activeRestTimerExerciseId: String?
    ): AdvanceProgressResult {
        var workingExercises = exercises
        var activeExerciseId = currentActiveExerciseId
        var currentIndex = workingExercises.indexOfFirst { exercise -> exercise.id == exerciseId }
        if (currentIndex < 0) {
            return unchanged(
                exercises = exercises,
                activeExerciseId = currentActiveExerciseId
            )
        }

        var exercise = workingExercises[currentIndex]
        if (!exercise.isUnlocked || exercise.type == ExerciseType.PLACEHOLDER) {
            return unchanged(
                exercises = exercises,
                activeExerciseId = currentActiveExerciseId
            )
        }

        if (shouldActivateBeforeAdvancing(exercise)) {
            val selection = selectActive(workingExercises, exercise.id)
            workingExercises = selection.exercises
            activeExerciseId = selection.activeExerciseId
            currentIndex = workingExercises.indexOfFirst { current -> current.id == exerciseId }
            exercise = workingExercises[currentIndex]
        }

        val totalSets = exercise.totalSets.coerceAtLeast(1)
        val wasCompleted = exercise.isCompleted()
        val nextCompletedSets = if (exercise.completedSets >= totalSets) {
            0
        } else {
            exercise.completedSets + 1
        }
        val isCompleted = nextCompletedSets >= totalSets

        val updatedExercise = exercise.copy(
            completedSets = nextCompletedSets,
            restSecondsRemaining = null
        )
        workingExercises = workingExercises
            .toMutableList()
            .apply { this[currentIndex] = updatedExercise }
            .toList()

        if (wasCompleted || isCompleted) {
            workingExercises = clearRecommendedNextFlags(workingExercises)
        }

        var newlyUnlockedGroupAnchorId: String? = null
        if (wasCompleted != isCompleted) {
            workingExercises = repositionExercise(
                exercises = workingExercises,
                currentIndex = currentIndex,
                exercise = updatedExercise
            )
            if (isCompleted) {
                val unlockResult = maybeUnlockNextGroup(workingExercises)
                workingExercises = unlockResult.exercises
                newlyUnlockedGroupAnchorId = unlockResult.newlyUnlockedGroupAnchorId
            }
        }

        if (!isCompleted) {
            val selection = selectActive(workingExercises, updatedExercise.id)
            workingExercises = selection.exercises
            activeExerciseId = selection.activeExerciseId
        } else {
            workingExercises = applyRecommendedNextFlags(
                exercises = workingExercises,
                completedExercise = updatedExercise
            )
        }

        return AdvanceProgressResult(
            changed = true,
            exercises = workingExercises,
            activeExerciseId = activeExerciseId,
            restDurationSeconds = when {
                nextCompletedSets == 0 -> null
                nextCompletedSets >= totalSets -> updatedExercise.restFinalSeconds.takeIf { it > 0 }
                else -> updatedExercise.restBetweenSeconds.takeIf { it > 0 }
            },
            cancelledRestTimerExerciseIds = setOfNotNull(activeRestTimerExerciseId),
            newlyUnlockedGroupAnchorId = newlyUnlockedGroupAnchorId
        )
    }

    private fun unchanged(
        exercises: List<ExerciseUiState>,
        activeExerciseId: String?
    ): AdvanceProgressResult = AdvanceProgressResult(
        changed = false,
        exercises = exercises,
        activeExerciseId = activeExerciseId,
        restDurationSeconds = null,
        cancelledRestTimerExerciseIds = emptySet(),
        newlyUnlockedGroupAnchorId = null
    )

    private fun maybeUnlockNextGroup(exercises: List<ExerciseUiState>): GroupUnlockResult {
        val activeGroups = groupSequence.filter { group ->
            exercises.any { exercise -> exercise.group == group }
        }
        if (activeGroups.isEmpty()) {
            return GroupUnlockResult(exercises = exercises, newlyUnlockedGroupAnchorId = null)
        }
        for ((index, group) in activeGroups.withIndex()) {
            if (isGroupUnlocked(exercises, group)) {
                continue
            }
            val previousGroup = activeGroups.getOrNull(index - 1)
            val canUnlock = previousGroup == null || areGroupExercisesCompleted(exercises, previousGroup)
            if (canUnlock) {
                return unlockGroup(exercises, group)
            }
            return GroupUnlockResult(exercises = exercises, newlyUnlockedGroupAnchorId = null)
        }
        return GroupUnlockResult(exercises = exercises, newlyUnlockedGroupAnchorId = null)
    }

    private fun isGroupUnlocked(
        exercises: List<ExerciseUiState>,
        group: ExerciseGroup
    ): Boolean {
        val groupExercises = exercises.filter { exercise -> exercise.group == group }
        if (groupExercises.isEmpty()) {
            return true
        }
        return groupExercises.any { exercise -> exercise.isUnlocked }
    }

    private fun areGroupExercisesCompleted(
        exercises: List<ExerciseUiState>,
        group: ExerciseGroup
    ): Boolean {
        val groupExercises = exercises.filter { exercise -> exercise.group == group }
        if (groupExercises.isEmpty()) {
            return true
        }
        return groupExercises.all { exercise -> exercise.isCompleted() }
    }

    private fun unlockGroup(
        exercises: List<ExerciseUiState>,
        group: ExerciseGroup
    ): GroupUnlockResult {
        var firstUnlockedId: String? = null
        val updatedExercises = exercises.map { exercise ->
            if (exercise.group == group && !exercise.isUnlocked) {
                if (firstUnlockedId == null) {
                    firstUnlockedId = exercise.id
                }
                exercise.copy(isUnlocked = true)
            } else {
                exercise
            }
        }
        return GroupUnlockResult(
            exercises = updatedExercises,
            newlyUnlockedGroupAnchorId = firstUnlockedId
        )
    }

    private fun repositionExercise(
        exercises: List<ExerciseUiState>,
        currentIndex: Int,
        exercise: ExerciseUiState
    ): List<ExerciseUiState> {
        if (currentIndex !in exercises.indices) {
            return exercises
        }
        val reorderedExercises = exercises.toMutableList()
        val removedExercise = reorderedExercises.removeAt(currentIndex)
        val itemToInsert = if (removedExercise.id == exercise.id) {
            exercise
        } else {
            removedExercise
        }
        val insertionIndex = reorderedExercises.indexOfFirst { current -> current.isCompleted() }
            .let { index -> if (index == -1) reorderedExercises.size else index }
        reorderedExercises.add(insertionIndex, itemToInsert)
        return reorderedExercises
    }

    private fun clearRecommendedNextFlags(exercises: List<ExerciseUiState>): List<ExerciseUiState> {
        if (exercises.none { exercise -> exercise.isRecommendedNext }) {
            return exercises
        }
        return exercises.map { exercise ->
            if (exercise.isRecommendedNext) {
                exercise.copy(isRecommendedNext = false)
            } else {
                exercise
            }
        }
    }

    private fun applyRecommendedNextFlags(
        exercises: List<ExerciseUiState>,
        completedExercise: ExerciseUiState
    ): List<ExerciseUiState> {
        val recommendedIds = completedExercise.recommendedNextExerciseIds.toSet()
        if (recommendedIds.isEmpty()) {
            return exercises
        }
        return exercises.map { exercise ->
            val shouldBeRecommended = exercise.isUnlocked &&
                exercise.type != ExerciseType.PLACEHOLDER &&
                !exercise.isCompleted() &&
                exercise.id in recommendedIds
            if (exercise.isRecommendedNext == shouldBeRecommended) {
                exercise
            } else {
                exercise.copy(isRecommendedNext = shouldBeRecommended)
            }
        }
    }

    private data class GroupUnlockResult(
        val exercises: List<ExerciseUiState>,
        val newlyUnlockedGroupAnchorId: String?
    )
}
