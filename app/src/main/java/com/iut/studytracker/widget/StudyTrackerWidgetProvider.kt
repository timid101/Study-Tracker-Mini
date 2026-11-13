package com.iut.studytracker.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.iut.studytracker.MainActivity
import com.iut.studytracker.R
import com.iut.studytracker.data.StudyTrackerDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

class StudyTrackerWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        refreshWidgetsAsync(context, appWidgetManager, appWidgetIds)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == ACTION_REFRESH_WIDGET) {
            updateAllWidgets(context)
        }
    }

    companion object {
        const val ACTION_REFRESH_WIDGET = "com.iut.studytracker.ACTION_REFRESH_WIDGET"

        fun updateAllWidgets(context: Context) {
            val appContext = context.applicationContext
            val manager = AppWidgetManager.getInstance(appContext)
            val ids = manager.getAppWidgetIds(
                ComponentName(appContext, StudyTrackerWidgetProvider::class.java)
            )
            if (ids.isNotEmpty()) {
                refreshWidgetsAsync(appContext, manager, ids)
            }
        }

        private fun refreshWidgetsAsync(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetIds: IntArray
        ) {
            val pendingResult = goAsyncIfPossible()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val dao = StudyTrackerDatabase.getInstance(context).dao()
                    val summaries = dao.getSubjectSummariesOnce()

                    val totalStudiedSec = summaries.sumOf { it.totalSeconds }
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

                    for (widgetId in appWidgetIds) {
                        val views = RemoteViews(context.packageName, R.layout.widget_study_tracker)
                        views.setOnClickPendingIntent(R.id.widget_root, openAppIntent)

                        val studiedHrs = String.format("%.1fh", totalStudiedSec / 3600.0)
                        val targetHrs = String.format("%.1fh", totalTargetSec / 3600.0)
                        views.setTextViewText(
                            R.id.widget_header_overall,
                            "$overallPercent% ($studiedHrs / $targetHrs)"
                        )

                        views.removeAllViews(R.id.widget_courses_container)

                        if (summaries.isEmpty()) {
                            views.setViewVisibility(R.id.widget_empty_view, View.VISIBLE)
                        } else {
                            views.setViewVisibility(R.id.widget_empty_view, View.GONE)
                            summaries.take(8).forEach { item ->
                                val row = RemoteViews(context.packageName, R.layout.widget_course_row)
                                val displayName = if (item.code.isNotBlank()) {
                                    "${item.code} • ${item.name}"
                                } else {
                                    item.name
                                }
                                val pct = item.completionPercent.roundToInt().coerceIn(0, 100)
                                val completedH = String.format("%.1fh", item.totalSeconds / 3600.0)
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
                } finally {
                    pendingResult?.finish()
                }
            }
        }

        private fun goAsyncIfPossible(): BroadcastReceiver.PendingResult? = null
    }
}
