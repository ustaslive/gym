package com.example.gymprogress.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
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

private const val SPEECH_LANGUAGE = "ru-RU"

/**
 * A press-and-hold microphone button that performs speech-to-text recognition.
 *
 * While held, a blue circle appears behind the mic icon and the device listens
 * for spoken Russian. On release the recognised text is delivered via [onTextRecognised].
 */
@Composable
fun VoiceInputButton(
    onTextRecognised: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isListening by remember { mutableStateOf(false) }
    var recognizer by remember { mutableStateOf<SpeechRecognizer?>(null) }

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
            recognizer?.apply {
                cancel()
                destroy()
            }
            recognizer = null
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
                        recognizer = sr
                        isListening = true

                        sr.setRecognitionListener(object : RecognitionListener {
                            override fun onReadyForSpeech(params: Bundle?) {}
                            override fun onBeginningOfSpeech() {}
                            override fun onRmsChanged(rmsdB: Float) {}
                            override fun onBufferReceived(buffer: ByteArray?) {}
                            override fun onEndOfSpeech() {}
                            override fun onPartialResults(partialResults: Bundle?) {}
                            override fun onEvent(eventType: Int, params: Bundle?) {}

                            override fun onResults(results: Bundle?) {
                                val matches = results
                                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                                val text = matches?.firstOrNull()
                                if (!text.isNullOrBlank()) {
                                    onTextRecognised(text)
                                }
                                isListening = false
                            }

                            override fun onError(error: Int) {
                                isListening = false
                            }
                        })

                        val intent = createRecognizerIntent()
                        sr.startListening(intent)

                        // Wait for finger lift.
                        val released = tryAwaitRelease()
                        if (released || !released) {
                            // On release, stop listening – results arrive via onResults callback.
                            sr.stopListening()
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

private fun createRecognizerIntent(): Intent =
    Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, SPEECH_LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, SPEECH_LANGUAGE)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
    }
