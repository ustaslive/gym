package com.example.gymprogress

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

class GymViewModel(application: Application) : AndroidViewModel(application) {
    private val _exercises = mutableStateListOf<ExerciseUiState>()
    val exercises: List<ExerciseUiState> get() = _exercises.filter { it.isUnlocked }

    private val workoutRepository = WorkoutRepository(application)
    private val workoutSessionStore = WorkoutSessionStore(application)
    private val shareContentBuilder = WorkoutShareContentBuilder(application)
    private val restTimerController by lazy(LazyThreadSafetyMode.NONE) {
        RestTimerController(
            application = application,
            coroutineScope = viewModelScope,
            onTimerUpdated = ::updateExerciseRest,
            onTimerStateChanged = { exerciseId, endEpochMillis ->
                activeStatusExerciseId = exerciseId
                activeRestTimerEndEpochMillis = endEpochMillis
                if (exerciseId == null) {
                    statusText = null
                }
            },
            onPersistRequested = ::persistWorkoutSessionState
        )
    }
    private val defaultOrder = mutableListOf<String>()

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
        generalNote = workoutRepository.loadGeneralNote()
        workoutRepository.cleanupPersistedWeights()
        initialGroup = GROUP_SEQUENCE.firstOrNull { group ->
            workoutRepository
                .templatesForDayType(WorkoutDayType.GENERAL)
                .any { exercise -> exercise.group == group }
        } ?: initialGroup
        val savedSession = workoutSessionStore.load()
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
                restTimerController.start(restoredExercise.id, restoredSession.restTimerRemainingSeconds)
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
                restTimerController.cancel(activeId)
            }
            restTimerController.cancel(exercise.id)
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
                restTimerController.start(updatedExercise.id, duration)
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
                val persistedWeight = workoutRepository.persistWeight(
                    exerciseId = exerciseId,
                    newWeight = newWeight,
                    defaultWeight = exercise.defaultWeight
                )
                _exercises[index] = exercise.copy(
                    selectedWeight = newWeight,
                    persistedWeight = persistedWeight
                )
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
        workoutRepository.clearUserData()
        generalNote = null
        applyNewDayType(currentDayType)
    }

    fun updatePersonalNote(exerciseId: String, newNote: String) {
        val index = _exercises.indexOfFirst { it.id == exerciseId }
        if (index >= 0) {
            val exercise = _exercises[index]
            val updated = exercise.copy(
                personalNote = workoutRepository.savePersonalNote(exerciseId, newNote)
            )
            _exercises[index] = updated
        }
    }

    fun updateGeneralNote(newNote: String) {
        generalNote = workoutRepository.saveGeneralNote(newNote)
    }

    fun buildShareContent(): ShareContent? {
        return shareContentBuilder.build(
            generalNote = generalNote,
            exercises = _exercises
        )
    }

    fun stopActiveRestTimer() {
        val activeId = activeStatusExerciseId ?: return
        restTimerController.cancel(activeId)
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
        restTimerController.clear()
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

    private fun buildExercisesForDayType(dayType: WorkoutDayType): List<ExerciseUiState> =
        workoutRepository.preparedExercisesForDayType(
            dayType = dayType,
            initialGroup = initialGroup
        )

    private fun applyNewDayType(dayType: WorkoutDayType) {
        restTimerController.clear()
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
        return String.format("%d:%02d", minutes, secs)
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
        workoutSessionStore.save(snapshot)
    }

    companion object {
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
