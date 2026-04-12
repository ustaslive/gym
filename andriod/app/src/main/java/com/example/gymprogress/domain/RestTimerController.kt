package com.example.gymprogress

import android.app.Application
import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class RestTimerController(
    private val application: Application,
    private val coroutineScope: CoroutineScope,
    private val onTimerUpdated: (exerciseId: String, secondsRemaining: Int?) -> Unit,
    private val onTimerStateChanged: (exerciseId: String?, endEpochMillis: Long?) -> Unit,
    private val onPersistRequested: () -> Unit
) {
    private var activeJob: Job? = null
    private var activeExerciseId: String? = null
    private var activeEndEpochMillis: Long? = null

    fun start(exerciseId: String, durationSeconds: Int) {
        if (durationSeconds <= 0) return

        clearActiveTimer(stopSound = true, persist = false)

        activeExerciseId = exerciseId
        activeEndEpochMillis = System.currentTimeMillis() + durationSeconds.toLong() * 1_000L
        onTimerStateChanged(activeExerciseId, activeEndEpochMillis)
        onTimerUpdated(exerciseId, durationSeconds)
        RestTimerSoundService.start(application, durationSeconds)
        onPersistRequested()

        val endElapsedRealtimeMs = SystemClock.elapsedRealtime() + durationSeconds.toLong() * 1_000L
        val job = coroutineScope.launch {
            val currentJob = coroutineContext[Job]
            var lastReportedRemaining = durationSeconds
            try {
                while (true) {
                    val nowElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    val remaining = computeRemainingSeconds(endElapsedRealtimeMs, nowElapsedRealtimeMs)
                    if (remaining != lastReportedRemaining) {
                        onTimerUpdated(exerciseId, remaining)
                        lastReportedRemaining = remaining
                    }
                    if (remaining <= 0) {
                        break
                    }
                    delay(computeDelayUntilNextTick(endElapsedRealtimeMs, nowElapsedRealtimeMs))
                }
            } finally {
                if (activeJob == currentJob) {
                    activeJob = null
                    activeExerciseId = null
                    activeEndEpochMillis = null
                    onTimerStateChanged(null, null)
                    onTimerUpdated(exerciseId, null)
                    onPersistRequested()
                }
            }
        }
        activeJob = job
    }

    fun cancel(exerciseId: String) {
        if (activeExerciseId == exerciseId) {
            clearActiveTimer(stopSound = true, persist = true)
        } else {
            onTimerUpdated(exerciseId, null)
        }
    }

    fun clear() {
        clearActiveTimer(stopSound = true, persist = false)
    }

    private fun clearActiveTimer(stopSound: Boolean, persist: Boolean) {
        val previousExerciseId = activeExerciseId
        val previousJob = activeJob

        activeJob = null
        activeExerciseId = null
        activeEndEpochMillis = null

        previousJob?.cancel()
        if (previousExerciseId != null) {
            onTimerUpdated(previousExerciseId, null)
        }
        onTimerStateChanged(null, null)

        if (stopSound) {
            RestTimerSoundService.stop(application)
        }
        if (persist) {
            onPersistRequested()
        }
    }
}
