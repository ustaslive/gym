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

    private var catalogExercises: List<ExerciseUiState> = emptyList()
    private var activeStatusExerciseId: String? = null
    private var activeExerciseId: String? = null
    private var activeRestTimerEndEpochMillis: Long? = null
    private var initialGroup: ExerciseGroup? = GROUP_SEQUENCE.firstOrNull()
    private var currentDayType by mutableStateOf(WorkoutDayType.GENERAL)

    var selectedDayType by mutableStateOf(WorkoutDayType.GENERAL)
        private set

    var statusText by mutableStateOf<String?>(null)
        private set

    var generalNote by mutableStateOf<String?>(null)
        private set

    var newlyUnlockedGroupAnchorId by mutableStateOf<String?>(null)
        private set


    init {
        generalNote = loadGeneralNote()
        catalogExercises = builtInExercises()
        cleanupPersistedWeights()
        initialGroup = GROUP_SEQUENCE.firstOrNull { group -> catalogExercises.any { it.group == group } } ?: initialGroup
        val savedSession = loadWorkoutSession()
        currentDayType = savedSession?.currentDayType ?: WorkoutDayType.GENERAL
        selectedDayType = savedSession?.selectedDayType ?: currentDayType
        val baseExercises = buildExercisesForDayType(currentDayType)
        defaultOrder.clear()
        defaultOrder.addAll(baseExercises.map { it.id })
        val restoredSession = restoreWorkoutSession(
            baseExercises = baseExercises,
            snapshot = savedSession,
            defaultOrder = defaultOrder,
            nowEpochMillis = System.currentTimeMillis()
        )
        currentDayType = restoredSession.currentDayType
        selectedDayType = restoredSession.selectedDayType
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
            if (!exercise.isUnlocked || exercise.type == ExerciseType.PLACEHOLDER) {
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
                val persistedWeight = newWeight.takeUnless { it == exercise.defaultWeight }
                _exercises[index] = exercise.copy(
                    selectedWeight = newWeight,
                    persistedWeight = persistedWeight
                )
                val editor = weightsPrefs.edit()
                if (persistedWeight == null) {
                    editor.remove(exerciseId)
                } else {
                    editor.putInt(exerciseId, newWeight)
                }
                editor.apply()
                markExerciseActive(exerciseId)
            }
        }
    }

    fun selectDayType(dayType: WorkoutDayType) {
        if (selectedDayType == dayType) {
            return
        }
        selectedDayType = dayType
        persistWorkoutSessionState()
    }

    fun resetAllSets() {
        applyNewDayType(selectedDayType)
    }

    fun performFullReset() {
        notesPrefs.edit().clear().apply()
        generalNote = null
        weightsPrefs.edit().clear().apply()
        applyNewDayType(currentDayType)
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
        if (!target.isUnlocked || target.isCompleted() || target.type == ExerciseType.PLACEHOLDER) {
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
            ExerciseType.COOLDOWN,
            ExerciseType.PLACEHOLDER -> null
        }
        return if (metadata != null) "$baseName$metadata" else baseName
    }

    private fun buildWeightShareMetadata(exercise: ExerciseUiState, app: Application): String? {
        val defaultWeight = exercise.defaultWeight.takeIf { it > 0 }
        val selectedWeight = exercise.selectedWeight.takeIf { it > 0 }
        if (defaultWeight == null && selectedWeight == null) return null

        val labelOverride = exercise.weightLabel
            ?.takeIf { it.isNotBlank() && exercise.weightOptions.size <= 1 }
        val labelTemplate = exercise.weightOptionLabelTemplate?.takeIf { it.isNotBlank() }
        val parts = listOfNotNull(
            defaultWeight?.let { weight -> "default=${formatShareWeight(app, weight, labelOverride, labelTemplate)}" },
            selectedWeight?.let { weight -> "selected=${formatShareWeight(app, weight, labelOverride, labelTemplate)}" }
        )
        if (parts.isEmpty()) return null
        return parts.joinToString(prefix = "(", postfix = ")", separator = ",")
    }

    private fun buildActivityShareMetadata(exercise: ExerciseUiState): String? =
        exercise.level?.takeIf { it > 0 }?.let { level -> "(level=$level)" }

    private fun formatShareWeight(
        app: Application,
        weight: Int,
        labelOverride: String? = null,
        labelTemplate: String? = null
    ): String = (
        labelOverride
            ?: labelTemplate?.let { template -> String.format(Locale.getDefault(), template, weight) }
            ?: app.getString(R.string.weight_label_template, weight)
        ).replace(" ", "")

    private fun escapeNoteToHtml(note: String): String =
        note.split('\n').joinToString("<br>") { line ->
            Html.escapeHtml(line)
        }

    private fun shouldUnlockInitially(exercise: ExerciseUiState): Boolean =
        if (exercise.type == ExerciseType.PLACEHOLDER) {
            true
        } else {
            initialGroup?.let { exercise.group == it } ?: true
        }

    private fun isGroupUnlocked(group: ExerciseGroup): Boolean {
        val groupExercises = _exercises.filter { it.group == group }
        if (groupExercises.isEmpty()) {
            return true
        }
        return groupExercises.any { it.isUnlocked }
    }

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
        val activeGroups = GROUP_SEQUENCE.filter { group ->
            _exercises.any { exercise -> exercise.group == group }
        }
        if (activeGroups.isEmpty()) return
        for ((index, group) in activeGroups.withIndex()) {
            if (isGroupUnlocked(group)) {
                continue
            }
            val previousGroup = activeGroups.getOrNull(index - 1)
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

    private fun buildExercisesForDayType(dayType: WorkoutDayType): List<ExerciseUiState> = when (dayType) {
        WorkoutDayType.GENERAL -> buildPreparedExercises(catalogExercises)
        WorkoutDayType.HANDS -> buildPreparedExercises(buildHandsDayTemplates())
        WorkoutDayType.LEGS -> listOf(buildPlaceholderExercise(dayType))
    }

    private fun buildPreparedExercises(templates: List<ExerciseUiState>): List<ExerciseUiState> {
        val savedNotes = loadSavedNotes()
        val savedWeights = loadSavedWeights()
        return templates.map { exercise ->
            val note = savedNotes[exercise.id]
            val persistedWeight = savedWeights[exercise.id]?.takeIf {
                it in exercise.weightOptions && it != exercise.defaultWeight
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
                isUnlocked = shouldUnlockInitially(exercise)
            )
        }
    }

    private fun buildHandsDayTemplates(): List<ExerciseUiState> = listOf(
        handsDayGuidedExercise(
            id = "hands_warmup_shoulder_circles",
            name = "Круги плечами",
            group = ExerciseGroup.WARM_UP,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Сделай 15 кругов вперед.",
                "Сделай 15 кругов назад.",
                "Двигайся мягко, без рывков и без боли."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_warmup_shoulder_pendulum",
            name = "Плечевой маятник",
            group = ExerciseGroup.WARM_UP,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Подними правое плечо вверх и одновременно опусти левое вниз.",
                "Потом поменяй стороны.",
                "Сделай 20 чередований в спокойном темпе."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_warmup_wrist_circles",
            name = "Круги запястьями",
            group = ExerciseGroup.WARM_UP,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Сделай по 10 кругов в каждую сторону.",
                "Локти держи расслабленными.",
                "Подготовь кисти без резких движений."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_warmup_scissors",
            name = "Ножницы",
            group = ExerciseGroup.WARM_UP,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Сядь на скамью. Разведи обе прямые руки широко в стороны на уровне плеч, затем одновременно сведи их перед собой накрест.",
                "Сделай 40 таких скрещиваний. При каждом движении чередуй руки: один раз правая рука идет поверх левой, следующий раз — левая поверх правой.",
                "Двигайся динамично, без пауз."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_warmup_vertical_swings",
            name = "Вертикальные махи",
            group = ExerciseGroup.WARM_UP,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Сядь на скамью. Вытяни прямые руки перед собой. Подними правую руку вверх до уровня уха, а левую одновременно опусти вниз и чуть заведи за спину.",
                "Сделай 40 махов (по 20 раз на каждую руку), меняя руки в динамичном темпе."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_warmup_wide_arm_circles",
            name = "Широкие круги прямыми руками",
            group = ExerciseGroup.WARM_UP,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Сядь на скамью. Опусти руки вниз по бокам.",
                "Сделай 20 максимально широких кругов обеими руками вперед, затем 20 кругов назад. Обе руки идут одновременно и синхронно в одном направлении.",
                "Движение должно быть амплитудным, но без боли."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_warmup_dynamic_elbow_flexion",
            name = "Разминка бицепса",
            group = ExerciseGroup.WARM_UP,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Выполни 20 обычных сгибаний на бицепс в спокойном темпе с гантелями в 2 кг."
            )
        ),
        handsDayWeightExercise(
            id = "hands_chest_press",
            name = "Chest Press",
            weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
            defaultWeight = 21,
            restBetweenSeconds = 70,
            settingsNote = """
                Отрегулируйте высоту сиденья на уровень 4 и убедитесь, что руки выстроены ровно.
            """.trimIndent()
        ),
        handsDayWeightExercise(
            id = "hands_pec_deck",
            name = "Pec Deck / Butterfly",
            weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
            defaultWeight = 28,
            restBetweenSeconds = 45,
            settingsNote = PEC_DECK_SETTINGS_NOTE
        ),
        handsDayWeightExercise(
            id = "hands_dumbbell_chest_press",
            name = "Dumbbell Chest Press",
            weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
            defaultWeight = 7,
            restBetweenSeconds = 70,
            settingsNote = """
                Ляг на горизонтальную скамью и плотно прижми к ней спину. Ступни поставь на скамью, чтобы убрать прогиб в пояснице и исключить помощь ногами. Возьми легкие гантели и держи их перед грудью, постоянно прижимая друг к другу.

                На протяжении всего подхода не ослабляй давление между гантелями. Сильно сжимай их друг в друга ладонями и сознательно напрягай грудные мышцы в каждом повторении.

                Медленно опускай гантели к груди под контролем, сохраняя их плотный контакт. Затем так же медленно выжимай вверх, не теряя давления между гантелями. В верхней точке не бросай напряжение и не блокируй локти полностью.

                Не разводи гантели в стороны, не расцепляй их и не раскачивай корпус. Вес должен быть таким, чтобы ты мог держать постоянное сжатие гантелей и чувствовать именно работу груди, а не рук и плеч.
            """.trimIndent()
        ),
        handsDayWeightExercise(
            id = "hands_incline_db_press",
            name = "Incline DB Press",
            weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
            defaultWeight = 6,
            restBetweenSeconds = 70,
            settingsNote = """
                Техника:
                Установи спинку скамьи под углом 30–45° к горизонтали. Сядь, затем ляг на спинку и плотно прижми к ней спину. Ноги стоят на полу устойчиво.

                Держи гантели над верхней частью груди. Локти слегка разведены в стороны примерно на 30–60° от корпуса, предплечья направлены почти вертикально. Запястья держи прямыми.

                Опускай гантели по сторонам верхней части груди под контролем. Затем выжимай их вверх по той же траектории. В верхней точке гантели находятся над плечами, локти почти выпрямлены без жёсткой блокировки. Держи гантели стабильно и не позволяй им расходиться в стороны.
            """.trimIndent()
        ),
        ExerciseUiState(
            id = "hands_seated_row",
            name = "Seated Row",
            type = ExerciseType.WEIGHTS,
            group = ExerciseGroup.MAIN,
            weightOptions = listOf(60),
            selectedWeight = 60,
            defaultWeight = 60,
            weightLabel = "2x30кг",
            restBetweenSeconds = 45,
            restFinalSeconds = 120,
            totalSets = 3,
            completedSets = 0,
            hasSettings = false
        ),
        handsDayWeightExercise(
            id = "hands_reverse_pec_deck",
            name = "Reverse Pec Deck",
            weightOptions = listOf(7, 14, 21, 28, 35, 42, 49, 56, 63, 70, 77, 84, 91),
            defaultWeight = 28,
            restBetweenSeconds = 45,
            settingsNote = """
                Сядь в тренажер лицом к спинке. Грудь плотно прижата, ноги расслаблены.

                Разводи рукояти назад за счет задней дельты и мышц спины, сводя лопатки. Плавно возвращай в исходное положение.
            """.trimIndent()
        ),
        handsDayWeightExercise(
            id = "hands_biceps_machine",
            name = "Biceps Machine",
            weightOptions = (1..12).toList(),
            defaultWeight = 9,
            restBetweenSeconds = 45,
            weightOptionLabelTemplate = "%d",
            settingsNote = """
                Сядь, упрись грудью и локтями в подушку тренажера. Сгибай руки плавно, не забрасывая вес всем телом.
            """.trimIndent()
        ),
        handsDayWeightExercise(
            id = "hands_dumbbell_biceps",
            name = "Dumbbell Biceps",
            weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
            defaultWeight = 7,
            restBetweenSeconds = 45,
            settingsNote = """
                Подготовка: установи спинку скамьи вертикально, примерно на 90 градусов. Сядь ровно и плотно прижми спину. Руки с гантелями опущены вниз по бокам корпуса.

                Выполнение: держи ладони развернутыми вперед и вверх, то есть обычным хватом на бицепс. Сгибай руки к плечам, поднимая гантели за счет локтей. Локти держи близко к корпусу и не уводи вперед. В верхней точке бицепс напряжен, затем плавно опускай гантели вниз под контролем.
            """.trimIndent()
        ),
        handsDayWeightExercise(
            id = "hands_seated_hammer_curl",
            name = "Hammer Curl",
            weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
            defaultWeight = 6,
            restBetweenSeconds = 45,
            settingsNote = """
                Подготовка: установи спинку скамьи вертикально, примерно на 90 градусов. Сядь и прижми спину.
                Выполнение: держи гантели нейтральным хватом, ладони смотрят друг на друга. Сгибай руки к плечам. Упражнение развивает предплечья и мышцу под бицепсом.
            """.trimIndent()
        ),
        handsDayWeightExercise(
            id = "hands_incline_db_biceps",
            name = "Incline DB Biceps",
            weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
            defaultWeight = 6,
            restBetweenSeconds = 45,
            settingsNote = """
                Подготовка: установи скамью под углом 45-60 градусов к горизонтали. Сядь и откинься на спинку. Руки с гантелями свободно свисают вниз. Плечо каждой руки всё время направлено вертикально вниз и не уходит вперед.
                Выполнение: сгибай руки именно в локтях, поднимая гантели вверх. Ладони направлены вперед и вверх, то есть используй обычный хват на бицепс. Не разворачивай плечи и не подавай локти вперед. В верхней точке бицепс напряжен, затем плавно опускай гантели вниз до полного контролируемого растяжения.
            """.trimIndent()
        ),
        handsDayWeightExercise(
            id = "hands_triceps_machine",
            name = "Triceps Machine",
            weightOptions = listOf(20, 25, 30, 35, 40, 45, 50, 55, 60),
            defaultWeight = 25,
            restBetweenSeconds = 60,
            weightOptionLabelTemplate = "2x%dкг",
            settingsNote = """
                Отрегулируй сиденье так, чтобы в верхней позиции локти были немного позади корпуса, а плечи оставались опущенными. Возьмись за рукояти и зафиксируй ноги под валиком, спина прямая. В стартовой точке локти согнуты примерно под 90°, предплечья почти вертикальны.

                Разгибай руки вниз, выжимая рукояти за счёт работы трицепса. Локти держи близко к корпусу, корпусом не помогай и плечи не поднимай. Внизу почти полностью выпрями руки без жёсткой блокировки в локтях, затем плавно верни рукояти вверх под контролем.
            """.trimIndent()
        ),
        handsDayWeightExercise(
            id = "hands_dumbbell_french_press",
            name = "Dumbbell French Press",
            weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
            defaultWeight = 6,
            restBetweenSeconds = 60,
            settingsNote = """
                Сядь на скамью без поднятой спинки, держи спину ровной. Возьми одну гантель двумя руками, обхвати ладонями верхний блин и подними её над головой.

                Медленно опускай гантель за голову, сгибая локти. Верхняя часть рук остаётся почти вертикальной и неподвижной, локти направлены вверх и не расходятся сильно в стороны. Опускай гантель достаточно глубоко, но контролируй траекторию, чтобы она не задевала голову.

                Разгибай руки вверх до почти полного выпрямления и снова плавно опускай гантель. Двигай только предплечьями, корпус не раскачивай. Если появляется дискомфорт в спине, уменьши вес.
            """.trimIndent()
        ),
        handsDayWeightExercise(
            id = "hands_lying_db_triceps",
            name = "Lying DB Triceps",
            weightOptions = listOf(3, 4, 5, 6, 7, 8, 9, 10),
            defaultWeight = 5,
            restBetweenSeconds = 45,
            settingsNote = """
                Подготовка: ляг на горизонтальную скамью. Ступни поставь на скамью для защиты поясницы и отключения ног. Подними руки с гантелями вверх перед собой.

                Положение рук: верхняя часть руки должна быть почти вертикальной, но не обязательно строго под 90° к полу. Можно держать плечо с небольшим наклоном назад или немного в стороны, если так движение получается свободнее. Локти не разводи широко, но и не своди слишком узко. Подбери такую ширину положения рук, чтобы гантели свободно проходили по бокам головы и не задевали ничего по траектории.

                Выполнение: сгибай руки в локтях, опуская гантели по бокам от головы. Движение происходит в локтях, корпус и плечи не раскачивай. Опускай гантели настолько глубоко, насколько можешь под контролем и без дискомфорта, затем разгибай руки обратно вверх почти до полного выпрямления.
            """.trimIndent()
        ),
        ExerciseUiState(
            id = "hands_upper_body_only_rowing",
            name = "Upper Body Only Rowing",
            type = ExerciseType.ACTIVITY,
            group = ExerciseGroup.CARDIO,
            mode = "rowing",
            durationMinutes = 15,
            level = 3,
            weightOptions = emptyList(),
            selectedWeight = 0,
            defaultWeight = 0,
            restBetweenSeconds = 0,
            restFinalSeconds = 120,
            totalSets = 1,
            completedSets = 0,
            hasSettings = true,
            settingsNote = """
                Гребной тренажер без включения ног. Это упражнение дает монотонную кардио-нагрузку в целевой зоне пульса без движения в коленных суставах.

                Нагрузка: сопротивление 3-4. Время: 10-15 минут или до достижения нужного объема кардио. Целевая зона пульса: 130-140 bpm.

                Подготовка: сядь на сиденье тренажера и зафиксируй стопы в ремешках. Выпрями ноги так, чтобы коленям было комфортно. Можно оставить их слегка согнутыми, если полное выпрямление провоцирует заклинивание. Зафиксируй ноги: на протяжении всего упражнения сиденье не должно ездить по рельсе.

                Выполнение:
                1. Слегка наклони корпус вперед от бедра, держи спину прямой, а руки вытянутыми к маховику.
                2. Начни тягу: сначала немного отклони корпус назад до угла примерно 100-110 градусов.
                3. Затем мощно, но плавно потяни рукоять к низу груди или к животу, сводя лопатки вместе.
                4. Вернись в исходное положение в обратном порядке: сначала выпрями руки, затем снова наклони корпус вперед.

                Ошибки: не сгибай и не разгибай колени, не округляй поясницу при наклоне вперед и не отклоняй спину слишком далеко назад в конце тяги.
            """.trimIndent()
        ),
        handsDayGuidedExercise(
            id = "hands_cooldown_chest_stretch",
            name = "Растяжка груди",
            group = ExerciseGroup.COOLDOWN,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Встань боком к стене.",
                "Прямую руку оставь на стене чуть позади линии корпуса.",
                "Плавно разворачивай грудную клетку от руки до ощущения растяжения.",
                "Удерживай 20-30 секунд (5 вдохов-выдохов) на каждую сторону."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_cooldown_shoulder_stretch",
            name = "Растяжка плеч",
            group = ExerciseGroup.COOLDOWN,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Прямую руку прижми к груди другой рукой.",
                "Не поднимай плечо к уху.",
                "Удерживай 20-30 секунд (5 вдохов-выдохов) на каждую сторону."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_cooldown_triceps_stretch",
            name = "Растяжка трицепса",
            group = ExerciseGroup.COOLDOWN,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Сядь на скамью. Подними одну руку вверх, согни в локте и заведи ладонь за голову (между лопаток).",
                "Другой рукой мягко потяни за локоть в сторону головы, усиливая растяжение.",
                "Удерживай на 20-30 медленных счетов на каждую руку."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_cooldown_biceps_forearms_stretch",
            name = "Растяжка бицепса и предплечий",
            group = ExerciseGroup.COOLDOWN,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Сядь на скамью. Вытяни прямую руку перед собой ладонью вверх.",
                "Другой рукой потяни пальцы вытянутой руки вниз и на себя до натяжения от запястья до локтя.",
                "Удерживай на 20-30 медленных счетов на каждую руку."
            )
        ),
        handsDayGuidedExercise(
            id = "hands_cooldown_wall_hang",
            name = "Вис у стенки",
            group = ExerciseGroup.COOLDOWN,
            restBetweenSeconds = 0,
            instructions = listOf(
                "Используй этот вариант только если он не вызывает дискомфорта в пояснице и не требует спрыгивания на ноги.",
                "Плавно потянись и повиси 20-30 секунд (5 вдохов-выдохов).",
                "Прекрати сразу, если появляется неприятное ощущение в пояснице или седалищном нерве."
            )
        )
    )

    private fun handsDayWeightExercise(
        id: String,
        name: String,
        weightOptions: List<Int>,
        defaultWeight: Int,
        restBetweenSeconds: Int = 45,
        weightOptionLabelTemplate: String? = null,
        settingsNote: String
    ): ExerciseUiState = ExerciseUiState(
        id = id,
        name = name,
        type = ExerciseType.WEIGHTS,
        group = ExerciseGroup.MAIN,
        weightOptions = weightOptions,
        selectedWeight = defaultWeight,
        defaultWeight = defaultWeight,
        weightOptionLabelTemplate = weightOptionLabelTemplate,
        restBetweenSeconds = restBetweenSeconds,
        restFinalSeconds = 120,
        totalSets = 3,
        completedSets = 0,
        hasSettings = true,
        settingsNote = settingsNote
    )

    private fun handsDayGuidedExercise(
        id: String,
        name: String,
        group: ExerciseGroup,
        restBetweenSeconds: Int,
        instructions: List<String>
    ): ExerciseUiState = ExerciseUiState(
        id = id,
        name = name,
        type = ExerciseType.COOLDOWN,
        group = group,
        weightOptions = emptyList(),
        selectedWeight = 0,
        defaultWeight = 0,
        restBetweenSeconds = restBetweenSeconds,
        restFinalSeconds = 0,
        totalSets = 1,
        completedSets = 0,
        hasSettings = false,
        detailSections = instructions
    )

    private fun buildPlaceholderExercise(dayType: WorkoutDayType): ExerciseUiState {
        val app = getApplication<Application>()
        val (id, titleRes, messageRes) = when (dayType) {
            WorkoutDayType.HANDS -> Triple(
                "hands_placeholder",
                R.string.day_placeholder_hands_title,
                R.string.day_placeholder_hands_text
            )
            WorkoutDayType.LEGS -> Triple(
                "legs_placeholder",
                R.string.day_placeholder_legs_title,
                R.string.day_placeholder_legs_text
            )
            WorkoutDayType.GENERAL -> error("General day does not use a placeholder card.")
        }
        return ExerciseUiState(
            id = id,
            name = app.getString(titleRes),
            type = ExerciseType.PLACEHOLDER,
            group = ExerciseGroup.MAIN,
            weightOptions = emptyList(),
            selectedWeight = 0,
            defaultWeight = 0,
            restBetweenSeconds = 0,
            restFinalSeconds = 0,
            totalSets = 0,
            completedSets = 0,
            hasSettings = false,
            isUnlocked = true,
            supportingText = app.getString(messageRes)
        )
    }

    private fun applyNewDayType(dayType: WorkoutDayType) {
        restTimers.values.forEach { it.cancel() }
        restTimers.clear()
        RestTimerSoundService.stop(getApplication())
        activeRestTimerEndEpochMillis = null
        activeStatusExerciseId = null
        updateActiveSelection(null)
        statusText = null
        newlyUnlockedGroupAnchorId = null
        currentDayType = dayType
        val resetExercises = buildExercisesForDayType(dayType)
        defaultOrder.clear()
        defaultOrder.addAll(resetExercises.map { it.id })
        _exercises.clear()
        _exercises.addAll(resetExercises)
        persistWorkoutSessionState()
    }

    private fun cleanupPersistedWeights() {
        val savedWeights = loadSavedWeights()
        if (savedWeights.isEmpty()) return

        val weightExercises = (catalogExercises + buildHandsDayTemplates())
            .filter { it.type == ExerciseType.WEIGHTS }
            .associateBy { it.id }
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
            _exercises.any { it.id == id && it.type != ExerciseType.PLACEHOLDER && !it.isCompleted() }
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
            restTimerEndEpochMillis = activeRestTimerEndEpochMillis,
            currentDayType = currentDayType,
            selectedDayType = selectedDayType
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
        internal fun builtInExercises(): List<ExerciseUiState> =
            com.example.gymprogress.builtInExercises()
    }
}
