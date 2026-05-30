package com.example.gymprogress

import android.app.Application
import android.util.Log

internal class SharedExerciseBundleStore(
    private val application: Application,
    private val assetName: String = ASSET_NAME
) {
    fun loadBundle(): SharedExerciseBundle =
        runCatching {
            application.assets.open(assetName).bufferedReader().use { reader ->
                parseSharedExerciseBundle(reader.readText())
            }
        }.getOrElse { error ->
            Log.e(LOG_TAG, "Could not load shared exercise bundle from assets.", error)
            SharedExerciseBundle.EMPTY
        }

    companion object {
        private const val LOG_TAG = "SharedExerciseBundle"
        private const val ASSET_NAME = "exercise-bundle.json"
    }
}
