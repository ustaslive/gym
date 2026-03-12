package com.example.gymprogress

import android.app.Application
import android.content.Context
import android.os.SystemClock
import android.text.Html
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale

class GymViewModel(application: Application) : AndroidViewModel(application) {
    private val _exercises = mutableStateListOf<ExerciseUiState>()
    val exercises: List<ExerciseUiState> get() = _exercises.filter { it.isUnlocked }

    private val notesPrefs = application.getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
    private val weightsPrefs = application.getSharedPreferences(WEIGHTS_PREFS, Context.MODE_PRIVATE)
    private val sessionPrefs = application.getSharedPreferences(WORKOUT_SESSION_PREFS, Context.MODE_PRIVATE)
    private val defaultOrder = mutableListOf<String>()
    private val restTimers = mutableMapOf<String, Job>()

    private var activeStatusExerciseId: String? = null
    private var activeExerciseId: String? = null
    private var activeRestTimerEndEpochMillis: Long? = null
    private var initialGroup: ExerciseGroup? = GROUP_SEQUENCE.firstOrNull()

    var statusText by mutableStateOf<String?>(null)
        private set

    var generalNote by mutableStateOf<String?>(null)
        private set

    var newlyUnlockedGroupAnchorId by mutableStateOf<String?>(null)
        private set

    var exerciseAssetIssueMessage by mutableStateOf<String?>(null)
        private set

    init {
        generalNote = loadGeneralNote()
        val savedNotes = loadSavedNotes()
        val savedWeights = loadSavedWeights()
        val loadResult = loadExercisesFromAssets(application)
        exerciseAssetIssueMessage = loadResult.issueMessage
        val defaultExercises = loadResult.exercises
        initialGroup = GROUP_SEQUENCE.firstOrNull { group -> defaultExercises.any { it.group == group } } ?: initialGroup
        val defaults = defaultExercises.map { exercise ->
            val note = savedNotes[exercise.id]
            val persistedWeight = savedWeights[exercise.id]?.takeIf { it in exercise.weightOptions }
            val unlocked = shouldUnlockInitially(exercise)
            exercise.copy(
                personalNote = note?.takeIf { it.isNotBlank() },
                selectedWeight = persistedWeight ?: exercise.selectedWeight,
                persistedWeight = persistedWeight,
                restSecondsRemaining = null,
                isUnlocked = unlocked
            )
        }
        defaultOrder.clear()
        defaultOrder.addAll(defaultExercises.map { it.id })
        val savedSession = loadWorkoutSession()
        val restoredSession = restoreWorkoutSession(
            baseExercises = defaults,
            snapshot = savedSession,
            defaultOrder = defaultOrder,
            nowEpochMillis = System.currentTimeMillis()
        )
        activeExerciseId = restoredSession.activeExerciseId
        activeStatusExerciseId = restoredSession.activeRestTimerExerciseId
        activeRestTimerEndEpochMillis = restoredSession.restTimerRemainingSeconds
            ?.let { remainingSeconds -> System.currentTimeMillis() + remainingSeconds.toLong() * 1_000L }
        statusText = restoredSession.restTimerRemainingSeconds?.let { remaining ->
            formatRestTime(remaining)
        }
        _exercises.addAll(restoredSession.exercises)
        if (
            restoredSession.activeRestTimerExerciseId != null &&
            restoredSession.restTimerRemainingSeconds != null
        ) {
            val restoredExercise = _exercises.firstOrNull { exercise ->
                exercise.id == restoredSession.activeRestTimerExerciseId
            }
            if (restoredExercise != null) {
                startRestTimer(restoredExercise, restoredSession.restTimerRemainingSeconds)
            }
        } else if (savedSession != null) {
            persistWorkoutSessionState()
        }
    }

    fun advanceProgress(exerciseId: String) {
        val index = _exercises.indexOfFirst { it.id == exerciseId }
        if (index >= 0) {
            var exercise = _exercises[index]
            if (!exercise.isUnlocked) {
                return
            }
            if (shouldActivateBeforeAdvancing(exercise)) {
                updateActiveSelection(exercise.id)
                exercise = _exercises[index]
            }
            val total = exercise.totalSets.coerceAtLeast(1)
            val wasCompleted = exercise.isCompleted()
            val nextValue = if (exercise.completedSets >= total) 0 else exercise.completedSets + 1
            val isCompleted = nextValue >= total
            val activeId = activeStatusExerciseId
            if (activeId != null && activeId != exercise.id) {
                cancelRestTimer(activeId)
            }
            cancelRestTimer(exercise.id)
            val updatedExercise = exercise.copy(
                completedSets = nextValue,
                restSecondsRemaining = null
            )
            _exercises[index] = updatedExercise
            if (wasCompleted != isCompleted) {
                repositionExercise(index, updatedExercise)
                if (isCompleted) {
                    maybeUnlockNextGroup()
                }
            }
            if (!isCompleted) {
                updateActiveSelection(updatedExercise.id)
            }
            if (nextValue == 0) {
                persistWorkoutSessionState()
                return
            }
            val duration = if (nextValue >= total) {
                updatedExercise.restFinalSeconds
            } else {
                updatedExercise.restBetweenSeconds
            }
            if (duration > 0) {
                startRestTimer(updatedExercise, duration)
            } else {
                persistWorkoutSessionState()
            }
        }
    }

    fun updateWeight(exerciseId: String, newWeight: Int) {
        val index = _exercises.indexOfFirst { it.id == exerciseId }
        if (index >= 0) {
            val exercise = _exercises[index]
            if (!exercise.isUnlocked || exercise.type != ExerciseType.WEIGHTS) {
                return
            }
            if (newWeight in exercise.weightOptions) {
                _exercises[index] = exercise.copy(
                    selectedWeight = newWeight,
                    persistedWeight = newWeight
                )
                weightsPrefs.edit().putInt(exerciseId, newWeight).apply()
                markExerciseActive(exerciseId)
            }
        }
    }

    fun resetAllSets() {
        restTimers.values.forEach { it.cancel() }
        restTimers.clear()
        RestTimerSoundService.stop(getApplication())
        activeRestTimerEndEpochMillis = null
        activeStatusExerciseId = null
        updateActiveSelection(null)
        statusText = null
        newlyUnlockedGroupAnchorId = null
        val resetExercises = _exercises.map { exercise ->
            exercise.copy(
                completedSets = 0,
                restSecondsRemaining = null,
                isUnlocked = shouldUnlockInitially(exercise)
            )
        }
        val ordered = reorderToDefaultOrder(resetExercises)
        _exercises.clear()
        _exercises.addAll(ordered)
        clearWorkoutSessionState()
    }

    fun performFullReset() {
        restTimers.values.forEach { it.cancel() }
        restTimers.clear()
        RestTimerSoundService.stop(getApplication())
        activeRestTimerEndEpochMillis = null
        activeStatusExerciseId = null
        updateActiveSelection(null)
        statusText = null
        notesPrefs.edit().clear().apply()
        generalNote = null
        weightsPrefs.edit().clear().apply()
        newlyUnlockedGroupAnchorId = null
        val resetExercises = _exercises.map { exercise ->
            exercise.copy(
                completedSets = 0,
                personalNote = null,
                selectedWeight = exercise.defaultWeight,
                persistedWeight = null,
                restSecondsRemaining = null,
                isUnlocked = shouldUnlockInitially(exercise)
            )
        }
        val ordered = reorderToDefaultOrder(resetExercises)
        _exercises.clear()
        _exercises.addAll(ordered)
        clearWorkoutSessionState()
    }

    fun updatePersonalNote(exerciseId: String, newNote: String) {
        val index = _exercises.indexOfFirst { it.id == exerciseId }
        if (index >= 0) {
            val trimmed = newNote.trim()
            val exercise = _exercises[index]
            val updated = exercise.copy(personalNote = trimmed.takeIf { it.isNotEmpty() })
            _exercises[index] = updated
            if (trimmed.isEmpty()) {
                notesPrefs.edit().remove(exerciseId).apply()
            } else {
                notesPrefs.edit().putString(exerciseId, trimmed).apply()
            }
        }
    }

    fun updateGeneralNote(newNote: String) {
        val trimmed = newNote.trim()
        val normalized = trimmed.takeIf { it.isNotEmpty() }
        generalNote = normalized
        val editor = notesPrefs.edit()
        if (normalized == null) {
            editor.remove(GENERAL_NOTE_PREF_KEY)
        } else {
            editor.putString(GENERAL_NOTE_PREF_KEY, trimmed)
        }
        editor.apply()
    }

    fun buildShareContent(): ShareContent? {
        val app = getApplication<Application>()
        val entries = mutableListOf<Pair<String, String>>()
        val general = generalNote?.trim()?.takeIf { it.isNotEmpty() }
        if (general != null) {
            val generalTitle = app.getString(R.string.general_note_title)
            entries += generalTitle to general
        }
        entries += _exercises.mapNotNull { exercise ->
            val name = exercise.name.trim()
            val note = exercise.personalNote?.trim()?.takeIf { it.isNotEmpty() }
            if (name.isNotEmpty() && note != null) {
                val displayName = buildShareExerciseTitle(exercise, app, name)
                displayName to note
            } else {
                null
            }
        }
        if (entries.isEmpty()) return null

        val plainText = entries.joinToString(separator = "\n\n") { (name, note) ->
            "$name: $note"
        }
        val htmlBody = entries.joinToString(separator = "") { (name, note) ->
            "<p><strong>${Html.escapeHtml(name)}</strong>: ${escapeNoteToHtml(note)}</p>"
        }
        val htmlText = "<html><body>$htmlBody</body></html>"

        return ShareContent(
            plainText = plainText,
            htmlText = htmlText
        )
    }

    fun stopActiveRestTimer() {
        val activeId = activeStatusExerciseId ?: return
        cancelRestTimer(activeId)
    }

    fun markExerciseActive(exerciseId: String) {
        val target = _exercises.firstOrNull { it.id == exerciseId } ?: return
        if (!target.isUnlocked || target.isCompleted()) {
            return
        }
        if (activeExerciseId == exerciseId && target.isActive) {
            return
        }
        updateActiveSelection(exerciseId)
        persistWorkoutSessionState()
    }

    fun consumeNewlyUnlockedAnchor() {
        newlyUnlockedGroupAnchorId = null
    }

    fun dismissExerciseAssetIssue() {
        exerciseAssetIssueMessage = null
    }

    override fun onCleared() {
        super.onCleared()
        restTimers.values.forEach { it.cancel() }
        restTimers.clear()
        RestTimerSoundService.stop(getApplication())
    }

    private fun buildShareExerciseTitle(
        exercise: ExerciseUiState,
        app: Application,
        baseName: String
    ): String {
        val metadata = when (exercise.type) {
            ExerciseType.WEIGHTS -> buildWeightShareMetadata(exercise, app)
            ExerciseType.ACTIVITY -> buildActivityShareMetadata(exercise)
            ExerciseType.COOLDOWN -> null
        }
        return if (metadata != null) "$baseName$metadata" else baseName
    }

    private fun buildWeightShareMetadata(exercise: ExerciseUiState, app: Application): String? {
        val defaultWeight = exercise.defaultWeight.takeIf { it > 0 }
        val selectedWeight = exercise.selectedWeight.takeIf { it > 0 }
        if (defaultWeight == null && selectedWeight == null) return null

        val labelOverride = exercise.weightLabel
            ?.takeIf { it.isNotBlank() && exercise.weightOptions.size <= 1 }
        val parts = listOfNotNull(
            defaultWeight?.let { weight -> "default=${formatShareWeight(app, weight, labelOverride)}" },
            selectedWeight?.let { weight -> "selected=${formatShareWeight(app, weight, labelOverride)}" }
        )
        if (parts.isEmpty()) return null
        return parts.joinToString(prefix = "(", postfix = ")", separator = ",")
    }

    private fun buildActivityShareMetadata(exercise: ExerciseUiState): String? =
        exercise.level?.takeIf { it > 0 }?.let { level -> "(level=$level)" }

    private fun formatShareWeight(app: Application, weight: Int, labelOverride: String? = null): String =
        (labelOverride ?: app.getString(R.string.weight_label_template, weight)).replace(" ", "")

    private fun escapeNoteToHtml(note: String): String =
        note.split('\n').joinToString("<br>") { line ->
            Html.escapeHtml(line)
        }

    private fun shouldUnlockInitially(exercise: ExerciseUiState): Boolean =
        initialGroup?.let { exercise.group == it } ?: true

    private fun isGroupUnlocked(group: ExerciseGroup): Boolean =
        _exercises.any { it.group == group && it.isUnlocked }

    private fun areGroupExercisesCompleted(group: ExerciseGroup): Boolean {
        val groupExercises = _exercises.filter { it.group == group }
        if (groupExercises.isEmpty()) {
            return true
        }
        return groupExercises.all { it.isCompleted() }
    }

    private fun unlockGroup(group: ExerciseGroup): String? {
        var firstUnlockedId: String? = null
        _exercises.replaceAll { exercise ->
            if (exercise.group == group && !exercise.isUnlocked) {
                if (firstUnlockedId == null) {
                    firstUnlockedId = exercise.id
                }
                exercise.copy(isUnlocked = true)
            } else {
                exercise
            }
        }
        return firstUnlockedId
    }

    private fun maybeUnlockNextGroup() {
        if (GROUP_SEQUENCE.isEmpty()) return
        for ((index, group) in GROUP_SEQUENCE.withIndex()) {
            if (isGroupUnlocked(group)) {
                continue
            }
            val previousGroup = GROUP_SEQUENCE.getOrNull(index - 1)
            val canUnlock = previousGroup == null || areGroupExercisesCompleted(previousGroup)
            if (canUnlock) {
                val anchorId = unlockGroup(group)
                if (anchorId != null) {
                    newlyUnlockedGroupAnchorId = anchorId
                }
            }
            return
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

    private fun loadGeneralNote(): String? =
        notesPrefs.getString(GENERAL_NOTE_PREF_KEY, null)?.trim()?.takeIf { it.isNotEmpty() }

    private fun startRestTimer(exercise: ExerciseUiState, durationSeconds: Int) {
        if (durationSeconds <= 0) return
        restTimers.remove(exercise.id)?.cancel()
        activeStatusExerciseId = exercise.id
        activeRestTimerEndEpochMillis = System.currentTimeMillis() + durationSeconds.toLong() * 1_000L
        updateExerciseRest(exercise.id, durationSeconds)
        RestTimerSoundService.start(getApplication(), durationSeconds)
        val endElapsedRealtimeMs = SystemClock.elapsedRealtime() + durationSeconds.toLong() * 1_000L
        persistWorkoutSessionState()
        val job = viewModelScope.launch {
            val currentJob = coroutineContext[Job]
            var lastReportedRemaining = durationSeconds
            try {
                while (true) {
                    val nowElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    val remaining = computeRemainingSeconds(endElapsedRealtimeMs, nowElapsedRealtimeMs)
                    if (remaining != lastReportedRemaining) {
                        updateExerciseRest(exercise.id, remaining)
                        lastReportedRemaining = remaining
                    }
                    if (remaining <= 0) {
                        break
                    }
                    delay(computeDelayUntilNextTick(endElapsedRealtimeMs, nowElapsedRealtimeMs))
                }
            } finally {
                if (restTimers[exercise.id] == currentJob) {
                    restTimers.remove(exercise.id)
                    activeRestTimerEndEpochMillis = null
                    updateExerciseRest(exercise.id, null)
                    persistWorkoutSessionState()
                }
            }
        }
        restTimers[exercise.id] = job
    }

    private fun cancelRestTimer(exerciseId: String) {
        restTimers.remove(exerciseId)?.cancel()
        activeRestTimerEndEpochMillis = null
        updateExerciseRest(exerciseId, null)
        RestTimerSoundService.stop(getApplication())
        persistWorkoutSessionState()
    }

    private fun repositionExercise(currentIndex: Int, exercise: ExerciseUiState) {
        if (currentIndex !in _exercises.indices) return
        val removed = _exercises.removeAt(currentIndex)
        val itemToInsert = if (removed.id == exercise.id) exercise else removed
        val insertionIndex = _exercises.indexOfFirst { it.isCompleted() }
            .let { if (it == -1) _exercises.size else it }
        _exercises.add(insertionIndex, itemToInsert)
    }

    private fun reorderToDefaultOrder(items: List<ExerciseUiState>): List<ExerciseUiState> {
        if (defaultOrder.isEmpty()) return items
        val positions = defaultOrder.withIndex().associate { it.value to it.index }
        return items.sortedWith(compareBy { positions[it.id] ?: Int.MAX_VALUE })
    }

    private fun updateExerciseRest(exerciseId: String, secondsRemaining: Int?) {
        val index = _exercises.indexOfFirst { it.id == exerciseId }
        if (index >= 0) {
            val current = _exercises[index]
            if (current.restSecondsRemaining != secondsRemaining) {
                _exercises[index] = current.copy(restSecondsRemaining = secondsRemaining)
            }
        }
        if (activeStatusExerciseId == exerciseId) {
            statusText = secondsRemaining?.let { formatRestTime(it) }
            if (secondsRemaining == null) {
                activeStatusExerciseId = null
            }
        }
    }

    private fun formatRestTime(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = safe / 60
        val secs = safe % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }

    private fun updateActiveSelection(newActiveId: String?) {
        val sanitizedId = newActiveId?.takeIf { id ->
            _exercises.any { it.id == id && !it.isCompleted() }
        }
        if (activeExerciseId == sanitizedId) {
            val mismatchExists = _exercises.any { exercise ->
                val shouldBeActive = sanitizedId != null && exercise.id == sanitizedId
                exercise.isActive != shouldBeActive
            }
            if (!mismatchExists) {
                return
            }
        }
        activeExerciseId = sanitizedId
        _exercises.replaceAll { exercise ->
            val shouldBeActive = sanitizedId != null && exercise.id == sanitizedId
            if (exercise.isActive == shouldBeActive) {
                exercise
            } else {
                exercise.copy(isActive = shouldBeActive)
            }
        }
    }

    private fun loadWorkoutSession(): WorkoutSessionSnapshot? {
        val raw = sessionPrefs.getString(WORKOUT_SESSION_PREF_KEY, null) ?: return null
        return runCatching {
            deserializeWorkoutSessionSnapshot(raw)
        }.getOrElse { error ->
            Log.e(LOG_TAG, "Could not parse persisted workout session. Clearing saved state.", error)
            clearWorkoutSessionState()
            null
        }
    }

    private fun persistWorkoutSessionState() {
        val snapshot = WorkoutSessionSnapshot(
            exerciseStates = _exercises.map { exercise ->
                WorkoutSessionExerciseState(
                    id = exercise.id,
                    completedSets = exercise.completedSets,
                    isUnlocked = exercise.isUnlocked
                )
            },
            exerciseOrder = _exercises.map { exercise -> exercise.id },
            activeExerciseId = activeExerciseId,
            activeRestTimerExerciseId = activeStatusExerciseId,
            restTimerEndEpochMillis = activeRestTimerEndEpochMillis
        )
        sessionPrefs.edit().putString(
            WORKOUT_SESSION_PREF_KEY,
            serializeWorkoutSessionSnapshot(snapshot)
        ).apply()
    }

    private fun clearWorkoutSessionState() {
        sessionPrefs.edit().remove(WORKOUT_SESSION_PREF_KEY).apply()
    }

    companion object {
        private const val LOG_TAG = "GymViewModel"
        private const val NOTES_PREFS = "exercise_notes"
        private const val GENERAL_NOTE_PREF_KEY = "general_note"
        private const val WEIGHTS_PREFS = "exercise_weights"
        private const val WORKOUT_SESSION_PREFS = "workout_session"
        private const val WORKOUT_SESSION_PREF_KEY = "session_snapshot"
        private val GROUP_SEQUENCE = listOf(
            ExerciseGroup.WARM_UP,
            ExerciseGroup.MAIN,
            ExerciseGroup.CARDIO,
            ExerciseGroup.COOLDOWN
        )

        internal fun parseExercisesFromJson(raw: String): List<ExerciseUiState> =
            com.example.gymprogress.parseExercisesFromJson(raw)

        internal fun fallbackExercises(): List<ExerciseUiState> =
            com.example.gymprogress.fallbackExercises()
    }
}
