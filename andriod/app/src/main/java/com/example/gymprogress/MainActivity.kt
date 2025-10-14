package com.example.gymprogress

import android.app.Application
import android.os.Bundle
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.luminance
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gymprogress.ui.theme.ExerciseCompletedDark
import com.example.gymprogress.ui.theme.ExerciseCompletedLight
import com.example.gymprogress.ui.theme.GymProgressTheme
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

data class ExerciseUiState(
    val id: String,
    val name: String,
    val weightOptions: List<Int>,
    val selectedWeight: Int,
    val defaultWeight: Int,
    val sets: List<Boolean>,
    val hasSettings: Boolean,
    val settingsNote: String? = null,
    val personalNote: String? = null,
    val persistedWeight: Int? = null
)

class GymViewModel(application: Application) : AndroidViewModel(application) {
    private val _exercises = mutableStateListOf<ExerciseUiState>()
    val exercises: List<ExerciseUiState> get() = _exercises
    private val notesPrefs = application.getSharedPreferences(NOTES_PREFS, Context.MODE_PRIVATE)
    private val weightsPrefs = application.getSharedPreferences(WEIGHTS_PREFS, Context.MODE_PRIVATE)

    init {
        val savedNotes = loadSavedNotes()
        val savedWeights = loadSavedWeights()
        val defaults = loadExercisesFromAssets() ?: fallbackExercises()
        _exercises.addAll(defaults.map { exercise ->
            val note = savedNotes[exercise.id]
            val persistedWeight = savedWeights[exercise.id]
            exercise.copy(
                personalNote = note?.takeIf { it.isNotBlank() },
                selectedWeight = persistedWeight ?: exercise.selectedWeight,
                persistedWeight = persistedWeight
            )
        })
    }

    fun toggleSet(exerciseId: String, setIndex: Int) {
        val index = _exercises.indexOfFirst { it.id == exerciseId }
        if (index >= 0) {
            val exercise = _exercises[index]
            if (setIndex in exercise.sets.indices) {
                val updatedSets = exercise.sets.mapIndexed { i, value -> if (i == setIndex) !value else value }
                _exercises[index] = exercise.copy(sets = updatedSets)
            }
        }
    }

    fun updateWeight(exerciseId: String, newWeight: Int) {
        val index = _exercises.indexOfFirst { it.id == exerciseId }
        if (index >= 0) {
            val exercise = _exercises[index]
            if (newWeight in exercise.weightOptions) {
                _exercises[index] = exercise.copy(
                    selectedWeight = newWeight,
                    persistedWeight = newWeight
                )
                weightsPrefs.edit().putInt(exerciseId, newWeight).apply()
            }
        }
    }

    fun resetAllSets() {
        _exercises.replaceAll { exercise ->
            exercise.copy(sets = List(exercise.sets.size) { false })
        }
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

    private fun loadSavedNotes(): Map<String, String> =
        notesPrefs.all.mapNotNull { (key, value) ->
            (value as? String)?.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()

    private fun loadSavedWeights(): Map<String, Int> =
        weightsPrefs.all.mapNotNull { (key, value) ->
            (value as? Int)?.let { key to it }
        }.toMap()

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
            val options = obj.optJSONArray("weightOptions")?.toIntList().orEmpty()
            if (options.isEmpty()) continue
            val setsCount = obj.optInt("sets", 3).coerceAtLeast(1)
            val defaultWeight = obj.optInt("defaultWeight", options.first())
            val settingsNote = obj.optString("settingsNote")
                .takeIf { it.isNotBlank() }
            items += ExerciseUiState(
                id = obj.getString("id"),
                name = obj.optString("label", obj.getString("id")),
                weightOptions = options,
                selectedWeight = if (defaultWeight in options) defaultWeight else options.first(),
                defaultWeight = if (defaultWeight in options) defaultWeight else options.first(),
                sets = List(setsCount) { false },
                hasSettings = obj.optBoolean("hasSettings", false) || settingsNote != null,
                settingsNote = settingsNote
            )
        }
        return items
    }

    companion object {
        private const val DEFAULT_ASSET = "exercises.json"
        private const val NOTES_PREFS = "exercise_notes"
        private const val WEIGHTS_PREFS = "exercise_weights"

        internal fun fallbackExercises(): List<ExerciseUiState> = listOf(
            ExerciseUiState(
                id = "leg_press",
                name = "Leg Press",
                weightOptions = listOf(23, 30, 37, 44, 51, 58, 65, 72, 79),
                selectedWeight = 37,
                defaultWeight = 37,
                sets = List(3) { false },
                hasSettings = true,
                settingsNote = "Set the sled length to position 8 before starting."
            ),
            ExerciseUiState(
                id = "leg_extension",
                name = "Leg Extension",
                weightOptions = listOf(7, 14, 21, 28, 35, 42, 49),
                selectedWeight = 14,
                defaultWeight = 14,
                sets = List(3) { false },
                hasSettings = true,
                settingsNote = "Backrest length on slot 4 and leg pad at XL position."
            ),
            ExerciseUiState(
                id = "leg_curl",
                name = "Leg Curl",
                weightOptions = listOf(7, 14, 21, 28, 35, 42, 49),
                selectedWeight = 14,
                defaultWeight = 14,
                sets = List(3) { false },
                hasSettings = true,
                settingsNote = "Set the leg pad to the XL notch before curling."
            ),
            ExerciseUiState(
                id = "shoulder_press",
                name = "Shoulder Press",
                weightOptions = listOf(7, 14, 21, 28, 35, 42),
                selectedWeight = 7,
                defaultWeight = 7,
                sets = List(3) { false },
                hasSettings = true,
                settingsNote = "Adjust the seat height to level 8."
            ),
            ExerciseUiState(
                id = "leg_abductor",
                name = "Leg Abductor",
                weightOptions = listOf(14, 21, 28, 35, 42, 49),
                selectedWeight = 28,
                defaultWeight = 28,
                sets = List(3) { false },
                hasSettings = true,
                settingsNote = "Use settings position 1 for the starting width."
            ),
            ExerciseUiState(
                id = "lat_pulldown",
                name = "Lat Pulldown",
                weightOptions = listOf(7, 14, 21, 28, 35, 42, 49),
                selectedWeight = 28,
                defaultWeight = 28,
                sets = List(3) { false },
                hasSettings = true,
                settingsNote = "Seat height on level 3 before pulling."
            ),
            ExerciseUiState(
                id = "chest_press",
                name = "Chest Press",
                weightOptions = listOf(7, 14, 21, 28, 35, 42),
                selectedWeight = 14,
                defaultWeight = 14,
                sets = List(3) { false },
                hasSettings = true,
                settingsNote = "Set the seat height to level 5 and verify arm alignment."
            ),
            ExerciseUiState(
                id = "seated_row",
                name = "Seated Row",
                weightOptions = listOf(14, 21, 28, 35, 42, 49),
                selectedWeight = 28,
                defaultWeight = 28,
                sets = List(3) { false },
                hasSettings = true,
                settingsNote = "Sit tall with the chest pad just touching your torso."
            )
        )
    }
}

@Composable
fun GymApp(viewModel: GymViewModel = viewModel()) {
    val exercises = viewModel.exercises
    GymScreen(
        exercises = exercises,
        onToggleSet = viewModel::toggleSet,
        onWeightSelected = viewModel::updateWeight,
        onResetDay = viewModel::resetAllSets,
        onPersonalNoteSaved = viewModel::updatePersonalNote
    )
}

@Composable
fun GymScreen(
    exercises: List<ExerciseUiState>,
    onToggleSet: (String, Int) -> Unit,
    onWeightSelected: (String, Int) -> Unit,
    onResetDay: () -> Unit,
    onPersonalNoteSaved: (String, String) -> Unit
) {
    var weightDialogFor by remember { mutableStateOf<String?>(null) }
    var settingsDialogFor by remember { mutableStateOf<String?>(null) }
    var noteDialogFor by remember { mutableStateOf<String?>(null) }
    val dialogExercise = exercises.firstOrNull { it.id == weightDialogFor }
    val settingsDialogExercise = exercises.firstOrNull {
        it.id == settingsDialogFor && !it.settingsNote.isNullOrBlank()
    }
    val noteDialogExercise = exercises.firstOrNull { it.id == noteDialogFor }

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
                    onToggleSet = { setIndex -> onToggleSet(exercise.id, setIndex) },
                    onWeightClick = { weightDialogFor = exercise.id },
                    onSettingsClick = {
                        if (!exercise.settingsNote.isNullOrBlank()) {
                            settingsDialogFor = exercise.id
                        }
                    },
                    onNoteClick = { noteDialogFor = exercise.id }
                )
            }
        }

        Divider()
        var overflowExpanded by remember { mutableStateOf(false) }
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
            ) {
                Text(text = stringResource(R.string.new_day))
            }
            Box {
                IconButton(onClick = { overflowExpanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = stringResource(R.string.more_actions)
                    )
                }
                DropdownMenu(
                    expanded = overflowExpanded,
                    onDismissRequest = { overflowExpanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(text = stringResource(R.string.secondary_reset_hint)) },
                        onClick = { overflowExpanded = false }
                    )
                }
            }
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseUiState,
    onToggleSet: (Int) -> Unit,
    onWeightClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onNoteClick: () -> Unit
) {
    val isCompleted = exercise.sets.all { it }
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val containerColor = when {
        isCompleted && isDarkTheme -> ExerciseCompletedDark
        isCompleted -> ExerciseCompletedLight
        else -> MaterialTheme.colorScheme.surface
    }
    val hasSettingsNote = exercise.hasSettings && !exercise.settingsNote.isNullOrBlank()
    val hasPersonalNote = !exercise.personalNote.isNullOrBlank()
    val weightText = stringResource(R.string.weight_label_template, exercise.selectedWeight)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = exercise.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                WeightChip(value = weightText, onClick = onWeightClick)
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onSettingsClick, enabled = hasSettingsNote) {
                    val iconAlpha = if (hasSettingsNote) 1f else 0.4f
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onNoteClick) {
                    val noteTint = if (hasPersonalNote) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                    }
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = null,
                        tint = noteTint
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                exercise.sets.forEachIndexed { index, isChecked ->
                    Checkbox(
                        checked = isChecked,
                        onCheckedChange = { onToggleSet(index) },
                        modifier = Modifier.size(32.dp)
                    )
                }

                if (isCompleted) {
                    Spacer(modifier = Modifier.weight(1f, fill = true))
                    Text(
                        text = stringResource(R.string.completed_badge),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.End,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WeightChip(value: String, onClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

@Composable
private fun WeightPickerDialog(
    exercise: ExerciseUiState,
    onDismiss: () -> Unit,
    onWeightSelected: (Int) -> Unit
) {
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
            LazyColumn(
                modifier = Modifier.heightIn(max = 320.dp),
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
        }
    )
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
            TextButton(onClick = { onSave(draftNote) }) {
                Text(text = stringResource(R.string.note_dialog_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.note_dialog_cancel))
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

@Preview(showBackground = true)
@Composable
private fun GymScreenPreview() {
    GymProgressTheme {
        GymScreen(
            exercises = GymViewModel.fallbackExercises(),
            onToggleSet = { _, _ -> },
            onWeightSelected = { _, _ -> },
            onResetDay = {},
            onPersonalNoteSaved = { _, _ -> }
        )
    }
}

private fun JSONArray.toIntList(): List<Int> = buildList {
    for (i in 0 until length()) {
        add(getInt(i))
    }
}
