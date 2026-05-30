package com.example.gymprogress

import android.app.Application
import android.content.Context

internal class WorkoutRepository(
    private val application: Application,
    private val exerciseBundleStore: SharedExerciseBundleStore = SharedExerciseBundleStore(application)
) {
    private val notesPrefs = application.getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
    private val weightsPrefs = application.getSharedPreferences(WEIGHTS_PREFS, Context.MODE_PRIVATE)
    private var templateRepository = createTemplateRepository()

    fun sessionOptions(): List<WorkoutSessionOption> =
        templateRepository.sessionOptions()

    fun defaultSessionId(): String =
        templateRepository.defaultSessionId()

    fun hasSession(sessionId: String): Boolean =
        templateRepository.hasSession(sessionId)

    fun templatesForSession(sessionId: String): List<ExerciseUiState> =
        templateRepository.templatesForSession(sessionId)

    fun downloadAndReloadExerciseBundle(): ExerciseBundleImportResult {
        val result = exerciseBundleStore.downloadAndCacheBundle()
        if (result is ExerciseBundleImportResult.Success) {
            templateRepository = createTemplateRepository()
            cleanupPersistedWeights()
        }
        return result
    }

    fun preparedExercisesForSession(
        sessionId: String,
        initialGroup: ExerciseGroup?
    ): List<ExerciseUiState> {
        val templates = templateRepository.templatesForSession(sessionId)
        if (templates.any { exercise -> exercise.type == ExerciseType.PLACEHOLDER }) {
            return templates
        }
        return prepareExercises(
            templates = templates,
            savedNotes = loadSavedNotes(),
            savedWeights = loadSavedWeights(),
            initialGroup = initialGroup
        )
    }

    fun loadGeneralNote(): String? =
        notesPrefs.getString(GENERAL_NOTE_PREF_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun saveGeneralNote(newNote: String): String? {
        val trimmed = newNote.trim()
        val normalized = trimmed.takeIf { it.isNotEmpty() }
        val editor = notesPrefs.edit()
        if (normalized == null) {
            editor.remove(GENERAL_NOTE_PREF_KEY)
        } else {
            editor.putString(GENERAL_NOTE_PREF_KEY, trimmed)
        }
        editor.apply()
        return normalized
    }

    fun savePersonalNote(exerciseId: String, newNote: String): String? {
        val trimmed = newNote.trim()
        val normalized = trimmed.takeIf { it.isNotEmpty() }
        val editor = notesPrefs.edit()
        if (normalized == null) {
            editor.remove(exerciseId)
        } else {
            editor.putString(exerciseId, trimmed)
        }
        editor.apply()
        return normalized
    }

    fun persistWeight(exerciseId: String, newWeight: Int, defaultWeight: Int): Int? {
        val persistedWeight = newWeight.takeUnless { it == defaultWeight }
        val editor = weightsPrefs.edit()
        if (persistedWeight == null) {
            editor.remove(exerciseId)
        } else {
            editor.putInt(exerciseId, newWeight)
        }
        editor.apply()
        return persistedWeight
    }

    fun clearUserData() {
        notesPrefs.edit().clear().apply()
        weightsPrefs.edit().clear().apply()
    }

    fun clearAllNotes() {
        notesPrefs.edit().clear().apply()
    }

    fun cleanupPersistedWeights() {
        val savedWeights = loadSavedWeights()
        if (savedWeights.isEmpty()) return

        val weightExercises = templateRepository.weightTemplatesById()
        val editor = weightsPrefs.edit()
        var changed = false

        savedWeights.forEach { (exerciseId, savedWeight) ->
            val exercise = weightExercises[exerciseId] ?: return@forEach
            if (savedWeight == exercise.defaultWeight || savedWeight !in exercise.weightOptions) {
                editor.remove(exerciseId)
                changed = true
            }
        }

        if (changed) {
            editor.apply()
        }
    }

    private fun loadSavedNotes(): Map<String, String> =
        notesPrefs.all.mapNotNull { (key, value) ->
            if (key == GENERAL_NOTE_PREF_KEY) return@mapNotNull null
            (value as? String)?.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()

    private fun loadSavedWeights(): Map<String, Int> =
        weightsPrefs.all.mapNotNull { (key, value) ->
            (value as? Int)?.let { key to it }
        }.toMap()

    private fun createTemplateRepository(): WorkoutTemplateRepository =
        WorkoutTemplateRepository(
            application = application,
            exerciseBundleStore = exerciseBundleStore
        )

    companion object {
        private const val NOTES_PREFS = "exercise_notes"
        private const val GENERAL_NOTE_PREF_KEY = "general_note"
        private const val WEIGHTS_PREFS = "exercise_weights"
    }
}

private fun prepareExercises(
    templates: List<ExerciseUiState>,
    savedNotes: Map<String, String>,
    savedWeights: Map<String, Int>,
    initialGroup: ExerciseGroup?
): List<ExerciseUiState> = templates.map { exercise ->
    val note = savedNotes[exercise.id]
    val persistedWeight = savedWeights[exercise.id]?.takeIf { weight ->
        weight in exercise.weightOptions && weight != exercise.defaultWeight
    }
    exercise.copy(
        completedSets = 0,
        personalNote = note?.takeIf { it.isNotBlank() },
        selectedWeight = if (exercise.type == ExerciseType.WEIGHTS) {
            persistedWeight ?: exercise.defaultWeight
        } else {
            exercise.defaultWeight
        },
        persistedWeight = persistedWeight,
        restSecondsRemaining = null,
        isActive = false,
        isUnlocked = shouldUnlockInitially(exercise, initialGroup)
    )
}

private fun shouldUnlockInitially(
    exercise: ExerciseUiState,
    initialGroup: ExerciseGroup?
): Boolean = if (exercise.type == ExerciseType.PLACEHOLDER) {
    true
} else {
    initialGroup?.let { group -> exercise.group == group } ?: true
}
