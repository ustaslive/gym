package com.example.gymprogress

import android.app.Application
import android.os.Bundle
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gymprogress.ui.theme.ExerciseCompleted
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
    val sets: List<Boolean>,
    val hasSettings: Boolean
)

class GymViewModel(application: Application) : AndroidViewModel(application) {
    private val _exercises = mutableStateListOf<ExerciseUiState>()
    val exercises: List<ExerciseUiState> get() = _exercises

    init {
        val defaults = loadExercisesFromAssets() ?: fallbackExercises()
        _exercises.addAll(defaults)
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
                _exercises[index] = exercise.copy(selectedWeight = newWeight)
            }
        }
    }

    fun resetAllSets() {
        _exercises.replaceAll { exercise ->
            exercise.copy(sets = List(exercise.sets.size) { false })
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
            val options = obj.optJSONArray("weightOptions")?.toIntList().orEmpty()
            if (options.isEmpty()) continue
            val setsCount = obj.optInt("sets", 3).coerceAtLeast(1)
            val defaultWeight = obj.optInt("defaultWeight", options.first())
            items += ExerciseUiState(
                id = obj.getString("id"),
                name = obj.optString("label", obj.getString("id")),
                weightOptions = options,
                selectedWeight = if (defaultWeight in options) defaultWeight else options.first(),
                sets = List(setsCount) { false },
                hasSettings = obj.optBoolean("hasSettings", false)
            )
        }
        return items
    }

    companion object {
        private const val DEFAULT_ASSET = "exercises.json"

        internal fun fallbackExercises(): List<ExerciseUiState> = listOf(
            ExerciseUiState(
                id = "leg_press",
                name = "Leg Press",
                weightOptions = listOf(20, 22, 24, 26, 28, 30, 32, 34, 36, 38),
                selectedWeight = 22,
                sets = List(3) { false },
                hasSettings = true
            ),
            ExerciseUiState(
                id = "leg_extension",
                name = "Leg Extension",
                weightOptions = listOf(30, 32, 34, 36, 38, 40, 42, 44, 46, 48),
                selectedWeight = 42,
                sets = List(3) { false },
                hasSettings = true
            ),
            ExerciseUiState(
                id = "shoulder_press",
                name = "Shoulder Press",
                weightOptions = listOf(8, 10, 12, 14, 16, 18, 20, 22, 24, 26),
                selectedWeight = 12,
                sets = List(3) { false },
                hasSettings = false
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
        onResetDay = viewModel::resetAllSets
    )
}

@Composable
fun GymScreen(
    exercises: List<ExerciseUiState>,
    onToggleSet: (String, Int) -> Unit,
    onWeightSelected: (String, Int) -> Unit,
    onResetDay: () -> Unit
) {
    var weightDialogFor by remember { mutableStateOf<String?>(null) }
    val dialogExercise = exercises.firstOrNull { it.id == weightDialogFor }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
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
                    onSettingsClick = { /* TODO: open settings detail screen */ }
                )
            }
        }

        Divider()
        Button(
            onClick = onResetDay,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(text = stringResource(R.string.new_day))
        }
    }
}

@Composable
private fun ExerciseCard(
    exercise: ExerciseUiState,
    onToggleSet: (Int) -> Unit,
    onWeightClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    val isCompleted = exercise.sets.all { it }
    val containerColor = if (isCompleted) ExerciseCompleted else MaterialTheme.colorScheme.surface
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
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = weightText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                WeightChip(value = weightText, onClick = onWeightClick)
                Spacer(modifier = Modifier.width(4.dp))
                IconButton(onClick = onSettingsClick, enabled = exercise.hasSettings) {
                    val iconAlpha = if (exercise.hasSettings) 1f else 0.4f
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = iconAlpha)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
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
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
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
            onResetDay = {}
        )
    }
}

private fun JSONArray.toIntList(): List<Int> = buildList {
    for (i in 0 until length()) {
        add(getInt(i))
    }
}
