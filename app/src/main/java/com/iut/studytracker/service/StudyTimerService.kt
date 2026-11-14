package com.iut.studytracker.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.core.app.NotificationCompat
import com.iut.studytracker.MainActivity
import com.iut.studytracker.R
import com.iut.studytracker.StudyTrackerApp
import com.iut.studytracker.data.StudySessionEntity
import com.iut.studytracker.data.TimerMode
import com.iut.studytracker.widget.StudyTrackerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import kotlin.math.roundToInt

data class ActiveTimerSnapshot(
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val subjectId: Long = 0L,
    val subjectName: String = "",
    val topicStudied: String = "",
    val mode: TimerMode = TimerMode.STOPWATCH,
    val targetSeconds: Long = 0L,
    val elapsedSeconds: Long = 0L,
    val sessionStartEpochMs: Long = 0L
)

class StudyTimerService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var tickerJob: Job? = null
    private var sirenJob: Job? = null

    private var baseElapsedSeconds: Long = 0L
    private var lastResumeRealtimeMs: Long = 0L
    private var isSavingInProgress: Boolean = false

    // Cached today's base progress for the active subject so the Lock Screen shows live % and progress bar!
    private var subjectBaseStudiedTodaySec: Long = 0L
    private var subjectDailyTargetSec: Long = 7200L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                stopSirenEffects(applicationContext)
                val subjectId = intent.getLongExtra(EXTRA_SUBJECT_ID, 0L)
                val subjectName = intent.getStringExtra(EXTRA_SUBJECT_NAME) ?: "Study Session"
                val topic = intent.getStringExtra(EXTRA_TOPIC) ?: ""
                val modeName = intent.getStringExtra(EXTRA_MODE) ?: TimerMode.STOPWATCH.name
                val targetSec = intent.getLongExtra(EXTRA_TARGET_SECONDS, 0L)
                startNewSession(subjectId, subjectName, topic, TimerMode.valueOf(modeName), targetSec)
            }
            ACTION_PAUSE -> pauseTimer()
            ACTION_RESUME -> resumeTimer()
            ACTION_STOP_AND_SAVE -> stopAndSaveSession(completedByTarget = false)
            ACTION_DISCARD -> discardSession()
            ACTION_STOP_SIREN -> {
                stopSirenEffects(applicationContext)
                stopSelf()
            }
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
        isSavingInProgress = false
        val nowEpoch = System.currentTimeMillis()
        baseElapsedSeconds = 0L
        lastResumeRealtimeMs = SystemClock.elapsedRealtime()

        // Load today's already-studied seconds and daily target for this subject so Lock Screen shows live %
        val app = application as StudyTrackerApp
        serviceScope.launch(Dispatchers.IO) {
            val todayIso = LocalDate.now().toString()
            val dao = app.database.dao()
            val subject = dao.getAllSubjectsOnce().find { it.id == subjectId }
            val todayDone = dao.getAllSessionsOnce()
                .filter { it.subjectId == subjectId && it.dateIso == todayIso }
                .sumOf { it.durationSeconds }
                .coerceAtLeast(0L)
            subjectBaseStudiedTodaySec = todayDone
            subjectDailyTargetSec = ((subject?.targetHoursPerWeek ?: 2.0f).coerceAtLeast(0.1f) * 3600f).toLong()
        }

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
        StudyTrackerWidgetProvider.updateAllWidgets(applicationContext)
        startTickerLoop()
    }

    private fun pauseTimer() {
        val current = _timerState.value
        if (!current.isRunning || current.isPaused) return
        val delta = ((SystemClock.elapsedRealtime() - lastResumeRealtimeMs) / 1000L).coerceAtLeast(0L)
        baseElapsedSeconds += delta
        tickerJob?.cancel()

        val updated = current.copy(isPaused = true, elapsedSeconds = baseElapsedSeconds)
        _timerState.value = updated
        persistState(updated)
        updateNotification(updated)
        StudyTrackerWidgetProvider.updateAllWidgets(applicationContext)
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
                val delta = ((SystemClock.elapsedRealtime() - lastResumeRealtimeMs) / 1000L).coerceAtLeast(0L)
                val totalElapsed = baseElapsedSeconds + delta
                val next = current.copy(elapsedSeconds = totalElapsed)
                _timerState.value = next
                persistState(next)
                updateNotification(next)

                // Update Home Screen & Lock Screen Summary every 15 seconds
                if (totalElapsed > 0L && totalElapsed % 15L == 0L) {
                    StudyTrackerWidgetProvider.updateAllWidgets(applicationContext)
                }

                val liveTodayTotal = subjectBaseStudiedTodaySec + totalElapsed
                val hitDailyTarget = subjectDailyTargetSec > 0L &&
                    subjectBaseStudiedTodaySec < subjectDailyTargetSec &&
                    liveTodayTotal >= subjectDailyTargetSec

                val hitCountdown = current.targetSeconds > 0L && totalElapsed >= current.targetSeconds

                if (hitCountdown || hitDailyTarget) {
                    stopAndSaveSession(completedByTarget = true)
                    break
                }
                delay(1000L)
            }
        }
    }

    private fun stopAndSaveSession(completedByTarget: Boolean) {
        if (isSavingInProgress) return
        val current = _timerState.value
        if (!current.isRunning && current.subjectId <= 0L) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        isSavingInProgress = true
        tickerJob?.cancel()

        val liveElapsed = if (current.isPaused) {
            current.elapsedSeconds
        } else {
            val delta = ((SystemClock.elapsedRealtime() - lastResumeRealtimeMs) / 1000L).coerceAtLeast(0L)
            (baseElapsedSeconds + delta).coerceAtLeast(current.elapsedSeconds)
        }
        val finalSeconds = liveElapsed.coerceAtLeast(1L)
        val endEpoch = System.currentTimeMillis()
        val startEpoch = if (current.sessionStartEpochMs > 0L) current.sessionStartEpochMs else (endEpoch - finalSeconds * 1000L)

        _timerState.value = ActiveTimerSnapshot()

        val app = application as StudyTrackerApp
        serviceScope.launch {
            withContext(Dispatchers.IO + NonCancellable) {
                if (current.subjectId > 0L && finalSeconds >= 1L) {
                    app.database.dao().insertSession(
                        StudySessionEntity(
                            subjectId = current.subjectId,
                            startTimeEpochMs = startEpoch,
                            endTimeEpochMs = endEpoch,
                            durationSeconds = finalSeconds,
                            mode = current.mode,
                            topicStudied = current.topicStudied.ifBlank { "Timer Study Session" },
                            focusRating = 5,
                            notes = if (completedByTarget) "Completed target (Siren)" else "Recorded via Subject Timer",
                            dateIso = LocalDate.now().toString()
                        )
                    )
                }
                clearPersistedState()
                StudyTrackerWidgetProvider.updateAllWidgets(applicationContext)
            }

            if (completedByTarget) {
                startSirenAlarm(current.subjectName, finalSeconds)
            } else {
                isSavingInProgress = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private fun startSirenAlarm(subjectName: String, durationSeconds: Long) {
        _isSirenActive.value = true

        runCatching {
            val manager = getSystemService(NotificationManager::class.java)
            val alertChannel = NotificationChannel(
                COMPLETE_CHANNEL_ID,
                "Study Timer Siren & Target Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Plays alarm siren when a subject study target or countdown finishes"
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                enableVibration(true)
            }
            manager.createNotificationChannel(alertChannel)

            val openIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopSirenIntent = PendingIntent.getService(
                this,
                3,
                Intent(this, StudyTimerService::class.java).apply { action = ACTION_STOP_SIREN },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val mins = (durationSeconds / 60L).coerceAtLeast(1L)
            val alertNotification = NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_timer_notification)
                .setContentTitle("🚨 TARGET TIME REACHED! • $subjectName")
                .setContentText("Completed ${mins}m of study (100% Target). Tap 'Stop Siren' to silence.")
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setOngoing(true)
                .setAutoCancel(true)
                .setContentIntent(openIntent)
                .addAction(0, "Stop Siren", stopSirenIntent)
                .build()

            manager.notify(COMPLETE_NOTIFICATION_ID, alertNotification)
        }

        sirenJob?.cancel()
        sirenJob = serviceScope.launch(Dispatchers.Default) {
            try {
                withContext(Dispatchers.Main) {
                    runCatching {
                        activeRingtone?.stop()
                        val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                        val r = RingtoneManager.getRingtone(applicationContext, uri)
                        r.audioAttributes = AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                        r.play()
                        activeRingtone = r
                    }
                }

                val toneGen = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
                @Suppress("DEPRECATION")
                val vibrator = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

                for (cycle in 0 until 25) {
                    if (!_isSirenActive.value) break
                    runCatching {
                        vibrator?.vibrate(VibrationEffect.createOneShot(450L, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    runCatching {
                        val tone = if (cycle % 2 == 0) ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK else ToneGenerator.TONE_CDMA_HIGH_L
                        toneGen?.startTone(tone, 450)
                    }
                    delay(550L)
                }
                runCatching { toneGen?.release() }
            } finally {
                stopSirenEffects(applicationContext)
                withContext(Dispatchers.Main) {
                    isSavingInProgress = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }
    }

    private fun discardSession() {
        tickerJob?.cancel()
        stopSirenEffects(applicationContext)
        clearPersistedState()
        _timerState.value = ActiveTimerSnapshot()
        StudyTrackerWidgetProvider.updateAllWidgets(applicationContext)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun updateNotification(snapshot: ActiveTimerSnapshot) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildNotification(snapshot))
    }

    private fun formatHm(seconds: Long): String {
        val safe = seconds.coerceAtLeast(0L)
        val h = safe / 3600L
        val m = (safe % 3600L) / 60L
        return "${h}h ${m}m"
    }

    private fun buildNotification(snapshot: ActiveTimerSnapshot): Notification {
        val displaySeconds = if (snapshot.mode == TimerMode.POMODORO && snapshot.targetSeconds > 0L) {
            (snapshot.targetSeconds - snapshot.elapsedSeconds).coerceAtLeast(0L)
        } else {
            snapshot.elapsedSeconds
        }
        val hrs = displaySeconds / 3600
        val mins = (displaySeconds % 3600) / 60
        val secs = displaySeconds % 60
        val formattedTime = String.format("%02d:%02d:%02d", hrs, mins, secs)

        // Calculate live study completion percentage for the Lock Screen card!
        val liveTodayStudiedSec = (subjectBaseStudiedTodaySec + snapshot.elapsedSeconds).coerceAtLeast(0L)
        val targetSec = subjectDailyTargetSec.coerceAtLeast(1L)
        val remTodaySec = (targetSec - liveTodayStudiedSec).coerceAtLeast(0L)
        val livePercent = ((liveTodayStudiedSec.toDouble() / targetSec.toDouble()) * 100.0)
            .roundToInt()
            .coerceIn(0, 100)

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

        val statusPrefix = if (snapshot.isPaused) "⏸ PAUSED" else "▶ STUDYING"
        val lockScreenBody = "$statusPrefix • Today: ${formatHm(liveTodayStudiedSec)} / ${formatHm(targetSec)} (${formatHm(remTodaySec)} left)"

        return NotificationCompat.Builder(this, StudyTrackerApp.TIMER_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_timer_notification)
            .setContentTitle("${snapshot.subjectName} • $formattedTime ($livePercent%)")
            .setContentText(lockScreenBody)
            .setSubText("$livePercent% of Daily Target")
            .setStyle(NotificationCompat.BigTextStyle().bigText(lockScreenBody))
            .setProgress(100, livePercent, false)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openIntent)
            .addAction(0, if (snapshot.isPaused) "Resume" else "Pause", toggleIntent)
            .addAction(0, "Save & Stop", saveIntent)
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
        const val COMPLETE_NOTIFICATION_ID = 1002
        const val COMPLETE_CHANNEL_ID = "study_timer_siren_alert_channel_v3"
        const val PREFS_NAME = "active_study_timer_prefs"

        const val ACTION_START = "com.iut.studytracker.ACTION_START"
        const val ACTION_PAUSE = "com.iut.studytracker.ACTION_PAUSE"
        const val ACTION_RESUME = "com.iut.studytracker.ACTION_RESUME"
        const val ACTION_STOP_AND_SAVE = "com.iut.studytracker.ACTION_STOP_AND_SAVE"
        const val ACTION_DISCARD = "com.iut.studytracker.ACTION_DISCARD"
        const val ACTION_STOP_SIREN = "com.iut.studytracker.ACTION_STOP_SIREN"

        const val EXTRA_SUBJECT_ID = "extra_subject_id"
        const val EXTRA_SUBJECT_NAME = "extra_subject_name"
        const val EXTRA_TOPIC = "extra_topic"
        const val EXTRA_MODE = "extra_mode"
        const val EXTRA_TARGET_SECONDS = "extra_target_seconds"

        private var activeRingtone: Ringtone? = null
        private val _isSirenActive = MutableStateFlow(false)
        val isSirenActive: StateFlow<Boolean> = _isSirenActive.asStateFlow()

        private val _timerState = MutableStateFlow(ActiveTimerSnapshot())
        val timerState: StateFlow<ActiveTimerSnapshot> = _timerState.asStateFlow()

        fun stopSirenEffects(context: Context) {
            _isSirenActive.value = false
            runCatching {
                activeRingtone?.stop()
                activeRingtone = null
            }
            runCatching {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                vibrator?.cancel()
            }
            runCatching {
                val manager = context.getSystemService(NotificationManager::class.java)
                manager?.cancel(COMPLETE_NOTIFICATION_ID)
            }
        }
    }
}
