package com.iut.studytracker.service

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import com.iut.studytracker.MainActivity
import com.iut.studytracker.R
import com.iut.studytracker.StudyTrackerApp
import com.iut.studytracker.data.StudySessionEntity
import com.iut.studytracker.data.TimerMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ActiveTimerSnapshot(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val subjectId: Long = 1L,
    val subjectName: String = "CEE 4311",
    val topicStudied: String = "",
    val mode: TimerMode = TimerMode.POMODORO,
    val targetSeconds: Long = 25 * 60L,
    val elapsedSeconds: Long = 0L,
    val sessionStartEpochMs: Long = 0L
)

class StudyTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null

    private var baseElapsedSeconds: Long = 0L
    private var lastResumeRealtimeMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val subjectId = intent.getLongExtra(EXTRA_SUBJECT_ID, 1L)
                val subjectName = intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: "Study Session"
                val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: TimerMode.POMODORO.name
                val targetSec = intent.getLongExtra(EXTRA_TARGET_SECONDS, 25 * 60L)
                startNewSession(subjectId, subjectName, topic, TimerMode.valueOf(modeName), targetSec)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP_AND_SAVE -> stopAndSaveSession()
            ACTION_DISCARD -> discardSession()
        }
        return START_STICKY
    }

    private fun startNewSession(
        subjectId: Long,
        subjectName: String,
        topic: String,
        mode: TimerMode,
        targetSeconds: Long
    ) {
        val nowEpoch = System.currentTimeMillis()
        baseElapsedSeconds = 0L
        lastResumeRealtimeMs = SystemClock.elapsedRealtime()

        val snapshot = ActiveTimerSnapshot(
            isRunning = true,
            isPaused = false,
            subjectId = subjectId,
            subjectName = subjectName,
            topicStudied = topic,
            mode = mode,
            targetSeconds = targetSeconds,
            elapsedSeconds = 0L,
            sessionStartEpochMs = nowEpoch
        )
        _timerState.value = snapshot
        persistState(snapshot)

        val notification = buildNotification(snapshot)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        startTickerLoop()
    }

    private fun pauseTimer() {
        val current = _timerState.value
        if (!current.isRunning || current.isPaused) return
        val delta = (SystemClock.elapsedRealtime() - lastResumeRealtimeMs) / 1000L
        baseElapsedSeconds += delta
        tickerJob?.cancel()

        val updated = current.copy(isPaused = true, elapsedSeconds = baseElapsedSeconds)
        _timerState.value = updated
        persistState(updated)
        updateNotification(updated)
    }

    private fun resumeTimer() {
        val current = _timerState.value
        if (!current.isRunning || !current.isPaused) return
        lastResumeRealtimeMs = SystemClock.elapsedRealtime()
        val updated = current.copy(isPaused = false)
        _timerState.value = updated
        persistState(updated)
        startTickerLoop()
    }

    private fun startTickerLoop() {
        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (true) {
                val current = _timerState.value
                if (!current.isRunning || current.isPaused) break
                val delta = (SystemClock.elapsedRealtime() - lastResumeRealtimeMs) / 1000L
                val totalElapsed = baseElapsedSeconds + delta
                val next = current.copy(elapsedSeconds = totalElapsed)
                _timerState.value = next
                updateNotification(next)

                if (current.mode == TimerMode.POMODORO && totalElapsed >= current.targetSeconds) {
                    stopAndSaveSession()
                    break
                }
                delay(1000L)
            }
        }
    }

    private fun stopAndSaveSession() {
        val current = _timerState.value
        tickerJob?.cancel()
        val finalSeconds = current.elapsedSeconds.coerceAtLeast(1L)
        val endEpoch = System.currentTimeMillis()
        val startEpoch = if (current.sessionStartEpochMs > 0L) current.sessionStartEpochMs else (endEpoch - finalSeconds * 1000L)

        val app = application as StudyTrackerApp
        serviceScope.launch(Dispatchers.IO) {
            if (finalSeconds >= 10L) {
                app.database.dao().insertSession(
                    StudySessionEntity(
                        subjectId = current.subjectId,
                        startTimeEpochMs = startEpoch,
                        endTimeEpochMs = endEpoch,
                        durationSeconds = finalSeconds,
                        mode = current.mode,
                        topicStudied = current.topicStudied.ifBlank { "Focused Study Block" },
                        focusRating = 5,
                        notes = "Recorded via Foreground Timer",
                        dateIso = LocalDate.now().toString()
                    )
                )
            }
            clearPersistedState()
        }

        _timerState.value = ActiveTimerSnapshot()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun discardSession() {
        tickerJob?.cancel()
        clearPersistedState()
        _timerState.value = ActiveTimerSnapshot()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(snapshot: ActiveTimerSnapshot) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun buildNotification(snapshot: ActiveTimerSnapshot): Notification {
        val displaySeconds = if (snapshot.mode == TimerMode.POMODORO) {
            (snapshot.targetSeconds - snapshot.elapsedSeconds).coerceAtLeast(0L)
        } else {
            snapshot.elapsedSeconds
        }
        val hrs = displaySeconds / 3600
        val mins = (displaySeconds % 3600) / 60
        val secs = displaySeconds % 60
        val formattedTime = if (hrs > 0) {
            String.format("%02d:%02d:%02d", hrs, mins, secs)
        } else {
            String.format("%02d:%02d", mins, secs)
        }

        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val toggleIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, StudyTimerService::class.java).apply {
                action = if (snapshot.isPaused) ACTION_RESUME else ACTION_PAUSE
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val saveIntent = PendingIntent.getService(
            this,
            2,
            Intent(this, StudyTimerService::class.java).apply { action = ACTION_STOP_AND_SAVE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val statusLabel = if (snapshot.isPaused) "PAUSED" else snapshot.mode.name
        return NotificationCompat.Builder(this, StudyTrackerApp.TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("${snapshot.subjectName} • $formattedTime")
            .setContentText("$statusLabel — ${snapshot.topicStudied.ifBlank { "Stay focused!" }}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, if (snapshot.isPaused) "Resume" else "Pause", toggleIntent)
            .addAction(0, "Finish & Save", saveIntent)
            .build()
    }

    private fun persistState(snapshot: ActiveTimerSnapshot) {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().apply {
            putBoolean("isRunning", snapshot.isRunning)
            putBoolean("isPaused", snapshot.isPaused)
            putLong("subjectId", snapshot.subjectId)
            putString("subjectName", snapshot.subjectName)
            putString("topicStudied", snapshot.topicStudied)
            putString("mode", snapshot.mode.name)
            putLong("targetSeconds", snapshot.targetSeconds)
            putLong("elapsedSeconds", snapshot.elapsedSeconds)
            putLong("sessionStartEpochMs", snapshot.sessionStartEpochMs)
            apply()
        }
    }

    private fun clearPersistedState() {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().apply()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val PREFS_NAME = "active_study_timer_prefs"

        const val ACTION_START = "com.iut.studytracker.ACTION_START"
        const val ACTION_PAUSE = "com.iut.studytracker.ACTION_PAUSE"
        const val ACTION_RESUME = "com.iut.studytracker.ACTION_RESUME"
        const val ACTION_STOP_AND_SAVE = "com.iut.studytracker.ACTION_STOP_AND_SAVE"
        const val ACTION_DISCARD = "com.iut.studytracker.ACTION_DISCARD"

        const val EXTRA_SUBJECT_ID = "extra_subject_id"
        const val EXTRA_SUBJECT_NAME = "extra_subject_name"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_TARGET_SECONDS = "extra_target_seconds"

        private val _timerState = MutableStateFlow(ActiveTimerSnapshot())
        val timerState: StateFlow<ActiveTimerSnapshot> = _timerState.asStateFlow()
    }
}
