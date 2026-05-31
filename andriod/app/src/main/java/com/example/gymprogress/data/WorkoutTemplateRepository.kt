package com.example.gymprogress

import android.app.Application

internal class WorkoutTemplateRepository(
    application: Application,
    private val exerciseBundleStore: SharedExerciseBundleStore = SharedExerciseBundleStore(application)
) {
    private val bundle by lazy(LazyThreadSafetyMode.NONE) {
        exerciseBundleStore.loadBundle()
    }
    private val templatesBySessionId by lazy(LazyThreadSafetyMode.NONE) {
        bundle.templatesBySessionId
    }
    private val sessionOptionsCache by lazy(LazyThreadSafetyMode.NONE) {
        bundle.sessionOptions
    }
    private val weightTemplatesByIdCache by lazy(LazyThreadSafetyMode.NONE) {
        templatesBySessionId.values.flatten()
            .filter { exercise -> exercise.type == ExerciseType.WEIGHTS }
            .associateBy { exercise -> exercise.id }
    }

    fun sessionOptions(): List<WorkoutSessionOption> = sessionOptionsCache

    fun defaultSessionId(): String = sessionOptionsCache.firstOrNull()?.id.orEmpty()

    fun hasSession(sessionId: String): Boolean =
        templatesBySessionId.containsKey(sessionId)

    fun templatesForSession(sessionId: String): List<ExerciseUiState> =
        templatesBySessionId[sessionId]
            ?: templatesBySessionId[defaultSessionId()]
            ?: emptyList()

    fun weightTemplatesById(): Map<String, ExerciseUiState> = weightTemplatesByIdCache
}
