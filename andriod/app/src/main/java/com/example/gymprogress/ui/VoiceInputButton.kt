package com.example.gymprogress.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.example.gymprogress.R
import com.example.gymprogress.ui.theme.ActiveGlowBlue
import java.util.concurrent.atomic.AtomicLong

private const val LOG_TAG = "VoiceInputButton"
private const val SPEECH_LANGUAGE = "ru-RU"
private const val RESTART_DELAY_MILLIS = 180L
private const val FINAL_RESULT_TIMEOUT_MILLIS = 5_000L
private const val PARTIAL_TEXT_SEGMENT_GAP_MILLIS = 1_100L

data class VoiceInputTextUpdate(
    val segmentId: Long,
    val text: String,
    val isFinal: Boolean
)

/**
 * A press-and-hold microphone button that performs speech-to-text recognition.
 *
 * While held, a blue circle appears behind the mic icon and the component keeps
 * starting recognition segments for spoken Russian until the finger is released.
 */
@Composable
fun VoiceInputButton(
    onTextUpdated: (VoiceInputTextUpdate) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val latestOnTextUpdated by rememberUpdatedState(onTextUpdated)
    var isListening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val activePressToken = remember { AtomicLong(0L) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                context,
                context.getString(R.string.voice_input_permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // Clean up recognizer when composable leaves composition.
    DisposableEffect(Unit) {
        onDispose {
            activePressToken.incrementAndGet()
            mainHandler.removeCallbacksAndMessages(null)
            recognizer?.apply {
                cancel()
                destroy()
            }
            recognizer = null
            isListening = false
        }
    }

    val circleScale by animateFloatAsState(
        targetValue = if (isListening) 1f else 0f,
        animationSpec = tween(durationMillis = 150),
        label = "micCircleScale"
    )

    val circleColor = ActiveGlowBlue
    val iconTint = if (isListening) Color.White else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = modifier
            .size(44.dp)
            .zIndex(if (isListening) 10f else 0f)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        // Check permission first.
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            return@detectTapGestures
                        }

                        // Check availability.
                        if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.voice_input_no_recognizer),
                                Toast.LENGTH_SHORT
                            ).show()
                            return@detectTapGestures
                        }

                        val sr = SpeechRecognizer.createSpeechRecognizer(context)
                        val pressToken = activePressToken.incrementAndGet()
                        val pressStartedAtMillis = SystemClock.elapsedRealtime()
                        var isFingerHeld = true
                        var currentRecognizer: SpeechRecognizer? = sr
                        var segmentIndex = 1
                        val deliverTextUpdate: (VoiceInputTextUpdate) -> Unit = { update ->
                            latestOnTextUpdated(update)
                        }

                        fun elapsedMillis(): Long =
                            SystemClock.elapsedRealtime() - pressStartedAtMillis

                        fun isCurrentPress(): Boolean =
                            activePressToken.get() == pressToken

                        fun destroyRecognizer(target: SpeechRecognizer, reason: String) {
                            runCatching {
                                target.destroy()
                            }.onFailure { error ->
                                Log.w(LOG_TAG, "Failed to destroy recognizer after $reason", error)
                            }
                        }

                        fun finishSegment(
                            segmentNumber: Int,
                            target: SpeechRecognizer,
                            reason: String,
                            shouldRestart: Boolean,
                            restartDelayMillis: Long = RESTART_DELAY_MILLIS
                        ) {
                            if (!isCurrentPress()) {
                                destroyRecognizer(target, "$reason after stale press")
                                return
                            }
                            if (currentRecognizer === target) {
                                currentRecognizer = null
                            }
                            if (recognizer === target) {
                                recognizer = null
                            }
                            Log.d(
                                LOG_TAG,
                                "Voice segment #$segmentNumber finished: reason=$reason, " +
                                    "fingerHeld=$isFingerHeld, restart=$shouldRestart, " +
                                    "elapsed=${elapsedMillis()}ms"
                            )
                            destroyRecognizer(target, reason)
                            if (shouldRestart && isFingerHeld) {
                                mainHandler.postDelayed(
                                    {
                                        if (isCurrentPress() && isFingerHeld) {
                                            startListeningSegment(
                                                context = context,
                                                pressStartedAtMillis = pressStartedAtMillis,
                                                pressToken = pressToken,
                                                nextSegmentNumber = ++segmentIndex,
                                                onTextUpdated = deliverTextUpdate,
                                                isCurrentPress = ::isCurrentPress,
                                                isFingerHeld = { isFingerHeld },
                                                setCurrentRecognizer = { nextRecognizer ->
                                                    currentRecognizer = nextRecognizer
                                                    recognizer = nextRecognizer
                                                },
                                                finishSegment = ::finishSegment
                                            )
                                        }
                                    },
                                    restartDelayMillis
                                )
                            } else if (isFingerHeld) {
                                isListening = false
                            }
                        }

                        fun startFirstSegment() {
                            sr.setVoiceRecognitionListener(
                                segmentNumber = segmentIndex,
                                segmentId = voiceSegmentId(pressToken, segmentIndex),
                                pressStartedAtMillis = pressStartedAtMillis,
                                onTextUpdated = deliverTextUpdate,
                                isCurrentPress = ::isCurrentPress,
                                isFingerHeld = { isFingerHeld },
                                finishSegment = ::finishSegment
                            )
                            Log.d(
                                LOG_TAG,
                                "Starting voice segment #$segmentIndex: reason=press, elapsed=0ms"
                            )
                            runCatching {
                                sr.startListening(createRecognizerIntent())
                            }.onFailure { error ->
                                Log.w(LOG_TAG, "Failed to start voice segment #$segmentIndex", error)
                                finishSegment(
                                    segmentNumber = segmentIndex,
                                    target = sr,
                                    reason = "start failure",
                                    shouldRestart = false
                                )
                                isListening = false
                            }
                        }

                        recognizer = sr
                        isListening = true
                        startFirstSegment()

                        // Wait for finger lift.
                        val released = tryAwaitRelease()
                        isFingerHeld = false
                        isListening = false
                        Log.d(
                            LOG_TAG,
                            "Voice press ended: released=$released, elapsed=${elapsedMillis()}ms"
                        )
                        currentRecognizer?.let { activeRecognizer ->
                            runCatching {
                                activeRecognizer.stopListening()
                            }.onFailure { error ->
                                Log.w(LOG_TAG, "Failed to stop listening on press end", error)
                                if (currentRecognizer === activeRecognizer) {
                                    currentRecognizer = null
                                }
                                if (recognizer === activeRecognizer) {
                                    recognizer = null
                                }
                                destroyRecognizer(activeRecognizer, "press end stop failure")
                            }
                            mainHandler.postDelayed(
                                {
                                    if (currentRecognizer === activeRecognizer) {
                                        currentRecognizer = null
                                        if (recognizer === activeRecognizer) {
                                            recognizer = null
                                        }
                                        Log.d(
                                            LOG_TAG,
                                            "Destroying voice segment after final result timeout"
                                        )
                                        destroyRecognizer(activeRecognizer, "final result timeout")
                                    }
                                },
                                FINAL_RESULT_TIMEOUT_MILLIS
                            )
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        // Blue circle background – visible while listening.
        // Uses wrapContentSize(unbounded = true) so the circle can overflow
        // the 44dp touch-target Box and remain visible behind the finger.
        if (circleScale > 0f) {
            Box(
                modifier = Modifier
                    .wrapContentSize(unbounded = true)
                    .requiredSize(88.dp)
                    .scale(circleScale)
                    .clip(CircleShape)
                    .background(circleColor)
            )
        }

        Icon(
            imageVector = Icons.Filled.Mic,
            contentDescription = stringResource(R.string.voice_input_action),
            modifier = Modifier.size(22.dp),
            tint = iconTint
        )
    }
}

private fun startListeningSegment(
    context: Context,
    pressStartedAtMillis: Long,
    pressToken: Long,
    nextSegmentNumber: Int,
    onTextUpdated: (VoiceInputTextUpdate) -> Unit,
    isCurrentPress: () -> Boolean,
    isFingerHeld: () -> Boolean,
    setCurrentRecognizer: (SpeechRecognizer) -> Unit,
    finishSegment: (
        segmentNumber: Int,
        target: SpeechRecognizer,
        reason: String,
        shouldRestart: Boolean,
        restartDelayMillis: Long
    ) -> Unit
) {
    if (!isCurrentPress() || !isFingerHeld()) {
        return
    }
    val sr = SpeechRecognizer.createSpeechRecognizer(context)
    setCurrentRecognizer(sr)
    sr.setVoiceRecognitionListener(
        segmentNumber = nextSegmentNumber,
        segmentId = voiceSegmentId(pressToken, nextSegmentNumber),
        pressStartedAtMillis = pressStartedAtMillis,
        onTextUpdated = onTextUpdated,
        isCurrentPress = isCurrentPress,
        isFingerHeld = isFingerHeld,
        finishSegment = finishSegment
    )
    Log.d(
        LOG_TAG,
        "Starting voice segment #$nextSegmentNumber: reason=auto-restart, " +
            "elapsed=${SystemClock.elapsedRealtime() - pressStartedAtMillis}ms"
    )
    runCatching {
        sr.startListening(createRecognizerIntent())
    }.onFailure { error ->
        Log.w(LOG_TAG, "Failed to start voice segment #$nextSegmentNumber", error)
        finishSegment(
            nextSegmentNumber,
            sr,
            "start failure",
            false,
            RESTART_DELAY_MILLIS
        )
    }
}

private fun SpeechRecognizer.setVoiceRecognitionListener(
    segmentNumber: Int,
    segmentId: Long,
    pressStartedAtMillis: Long,
    onTextUpdated: (VoiceInputTextUpdate) -> Unit,
    isCurrentPress: () -> Boolean,
    isFingerHeld: () -> Boolean,
    finishSegment: (
        segmentNumber: Int,
        target: SpeechRecognizer,
        reason: String,
        shouldRestart: Boolean,
        restartDelayMillis: Long
    ) -> Unit
) {
    setRecognitionListener(object : RecognitionListener {
        private var lastPartialText: String? = null
        private var lastPartialAtMillis = 0L
        private var textSegmentOffset = 0
        private var currentTextSegmentId = segmentId

        override fun onReadyForSpeech(params: Bundle?) {
            Log.d(LOG_TAG, "Voice segment #$segmentNumber ready")
        }

        override fun onBeginningOfSpeech() {
            Log.d(LOG_TAG, "Voice segment #$segmentNumber speech started")
        }

        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d(
                LOG_TAG,
                "Voice segment #$segmentNumber end of speech at " +
                    "${SystemClock.elapsedRealtime() - pressStartedAtMillis}ms"
            )
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!isCurrentPress()) {
                return
            }
            val text = partialResults.recognitionText()
            Log.d(
                LOG_TAG,
                "Voice segment #$segmentNumber partial: textLength=${text?.length ?: 0}"
            )
            if (!text.isNullOrBlank() && text != lastPartialText) {
                val nowMillis = SystemClock.elapsedRealtime()
                val previousText = lastPartialText
                val partialGapMillis = if (lastPartialAtMillis == 0L) {
                    0L
                } else {
                    nowMillis - lastPartialAtMillis
                }
                if (
                    previousText != null &&
                    partialGapMillis >= PARTIAL_TEXT_SEGMENT_GAP_MILLIS &&
                    !isRecognizerPartialContinuation(previousText, text)
                ) {
                    textSegmentOffset += 1
                    currentTextSegmentId = segmentId + textSegmentOffset
                    Log.d(
                        LOG_TAG,
                        "Voice segment #$segmentNumber split partial text segment: " +
                            "gap=${partialGapMillis}ms"
                    )
                }
                lastPartialAtMillis = nowMillis
                lastPartialText = text
                onTextUpdated(
                    VoiceInputTextUpdate(
                        segmentId = currentTextSegmentId,
                        text = text,
                        isFinal = false
                    )
                )
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onResults(results: Bundle?) {
            if (!isCurrentPress()) {
                finishSegment(segmentNumber, this@setVoiceRecognitionListener, "stale results", false, 0L)
                return
            }
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val text = results.recognitionText()
            Log.d(
                LOG_TAG,
                "Voice segment #$segmentNumber results: " +
                    "textLength=${text?.length ?: 0}, alternatives=${matches?.size ?: 0}"
            )
            onTextUpdated(
                VoiceInputTextUpdate(
                    segmentId = currentTextSegmentId,
                    text = text.orEmpty(),
                    isFinal = true
                )
            )
            finishSegment(
                segmentNumber,
                this@setVoiceRecognitionListener,
                "results",
                isFingerHeld(),
                RESTART_DELAY_MILLIS
            )
        }

        override fun onError(error: Int) {
            if (!isCurrentPress()) {
                finishSegment(segmentNumber, this@setVoiceRecognitionListener, "stale error", false, 0L)
                return
            }
            val errorName = speechRecognizerErrorName(error)
            val shouldRestart = isFingerHeld() && isRecoverableSpeechRecognizerError(error)
            Log.d(
                LOG_TAG,
                "Voice segment #$segmentNumber error: code=$error, name=$errorName, " +
                    "restart=$shouldRestart"
            )
            finishSegment(
                segmentNumber,
                this@setVoiceRecognitionListener,
                "error $errorName",
                shouldRestart,
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                    500L
                } else {
                    RESTART_DELAY_MILLIS
                }
            )
        }
    })
}

private fun isRecoverableSpeechRecognizerError(error: Int): Boolean =
    when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_SERVER,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> true
        else -> false
    }

private fun speechRecognizerErrorName(error: Int): String =
    when (error) {
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "ERROR_NETWORK_TIMEOUT"
        SpeechRecognizer.ERROR_NETWORK -> "ERROR_NETWORK"
        SpeechRecognizer.ERROR_AUDIO -> "ERROR_AUDIO"
        SpeechRecognizer.ERROR_SERVER -> "ERROR_SERVER"
        SpeechRecognizer.ERROR_CLIENT -> "ERROR_CLIENT"
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "ERROR_SPEECH_TIMEOUT"
        SpeechRecognizer.ERROR_NO_MATCH -> "ERROR_NO_MATCH"
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "ERROR_RECOGNIZER_BUSY"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "ERROR_INSUFFICIENT_PERMISSIONS"
        SpeechRecognizer.ERROR_TOO_MANY_REQUESTS -> "ERROR_TOO_MANY_REQUESTS"
        SpeechRecognizer.ERROR_SERVER_DISCONNECTED -> "ERROR_SERVER_DISCONNECTED"
        SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED -> "ERROR_LANGUAGE_NOT_SUPPORTED"
        SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> "ERROR_LANGUAGE_UNAVAILABLE"
        SpeechRecognizer.ERROR_CANNOT_CHECK_SUPPORT -> "ERROR_CANNOT_CHECK_SUPPORT"
        SpeechRecognizer.ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS -> {
            "ERROR_CANNOT_LISTEN_TO_DOWNLOAD_EVENTS"
        }
        else -> "UNKNOWN"
}

private fun voiceSegmentId(pressToken: Long, segmentNumber: Int): Long =
    pressToken * 1_000_000L + segmentNumber * 1_000L

private fun isRecognizerPartialContinuation(previousText: String, nextText: String): Boolean =
    nextText.startsWith(previousText, ignoreCase = true) ||
        previousText.startsWith(nextText, ignoreCase = true)

private fun Bundle?.recognitionText(): String? =
    this
        ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
        ?.firstOrNull()
        ?.trim()
        ?.takeIf { it.isNotEmpty() }

private fun createRecognizerIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, SPEECH_LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, SPEECH_LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
    }
