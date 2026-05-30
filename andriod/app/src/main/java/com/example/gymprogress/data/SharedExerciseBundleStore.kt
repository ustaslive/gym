package com.example.gymprogress

import android.app.Application
import android.util.Log
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val LOG_TAG = "SharedExerciseBundle"
private const val ASSET_NAME = "exercise-bundle.json"
private const val CACHE_FILE_NAME = "exercise-bundle-cache.json"
private const val CACHE_BACKUP_FILE_NAME = "exercise-bundle-cache.json.bak"
private const val DEFAULT_REMOTE_BUNDLE_URL = "https://ustaslive.github.io/gym/data/exercise-bundle.json"
private const val CONNECT_TIMEOUT_MILLIS = 10_000
private const val READ_TIMEOUT_MILLIS = 15_000

internal sealed class ExerciseBundleImportResult {
    data class Success(val sessionCount: Int) : ExerciseBundleImportResult()
    data class Failure(val reason: ExerciseBundleImportFailureReason) : ExerciseBundleImportResult()
}

internal enum class ExerciseBundleImportFailureReason {
    DOWNLOAD_FAILED,
    INVALID_DATA,
    SAVE_FAILED
}

internal class SharedExerciseBundleStore(
    private val application: Application,
    private val assetName: String = ASSET_NAME,
    private val cacheFileName: String = CACHE_FILE_NAME,
    private val remoteBundleUrl: String = DEFAULT_REMOTE_BUNDLE_URL
) {
    fun loadBundle(): SharedExerciseBundle =
        loadCachedBundle() ?: loadBundledBundle()

    fun downloadAndCacheBundle(): ExerciseBundleImportResult {
        val raw = runCatching { downloadBundleText(remoteBundleUrl) }
            .getOrElse { error ->
                Log.e(LOG_TAG, "Could not download shared exercise bundle.", error)
                return ExerciseBundleImportResult.Failure(ExerciseBundleImportFailureReason.DOWNLOAD_FAILED)
            }
        return cacheBundleText(raw)
    }

    fun cacheBundleText(raw: String): ExerciseBundleImportResult {
        val bundle = runCatching { parseSharedExerciseBundle(raw) }
            .getOrElse { error ->
                Log.e(LOG_TAG, "Downloaded shared exercise bundle is invalid.", error)
                return ExerciseBundleImportResult.Failure(ExerciseBundleImportFailureReason.INVALID_DATA)
            }
        if (bundle.sessionOptions.isEmpty() || bundle.templatesBySessionId.isEmpty()) {
            Log.e(LOG_TAG, "Downloaded shared exercise bundle has no sessions.")
            return ExerciseBundleImportResult.Failure(ExerciseBundleImportFailureReason.INVALID_DATA)
        }

        return runCatching {
            val cacheFile = cachedBundleFile()
            val backupFile = cachedBundleBackupFile()
            val tempFile = File(cacheFile.parentFile, "$cacheFileName.tmp")
            tempFile.writeText(raw, Charsets.UTF_8)
            if (backupFile.exists() && !backupFile.delete()) {
                error("Could not remove previous shared exercise bundle backup.")
            }
            if (cacheFile.exists() && !cacheFile.renameTo(backupFile)) {
                error("Could not preserve previous cached shared exercise bundle.")
            }
            if (!tempFile.renameTo(cacheFile)) {
                if (backupFile.exists()) {
                    backupFile.renameTo(cacheFile)
                }
                error("Could not move shared exercise bundle into cache.")
            }
            if (backupFile.exists()) {
                backupFile.delete()
            }
            ExerciseBundleImportResult.Success(sessionCount = bundle.sessionOptions.size)
        }.getOrElse { error ->
            Log.e(LOG_TAG, "Could not cache shared exercise bundle.", error)
            ExerciseBundleImportResult.Failure(ExerciseBundleImportFailureReason.SAVE_FAILED)
        }
    }

    private fun loadCachedBundle(): SharedExerciseBundle? {
        val cacheFile = cachedBundleFile()
        val backupFile = cachedBundleBackupFile()
        return loadCachedBundleFile(cacheFile)
            ?: loadCachedBundleFile(backupFile)
    }

    private fun loadCachedBundleFile(file: File): SharedExerciseBundle? {
        if (!file.exists()) {
            return null
        }
        return runCatching {
            parseSharedExerciseBundle(file.readText(Charsets.UTF_8))
        }.getOrElse { error ->
            Log.e(LOG_TAG, "Cached shared exercise bundle is invalid; bundled asset will be used.", error)
            null
        }
    }

    private fun loadBundledBundle(): SharedExerciseBundle =
        runCatching {
            application.assets.open(assetName).bufferedReader().use { reader ->
                parseSharedExerciseBundle(reader.readText())
            }
        }.getOrElse { error ->
            Log.e(LOG_TAG, "Could not load shared exercise bundle from assets.", error)
            SharedExerciseBundle.EMPTY
        }

    private fun cachedBundleFile(): File = File(application.filesDir, cacheFileName)

    private fun cachedBundleBackupFile(): File = File(application.filesDir, CACHE_BACKUP_FILE_NAME)
}

private fun downloadBundleText(bundleUrl: String): String {
    val connection = (URL(bundleUrl).openConnection() as HttpURLConnection).apply {
        connectTimeout = CONNECT_TIMEOUT_MILLIS
        readTimeout = READ_TIMEOUT_MILLIS
        requestMethod = "GET"
        instanceFollowRedirects = true
    }
    return try {
        val responseCode = connection.responseCode
        if (responseCode !in 200..299) {
            error("Unexpected HTTP response code: $responseCode")
        }
        connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
            reader.readText()
        }
    } finally {
        connection.disconnect()
    }
}
