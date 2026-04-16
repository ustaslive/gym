package com.example.gymprogress

internal data class RestTimerUiState(
    val activeExerciseId: String? = null,
    val endEpochMillis: Long? = null,
    val statusText: String? = null
)

internal class WorkoutRestTimerStateReducer {
    fun restore(
        activeRestTimerExerciseId: String?,
        restTimerRemainingSeconds: Int?,
        nowEpochMillis: Long
    ): RestTimerUiState = RestTimerUiState(
        activeExerciseId = activeRestTimerExerciseId,
        endEpochMillis = restTimerRemainingSeconds?.let { remainingSeconds ->
            nowEpochMillis + remainingSeconds.toLong() * 1_000L
        },
        statusText = restTimerRemainingSeconds?.let(::formatRestTime)
    )

    fun onTimerStateChanged(
        currentState: RestTimerUiState,
        exerciseId: String?,
        endEpochMillis: Long?
    ): RestTimerUiState = currentState.copy(
        activeExerciseId = exerciseId,
        endEpochMillis = endEpochMillis,
        statusText = if (exerciseId == null) null else currentState.statusText
    )

    fun onTimerUpdated(
        currentState: RestTimerUiState,
        exerciseId: String,
        secondsRemaining: Int?
    ): RestTimerUiState {
        return if (currentState.activeExerciseId == exerciseId) {
            currentState.copy(statusText = secondsRemaining?.let(::formatRestTime))
        } else {
            currentState
        }
    }

    private fun formatRestTime(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        val minutes = safeSeconds / 60
        val remainingSeconds = safeSeconds % 60
        return String.format("%d:%02d", minutes, remainingSeconds)
    }
}
