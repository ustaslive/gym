package com.example.gymprogress

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RestTimerSoundService : Service() {
    private val serviceScope = CoroutineScope(Job() + Dispatchers.Default)
    private val powerManager by lazy { getSystemService(Context.POWER_SERVICE) as? PowerManager }
    private val toneGenerator: ToneGenerator? = runCatching {
        ToneGenerator(AudioManager.STREAM_ALARM, 100)
    }.getOrNull()

    private var countdownJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val durationSeconds = intent?.getIntExtra(EXTRA_DURATION_SECONDS, 0) ?: 0
        if (durationSeconds <= 0) {
            stopSelf()
            return START_NOT_STICKY
        }
        startCountdown(durationSeconds)
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        countdownJob?.cancel()
        countdownJob = null
        releaseWakeLock()
        stopTone()
        toneGenerator?.release()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startCountdown(durationSeconds: Int) {
        ensureNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification())

        countdownJob?.cancel()
        acquireWakeLock(durationSeconds)
        val endElapsedRealtimeMs = SystemClock.elapsedRealtime() + durationSeconds.toLong() * 1_000L
        val job = serviceScope.launch {
            val currentJob = coroutineContext[Job]
            var lastReportedRemaining = durationSeconds
            try {
                while (true) {
                    val nowElapsedRealtimeMs = SystemClock.elapsedRealtime()
                    val remaining = computeRemainingSeconds(endElapsedRealtimeMs, nowElapsedRealtimeMs)
                    if (remaining != lastReportedRemaining) {
                        if (remaining in 0..5) {
                            playRestTick(remaining)
                        }
                        lastReportedRemaining = remaining
                    }
                    if (remaining <= 0) {
                        break
                    }
                    delay(computeDelayUntilNextTick(endElapsedRealtimeMs, nowElapsedRealtimeMs))
                }
            } finally {
                if (countdownJob == currentJob) {
                    countdownJob = null
                    releaseWakeLock()
                    stopTone()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
        countdownJob = job
    }

    private fun acquireWakeLock(durationSeconds: Int) {
        val nextWakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            WAKE_LOCK_TAG
        ) ?: return
        nextWakeLock.setReferenceCounted(false)
        releaseWakeLock()
        nextWakeLock.acquire(durationSeconds.toLong() * 1_000L + WAKE_LOCK_BUFFER_MS)
        wakeLock = nextWakeLock
    }

    private fun releaseWakeLock() {
        val activeWakeLock = wakeLock ?: return
        if (activeWakeLock.isHeld) {
            runCatching { activeWakeLock.release() }
        }
        wakeLock = null
    }

    private fun playRestTick(remainingSeconds: Int) {
        val isFinalTick = remainingSeconds <= 0
        val generator = toneGenerator ?: return
        generator.stopTone()
        if (isFinalTick) {
            val pulseDurationMs = 40
            val gapDurationMs = 20
            generator.startTone(ToneGenerator.TONE_PROP_ACK, pulseDurationMs)
            serviceScope.launch {
                delay(pulseDurationMs.toLong())
                generator.stopTone()
                delay(gapDurationMs.toLong())
                generator.startTone(ToneGenerator.TONE_PROP_ACK, pulseDurationMs)
                delay(pulseDurationMs.toLong())
                generator.stopTone()
            }
        } else {
            generator.startTone(ToneGenerator.TONE_PROP_BEEP, 100)
        }
    }

    private fun stopTone() {
        toneGenerator?.stopTone()
    }

    private fun buildNotification(): Notification =
        NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.rest_timer_notification_title))
            .setContentText(getString(R.string.rest_timer_notification_text))
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.rest_timer_notification_channel_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            setSound(null, null)
            enableVibration(false)
        }
        notificationManager.createNotificationChannel(channel)
    }

    companion object {
        private const val EXTRA_DURATION_SECONDS = "duration_seconds"
        private const val NOTIFICATION_CHANNEL_ID = "rest_timer_sounds"
        private const val NOTIFICATION_ID = 1001
        private const val WAKE_LOCK_BUFFER_MS = 5_000L
        private const val WAKE_LOCK_TAG = "GymProgress:RestTimerService"

        fun start(context: Context, durationSeconds: Int) {
            val intent = Intent(context, RestTimerSoundService::class.java)
                .putExtra(EXTRA_DURATION_SECONDS, durationSeconds)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, RestTimerSoundService::class.java))
        }
    }
}
