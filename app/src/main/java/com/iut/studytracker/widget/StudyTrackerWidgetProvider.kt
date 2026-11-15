package com.iut.studytracker.widget

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import com.iut.studytracker.MainActivity
import com.iut.studytracker.R
import com.iut.studytracker.data.StudyTrackerDatabase
import com.iut.studytracker.data.SubjectStudySummary
import com.iut.studytracker.service.StudyTimerService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.roundToInt

class StudyTrackerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        refreshWidgetsInternal(context.applicationContext, appWidgetManager, appWidgetIds, pendingResult)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH_WIDGET,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_USER_PRESENT -> {
                updateAllWidgets(context)
            }
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.iut.studytracker.ACTION_REFRESH_WIDGET"
        private const val LOCKSCREEN_SUMMARY_CHANNEL_ID = "study_lockscreen_daily_progress_channel"
        private const val LOCKSCREEN_SUMMARY_NOTIFICATION_ID = 1005

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, StudyTrackerWidgetProvider::class.java)
            )
            refreshWidgetsInternal(appContext, manager, ids, null)
        }

        private fun formatHm(seconds: Long): String {
            val safe = seconds.coerceAtLeast(0L)
            val h = safe / 3600L
            val m = (safe % 3600L) / 60L
            return "${h}h ${m}m"
        }

        private fun refreshWidgetsInternal(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray,
            pendingResult: BroadcastReceiver.PendingResult?
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val todayIso = LocalDate.now().toString()
                    val dao = StudyTrackerDatabase.getInstance(context).dao()
                    val activeSubjects = dao.getAllSubjectsOnce().filter { !it.isArchived }

                    // STRICTLY filter sessions by TODAY (todayIso) so each new day starts at 0%
                    val todaySessionsBySub = dao.getAllSessionsOnce()
                        .filter { it.dateIso == todayIso }
                        .groupBy { it.subjectId }

                    val activeSnapshot = StudyTimerService.timerState.value
                    val rawSummaries = activeSubjects.map { sub ->
                        val subTodaySec = todaySessionsBySub[sub.id].orEmpty()
                            .sumOf { it.durationSeconds }
                            .coerceAtLeast(0L)
                        val liveExtra = if (activeSnapshot.isRunning && activeSnapshot.subjectId == sub.id) {
                            activeSnapshot.elapsedSeconds
                        } else 0L
                        SubjectStudySummary(
                            subjectId = sub.id,
                            code = sub.code,
                            name = sub.name,
                            colorHex = sub.colorHex,
                            targetHoursPerWeek = sub.targetHoursPerWeek,
                            totalSeconds = (subTodaySec + liveExtra).coerceAtLeast(0L),
                            sessionCount = todaySessionsBySub[sub.id].orEmpty().size
                        )
                    }

                    // SORTING RULE FOR WIDGETS:
                    // 1st: The subject currently being studied right now (active timer running)
                    // 2nd: Subjects ordered by highest completion percentage descending (then studied seconds descending)
                    // Last: Unstudied subjects (0%) at the bottom
                    val summaries = rawSummaries.sortedWith(
                        compareByDescending<SubjectStudySummary> {
                            activeSnapshot.isRunning && it.subjectId == activeSnapshot.subjectId
                        }
                            .thenByDescending { it.completionPercent }
                            .thenByDescending { it.totalSeconds }
                            .thenBy { it.subjectId }
                    )

                    val totalStudiedSec = summaries.sumOf { it.totalSeconds.coerceAtLeast(0L) }
                    val totalTargetSec = summaries.sumOf { it.targetSeconds }
                    val overallPercent = if (totalTargetSec > 0L) {
                        ((totalStudiedSec.toDouble() / totalTargetSec.toDouble()) * 100.0)
                            .roundToInt()
                            .coerceIn(0, 100)
                    } else 0

                    val openAppIntent = PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    // 1. Update all Home Screen Widgets with sorted subjects
                    if (appWidgetIds.isNotEmpty()) {
                        for (widgetId in appWidgetIds) {
                            val views = RemoteViews(context.packageName, R.layout.widget_study_tracker)
                            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

                            val studiedHrs = String.format("%.1fh", totalStudiedSec / 3600.0)
                            val targetHrs = String.format("%.1fh", totalTargetSec / 3600.0)
                            views.setTextViewText(
                                R.id.widget_header_title,
                                "Today ($todayIso)"
                            )
                            views.setTextViewText(
                                R.id.widget_header_overall,
                                "$overallPercent% ($studiedHrs / $targetHrs)"
                            )

                            views.removeAllViews(R.id.widget_courses_container)

                            if (summaries.isEmpty()) {
                                views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                            } else {
                                views.setViewVisibility(R.id.widget_empty_view, View.GONE)
                                summaries.take(12).forEach { item ->
                                    val row = RemoteViews(context.packageName, R.layout.widget_course_row)
                                    val isCurrentlyStudying = activeSnapshot.isRunning &&
                                        activeSnapshot.subjectId == item.subjectId
                                    val baseTitle = if (item.code.isNotBlank()) {
                                        "${item.code} • ${item.name}"
                                    } else {
                                        item.name
                                    }
                                    val displayName = if (isCurrentlyStudying) {
                                        if (activeSnapshot.isPaused) "⏸ $baseTitle" else "▶ $baseTitle"
                                    } else {
                                        baseTitle
                                    }
                                    val pct = item.completionPercent.roundToInt().coerceIn(0, 100)
                                    val completedH = String.format("%.1fh", item.totalSeconds.coerceAtLeast(0L) / 3600.0)
                                    val targetH = String.format("%.1fh", item.targetHoursPerWeek)

                                    row.setTextViewText(R.id.row_course_name, displayName)
                                    row.setTextViewText(R.id.row_course_hours, "$completedH / $targetH")
                                    row.setTextViewText(R.id.row_course_percent, "$pct%")
                                    row.setProgressBar(R.id.row_course_progress, 100, pct, false)
                                    views.addView(R.id.widget_courses_container, row)
                                }
                            }

                            appWidgetManager.updateAppWidget(widgetId, views)
                        }
                    }

                    // 2. Update Lock-Screen Daily Study Progress Card using the same priority order
                    updateLockScreenProgressCard(
                        context = context,
                        todayIso = todayIso,
                        summaries = summaries,
                        activeSubjectId = if (activeSnapshot.isRunning) activeSnapshot.subjectId else 0L,
                        totalStudiedSec = totalStudiedSec,
                        totalTargetSec = totalTargetSec,
                        overallPercent = overallPercent,
                        openAppIntent = openAppIntent
                    )
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        private fun updateLockScreenProgressCard(
            context: Context,
            todayIso: String,
            summaries: List<SubjectStudySummary>,
            activeSubjectId: Long,
            totalStudiedSec: Long,
            totalTargetSec: Long,
            overallPercent: Int,
            openAppIntent: PendingIntent
        ) {
            runCatching {
                val nm = context.getSystemService(NotificationManager::class.java) ?: return
                if (summaries.isEmpty()) {
                    nm.cancel(LOCKSCREEN_SUMMARY_NOTIFICATION_ID)
                    return
                }

                val channel = NotificationChannel(
                    LOCKSCREEN_SUMMARY_CHANNEL_ID,
                    "Lock Screen Daily Study Progress",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Shows your daily study completion percentage on the lock screen"
                    lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
                    setShowBadge(false)
                }
                nm.createNotificationChannel(channel)

                val perCourseLines = summaries.joinToString("\n") { item ->
                    val pct = item.completionPercent.roundToInt().coerceIn(0, 100)
                    val title = if (item.code.isNotBlank()) "${item.code} (${item.name})" else item.name
                    val prefix = if (item.subjectId == activeSubjectId) "▶ " else "• "
                    "$prefix$title: $pct%  (${formatHm(item.totalSeconds)} / ${formatHm(item.targetSeconds)})"
                }

                val summaryTitle = "Today's Study: $overallPercent% (${formatHm(totalStudiedSec)} / ${formatHm(totalTargetSec)})"
                val notification = NotificationCompat.Builder(context, LOCKSCREEN_SUMMARY_CHANNEL_ID)
                    .setSmallIcon(R.drawable.ic_timer_notification)
                    .setContentTitle(summaryTitle)
                    .setContentText(perCourseLines.lines().firstOrNull() ?: "Tap to open Study Tracker")
                    .setSubText("Today $todayIso")
                    .setStyle(NotificationCompat.BigTextStyle().bigText(perCourseLines))
                    .setProgress(100, overallPercent, false)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .setContentIntent(openAppIntent)
                    .build()

                nm.notify(LOCKSCREEN_SUMMARY_NOTIFICATION_ID, notification)
            }
        }
    }
}
