package com.example.gymprogress

import android.app.Activity
import android.app.Application
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.text.Html
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import com.example.gymprogress.ui.theme.ActiveGlowBlue
import com.example.gymprogress.ui.theme.GymProgressTheme
import com.example.gymprogress.ui.theme.HighlightGreen
import com.example.gymprogress.ui.theme.PrimaryGreen
import com.example.gymprogress.ui.theme.SecondaryGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GymProgressTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    GymApp()
                }
            }
        }
    }
}

enum class ExerciseType {
    WEIGHTS,
    ACTIVITY
}

data class ShareContent(
    val plainText: String,
    val htmlText: String
)

private const val SHARE_SUBJECT_DATE_PATTERN = "yyyy-MM-dd"

private fun buildShareNotesSubject(context: Context): String {
    val currentDate = SimpleDateFormat(SHARE_SUBJECT_DATE_PATTERN, Locale.getDefault()).format(Date())
    val baseSubject = context.getString(R.string.share_notes_subject)
    return "$currentDate - $baseSubject"
}

data class ExerciseUiState(
    val id: String,
    val name: String,
    val type: ExerciseType,
    val mode: String? = null,
    val durationMinutes: Int? = null,
    val level: Int? = null,
    val weightOptions: List<Int>,
    val selectedWeight: Int,
    val defaultWeight: Int,
    val restBetweenSeconds: Int,
    val restFinalSeconds: Int,
    val totalSets: Int,
    val completedSets: Int,
    val hasSettings: Boolean,
    val settingsNote: String? = null,
    val personalNote: String? = null,
    val persistedWeight: Int? = null,
    val restSecondsRemaining: Int? = null,
    val isActive: Boolean = false
)

private fun ExerciseUiState.isCompleted(): Boolean =
    totalSets > 0 && completedSets >= totalSets

class GymViewModel(application: Application) : AndroidViewModel(application) {
    private val _exercises = mutableStateListOf<ExerciseUiState>()
    val exercises: List<ExerciseUiState> get() = _exercises
    private val notesPrefs = application.getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
    private val weightsPrefs = application.getSharedPreferences(WEIGHTS_PREFS, Context.MODE_PRIVATE)
    private val defaultOrder = mutableListOf<String>()
    private val restTimers = mutableMapOf<String, Job>()
    private var activeStatusExerciseId: String? = null
    private var activeExerciseId: String? = null
    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_NOTIFICATION, 100)
    }.getOrNull()

    var statusText by mutableStateOf<String?>(null)
        private set

    var generalNote by mutableStateOf<String?>(null)
        private set

    init {
        generalNote = loadGeneralNote()
        val savedNotes = loadSavedNotes()
        val savedWeights = loadSavedWeights()
        val defaults = loadExercisesFromAssets() ?: fallbackExercises()
        _exercises.addAll(defaults.map { exercise ->
            val note = savedNotes[exercise.id]
            val persistedWeight = savedWeights[exercise.id]?.takeIf { it in exercise.weightOptions }
            exercise.copy(
                personalNote = note?.takeIf { it.isNotBlank() },
                selectedWeight = persistedWeight ?: exercise.selectedWeight,
                persistedWeight = persistedWeight,
                restSecondsRemaining = null
            )
        })
        defaultOrder.clear()
        defaultOrder.addAll(_exercises.map { it.id })
    }

    fun advanceProgress(exerciseId: String) {
        val index = _exercises.indexOfFirst { it.id == exerciseId }
        if (index >= 0) {
            val exercise = _exercises[index]
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
            }
            if (!isCompleted) {
                updateActiveSelection(updatedExercise.id)
            }
            if (nextValue == 0) {
                return
            }
            val duration = if (nextValue >= total) {
                updatedExercise.restFinalSeconds
            } else {
                updatedExercise.restBetweenSeconds
            }
            startRestTimer(updatedExercise, duration)
        }
    }

    fun updateWeight(exerciseId: String, newWeight: Int) {
        val index = _exercises.indexOfFirst { it.id == exerciseId }
        if (index >= 0) {
            val exercise = _exercises[index]
            if (exercise.type != ExerciseType.WEIGHTS) {
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
        activeStatusExerciseId = null
        updateActiveSelection(null)
        statusText = null
        stopTone()
        val resetExercises = _exercises.map { exercise ->
            exercise.copy(
                completedSets = 0,
                restSecondsRemaining = null
            )
        }
        val ordered = reorderToDefaultOrder(resetExercises)
        _exercises.clear()
        _exercises.addAll(ordered)
    }

    fun performFullReset() {
        restTimers.values.forEach { it.cancel() }
        restTimers.clear()
        activeStatusExerciseId = null
        updateActiveSelection(null)
        statusText = null
        stopTone()
        notesPrefs.edit().clear().apply()
        generalNote = null
        weightsPrefs.edit().clear().apply()
        val resetExercises = _exercises.map { exercise ->
            exercise.copy(
                completedSets = 0,
                personalNote = null,
                selectedWeight = exercise.defaultWeight,
                persistedWeight = null,
                restSecondsRemaining = null
            )
        }
        val ordered = reorderToDefaultOrder(resetExercises)
        _exercises.clear()
        _exercises.addAll(ordered)
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
        val entries = mutableListOf<Pair<String, String>>()
        val general = generalNote?.trim()?.takeIf { it.isNotEmpty() }
        if (general != null) {
            val generalTitle = getApplication<Application>().getString(R.string.general_note_title)
            entries += generalTitle to general
        }
        entries += _exercises.mapNotNull { exercise ->
            val name = exercise.name.trim()
            val note = exercise.personalNote?.trim()?.takeIf { it.isNotEmpty() }
            if (name.isNotEmpty() && note != null) {
                name to note
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

    private fun escapeNoteToHtml(note: String): String =
        note.split('\n').joinToString("<br>") { line ->
            Html.escapeHtml(line)
        }

    fun stopActiveRestTimer() {
        val activeId = activeStatusExerciseId ?: return
        cancelRestTimer(activeId)
    }

    fun markExerciseActive(exerciseId: String) {
        val target = _exercises.firstOrNull { it.id == exerciseId } ?: return
        if (target.isCompleted()) {
            return
        }
        if (activeExerciseId == exerciseId && target.isActive) {
            return
        }
        updateActiveSelection(exerciseId)
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
        updateExerciseRest(exercise.id, durationSeconds)
        val job = viewModelScope.launch {
            val currentJob = coroutineContext[Job]
            try {
                var remaining = durationSeconds
                while (remaining > 0) {
                    delay(1_000L)
                    remaining -= 1
                    updateExerciseRest(exercise.id, remaining)
                    if (remaining in 0..6) {
                        playRestTick(remaining)
                    }
                }
            } finally {
                if (restTimers[exercise.id] == currentJob) {
                    restTimers.remove(exercise.id)
                    updateExerciseRest(exercise.id, null)
                }
            }
        }
        restTimers[exercise.id] = job
    }

    private fun cancelRestTimer(exerciseId: String) {
        restTimers.remove(exerciseId)?.cancel()
        updateExerciseRest(exerciseId, null)
        stopTone()
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
                stopTone()
            }
        }
    }

    private fun formatRestTime(seconds: Int): String {
        val safe = seconds.coerceAtLeast(0)
        val minutes = safe / 60
        val secs = safe % 60
        return String.format(Locale.getDefault(), "%d:%02d", minutes, secs)
    }

    override fun onCleared() {
        super.onCleared()
        restTimers.values.forEach { it.cancel() }
        restTimers.clear()
        stopTone()
        toneGenerator?.release()
    }

    private fun playRestTick(remainingSeconds: Int) {
        val isFinalTick = remainingSeconds <= 0
        toneGenerator?.let { generator ->
            generator.stopTone()
            if (isFinalTick) {
                val pulseDurationMs = 40
                val gapDurationMs = 20
                generator.startTone(ToneGenerator.TONE_PROP_ACK, pulseDurationMs)
                viewModelScope.launch {
                    delay(pulseDurationMs.toLong())
                    generator.stopTone()
                    delay(gapDurationMs.toLong())
                    generator.startTone(ToneGenerator.TONE_PROP_ACK, pulseDurationMs)
                    delay(pulseDurationMs.toLong())
                    generator.stopTone()
                }
            } else {
                generator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
            }
        }
    }

    private fun stopTone() {
        toneGenerator?.stopTone()
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

    private fun loadExercisesFromAssets(): List<ExerciseUiState>? {
        val context = getApplication<Application>()
        val assets = context.assets
        return runCatching {
            assets.open(DEFAULT_ASSET).bufferedReader().use { it.readText() }
        }
            .mapCatching { parseExercisesFromJson(it) }
            .getOrNull()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun parseExercisesFromJson(raw: String): List<ExerciseUiState> {
        val jsonArray = JSONArray(raw)
        val items = mutableListOf<ExerciseUiState>()
        for (index in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(index)
            val rawType = obj.optString("type", ExerciseType.WEIGHTS.name)
            val type = runCatching { ExerciseType.valueOf(rawType.uppercase(Locale.US)) }
                .getOrDefault(ExerciseType.WEIGHTS)
            when (type) {
                ExerciseType.WEIGHTS -> {
                    val options = obj.optJSONArray("weightOptions")?.toIntList().orEmpty()
                    if (options.isEmpty()) continue
                    val setsCount = obj.optInt("sets", 3).coerceAtLeast(1)
                    val defaultWeight = obj.optInt("defaultWeight", options.first())
                    val settingsNote = obj.optString("settingsNote")
                        .takeIf { it.isNotBlank() }
                    val restBetween = obj.optInt("restBetweenSeconds", DEFAULT_REST_BETWEEN_SECONDS)
                    val restFinal = obj.optInt("restFinalSeconds", DEFAULT_REST_FINAL_SECONDS)
                    val normalizedDefault = if (defaultWeight in options) defaultWeight else options.first()
                    items += ExerciseUiState(
                        id = obj.getString("id"),
                        name = obj.optString("label", obj.getString("id")),
                        type = ExerciseType.WEIGHTS,
                        mode = obj.optString("mode").takeIf { it.isNotBlank() },
                        durationMinutes = null,
                        level = null,
                        weightOptions = options,
                        selectedWeight = normalizedDefault,
                        defaultWeight = normalizedDefault,
                        restBetweenSeconds = restBetween,
                        restFinalSeconds = restFinal,
                        totalSets = setsCount,
                        completedSets = 0,
                        hasSettings = obj.optBoolean("hasSettings", false) || settingsNote != null,
                        settingsNote = settingsNote
                    )
                }

                ExerciseType.ACTIVITY -> {
                    val duration = obj.optInt("durationMinutes", 0).coerceAtLeast(0)
                        .takeIf { it > 0 }
                    val level = obj.optInt("level", 0).coerceAtLeast(0)
                        .takeIf { it > 0 }
                    val restFinal = obj.optInt("restFinalSeconds", DEFAULT_ACTIVITY_REST_SECONDS)
                        .coerceAtLeast(0)
                    val settingsNote = obj.optString("settingsNote")
                        .takeIf { it.isNotBlank() }
                    items += ExerciseUiState(
                        id = obj.getString("id"),
                        name = obj.optString("label", obj.getString("id")),
                        type = ExerciseType.ACTIVITY,
                        mode = obj.optString("mode").takeIf { it.isNotBlank() },
                        durationMinutes = duration,
                        level = level,
                        weightOptions = emptyList(),
                        selectedWeight = 0,
                        defaultWeight = 0,
                        restBetweenSeconds = 0,
                        restFinalSeconds = restFinal,
                        totalSets = 1,
                        completedSets = 0,
                        hasSettings = obj.optBoolean("hasSettings", false) || settingsNote != null,
                        settingsNote = settingsNote
                    )
                }
            }
        }
        return items
    }

    companion object {
        private const val DEFAULT_ASSET = "exercises.json"
        private const val NOTES_PREFS = "exercise_notes"
        private const val GENERAL_NOTE_PREF_KEY = "general_note"
        private const val WEIGHTS_PREFS = "exercise_weights"
        private const val DEFAULT_REST_BETWEEN_SECONDS = 45
        private const val DEFAULT_REST_FINAL_SECONDS = 120
        private const val DEFAULT_ACTIVITY_REST_SECONDS = 120
        private const val USER_WEIGHT_KG = 89
        private const val USER_WEIGHT_LB = 196
        private const val USER_AGE = 55
        private const val USER_MAX_HEART_RATE = 140

        internal fun fallbackExercises(): List<ExerciseUiState> = listOf(
            ExerciseUiState(
                id = "elliptical",
                name = "Elliptical",
                type = ExerciseType.ACTIVITY,
                mode = "elliptical",
                durationMinutes = 5,
                level = 7,
                weightOptions = emptyList(),
                selectedWeight = 0,
                defaultWeight = 0,
                restBetweenSeconds = 0,
                restFinalSeconds = DEFAULT_ACTIVITY_REST_SECONDS,
                totalSets = 1,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Вес: $USER_WEIGHT_KG кг ($USER_WEIGHT_LB lb). Возраст: $USER_AGE. Максимальный пульс: $USER_MAX_HEART_RATE. Установите уровень 7."
            ),
            ExerciseUiState(
                id = "leg_press",
                name = "Leg Press",
                type = ExerciseType.WEIGHTS,
                weightOptions = listOf(23, 30, 37, 44, 51, 58, 65, 72, 79, 86, 93, 100),
                selectedWeight = 44,
                defaultWeight = 44,
                restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
                restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
                totalSets = 3,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Перед началом установите длину салазок на позицию 8."
            ),
            ExerciseUiState(
                id = "leg_extension",
                name = "Leg Extension",
                type = ExerciseType.WEIGHTS,
                weightOptions = listOf(7, 12, 14, 19, 21, 26, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
                selectedWeight = 14,
                defaultWeight = 14,
                restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
                restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
                totalSets = 3,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Спинку зафиксируйте в пазе 5, валик для ног поставьте в положение XL."
            ),
            ExerciseUiState(
                id = "leg_curl",
                name = "Leg Curl",
                type = ExerciseType.WEIGHTS,
                weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
                selectedWeight = 14,
                defaultWeight = 14,
                restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
                restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
                totalSets = 3,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Перед упражнением установите валик для ног в положение XL."
            ),
            ExerciseUiState(
                id = "leg_abductor",
                name = "Hip abductor",
                type = ExerciseType.WEIGHTS,
                weightOptions = listOf(14, 21, 28, 35, 42, 49),
                selectedWeight = 35,
                defaultWeight = 35,
                restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
                restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
                totalSets = 3,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Установите начальную ширину в положение 1."
            ),
            ExerciseUiState(
                id = "shoulder_press",
                name = "Shoulder Press",
                type = ExerciseType.WEIGHTS,
                weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
                selectedWeight = 14,
                defaultWeight = 14,
                restBetweenSeconds = 60,
                restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
                totalSets = 3,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Отрегулируйте высоту сиденья на уровень 8."
            ),
            ExerciseUiState(
                id = "lat_pulldown",
                name = "Lat Pulldown",
                type = ExerciseType.WEIGHTS,
                weightOptions = listOf(7, 14, 21, 28, 35, 42, 49),
                selectedWeight = 35,
                defaultWeight = 35,
                restBetweenSeconds = 60,
                restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
                totalSets = 3,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Перед тягой установите высоту сиденья на уровень 3."
            ),
            ExerciseUiState(
                id = "chest_press",
                name = "Chest Press",
                type = ExerciseType.WEIGHTS,
                weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
                selectedWeight = 14,
                defaultWeight = 14,
                restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
                restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
                totalSets = 3,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Отрегулируйте высоту сиденья на уровень 7 и убедитесь, что руки выстроены ровно."
            ),
            ExerciseUiState(
                id = "seated_row",
                name = "Seated Row",
                type = ExerciseType.WEIGHTS,
                weightOptions = listOf(14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
                selectedWeight = 42,
                defaultWeight = 42,
                restBetweenSeconds = DEFAULT_REST_BETWEEN_SECONDS,
                restFinalSeconds = DEFAULT_REST_FINAL_SECONDS,
                totalSets = 3,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Сядьте прямо, чтобы грудной упор лишь касался корпуса. Если индикатор веса отсутствует, выберите уровень 9, вдавив девять кнопок и оставив остальные вынутыми."
            ),
            ExerciseUiState(
                id = "bike",
                name = "Exercise Bike",
                type = ExerciseType.ACTIVITY,
                mode = "bike",
                durationMinutes = 15,
                level = 18,
                weightOptions = emptyList(),
                selectedWeight = 0,
                defaultWeight = 0,
                restBetweenSeconds = 0,
                restFinalSeconds = DEFAULT_ACTIVITY_REST_SECONDS,
                totalSets = 1,
                completedSets = 0,
                hasSettings = true,
                settingsNote = "Вес: $USER_WEIGHT_KG кг ($USER_WEIGHT_LB lb). Возраст: $USER_AGE. Максимальный пульс: $USER_MAX_HEART_RATE. Установите уровень 18. Высота сиденья: 20."
            )
        )
    }
}

@Composable
fun GymApp(viewModel: GymViewModel = viewModel()) {
    val exercises = viewModel.exercises
    val statusText = viewModel.statusText
    val generalNote = viewModel.generalNote
    GymScreen(
        exercises = exercises,
        onExerciseSelected = viewModel::markExerciseActive,
        onProgressTapped = viewModel::advanceProgress,
        onWeightSelected = viewModel::updateWeight,
        onResetDay = viewModel::resetAllSets,
        onPersonalNoteSaved = viewModel::updatePersonalNote,
        generalNote = generalNote,
        onGeneralNoteSaved = viewModel::updateGeneralNote,
        statusText = statusText,
        onStatusTapped = viewModel::stopActiveRestTimer,
        onFullReset = viewModel::performFullReset,
        onShareNotes = viewModel::buildShareContent
    )
}

@Composable
fun GymScreen(
    exercises: List<ExerciseUiState>,
    onExerciseSelected: (String) -> Unit,
    onProgressTapped: (String) -> Unit,
    onWeightSelected: (String, Int) -> Unit,
    onResetDay: () -> Unit,
    onPersonalNoteSaved: (String, String) -> Unit,
    generalNote: String?,
    onGeneralNoteSaved: (String) -> Unit,
    statusText: String?,
    onStatusTapped: () -> Unit,
    onFullReset: () -> Unit,
    onShareNotes: () -> ShareContent?
) {
    var weightDialogFor by remember { mutableStateOf<String?>(null) }
    var settingsDialogFor by remember { mutableStateOf<String?>(null) }
    var noteDialogFor by remember { mutableStateOf<String?>(null) }
    var generalNoteDialogVisible by remember { mutableStateOf(false) }
    val dialogExercise = exercises.firstOrNull { it.id == weightDialogFor && it.type == ExerciseType.WEIGHTS }
    val settingsDialogExercise = exercises.firstOrNull {
        it.id == settingsDialogFor && !it.settingsNote.isNullOrBlank()
    }
    val noteDialogExercise = exercises.firstOrNull { it.id == noteDialogFor }
    val context = LocalContext.current

    if (dialogExercise != null) {
        WeightPickerDialog(
            exercise = dialogExercise,
            onDismiss = { weightDialogFor = null },
            onWeightSelected = { weight ->
                onWeightSelected(dialogExercise.id, weight)
                weightDialogFor = null
            }
        )
    }

    if (generalNoteDialogVisible) {
        GeneralNoteDialog(
            note = generalNote.orEmpty(),
            onDismiss = { generalNoteDialogVisible = false },
            onSave = { text ->
                onGeneralNoteSaved(text)
                generalNoteDialogVisible = false
            }
        )
    }

    if (settingsDialogExercise != null) {
        SettingsNoteDialog(
            exerciseName = settingsDialogExercise.name,
            note = settingsDialogExercise.settingsNote.orEmpty(),
            onDismiss = { settingsDialogFor = null }
        )
    }

    if (noteDialogExercise != null) {
        NoteEditorDialog(
            exercise = noteDialogExercise,
            onDismiss = { noteDialogFor = null },
            onSave = { text ->
                onPersonalNoteSaved(noteDialogExercise.id, text)
                noteDialogFor = null
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(exercises, key = { it.id }) { exercise ->
                ExerciseCard(
                    exercise = exercise,
                    onSelect = { onExerciseSelected(exercise.id) },
                    onProgressClick = { onProgressTapped(exercise.id) },
                    onWeightClick = {
                        if (exercise.type == ExerciseType.WEIGHTS) {
                            weightDialogFor = exercise.id
                        }
                    },
                    onSettingsClick = {
                        if (!exercise.settingsNote.isNullOrBlank()) {
                            settingsDialogFor = exercise.id
                        }
                    },
                    onNoteClick = { noteDialogFor = exercise.id }
                )
            }
        }

        HorizontalDivider()
        var overflowExpanded by remember { mutableStateOf(false) }
        val newDayGradient = Brush.verticalGradient(
            colors = listOf(
                HighlightGreen,
                SecondaryGreen,
                PrimaryGreen
            )
        )
        val isStatusEnabled = !statusText.isNullOrBlank()
        val statusShape = RoundedCornerShape(24.dp)
        val innerStatusShape = RoundedCornerShape(20.dp)
        val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
        val statusFrameBrush = Brush.verticalGradient(
            colors = listOf(
                surfaceVariant.blendWith(Color.White, 0.55f),
                surfaceVariant,
                surfaceVariant.blendWith(Color.Black, 0.32f)
            )
        )
        val statusInnerBrush = Brush.verticalGradient(
            colors = listOf(
                surfaceVariant.blendWith(Color.Black, 0.22f),
                surfaceVariant.blendWith(Color.Black, 0.28f)
            )
        )
        val hasGeneralNote = !generalNote.isNullOrBlank()
        val generalNoteIcon = if (hasGeneralNote) {
            Icons.Default.EditNote
        } else {
            Icons.Default.Edit
        }
        val generalNoteTint = MaterialTheme.colorScheme.onSurfaceVariant
        val statusWidthWeight = 2f / 3f
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onResetDay,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(newDayGradient),
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Transparent,
                    disabledContainerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    disabledContentColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.6f)
                ),
                border = BorderStroke(width = 1.dp, color = Color.White.copy(alpha = 0.35f)),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = 4.dp,
                    pressedElevation = 2.dp,
                    focusedElevation = 6.dp,
                    hoveredElevation = 5.dp,
                    disabledElevation = 0.dp
                )
            ) {
                Text(
                    text = stringResource(R.string.new_day),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
            Surface(
                modifier = Modifier
                    .weight(statusWidthWeight)
                    .height(48.dp),
                shape = statusShape,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                shadowElevation = 3.dp,
                tonalElevation = 1.dp,
                onClick = { onStatusTapped() },
                enabled = isStatusEnabled
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(statusShape)
                        .background(statusFrameBrush)
                        .padding(horizontal = 2.dp, vertical = 3.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(innerStatusShape)
                            .background(statusInnerBrush)
                            .drawWithContent {
                                val highlightHeight = 6.dp.toPx()
                                val shadowHeight = 10.dp.toPx()
                                val highlightAlpha = if (isStatusEnabled) 0.18f else 0.08f
                                val shadowAlpha = if (isStatusEnabled) 0.22f else 0.12f
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.White.copy(alpha = highlightAlpha),
                                            Color.Transparent
                                        ),
                                        startY = 0f,
                                        endY = highlightHeight
                                    ),
                                    size = Size(size.width, highlightHeight)
                                )
                                drawRect(
                                    brush = Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.Black.copy(alpha = shadowAlpha)
                                        ),
                                        startY = size.height - shadowHeight,
                                        endY = size.height
                                    ),
                                    topLeft = Offset(x = 0f, y = size.height - shadowHeight),
                                    size = Size(size.width, shadowHeight)
                                )
                                drawContent()
                            }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val statusColor = if (isStatusEnabled) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                            }
                            Text(
                                text = statusText ?: "",
                                color = statusColor,
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
            IconButton(
                modifier = Modifier.size(40.dp),
                onClick = { generalNoteDialogVisible = true }
            ) {
                Icon(
                    imageVector = generalNoteIcon,
                    contentDescription = stringResource(R.string.general_note_action),
                    tint = generalNoteTint
                )
            }
            Box {
                IconButton(
                    modifier = Modifier.size(40.dp),
                    onClick = { overflowExpanded = true }
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.share_notes_action)) },
                        onClick = {
                            overflowExpanded = false
                            val shareContent = onShareNotes()
                            if (shareContent != null) {
                                val shareSubject = buildShareNotesSubject(context)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/html"
                                    putExtra(
                                        Intent.EXTRA_SUBJECT,
                                        shareSubject
                                    )
                                    val htmlSpanned = Html.fromHtml(
                                        shareContent.htmlText,
                                        Html.FROM_HTML_MODE_LEGACY
                                    )
                                    putExtra(Intent.EXTRA_TEXT, htmlSpanned)
                                    putExtra(Intent.EXTRA_HTML_TEXT, shareContent.htmlText)
                                    clipData = ClipData.newHtmlText(
                                        shareSubject,
                                        shareContent.plainText,
                                        shareContent.htmlText
                                    )
                                }
                                val chooserTitle = context.getString(R.string.share_notes_chooser_title)
                                val resolved = shareIntent.resolveActivity(context.packageManager)
                                if (resolved != null) {
                                    val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
                                        if (context !is Activity) {
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                    }
                                    context.startActivity(chooserIntent)
                                } else {
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.share_notes_no_app),
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            } else {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.share_notes_empty),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.secondary_reset_hint)) },
                        onClick = {
                            overflowExpanded = false
                            onFullReset()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseUiState,
    onSelect: () -> Unit,
    onProgressClick: () -> Unit,
    onWeightClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNoteClick: () -> Unit
) {
    val isCompleted = exercise.totalSets > 0 && exercise.completedSets >= exercise.totalSets
    val isActivity = exercise.type == ExerciseType.ACTIVITY
    val hasSettingsNote = exercise.hasSettings && !exercise.settingsNote.isNullOrBlank()
    val hasPersonalNote = !exercise.personalNote.isNullOrBlank()
    val isActive = exercise.isActive
    val isActiveAndIncomplete = isActive && !isCompleted
    val isCompletedActive = isCompleted && isActive
    val activeGlowColor = ActiveGlowBlue
    val weightText = if (isActivity) {
        null
    } else {
        stringResource(R.string.weight_label_template, exercise.selectedWeight)
    }
    val durationText = exercise.durationMinutes
        ?.takeIf { isActivity }
        ?.let { duration -> stringResource(R.string.activity_duration_minutes, duration) }
    val titleStyle = when {
        isCompleted -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
        isActiveAndIncomplete -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        else -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    }
    val baseContentColor = MaterialTheme.colorScheme.onSurface
    val completedContentColor = baseContentColor.copy(alpha = 0.5f)
    val contentColor = when {
        isCompleted -> completedContentColor
        isActiveAndIncomplete -> activeGlowColor
        else -> baseContentColor
    }
    val iconTintBase = contentColor
    val noteIconTint = when {
        isCompleted -> contentColor.copy(alpha = 0.85f)
        isActiveAndIncomplete -> activeGlowColor
        else -> iconTintBase
    }
    val noteIcon = if (hasPersonalNote) {
        Icons.Default.EditNote
    } else {
        Icons.Default.Edit
    }
    val outlineColor = when {
        isCompleted -> MaterialTheme.colorScheme.outline.copy(alpha = 0.32f)
        isActiveAndIncomplete -> activeGlowColor.copy(alpha = 0.75f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    }
    val shape = RoundedCornerShape(16.dp)
    val surfaceBase = MaterialTheme.colorScheme.surface
    val raisedTop = surfaceBase.blendWith(Color.White, 0.06f)
    val raisedBottom = surfaceBase.blendWith(Color.Black, 0.12f)
    val raisedBrush = Brush.verticalGradient(listOf(raisedTop, raisedBottom))

    val pressedBackground = surfaceBase.blendWith(Color.Black, 0.4f)
    val highlightRaised = Color.White.copy(alpha = 0.1f)
    val shadowRaised = Color.Black.copy(alpha = 0.22f)
    val glowHighlight = activeGlowColor.copy(alpha = 0.55f)
    val glowShadow = Color.Black.copy(alpha = 0.45f)
    val glowBackground = Brush.verticalGradient(
        colors = listOf(
            surfaceBase.blendWith(activeGlowColor, 0.45f),
            surfaceBase.blendWith(activeGlowColor, 0.32f),
            surfaceBase.blendWith(Color.Black, 0.78f)
        )
    )

    val cardModifier = when {
        isCompletedActive -> Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(glowBackground)
            .border(1.2.dp, activeGlowColor.copy(alpha = 0.62f), shape)
        isCompleted -> Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(pressedBackground)
            .border(1.dp, outlineColor, shape)
        isActiveAndIncomplete -> Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 18.dp,
                shape = shape,
                clip = false,
                ambientColor = activeGlowColor.copy(alpha = 0.42f),
                spotColor = activeGlowColor.copy(alpha = 0.78f)
            )
            .clip(shape)
            .background(glowBackground)
            .border(1.5.dp, outlineColor, shape)
            .insetEdges(
                lightColor = glowHighlight,
                darkColor = glowShadow,
                strokeWidth = 1.5.dp,
                inverted = false
            )
        else -> Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape = shape, clip = false)
            .clip(shape)
            .background(raisedBrush)
            .border(1.dp, outlineColor, shape)
            .insetEdges(
                lightColor = highlightRaised,
                darkColor = shadowRaised,
                strokeWidth = 1.dp,
                inverted = false
            )
    }
    val neutralChipBackground = surfaceBase.blendWith(Color.White, 0.12f)
    val chipBackgroundColor = when {
        isCompleted -> pressedBackground
        isActiveAndIncomplete -> activeGlowColor.copy(alpha = 0.18f)
        else -> neutralChipBackground
    }
    val chipTextColor = when {
        isCompleted -> contentColor
        isActiveAndIncomplete -> activeGlowColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    val chipBorderColor = when {
        isActiveAndIncomplete -> activeGlowColor.copy(alpha = 0.65f)
        else -> outlineColor
    }
    val counterBackgroundColor = when {
        isCompleted -> pressedBackground
        isActiveAndIncomplete -> activeGlowColor.copy(alpha = 0.16f)
        else -> neutralChipBackground
    }
    val counterTextColor = when {
        isCompleted -> contentColor
        isActiveAndIncomplete -> activeGlowColor
        else -> MaterialTheme.colorScheme.onSurface
    }

    val showCompletedGlow = isCompletedActive
    val completedGlowDimming = Brush.verticalGradient(
        colors = listOf(
            Color.Black.copy(alpha = 0.18f),
            Color.Black.copy(alpha = 0.55f)
        )
    )

    Box(modifier = cardModifier) {
        if (showCompletedGlow) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(shape)
                    .background(completedGlowDimming)
            )
        }
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val titleModifier = if (isCompleted) {
                    Modifier.weight(1f)
                } else {
                    Modifier
                        .weight(1f)
                        .clickable(onClick = onSelect)
                }
                Column(modifier = titleModifier) {
                    Text(
                        text = exercise.name,
                        style = titleStyle,
                        color = contentColor
                    )
                }
                if (isActivity) {
                    Spacer(modifier = Modifier.width(12.dp))
                    ActivityCompletionRow(
                        checked = isCompleted,
                        durationText = durationText.orEmpty(),
                        onToggle = onProgressClick,
                        textColor = chipTextColor,
                        backgroundColor = chipBackgroundColor,
                        borderColor = chipBorderColor
                    )
                } else {
                    Spacer(modifier = Modifier.width(12.dp))
                    ProgressCounter(
                        value = exercise.completedSets,
                        onClick = onProgressClick,
                        textColor = counterTextColor,
                        backgroundColor = counterBackgroundColor,
                        borderColor = chipBorderColor
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    WeightChip(
                        value = weightText.orEmpty(),
                        onClick = onWeightClick,
                        textColor = chipTextColor,
                        backgroundColor = chipBackgroundColor,
                        borderColor = chipBorderColor
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                }
                IconButton(onClick = onSettingsClick, enabled = hasSettingsNote) {
                    val iconAlpha = if (hasSettingsNote) 1f else 0.4f
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = iconTintBase.copy(alpha = iconAlpha)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onNoteClick) {
                    Icon(
                        imageVector = noteIcon,
                        contentDescription = null,
                        tint = noteIconTint
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityCompletionRow(
    checked: Boolean,
    durationText: String,
    onToggle: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    borderColor: Color
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Checkbox(
            checked = checked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = textColor.copy(alpha = 0.32f),
                uncheckedColor = textColor.copy(alpha = 0.32f),
                checkmarkColor = textColor.copy(alpha = 0.9f),
                disabledCheckedColor = textColor.copy(alpha = 0.2f),
                disabledUncheckedColor = textColor.copy(alpha = 0.2f)
            )
        )
        WeightChip(
            value = durationText,
            onClick = null,
            textColor = textColor,
            backgroundColor = backgroundColor,
            borderColor = borderColor
        )
    }
}

@Composable
private fun ProgressCounter(
    value: Int,
    onClick: () -> Unit,
    textColor: Color,
    backgroundColor: Color,
    borderColor: Color
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    ) {
        Text(
            text = value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun WeightChip(
    value: String,
    onClick: (() -> Unit)? = null,
    textColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    backgroundColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    borderColor: Color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
) {
    val shape = RoundedCornerShape(20.dp)
    val baseModifier = Modifier.clip(shape)
    val clickableModifier = onClick?.let { baseModifier.clickable(onClick = it) } ?: baseModifier
    Surface(
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = clickableModifier
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

private fun Modifier.insetEdges(
    lightColor: Color,
    darkColor: Color,
    strokeWidth: Dp = 1.dp,
    inverted: Boolean = false
): Modifier = drawWithContent {
    drawContent()
    val strokePx = strokeWidth.toPx()
    if (strokePx <= 0f) return@drawWithContent
    val inset = strokePx / 2f
    val width = size.width
    val height = size.height
    val topColor = if (!inverted) lightColor else darkColor
    val leftColor = if (!inverted) lightColor else darkColor
    val bottomColor = if (!inverted) darkColor else lightColor
    val rightColor = if (!inverted) darkColor else lightColor
    drawLine(
        color = topColor,
        start = Offset(inset, inset),
        end = Offset(width - inset, inset),
        strokeWidth = strokePx
    )
    drawLine(
        color = leftColor,
        start = Offset(inset, inset),
        end = Offset(inset, height - inset),
        strokeWidth = strokePx
    )
    drawLine(
        color = rightColor,
            start = Offset(width - inset, inset),
            end = Offset(width - inset, height - inset),
            strokeWidth = strokePx
        )
    drawLine(
        color = bottomColor,
        start = Offset(inset, height - inset),
        end = Offset(width - inset, height - inset),
        strokeWidth = strokePx
    )
}

private fun Color.blendWith(other: Color, ratio: Float): Color {
    val clamped = ratio.coerceIn(0f, 1f)
    val inverse = 1f - clamped
    return Color(
        red = red * inverse + other.red * clamped,
        green = green * inverse + other.green * clamped,
        blue = blue * inverse + other.blue * clamped,
        alpha = alpha * inverse + other.alpha * clamped
    )
}

@Composable
private fun WeightPickerDialog(
    exercise: ExerciseUiState,
    onDismiss: () -> Unit,
    onWeightSelected: (Int) -> Unit
) {
    val listState = rememberLazyListState()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.weight_picker_cancel))
            }
        },
        title = {
            Text(text = stringResource(R.string.weight_picker_title))
        },
        text = {
            Box(
                modifier = Modifier
                    .heightIn(max = 320.dp)
                    .fillMaxWidth()
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(end = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(exercise.weightOptions) { weight ->
                        val isSelected = weight == exercise.selectedWeight
                        val isDefault = weight == exercise.defaultWeight
                        val defaultHighlight = Color(0xFFFACC15)
                        val textStyle = if (isDefault) {
                            MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                        } else {
                            MaterialTheme.typography.bodyLarge
                        }
                        val textColor = when {
                            isSelected && isDefault -> defaultHighlight
                            isSelected -> MaterialTheme.colorScheme.primary
                            isDefault -> defaultHighlight
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onWeightSelected(weight) }
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surface
                                )
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.weight_label_template, weight),
                                style = textStyle,
                                color = textColor
                            )
                        }
                    }
                }
                WeightListScrollbar(
                    state = listState,
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .fillMaxHeight()
                        .padding(vertical = 4.dp)
                )
            }
        }
    )
}

@Composable
private fun WeightListScrollbar(
    state: LazyListState,
    modifier: Modifier = Modifier,
    minThumbSize: Dp = 28.dp
) {
    val layoutInfo = state.layoutInfo
    val visibleItems = layoutInfo.visibleItemsInfo
    val totalItems = layoutInfo.totalItemsCount
    val canScroll = totalItems > visibleItems.size && visibleItems.isNotEmpty()
    if (!canScroll) {
        return
    }

    val density = LocalDensity.current
    val minThumbPx = with(density) { minThumbSize.toPx() }
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.65f)

    Canvas(
        modifier = modifier
            .width(6.dp)
            .padding(horizontal = 2.dp)
    ) {
        val trackWidth = size.width
        val trackHeight = size.height
        val visibleFraction = (visibleItems.size.toFloat() / totalItems.toFloat()).coerceIn(0f, 1f)
        val thumbHeight = (trackHeight * visibleFraction).coerceAtLeast(minThumbPx)
        val scrollableItems = (totalItems - visibleItems.size).coerceAtLeast(1)
        val firstItemSize = visibleItems.first().size.takeIf { it > 0 } ?: 1
        val offsetFraction = (state.firstVisibleItemScrollOffset / firstItemSize.toFloat()).coerceIn(0f, 1f)
        val scrollFraction = ((state.firstVisibleItemIndex + offsetFraction) / scrollableItems.toFloat()).coerceIn(0f, 1f)
        val thumbTop = (trackHeight - thumbHeight) * scrollFraction

        drawRoundRect(
            color = trackColor,
            size = Size(trackWidth, trackHeight),
            cornerRadius = CornerRadius(trackWidth / 2, trackWidth / 2)
        )
        drawRoundRect(
            color = thumbColor,
            topLeft = Offset(0f, thumbTop),
            size = Size(trackWidth, thumbHeight),
            cornerRadius = CornerRadius(trackWidth / 2, trackWidth / 2)
        )
    }
}

@Composable
private fun SettingsNoteDialog(
    exerciseName: String,
    note: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.settings_dialog_close))
            }
        },
        title = {
            Text(text = stringResource(R.string.settings_dialog_title, exerciseName))
        },
        text = {
            Text(
                text = note,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    )
}

@Composable
private fun NoteEditorDialog(
    exercise: ExerciseUiState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draftNote by remember(exercise.id, exercise.personalNote) {
        mutableStateOf(exercise.personalNote.orEmpty())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { draftNote = "" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.note_dialog_clear),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onSave(draftNote) }) {
                    Text(text = stringResource(R.string.note_dialog_save))
                }
            }
        },
        title = {
            Text(text = exercise.name)
        },
        text = {
            Column {
                TextField(
                    value = draftNote,
                    onValueChange = { draftNote = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.note_dialog_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 4
                )
            }
        }
    )
}

@Composable
private fun GeneralNoteDialog(
    note: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draftNote by remember(note) { mutableStateOf(note) }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { draftNote = "" },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.note_dialog_clear),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = { onSave(draftNote) }) {
                    Text(text = stringResource(R.string.note_dialog_save))
                }
            }
        },
        title = {
            Text(text = stringResource(R.string.general_note_title))
        },
        text = {
            Column {
                TextField(
                    value = draftNote,
                    onValueChange = { draftNote = it },
                    placeholder = {
                        Text(
                            text = stringResource(R.string.note_dialog_placeholder),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    maxLines = 4
                )
            }
        }
    )
}

@Preview(showBackground = true)
@Composable
private fun GymScreenPreview() {
    GymProgressTheme {
        GymScreen(
            exercises = GymViewModel.fallbackExercises(),
            onExerciseSelected = {},
            onProgressTapped = { _ -> },
            onWeightSelected = { _, _ -> },
            onResetDay = {},
            onPersonalNoteSaved = { _, _ -> },
            generalNote = null,
            onGeneralNoteSaved = { _ -> },
            statusText = null,
            onStatusTapped = {},
            onFullReset = {},
            onShareNotes = { null }
        )
    }
}

private fun JSONArray.toIntList(): List<Int> = buildList {
    for (i in 0 until length()) {
        add(getInt(i))
    }
}
