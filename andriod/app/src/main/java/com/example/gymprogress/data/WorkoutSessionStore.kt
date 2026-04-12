package com.example.gymprogress

import android.app.Application
import android.content.Context
import android.util.Log

internal class WorkoutSessionStore(application: Application) {
    private val sessionPrefs = application.getSharedPreferences(WORKOUT_SESSION_PREFS, Context.MODE_PRIVATE)

    fun load(): WorkoutSessionSnapshot? {
        val raw = sessionPrefs.getString(WORKOUT_SESSION_PREF_KEY, null) ?: return null
        return runCatching {
            deserializeWorkoutSessionSnapshot(raw)
        }.getOrElse { error ->
            Log.e(LOG_TAG, "Could not parse persisted workout session. Clearing saved state.", error)
            clear()
            null
        }
    }

    fun save(snapshot: WorkoutSessionSnapshot) {
        sessionPrefs.edit().putString(
            WORKOUT_SESSION_PREF_KEY,
            serializeWorkoutSessionSnapshot(snapshot)
        ).apply()
    }

    fun clear() {
        sessionPrefs.edit().remove(WORKOUT_SESSION_PREF_KEY).apply()
    }

    companion object {
        private const val LOG_TAG = "WorkoutSessionStore"
        private const val WORKOUT_SESSION_PREFS = "workout_session"
        private const val WORKOUT_SESSION_PREF_KEY = "session_snapshot"
    }
}
