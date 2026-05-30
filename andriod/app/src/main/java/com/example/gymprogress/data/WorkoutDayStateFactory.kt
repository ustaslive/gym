package com.example.gymprogress

internal class WorkoutDayStateFactory(
    private val workoutRepository: WorkoutRepository,
    private val groupSequence: List<ExerciseGroup>
) {
    fun defaultSessionId(): String = workoutRepository.defaultSessionId()

    fun hasSession(sessionId: String): Boolean = workoutRepository.hasSession(sessionId)

    fun build(sessionId: String): List<ExerciseUiState> =
        workoutRepository.preparedExercisesForSession(
            sessionId = sessionId,
            initialGroup = initialGroupForSession(sessionId)
        )

    private fun initialGroupForSession(sessionId: String): ExerciseGroup? {
        val templates = workoutRepository.templatesForSession(sessionId)
        return groupSequence.firstOrNull { group ->
            templates.any { exercise -> exercise.group == group }
        } ?: groupSequence.firstOrNull()
    }
}
