package com.iut.studytracker

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.iut.studytracker.data.StudyTrackerDatabase

class StudyTrackerApp : Application() {
    val database: StudyTrackerDatabase by lazy { StudyTrackerDatabase.getInstance(this) }

    override fun onCreate() {
        super.onCreate()
        createTimerNotificationChannel()
    }

    private fun createTimerNotificationChannel() {
        val channel = NotificationChannel(
            TIMER_CHANNEL_ID,
            "Active Study Timer",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Persistent lock-screen controls for active study sessions"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val TIMER_CHANNEL_ID = "study_timer_foreground_channel"
    }
}
