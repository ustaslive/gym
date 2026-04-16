package com.example.gymprogress

internal data class WorkoutSessionRestoreResult(
    val session: RestoredWorkoutSession,
    val hadSavedSnapshot: Boolean
)

internal data class WorkoutSessionPersistState(
    val exercises: List<ExerciseUiState>,
    val activeExerciseId: String?,
    val activeRestTimerExerciseId: String?,
    val restTimerEndEpochMillis: Long?,
    val currentDayType: WorkoutDayType,
    val selectedDayType: WorkoutDayType
)

internal class WorkoutSessionStateManager(
    private val workoutSessionStore: WorkoutSessionStore
) {
    fun restore(
        dayStateFactory: WorkoutDayStateFactory,
        nowEpochMillis: Long
    ): WorkoutSessionRestoreResult {
        val savedSnapshot = workoutSessionStore.load()
        val currentDayType = savedSnapshot?.currentDayType ?: WorkoutDayType.GENERAL
        val baseExercises = dayStateFactory.build(currentDayType)
        val restoredSession = restoreWorkoutSession(
            baseExercises = baseExercises,
            snapshot = savedSnapshot,
            defaultOrder = baseExercises.map { exercise -> exercise.id },
            nowEpochMillis = nowEpochMillis
        )
        return WorkoutSessionRestoreResult(
            session = restoredSession,
            hadSavedSnapshot = savedSnapshot != null
        )
    }

    fun persist(state: WorkoutSessionPersistState) {
        val snapshot = WorkoutSessionSnapshot(
            exerciseStates = state.exercises.map { exercise ->
                WorkoutSessionExerciseState(
                    id = exercise.id,
                    completedSets = exercise.completedSets,
                    isUnlocked = exercise.isUnlocked
                )
            },
            exerciseOrder = state.exercises.map { exercise -> exercise.id },
            activeExerciseId = state.activeExerciseId,
            activeRestTimerExerciseId = state.activeRestTimerExerciseId,
            restTimerEndEpochMillis = state.restTimerEndEpochMillis,
            currentDayType = state.currentDayType,
            selectedDayType = state.selectedDayType
        )
        workoutSessionStore.save(snapshot)
    }
}
