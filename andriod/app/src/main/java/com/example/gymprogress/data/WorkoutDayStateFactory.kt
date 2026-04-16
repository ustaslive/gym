package com.example.gymprogress

internal class WorkoutDayStateFactory(
    private val workoutRepository: WorkoutRepository,
    private val groupSequence: List<ExerciseGroup>
) {
    private val initialGroup by lazy(LazyThreadSafetyMode.NONE) {
        val generalTemplates = workoutRepository.templatesForDayType(WorkoutDayType.GENERAL)
        groupSequence.firstOrNull { group ->
            generalTemplates.any { exercise -> exercise.group == group }
        } ?: groupSequence.firstOrNull()
    }

    fun build(dayType: WorkoutDayType): List<ExerciseUiState> =
        workoutRepository.preparedExercisesForDayType(
            dayType = dayType,
            initialGroup = initialGroup
        )
}
