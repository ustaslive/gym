package com.example.gymprogress

import org.json.JSONArray
import org.json.JSONObject

private const val SUPPORTED_SCHEMA_VERSION = 1

internal data class SharedExerciseBundle(
    val sessionOptions: List<WorkoutSessionOption>,
    val templatesBySessionId: Map<String, List<ExerciseUiState>>
) {
    companion object {
        val EMPTY = SharedExerciseBundle(
            sessionOptions = emptyList(),
            templatesBySessionId = emptyMap()
        )
    }
}

private data class ParsedBundleSession(
    val option: WorkoutSessionOption,
    val exercises: List<ExerciseUiState>
)

internal fun parseSharedExerciseBundle(raw: String): SharedExerciseBundle {
    val root = JSONObject(raw)
    val schemaVersion = root.optInt("schemaVersion", -1)
    require(schemaVersion == SUPPORTED_SCHEMA_VERSION) {
        "Unsupported exercise bundle schemaVersion: $schemaVersion"
    }

    val catalog = root.getJSONObject("exerciseCatalog")
    val sessions = root.getJSONArray("sessions")

    val parsedSessions = buildList {
        for (sessionIndex in 0 until sessions.length()) {
            val session = sessions.getJSONObject(sessionIndex)
            val sessionId = session.getString("id")
            val title = session.optString("title").takeIf { it.isNotBlank() } ?: sessionId
            add(
                ParsedBundleSession(
                    option = WorkoutSessionOption(id = sessionId, title = title),
                    exercises = parseSessionExercises(session, catalog)
                )
            )
        }
    }

    return SharedExerciseBundle(
        sessionOptions = parsedSessions.map { session -> session.option },
        templatesBySessionId = parsedSessions.associate { session ->
            session.option.id to session.exercises
        }
    )
}

private fun parseSessionExercises(
    session: JSONObject,
    catalog: JSONObject
): List<ExerciseUiState> {
    val sections = session.getJSONArray("sections")
    return buildList {
        for (sectionIndex in 0 until sections.length()) {
            val section = sections.getJSONObject(sectionIndex)
            val group = section.optString("id").toExerciseGroup()
            val exercises = section.getJSONArray("exercises")
            for (exerciseIndex in 0 until exercises.length()) {
                val entry = exercises.getJSONObject(exerciseIndex)
                val exerciseId = entry.getString("exerciseId")
                val definition = catalog.getJSONObject(exerciseId).mergedWith(entry.optJSONObject("overrides"))
                add(parseExercise(entry, definition, group))
            }
        }
    }
}

private fun parseExercise(
    entry: JSONObject,
    definition: JSONObject,
    group: ExerciseGroup
): ExerciseUiState {
    val kind = definition.getString("kind")
    val type = kind.toExerciseType()
    val parameters = definition.optJSONObject("parameters") ?: JSONObject()
    val settings = definition.optJSONObject("settings")
    val instructions = definition.optJSONObject("instructions")
    val setupNote = settings?.optString("setupNote")?.takeIf { it.isNotBlank() }
    val detailSections = buildDetailSections(
        definition = definition,
        parameters = parameters,
        kind = kind,
        instructions = instructions,
        setupNote = setupNote,
        type = type
    )
    val defaultWeight = parameters.optInt("defaultWeight", 0)

    return ExerciseUiState(
        id = entry.getString("id"),
        name = definition.getString("title"),
        type = type,
        group = group,
        mode = parameters.optString("mode").takeIf { it.isNotBlank() },
        durationMinutes = parameters.optNullablePositiveInt("durationMinutes"),
        level = parameters.optNullableNonNegativeInt("level"),
        weightOptions = parameters.optJSONArray("weightOptions").toIntList(),
        selectedWeight = defaultWeight,
        defaultWeight = defaultWeight,
        weightLabel = parameters.optString("weightLabel").takeIf { it.isNotBlank() },
        weightOptionLabelTemplate = parameters.optString("weightOptionLabelTemplate").takeIf { it.isNotBlank() },
        restBetweenSeconds = parameters.optInt("restBetweenSeconds", 0),
        restFinalSeconds = parameters.optInt("restFinalSeconds", 0),
        totalSets = parameters.optInt("totalSets", 1),
        completedSets = 0,
        hasSettings = !setupNote.isNullOrBlank(),
        settingsNote = setupNote,
        detailSections = detailSections,
        muscleGroups = definition.optJSONArray("muscleGroups").toStringList(),
        recommendedNextExerciseIds = entry.optJSONArray("recommendedNextExerciseIds").toStringList()
    )
}

private fun buildDetailSections(
    definition: JSONObject,
    parameters: JSONObject,
    kind: String,
    instructions: JSONObject?,
    setupNote: String?,
    type: ExerciseType
): List<String> {
    val detailSections = instructions?.optJSONArray("detailSections").toStringList()
    val description = definition.optString("description").takeIf { it.isNotBlank() }
    val parameterSummary = browserParameterSummary(kind, parameters)

    return buildList {
        if (type == ExerciseType.GUIDED && !setupNote.isNullOrBlank()) {
            add(setupNote)
        }
        description?.let(::add)
        addAll(detailSections)
        parameterSummary?.let(::add)
    }
}

private fun browserParameterSummary(kind: String, parameters: JSONObject): String? = when (kind) {
    "timer" -> parameters.optNullablePositiveInt("durationSeconds")
        ?.let { duration -> "Duration: $duration seconds." }
    "repetitions" -> parameters.opt("repetitions")
        ?.toString()
        ?.takeIf { it.isNotBlank() }
        ?.let { repetitions -> "Repetitions: $repetitions." }
    "intervals" -> {
        val workSeconds = parameters.optNullablePositiveInt("workSeconds")
        val restSeconds = parameters.optNullableNonNegativeInt("restSeconds")
        val rounds = parameters.optNullablePositiveInt("rounds")
        if (workSeconds != null && restSeconds != null && rounds != null) {
            "Intervals: $rounds rounds, $workSeconds seconds work, $restSeconds seconds rest."
        } else {
            null
        }
    }
    "sequence" -> parameters.optJSONArray("steps")?.let { steps ->
        buildList {
            for (index in 0 until steps.length()) {
                val step = steps.getJSONObject(index)
                add("${step.getString("label")}: ${step.getInt("durationSeconds")} seconds.")
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(separator = "\n")
    }
    else -> null
}

private fun JSONObject.mergedWith(overrides: JSONObject?): JSONObject {
    if (overrides == null) {
        return JSONObject(toString())
    }
    val result = JSONObject(toString())
    overrides.keys().forEach { key ->
        val baseValue = result.opt(key)
        val overrideValue = overrides.opt(key)
        if (baseValue is JSONObject && overrideValue is JSONObject) {
            result.put(key, baseValue.mergedWith(overrideValue))
        } else {
            result.put(key, overrideValue)
        }
    }
    return result
}

private fun String.toExerciseGroup(): ExerciseGroup = when (lowercase()) {
    "warmup", "warm_up" -> ExerciseGroup.WARM_UP
    "cardio" -> ExerciseGroup.CARDIO
    "cooldown", "cool_down" -> ExerciseGroup.COOLDOWN
    else -> ExerciseGroup.MAIN
}

private fun String.toExerciseType(): ExerciseType = when (lowercase()) {
    "weights" -> ExerciseType.WEIGHTS
    "activity" -> ExerciseType.ACTIVITY
    else -> ExerciseType.GUIDED
}

private fun JSONObject.optNullablePositiveInt(key: String): Int? =
    optNullableInt(key)?.takeIf { it > 0 }

private fun JSONObject.optNullableNonNegativeInt(key: String): Int? =
    optNullableInt(key)?.takeIf { it >= 0 }

private fun JSONObject.optNullableInt(key: String): Int? =
    if (has(key) && !isNull(key)) optInt(key) else null

private fun JSONArray?.toStringList(): List<String> {
    if (this == null) {
        return emptyList()
    }
    return buildList {
        for (index in 0 until length()) {
            val value = optString(index)
            if (value.isNotBlank()) {
                add(value)
            }
        }
    }
}

private fun JSONArray?.toIntList(): List<Int> {
    if (this == null) {
        return emptyList()
    }
    return buildList {
        for (index in 0 until length()) {
            add(optInt(index))
        }
    }
}
