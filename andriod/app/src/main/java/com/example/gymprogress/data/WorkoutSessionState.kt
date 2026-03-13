package com.example.gymprogress

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

internal data class WorkoutSessionExerciseState(
    val id: String,
    val completedSets: Int,
    val isUnlocked: Boolean
)

internal data class WorkoutSessionSnapshot(
    val exerciseStates: List<WorkoutSessionExerciseState>,
    val exerciseOrder: List<String>,
    val activeExerciseId: String?,
    val activeRestTimerExerciseId: String?,
    val restTimerEndEpochMillis: Long?,
    val currentDayType: WorkoutDayType = WorkoutDayType.GENERAL,
    val selectedDayType: WorkoutDayType = currentDayType
)

internal data class RestoredWorkoutSession(
    val exercises: List<ExerciseUiState>,
    val activeExerciseId: String?,
    val activeRestTimerExerciseId: String?,
    val restTimerRemainingSeconds: Int?,
    val currentDayType: WorkoutDayType,
    val selectedDayType: WorkoutDayType
)

internal fun serializeWorkoutSessionSnapshot(snapshot: WorkoutSessionSnapshot): String {
    val root = JSONObject()
    val exerciseStates = JSONArray()
    snapshot.exerciseStates.forEach { exercise ->
        exerciseStates.put(
            JSONObject()
                .put("id", exercise.id)
                .put("completedSets", exercise.completedSets)
                .put("isUnlocked", exercise.isUnlocked)
        )
    }
    root.put("exerciseStates", exerciseStates)
    root.put("exerciseOrder", JSONArray(snapshot.exerciseOrder))
    root.put("currentDayType", snapshot.currentDayType.name)
    root.put("selectedDayType", snapshot.selectedDayType.name)
    snapshot.activeExerciseId?.let { root.put("activeExerciseId", it) }
    snapshot.activeRestTimerExerciseId?.let { root.put("activeRestTimerExerciseId", it) }
    snapshot.restTimerEndEpochMillis?.let { root.put("restTimerEndEpochMillis", it) }
    return root.toString()
}

internal fun deserializeWorkoutSessionSnapshot(raw: String): WorkoutSessionSnapshot {
    val root = JSONObject(raw)
    val exerciseStates = root.optJSONArray("exerciseStates")?.let { jsonArray ->
        buildList {
            for (index in 0 until jsonArray.length()) {
                val item = jsonArray.getJSONObject(index)
                add(
                    WorkoutSessionExerciseState(
                        id = item.getString("id"),
                        completedSets = item.optInt("completedSets", 0),
                        isUnlocked = item.optBoolean("isUnlocked", true)
                    )
                )
            }
        }
    }.orEmpty()
    val exerciseOrder = root.optJSONArray("exerciseOrder")?.toStringList().orEmpty()
    val currentDayType = root.optString("currentDayType")
        .toWorkoutDayTypeOrNull()
        ?: WorkoutDayType.GENERAL
    val selectedDayType = root.optString("selectedDayType")
        .toWorkoutDayTypeOrNull()
        ?: currentDayType
    return WorkoutSessionSnapshot(
        exerciseStates = exerciseStates,
        exerciseOrder = exerciseOrder,
        activeExerciseId = root.optString("activeExerciseId").takeIf { it.isNotBlank() },
        activeRestTimerExerciseId = root.optString("activeRestTimerExerciseId").takeIf { it.isNotBlank() },
        restTimerEndEpochMillis = root.takeIf { it.has("restTimerEndEpochMillis") }
            ?.optLong("restTimerEndEpochMillis"),
        currentDayType = currentDayType,
        selectedDayType = selectedDayType
    )
}

internal fun restoreWorkoutSession(
    baseExercises: List<ExerciseUiState>,
    snapshot: WorkoutSessionSnapshot?,
    defaultOrder: List<String>,
    nowEpochMillis: Long
): RestoredWorkoutSession {
    if (snapshot == null) {
        return RestoredWorkoutSession(
            exercises = baseExercises,
            activeExerciseId = null,
            activeRestTimerExerciseId = null,
            restTimerRemainingSeconds = null,
            currentDayType = WorkoutDayType.GENERAL,
            selectedDayType = WorkoutDayType.GENERAL
        )
    }
    val savedStates = snapshot.exerciseStates.associateBy { it.id }
    val defaultPositions = defaultOrder.withIndex().associate { it.value to it.index }
    val savedPositions = snapshot.exerciseOrder.withIndex().associate { it.value to it.index }
    val activeRestTimerExerciseId = snapshot.activeRestTimerExerciseId
        ?.takeIf { id -> baseExercises.any { it.id == id } }
    val restTimerRemainingSeconds = snapshot.restTimerEndEpochMillis
        ?.let { endEpochMillis -> computeRemainingSecondsFromEpochMillis(endEpochMillis, nowEpochMillis) }
        ?.takeIf { remaining -> activeRestTimerExerciseId != null && remaining > 0 }
    val restoredExercises = baseExercises
        .map { exercise ->
            val saved = savedStates[exercise.id]
            val restoredCompletedSets = saved?.completedSets?.coerceIn(0, exercise.totalSets.coerceAtLeast(0))
                ?: exercise.completedSets
            val restoredUnlocked = saved?.isUnlocked ?: exercise.isUnlocked
            exercise.copy(
                completedSets = restoredCompletedSets,
                restSecondsRemaining = if (exercise.id == activeRestTimerExerciseId) {
                    restTimerRemainingSeconds
                } else {
                    null
                }
            ).copy(isUnlocked = restoredUnlocked)
        }
        .sortedWith(
            compareBy<ExerciseUiState> { exercise ->
                savedPositions[exercise.id] ?: Int.MAX_VALUE
            }.thenBy { exercise ->
                defaultPositions[exercise.id] ?: Int.MAX_VALUE
            }
        )
    val activeExerciseId = snapshot.activeExerciseId
        ?.takeIf { id -> restoredExercises.any { it.id == id } }
    val exercisesWithActiveFlags = restoredExercises.map { exercise ->
        exercise.copy(isActive = activeExerciseId != null && exercise.id == activeExerciseId)
    }
    return RestoredWorkoutSession(
        exercises = exercisesWithActiveFlags,
        activeExerciseId = activeExerciseId,
        activeRestTimerExerciseId = activeRestTimerExerciseId.takeIf { restTimerRemainingSeconds != null },
        restTimerRemainingSeconds = restTimerRemainingSeconds,
        currentDayType = snapshot.currentDayType,
        selectedDayType = snapshot.selectedDayType
    )
}

private fun JSONArray.toStringList(): List<String> = buildList {
    for (i in 0 until length()) {
        val value = optString(i)
        if (value.isNotBlank()) {
            add(value)
        }
    }
}

private fun String.toWorkoutDayTypeOrNull(): WorkoutDayType? =
    takeIf { it.isNotBlank() }?.let { value ->
        runCatching { WorkoutDayType.valueOf(value.uppercase(Locale.US)) }.getOrNull()
    }
