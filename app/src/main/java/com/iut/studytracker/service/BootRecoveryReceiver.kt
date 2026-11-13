package com.iut.studytracker.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.iut.studytracker.StudyTrackerApp
import com.iut.studytracker.data.StudySessionEntity
import com.iut.studytracker.data.TimerMode
import com.iut.studytracker.widget.StudyTrackerWidgetProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate

class BootRecoveryReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED &&
            intent.action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) return

        val prefs = context.getSharedPreferences(StudyTimerService.PREFS_NAME, Context.MODE_PRIVATE)
        val wasRunning = prefs.getBoolean("isRunning", false)
        val elapsedSeconds = prefs.getLong("elapsedSeconds", 0L)
        val subjectId = prefs.getLong("subjectId", 0L)
        if (wasRunning && subjectId > 0L && elapsedSeconds >= 30L) {
            val topic = prefs.getString("topicStudied", "Recovered Session") ?: "Recovered Session"
            val modeName = prefs.getString("mode", TimerMode.STOPWATCH.name) ?: TimerMode.STOPWATCH.name
            val startEpoch = prefs.getLong("sessionStartEpochMs", System.currentTimeMillis() - elapsedSeconds * 1000L)

            val app = context.applicationContext as? StudyTrackerApp ?: return
            CoroutineScope(Dispatchers.IO).launch {
                app.database.dao().insertSession(
                    StudySessionEntity(
                        subjectId = subjectId,
                        startTimeEpochMs = startEpoch,
                        endTimeEpochMs = startEpoch + elapsedSeconds * 1000L,
                        durationSeconds = elapsedSeconds,
                        mode = runCatching { TimerMode.valueOf(modeName) }.getOrDefault(TimerMode.STOPWATCH),
                        topicStudied = "$topic (Recovered after reboot)",
                        focusRating = 5,
                        notes = "Recovered by BootRecoveryReceiver",
                        dateIso = LocalDate.now().toString()
                    )
                )
                prefs.edit().clear().apply()
                StudyTrackerWidgetProvider.updateAllWidgets(context)
            }
        } else {
            StudyTrackerWidgetProvider.updateAllWidgets(context)
        }
    }
}
