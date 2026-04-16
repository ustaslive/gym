package com.example.gymprogress

import android.app.Application
import kotlinx.coroutines.CoroutineScope

internal fun interface RestTimerControllerFactory {
    fun create(
        application: Application,
        coroutineScope: CoroutineScope,
        onTimerUpdated: (exerciseId: String, secondsRemaining: Int?) -> Unit,
        onTimerStateChanged: (exerciseId: String?, endEpochMillis: Long?) -> Unit,
        onPersistRequested: () -> Unit
    ): RestTimerController
}

internal object DefaultRestTimerControllerFactory : RestTimerControllerFactory {
    override fun create(
        application: Application,
        coroutineScope: CoroutineScope,
        onTimerUpdated: (exerciseId: String, secondsRemaining: Int?) -> Unit,
        onTimerStateChanged: (exerciseId: String?, endEpochMillis: Long?) -> Unit,
        onPersistRequested: () -> Unit
    ): RestTimerController = RestTimerController(
        application = application,
        coroutineScope = coroutineScope,
        onTimerUpdated = onTimerUpdated,
        onTimerStateChanged = onTimerStateChanged,
        onPersistRequested = onPersistRequested
    )
}
