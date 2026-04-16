package com.example.gymprogress

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope

class GymViewModel internal constructor(
    application: Application,
    providedWorkoutRepository: WorkoutRepository? = null,
    providedWorkoutSessionStore: WorkoutSessionStore? = null,
    providedShareContentBuilder: WorkoutShareContentBuilder? = null,
    providedDayStateFactory: WorkoutDayStateFactory? = null,
    providedSessionStateManager: WorkoutSessionStateManager? = null,
    providedExerciseStateReducer: WorkoutExerciseStateReducer? = null,
    providedRestTimerStateReducer: WorkoutRestTimerStateReducer? = null,
    providedRestTimerControllerFactory: RestTimerControllerFactory? = null
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application = application,
        providedWorkoutRepository = null,
        providedWorkoutSessionStore = null,
        providedShareContentBuilder = null,
        providedDayStateFactory = null,
        providedSessionStateManager = null,
        providedExerciseStateReducer = null,
        providedRestTimerStateReducer = null,
        providedRestTimerControllerFactory = null
    )

    private val _exercises = mutableStateListOf<ExerciseUiState>()
    val exercises: List<ExerciseUiState> get() = _exercises.filter { it.isUnlocked }

    private val workoutRepository = providedWorkoutRepository ?: WorkoutRepository(application)
    private val shareContentBuilder = providedShareContentBuilder ?: WorkoutShareContentBuilder(application)
    private val dayStateFactory = providedDayStateFactory ?: WorkoutDayStateFactory(
        workoutRepository = workoutRepository,
        groupSequence = GROUP_SEQUENCE
    )
    private val sessionStateManager = providedSessionStateManager ?: WorkoutSessionStateManager(
        workoutSessionStore = providedWorkoutSessionStore ?: WorkoutSessionStore(application)
    )
    private val exerciseStateReducer = providedExerciseStateReducer ?: WorkoutExerciseStateReducer(
        groupSequence = GROUP_SEQUENCE
    )
    private val restTimerStateReducer = providedRestTimerStateReducer ?: WorkoutRestTimerStateReducer()
    private val restTimerControllerFactory = providedRestTimerControllerFactory ?: DefaultRestTimerControllerFactory
    private val restTimerController by lazy(LazyThreadSafetyMode.NONE) {
        restTimerControllerFactory.create(
            application = application,
            coroutineScope = viewModelScope,
            onTimerUpdated = ::updateExerciseRest,
            onTimerStateChanged = ::updateRestTimerState,
            onPersistRequested = ::persistWorkoutSessionState
        )
    }

    private var activeExerciseId: String? = null
    private var restTimerUiState by mutableStateOf(RestTimerUiState())
    private var currentDayType by mutableStateOf(WorkoutDayType.GENERAL)

    var selectedDayType by mutableStateOf(WorkoutDayType.GENERAL)
        private set

    val statusText: String?
        get() = restTimerUiState.statusText

    var generalNote by mutableStateOf<String?>(null)
        private set

    var newlyUnlockedGroupAnchorId by mutableStateOf<String?>(null)
        private set

    init {
        val nowEpochMillis = System.currentTimeMillis()
        generalNote = workoutRepository.loadGeneralNote()
        workoutRepository.cleanupPersistedWeights()
        val restoreResult = sessionStateManager.restore(
            dayStateFactory = dayStateFactory,
            nowEpochMillis = nowEpochMillis
        )
        val restoredSession = restoreResult.session
        currentDayType = restoredSession.currentDayType
        selectedDayType = restoredSession.selectedDayType
        activeExerciseId = restoredSession.activeExerciseId
        restTimerUiState = restTimerStateReducer.restore(
            activeRestTimerExerciseId = restoredSession.activeRestTimerExerciseId,
            restTimerRemainingSeconds = restoredSession.restTimerRemainingSeconds,
            nowEpochMillis = nowEpochMillis
        )
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
        } else if (restoreResult.hadSavedSnapshot) {
            persistWorkoutSessionState()
        }
    }

    fun advanceProgress(exerciseId: String) {
        val progressResult = exerciseStateReducer.advanceProgress(
            exercises = _exercises.toList(),
            exerciseId = exerciseId,
            currentActiveExerciseId = activeExerciseId,
            activeRestTimerExerciseId = restTimerUiState.activeExerciseId
        )
        if (!progressResult.changed) {
            return
        }

        activeExerciseId = progressResult.activeExerciseId
        newlyUnlockedGroupAnchorId = progressResult.newlyUnlockedGroupAnchorId
        replaceExercises(progressResult.exercises)

        progressResult.cancelledRestTimerExerciseIds.forEach(restTimerController::cancel)

        val restDurationSeconds = progressResult.restDurationSeconds
        if (restDurationSeconds != null) {
            restTimerController.start(exerciseId, restDurationSeconds)
        } else {
            persistWorkoutSessionState()
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
        val activeId = restTimerUiState.activeExerciseId ?: return
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
        val currentExercises = _exercises.toList()
        val selection = exerciseStateReducer.selectActive(
            exercises = currentExercises,
            requestedExerciseId = exerciseId
        )
        activeExerciseId = selection.activeExerciseId
        replaceExercises(selection.exercises)
        persistWorkoutSessionState()
    }

    fun consumeNewlyUnlockedAnchor() {
        newlyUnlockedGroupAnchorId = null
    }


    override fun onCleared() {
        super.onCleared()
        restTimerController.clear()
    }

    private fun applyNewDayType(dayType: WorkoutDayType) {
        restTimerController.clear()
        activeExerciseId = null
        restTimerUiState = RestTimerUiState()
        newlyUnlockedGroupAnchorId = null
        currentDayType = dayType
        replaceExercises(dayStateFactory.build(dayType))
        persistWorkoutSessionState()
    }

    private fun updateRestTimerState(exerciseId: String?, endEpochMillis: Long?) {
        restTimerUiState = restTimerStateReducer.onTimerStateChanged(
            currentState = restTimerUiState,
            exerciseId = exerciseId,
            endEpochMillis = endEpochMillis
        )
    }

    private fun updateExerciseRest(exerciseId: String, secondsRemaining: Int?) {
        val index = _exercises.indexOfFirst { exercise -> exercise.id == exerciseId }
        if (index >= 0) {
            val exercise = _exercises[index]
            if (exercise.restSecondsRemaining != secondsRemaining) {
                _exercises[index] = exercise.copy(restSecondsRemaining = secondsRemaining)
            }
        }
        restTimerUiState = restTimerStateReducer.onTimerUpdated(
            currentState = restTimerUiState,
            exerciseId = exerciseId,
            secondsRemaining = secondsRemaining
        )
    }

    private fun replaceExercises(exercises: List<ExerciseUiState>) {
        _exercises.clear()
        _exercises.addAll(exercises)
    }

    private fun persistWorkoutSessionState() {
        sessionStateManager.persist(
            state = WorkoutSessionPersistState(
                exercises = _exercises.toList(),
                activeExerciseId = activeExerciseId,
                activeRestTimerExerciseId = restTimerUiState.activeExerciseId,
                restTimerEndEpochMillis = restTimerUiState.endEpochMillis,
                currentDayType = currentDayType,
                selectedDayType = selectedDayType
            )
        )
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
