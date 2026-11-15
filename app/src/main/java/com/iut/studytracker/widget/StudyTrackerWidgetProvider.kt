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
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
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

// =========================================================================
// WIDGET 1 & 2: PROGRESS BARS + PIE CHART WITH SUBJECT COLOR LEGEND
// Uses ONLY standard Android SDK views (android.R.layout.activity_list_item)
// and existing v2.0 R.id constants so it compiles without XML edits!
// =========================================================================
class StudyTrackerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        refreshAllWidgetsInternal(context.applicationContext, pendingResult)
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
            refreshAllWidgetsInternal(context.applicationContext, null)
        }

        private fun formatHm(seconds: Long): String {
            val safe = seconds.coerceAtLeast(0L)
            val h = safe / 3600L
            val m = (safe % 3600L) / 60L
            return "${h}h ${m}m"
        }

        private fun parseAndroidColor(hex: String): Int {
            return runCatching { Color.parseColor(hex) }.getOrDefault(Color.parseColor("#10B981"))
        }

        internal fun refreshAllWidgetsInternal(
            context: Context,
            pendingResult: BroadcastReceiver.PendingResult?
        ) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val appWidgetManager = AppWidgetManager.getInstance(context)
                    val listWidgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, StudyTrackerWidgetProvider::class.java)
                    )
                    val pieWidgetIds = appWidgetManager.getAppWidgetIds(
                        ComponentName(context, StudyPieWidgetProvider::class.java)
                    )

                    val todayIso = LocalDate.now().toString()
                    val dao = StudyTrackerDatabase.getInstance(context).dao()
                    val activeSubjects = dao.getAllSubjectsOnce().filter { !it.isArchived }

                    // Strictly filter sessions by TODAY (todayIso) so each new day starts at 0%
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

                    // Priority Sort:
                    // 1st: The subject currently being studied right now (active timer running)
                    // 2nd: Studied subjects ordered by highest completion percentage descending
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
                    val remainingPercent = (100 - overallPercent).coerceIn(0, 100)

                    val openAppIntent = PendingIntent.getActivity(
                        context,
                        0,
                        Intent(context, MainActivity::class.java),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )

                    val pieBitmap = renderPieChartAndLegendBitmap(
                        summaries = summaries,
                        activeSubjectId = if (activeSnapshot.isRunning) activeSnapshot.subjectId else 0L,
                        totalStudiedSec = totalStudiedSec,
                        totalTargetSec = totalTargetSec,
                        overallPercent = overallPercent,
                        remainingPercent = remainingPercent
                    )

                    // -----------------------------------------------------------------
                    // 1. Update WIDGET 1 (Shows Pie Chart + Sorted Subject Progress Bars)
                    // -----------------------------------------------------------------
                    if (listWidgetIds.isNotEmpty()) {
                        for (widgetId in listWidgetIds) {
                            val views = RemoteViews(context.packageName, R.layout.widget_study_tracker)
                            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

                            val studiedHrs = String.format("%.1fh", totalStudiedSec / 3600.0)
                            val targetHrs = String.format("%.1fh", totalTargetSec / 3600.0)
                            views.setTextViewText(R.id.widget_header_title, "Today ($todayIso)")
                            views.setTextViewText(
                                R.id.widget_header_overall,
                                "Studied: $overallPercent% • Left: $remainingPercent% ($studiedHrs/$targetHrs)"
                            )

                            views.removeAllViews(R.id.widget_courses_container)

                            if (summaries.isEmpty()) {
                                views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                            } else {
                                views.setViewVisibility(R.id.widget_empty_view, View.GONE)

                                // Add Pie Chart + Colored Subject Legend Row at the top of the widget container!
                                val pieRow = RemoteViews(context.packageName, android.R.layout.activity_list_item)
                                pieRow.setViewVisibility(android.R.id.text1, View.GONE)
                                pieRow.setImageViewBitmap(android.R.id.icon, pieBitmap)
                                views.addView(R.id.widget_courses_container, pieRow)

                                // Add Sorted Subject Progress Bar Rows below the Pie Chart
                                summaries.take(10).forEach { item ->
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
                                    row.setTextColor(R.id.row_course_name, parseAndroidColor(item.colorHex))
                                    row.setTextViewText(R.id.row_course_hours, "$completedH / $targetH")
                                    row.setTextViewText(R.id.row_course_percent, "$pct%")
                                    row.setTextColor(R.id.row_course_percent, parseAndroidColor(item.colorHex))
                                    row.setProgressBar(R.id.row_course_progress, 100, pct, false)
                                    views.addView(R.id.widget_courses_container, row)
                                }
                            }

                            appWidgetManager.updateAppWidget(widgetId, views)
                        }
                    }

                    // -----------------------------------------------------------------
                    // 2. Update WIDGET 2 (Standalone Pie Chart & Subject Color Legend Widget)
                    // -----------------------------------------------------------------
                    if (pieWidgetIds.isNotEmpty()) {
                        for (widgetId in pieWidgetIds) {
                            val views = RemoteViews(context.packageName, R.layout.widget_study_tracker)
                            views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)
                            views.removeAllViews(R.id.widget_courses_container)
                            views.setViewVisibility(R.id.widget_empty_view, View.GONE)

                            views.setTextViewText(R.id.widget_header_title, "Study Pie ($todayIso)")
                            views.setTextViewText(
                                R.id.widget_header_overall,
                                "Studied: $overallPercent% • Left: $remainingPercent%"
                            )

                            val pieRow = RemoteViews(context.packageName, android.R.layout.activity_list_item)
                            pieRow.setViewVisibility(android.R.id.text1, View.GONE)
                            pieRow.setImageViewBitmap(android.R.id.icon, pieBitmap)
                            views.addView(R.id.widget_courses_container, pieRow)

                            appWidgetManager.updateAppWidget(widgetId, views)
                        }
                    }

                    // -----------------------------------------------------------------
                    // 3. Update Lock-Screen Daily Study Progress Card
                    // -----------------------------------------------------------------
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

        private fun renderPieChartAndLegendBitmap(
            summaries: List<SubjectStudySummary>,
            activeSubjectId: Long,
            totalStudiedSec: Long,
            totalTargetSec: Long,
            overallPercent: Int,
            remainingPercent: Int
        ): Bitmap {
            val rowCount = summaries.size.coerceIn(1, 8)
            val bmpWidth = 540
            val bmpHeight = 200.coerceAtLeast(52 + rowCount * 26)
            val bitmap = Bitmap.createBitmap(bmpWidth, bmpHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

            // Left side: Pie Chart Circle
            val pieRadius = 66f
            val cx = 82f
            val cy = 78f
            val pieRect = RectF(cx - pieRadius, cy - pieRadius, cx + pieRadius, cy + pieRadius)

            // 1. Draw the Base Blank / White Pie Circle (represents 100% unstudied / remaining target)
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F3F4F6")
            canvas.drawOval(pieRect, paint)

            // 2. Fill studied slices in each subject's color proportional to Total Daily Target
            val denominatorSec = totalTargetSec.coerceAtLeast(totalStudiedSec).coerceAtLeast(1L)
            var currentAngle = -90f

            val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 2f
                color = Color.parseColor("#080B12")
            }

            summaries.forEach { item ->
                val cappedSec = item.totalSeconds.coerceAtLeast(0L)
                if (cappedSec > 0L) {
                    val sweep = ((cappedSec.toDouble() / denominatorSec.toDouble()) * 360.0).toFloat()
                        .coerceIn(0f, 360f - (currentAngle + 90f))
                    if (sweep > 0.5f) {
                        paint.style = Paint.Style.FILL
                        paint.color = parseAndroidColor(item.colorHex)
                        canvas.drawArc(pieRect, currentAngle, sweep, true, paint)
                        canvas.drawArc(pieRect, currentAngle, sweep, true, dividerPaint)
                        currentAngle += sweep
                    }
                }
            }

            // Outer ring border around the pie chart
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 2.5f
            paint.color = Color.parseColor("#374151")
            canvas.drawOval(pieRect, paint)

            // Bottom captions under the Pie Chart: Studied % vs Remaining %
            val remSec = (totalTargetSec - totalStudiedSec).coerceAtLeast(0L)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 15f
            textPaint.color = Color.parseColor("#10B981")
            canvas.drawText(
                "Studied: $overallPercent% (${formatHm(totalStudiedSec)})",
                cx,
                cy + pieRadius + 22f,
                textPaint
            )

            textPaint.textSize = 13.5f
            textPaint.color = Color.parseColor("#E5E7EB")
            canvas.drawText(
                "Left: $remainingPercent% (${formatHm(remSec)})",
                cx,
                cy + pieRadius + 40f,
                textPaint
            )

            // Right side: Subject Color Legend (with Subject Name, Color Swatch, and Studied % vs Unstudied)
            val legendStartX = 174f
            var curY = 24f
            textPaint.textAlign = Paint.Align.LEFT

            // Legend Row 0: White/Blank = Remaining Target
            paint.style = Paint.Style.FILL
            paint.color = Color.parseColor("#F3F4F6")
            canvas.drawRoundRect(
                RectF(legendStartX, curY - 12f, legendStartX + 14f, curY + 2f),
                3f,
                3f,
                paint
            )
            textPaint.textSize = 14f
            textPaint.color = Color.parseColor("#D1D5DB")
            canvas.drawText(
                "White = Remaining Target ($remainingPercent%)",
                legendStartX + 20f,
                curY,
                textPaint
            )
            curY += 24f

            if (summaries.isEmpty()) {
                textPaint.textSize = 14f
                textPaint.color = Color.parseColor("#9CA3AF")
                canvas.drawText("Tap to add your subjects", legendStartX, curY + 14f, textPaint)
            } else {
                summaries.take(8).forEach { item ->
                    val isActive = item.subjectId == activeSubjectId
                    val subPct = item.completionPercent.roundToInt().coerceIn(0, 100)
                    val isStudied = item.totalSeconds > 0L

                    paint.style = Paint.Style.FILL
                    paint.color = parseAndroidColor(item.colorHex)
                    canvas.drawRoundRect(
                        RectF(legendStartX, curY - 12f, legendStartX + 14f, curY + 2f),
                        3f,
                        3f,
                        paint
                    )

                    val rawName = if (item.code.isNotBlank()) "${item.code} (${item.name})" else item.name
                    val clippedName = if (rawName.length > 18) rawName.take(17) + "…" else rawName
                    val prefix = if (isActive) "▶ " else ""
                    val statusSuffix = if (isStudied) {
                        "$subPct% (${formatHm(item.totalSeconds)}/${formatHm(item.targetSeconds)})"
                    } else {
                        "0% (Not studied)"
                    }

                    textPaint.textSize = 14f
                    textPaint.color = if (isActive) {
                        Color.parseColor("#10B981")
                    } else if (isStudied) {
                        Color.WHITE
                    } else {
                        Color.parseColor("#9CA3AF")
                    }

                    canvas.drawText(
                        "$prefix$clippedName: $statusSuffix",
                        legendStartX + 20f,
                        curY,
                        textPaint
                    )
                    curY += 23f
                }
            }

            return bitmap
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

// =========================================================================
// WIDGET 2: STANDALONE PIE CHART & SUBJECT COLOR LEGEND WIDGET
// =========================================================================
class StudyPieWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        val pendingResult = goAsync()
        StudyTrackerWidgetProvider.refreshAllWidgetsInternal(context.applicationContext, pendingResult)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            StudyTrackerWidgetProvider.ACTION_REFRESH_WIDGET,
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_USER_PRESENT -> {
                StudyTrackerWidgetProvider.updateAllWidgets(context)
            }
        }
    }
}
