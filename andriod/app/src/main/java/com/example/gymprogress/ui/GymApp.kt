package com.example.gymprogress

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.text.Html
import android.widget.Toast
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.gymprogress.ui.theme.ActiveGlowBlue
import com.example.gymprogress.ui.theme.GymProgressTheme
import com.example.gymprogress.ui.theme.HighlightGreen
import com.example.gymprogress.ui.theme.PrimaryGreen
import com.example.gymprogress.ui.theme.RecommendedExerciseYellow
import com.example.gymprogress.ui.theme.SecondaryGreen
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val SHARE_SUBJECT_DATE_PATTERN = "yyyy-MM-dd"

@Composable
fun GymApp(viewModel: GymViewModel = viewModel()) {
    val exercises = viewModel.exercises
    val statusText = viewModel.statusText
    val generalNote = viewModel.generalNote
    val selectedDayType = viewModel.selectedDayType
    val newlyUnlockedAnchorId = viewModel.newlyUnlockedGroupAnchorId
    GymScreen(
        exercises = exercises,
        newlyUnlockedAnchorId = newlyUnlockedAnchorId,
        onNewGroupAnchorConsumed = viewModel::consumeNewlyUnlockedAnchor,
        onExerciseSelected = viewModel::markExerciseActive,
        onProgressTapped = viewModel::advanceProgress,
        onWeightSelected = viewModel::updateWeight,
        onResetDay = viewModel::resetAllSets,
        selectedDayType = selectedDayType,
        onDayTypeSelected = viewModel::selectDayType,
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
    newlyUnlockedAnchorId: String?,
    onNewGroupAnchorConsumed: () -> Unit,
    onExerciseSelected: (String) -> Unit,
    onProgressTapped: (String) -> Unit,
    onWeightSelected: (String, Int) -> Unit,
    onResetDay: () -> Unit,
    selectedDayType: WorkoutDayType,
    onDayTypeSelected: (WorkoutDayType) -> Unit,
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
    var detailsDialogFor by remember { mutableStateOf<String?>(null) }
    var generalNoteDialogVisible by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    val dialogExercise = exercises.firstOrNull { it.id == weightDialogFor && it.type == ExerciseType.WEIGHTS }
    val settingsDialogExercise = exercises.firstOrNull {
        it.id == settingsDialogFor && !it.settingsNote.isNullOrBlank()
    }
    val noteDialogExercise = exercises.firstOrNull { it.id == noteDialogFor }
    val detailsDialogExercise = exercises.firstOrNull {
        it.id == detailsDialogFor && it.detailSections.isNotEmpty()
    }
    val context = LocalContext.current

    LaunchedEffect(newlyUnlockedAnchorId, exercises) {
        val targetId = newlyUnlockedAnchorId ?: return@LaunchedEffect
        val targetIndex = exercises.indexOfFirst { it.id == targetId }
        if (targetIndex >= 0) {
            listState.animateScrollToItem(targetIndex)
        }
        onNewGroupAnchorConsumed()
    }

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

    if (detailsDialogExercise != null) {
        ExerciseDetailsDialog(
            exercise = detailsDialogExercise,
            onDismiss = { detailsDialogFor = null }
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
            verticalArrangement = Arrangement.spacedBy(12.dp),
            state = listState
        ) {
            items(exercises) { exercise ->
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
                    onInfoClick = {
                        if (exercise.detailSections.isNotEmpty()) {
                            detailsDialogFor = exercise.id
                        }
                    },
                    onNoteClick = { noteDialogFor = exercise.id }
                )
            }
        }

        HorizontalDivider()
        var dayTypeMenuExpanded by remember { mutableStateOf(false) }
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
        val selectedDayLabel = stringResource(dayTypeLabelRes(selectedDayType))
        val newDayLabel = stringResource(R.string.new_day_template, selectedDayLabel)
        val generalNoteTint = MaterialTheme.colorScheme.onSurfaceVariant
        val newDayWidthWeight = 1.28f
        val statusWidthWeight = 0.54f
        val bottomIconSize = 36.dp
        val splitNewDayShape = RoundedCornerShape(24.dp)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .weight(newDayWidthWeight)
                    .height(48.dp),
                shape = splitNewDayShape,
                color = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shadowElevation = 4.dp,
                tonalElevation = 1.dp,
                border = BorderStroke(width = 1.dp, color = Color.White.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(splitNewDayShape)
                        .background(newDayGradient),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(onClick = onResetDay)
                            .padding(horizontal = 16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = newDayLabel,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(44.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxHeight()
                                .padding(vertical = 9.dp)
                                .width(1.dp)
                                .background(Color.White.copy(alpha = 0.28f))
                        )
                        IconButton(
                            modifier = Modifier.fillMaxSize(),
                            onClick = { dayTypeMenuExpanded = true }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ArrowDropDown,
                                contentDescription = stringResource(R.string.day_type_menu_action),
                                tint = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                        DropdownMenu(
                            expanded = dayTypeMenuExpanded,
                            onDismissRequest = { dayTypeMenuExpanded = false }
                        ) {
                            WorkoutDayType.values().forEach { dayType ->
                                DropdownMenuItem(
                                    text = {
                                        Text(
                                            text = stringResource(dayTypeLabelRes(dayType)),
                                            fontWeight = if (dayType == selectedDayType) {
                                                FontWeight.SemiBold
                                            } else {
                                                FontWeight.Normal
                                            }
                                        )
                                    },
                                    onClick = {
                                        dayTypeMenuExpanded = false
                                        onDayTypeSelected(dayType)
                                    }
                                )
                            }
                        }
                    }
                }
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
                modifier = Modifier.size(bottomIconSize),
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
                    modifier = Modifier.size(bottomIconSize),
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
                                val chooserIntent = Intent.createChooser(shareIntent, chooserTitle).apply {
                                    if (context !is Activity) {
                                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    }
                                }
                                try {
                                    context.startActivity(chooserIntent)
                                } catch (_: ActivityNotFoundException) {
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
    onInfoClick: () -> Unit,
    onNoteClick: () -> Unit
) {
    if (exercise.type == ExerciseType.PLACEHOLDER) {
        PlaceholderExerciseCard(exercise = exercise)
        return
    }

    val isCompleted = exercise.totalSets > 0 && exercise.completedSets >= exercise.totalSets
    val isActivity = exercise.type == ExerciseType.ACTIVITY
    val isGuided = exercise.isGuided()
    val isWeights = exercise.type == ExerciseType.WEIGHTS
    val hasSettingsNote = exercise.hasSettings && !exercise.settingsNote.isNullOrBlank() && !isGuided
    val hasDetailSections = exercise.detailSections.isNotEmpty()
    val hasPersonalNote = !exercise.personalNote.isNullOrBlank()
    val isActive = exercise.isActive
    val isActiveAndIncomplete = isActive && !isCompleted
    val isCompletedActive = isCompleted && isActive
    val activeGlowColor = ActiveGlowBlue
    val weightLabel = exercise.weightLabel
        ?.takeIf { it.isNotBlank() && exercise.weightOptions.size <= 1 }
    val weightOptionLabelTemplate = exercise.weightOptionLabelTemplate?.takeIf { it.isNotBlank() }
    val weightText = if (isWeights) {
        weightLabel
            ?: weightOptionLabelTemplate?.let { template ->
                String.format(Locale.getDefault(), template, exercise.selectedWeight)
            }
            ?: stringResource(R.string.weight_label_template, exercise.selectedWeight)
    } else {
        null
    }
    val isWeightSelectable = isWeights && exercise.weightOptions.size > 1
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
    val titleColor = when {
        isCompleted -> contentColor
        exercise.isRecommendedNext -> RecommendedExerciseYellow
        else -> contentColor
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
        val contentVerticalPadding = if (isActive && isWeights) 6.dp else 12.dp
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = contentVerticalPadding)) {
            if (isGuided) {
                GuidedExerciseCardContent(
                    exercise = exercise,
                    titleStyle = titleStyle,
                    titleColor = titleColor,
                    checkboxColor = chipTextColor,
                    isCompleted = isCompleted,
                    onSelect = onSelect,
                    onToggle = onProgressClick,
                    onInfoClick = if (hasDetailSections) onInfoClick else null,
                    infoIconTint = iconTintBase,
                    onNoteClick = onNoteClick,
                    noteIcon = noteIcon,
                    noteIconTint = noteIconTint
                )
            } else {
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
                            color = titleColor
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
                    } else if (isWeights) {
                        Spacer(modifier = Modifier.width(12.dp))
                        ProgressCounter(
                            value = exercise.completedSets,
                            onClick = onProgressClick,
                            isActive = isActive,
                            textColor = counterTextColor,
                            backgroundColor = counterBackgroundColor,
                            borderColor = chipBorderColor
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        WeightChip(
                            value = weightText.orEmpty(),
                            onClick = if (isWeightSelectable) onWeightClick else null,
                            textColor = chipTextColor,
                            backgroundColor = chipBackgroundColor,
                            borderColor = chipBorderColor
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    IconButton(onClick = onSettingsClick, enabled = hasSettingsNote) {
                        val iconAlpha = if (hasSettingsNote) 1f else 0.4f
                        Icon(
                            imageVector = Icons.Default.Info,
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
}

@Composable
private fun PlaceholderExerciseCard(exercise: ExerciseUiState) {
    val shape = RoundedCornerShape(16.dp)
    val surfaceBase = MaterialTheme.colorScheme.surface
    val raisedTop = surfaceBase.blendWith(Color.White, 0.06f)
    val raisedBottom = surfaceBase.blendWith(Color.Black, 0.12f)
    val raisedBrush = Brush.verticalGradient(listOf(raisedTop, raisedBottom))
    val outlineColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val supportingColor = MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape = shape, clip = false)
            .clip(shape)
            .background(raisedBrush)
            .border(1.dp, outlineColor, shape)
            .insetEdges(
                lightColor = Color.White.copy(alpha = 0.1f),
                darkColor = Color.Black.copy(alpha = 0.22f),
                strokeWidth = 1.dp,
                inverted = false
            )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = exercise.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface
            )
            exercise.supportingText?.takeIf { it.isNotBlank() }?.let { supportingText ->
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = supportingColor
                )
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
private fun GuidedExerciseCardContent(
    exercise: ExerciseUiState,
    titleStyle: TextStyle,
    titleColor: Color,
    checkboxColor: Color,
    isCompleted: Boolean,
    onSelect: () -> Unit,
    onToggle: () -> Unit,
    onInfoClick: (() -> Unit)?,
    infoIconTint: Color,
    onNoteClick: () -> Unit,
    noteIcon: ImageVector,
    noteIconTint: Color
) {
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
                color = titleColor
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Checkbox(
            checked = isCompleted,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = checkboxColor.copy(alpha = 0.32f),
                uncheckedColor = checkboxColor.copy(alpha = 0.32f),
                checkmarkColor = checkboxColor.copy(alpha = 0.9f),
                disabledCheckedColor = checkboxColor.copy(alpha = 0.2f),
                disabledUncheckedColor = checkboxColor.copy(alpha = 0.2f)
            )
        )
        if (onInfoClick != null) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onInfoClick) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.cooldown_details_action),
                    tint = infoIconTint
                )
            }
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

@Composable
private fun ProgressCounter(
    value: Int,
    onClick: () -> Unit,
    isActive: Boolean,
    textColor: Color,
    backgroundColor: Color,
    borderColor: Color
) {
    val shape = if (isActive) CircleShape else RoundedCornerShape(12.dp)
    val counterModifier = if (isActive) {
        Modifier
            .size(60.dp)
            .clip(shape)
            .clickable(onClick = onClick)
    } else {
        Modifier
            .clip(shape)
            .clickable(onClick = onClick)
    }
    Surface(
        shape = shape,
        color = backgroundColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = counterModifier
    ) {
        Box(
            modifier = if (isActive) Modifier.fillMaxSize() else Modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = value.toString(),
                style = if (isActive) {
                    MaterialTheme.typography.headlineSmall
                } else {
                    MaterialTheme.typography.titleMedium
                },
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                color = textColor,
                modifier = if (isActive) {
                    Modifier
                } else {
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                }
            )
        }
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
                                    if (isSelected) {
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                                    } else {
                                        MaterialTheme.colorScheme.surface
                                    }
                                )
                                .padding(vertical = 10.dp, horizontal = 12.dp)
                        ) {
                            Text(
                                text = exercise.weightOptionLabelTemplate
                                    ?.takeIf { it.isNotBlank() }
                                    ?.let { template -> String.format(Locale.getDefault(), template, weight) }
                                    ?: stringResource(R.string.weight_label_template, weight),
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
    val scrollbarState by remember(state) {
        derivedStateOf {
            val layoutInfo = state.layoutInfo
            val visibleItems = layoutInfo.visibleItemsInfo
            val totalItems = layoutInfo.totalItemsCount
            if (visibleItems.isEmpty() || totalItems <= visibleItems.size) {
                null
            } else {
                WeightScrollbarState(
                    visibleItemCount = visibleItems.size,
                    totalItemCount = totalItems,
                    firstVisibleItemIndex = state.firstVisibleItemIndex,
                    firstVisibleItemScrollOffset = state.firstVisibleItemScrollOffset,
                    firstVisibleItemSize = visibleItems.first().size.takeIf { it > 0 } ?: 1
                )
            }
        }
    }
    val currentScrollbarState = scrollbarState ?: return

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
        val visibleFraction = (
            currentScrollbarState.visibleItemCount.toFloat() /
                currentScrollbarState.totalItemCount.toFloat()
            ).coerceIn(0f, 1f)
        val thumbHeight = (trackHeight * visibleFraction).coerceAtLeast(minThumbPx)
        val scrollableItems =
            (currentScrollbarState.totalItemCount - currentScrollbarState.visibleItemCount).coerceAtLeast(1)
        val offsetFraction = (
            currentScrollbarState.firstVisibleItemScrollOffset /
                currentScrollbarState.firstVisibleItemSize.toFloat()
            ).coerceIn(0f, 1f)
        val scrollFraction = (
            (currentScrollbarState.firstVisibleItemIndex + offsetFraction) /
                scrollableItems.toFloat()
            ).coerceIn(0f, 1f)
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

private data class WeightScrollbarState(
    val visibleItemCount: Int,
    val totalItemCount: Int,
    val firstVisibleItemIndex: Int,
    val firstVisibleItemScrollOffset: Int,
    val firstVisibleItemSize: Int
)

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
private fun ExerciseDetailsDialog(
    exercise: ExerciseUiState,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(R.string.cooldown_details_close))
            }
        },
        title = {
            Text(text = stringResource(R.string.cooldown_details_title, exercise.name))
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                exercise.detailSections.forEach { section ->
                    Text(
                        text = section,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    )
}

private object VisibleSpacesTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val transformed = buildString(text.text.length) {
            text.text.forEach { character ->
                append(if (character == ' ') '·' else character)
            }
        }
        return TransformedText(AnnotatedString(transformed), OffsetMapping.Identity)
    }
}

@Composable
private fun NoteEditorDialog(
    exercise: ExerciseUiState,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    var draftNote by remember(exercise.id, exercise.personalNote) {
        mutableStateOf(
            TextFieldValue(
                text = exercise.personalNote.orEmpty(),
                selection = TextRange.Zero
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val editorMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.72f).coerceAtLeast(280.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                draftNote = TextFieldValue(text = "", selection = TextRange.Zero)
                                focusRequester.requestFocus()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.note_dialog_clear),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            draftNote = draftNote.copy(selection = TextRange(draftNote.text.length))
                            focusRequester.requestFocus()
                        }
                    ) {
                        Text(text = stringResource(R.string.note_dialog_cursor_end))
                    }
                }
                TextButton(onClick = { onSave(draftNote.text) }) {
                    Text(text = stringResource(R.string.note_dialog_save))
                }
            }
        },
        title = {
            Text(text = exercise.name)
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = editorMaxHeight)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    minLines = 10,
                    maxLines = Int.MAX_VALUE,
                    visualTransformation = VisibleSpacesTransformation
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
    var draftNote by remember(note) {
        mutableStateOf(
            TextFieldValue(
                text = note,
                selection = TextRange.Zero
            )
        )
    }
    val focusRequester = remember { FocusRequester() }
    val editorMaxHeight = (LocalConfiguration.current.screenHeightDp.dp * 0.72f).coerceAtLeast(280.dp)

    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.fillMaxWidth(0.96f),
        properties = DialogProperties(usePlatformDefaultWidth = false),
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .clickable {
                                draftNote = TextFieldValue(text = "", selection = TextRange.Zero)
                                focusRequester.requestFocus()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = stringResource(R.string.note_dialog_clear),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(
                        onClick = {
                            draftNote = draftNote.copy(selection = TextRange(draftNote.text.length))
                            focusRequester.requestFocus()
                        }
                    ) {
                        Text(text = stringResource(R.string.note_dialog_cursor_end))
                    }
                }
                TextButton(onClick = { onSave(draftNote.text) }) {
                    Text(text = stringResource(R.string.note_dialog_save))
                }
            }
        },
        title = {
            Text(text = stringResource(R.string.general_note_title))
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
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
                        .fillMaxWidth()
                        .heightIn(min = 240.dp, max = editorMaxHeight)
                        .focusRequester(focusRequester),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    minLines = 10,
                    maxLines = Int.MAX_VALUE,
                    visualTransformation = VisibleSpacesTransformation
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
            exercises = GymViewModel.builtInExercises(),
            newlyUnlockedAnchorId = null,
            onNewGroupAnchorConsumed = {},
            onExerciseSelected = {},
            onProgressTapped = { _ -> },
            onWeightSelected = { _, _ -> },
            onResetDay = {},
            selectedDayType = WorkoutDayType.GENERAL,
            onDayTypeSelected = { _ -> },
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

private fun dayTypeLabelRes(dayType: WorkoutDayType): Int = when (dayType) {
    WorkoutDayType.GENERAL -> R.string.day_type_general
    WorkoutDayType.HANDS -> R.string.day_type_hands
    WorkoutDayType.LEGS -> R.string.day_type_legs
}

private fun buildShareNotesSubject(context: Context): String {
    val currentDate = SimpleDateFormat(SHARE_SUBJECT_DATE_PATTERN, Locale.getDefault()).format(Date())
    val baseSubject = context.getString(R.string.share_notes_subject)
    return "$currentDate - $baseSubject"
}
