package com.iut.studytracker.viewmodel

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iut.studytracker.StudyTrackerApp
import com.iut.studytracker.data.DailyStudyTotal
import com.iut.studytracker.data.StudySessionEntity
import com.iut.studytracker.data.SubjectEntity
import com.iut.studytracker.data.SubjectStudySummary
import com.iut.studytracker.data.TimerMode
import com.iut.studytracker.service.ActiveTimerSnapshot
import com.iut.studytracker.service.StudyTimerService
import com.iut.studytracker.util.SafBackupExportManager
import com.iut.studytracker.widget.StudyTrackerWidgetProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class AppPreferencesState(
    val defaultMode: TimerMode = TimerMode.STOPWATCH,
    val defaultCountdownMinutes: Int = 45,
    val autoStopAtTarget: Boolean = false
)

data class StudyTrackerUiState(
    val subjects: List<SubjectEntity> = emptyList(),
    val subjectSummaries: List<SubjectStudySummary> = emptyList(),
    val sessions: List<StudySessionEntity> = emptyList(),
    val dailyTotals: List<DailyStudyTotal> = emptyList(),
    val activeTimer: ActiveTimerSnapshot = ActiveTimerSnapshot(),
    val prefs: AppPreferencesState = AppPreferencesState(),
    val statusBanner: String? = null
)

class StudyTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as StudyTrackerApp).database.dao()
    private val settingsPrefs = application.getSharedPreferences("study_tracker_general_settings", Context.MODE_PRIVATE)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val prefsFlow = MutableStateFlow(loadPrefs())

    private fun loadPrefs(): AppPreferencesState {
        val modeStr = settingsPrefs.getString("defaultMode", TimerMode.STOPWATCH.name) ?: TimerMode.STOPWATCH.name
        val mins = settingsPrefs.getInt("defaultCountdownMinutes", 45)
        val autoStop = settingsPrefs.getBoolean("autoStopAtTarget", false)
        return AppPreferencesState(
            defaultMode = runCatching { TimerMode.valueOf(modeStr) }.getOrDefault(TimerMode.STOPWATCH),
            defaultCountdownMinutes = mins,
            autoStopAtTarget = autoStop
        )
    }

    val uiState: StateFlow<StudyTrackerUiState> = combine(
        dao.observeActiveSubjects(),
        dao.observeSubjectSummaries(),
        dao.observeAllSessions(),
        dao.observeDailyTotals(),
        StudyTimerService.timerState,
        prefsFlow,
        statusMessage
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        val subjects = values[0] as List<SubjectEntity>
        @Suppress("UNCHECKED_CAST")
        val summaries = values[1] as List<SubjectStudySummary>
        @Suppress("UNCHECKED_CAST")
        val sessions = values[2] as List<StudySessionEntity>
        @Suppress("UNCHECKED_CAST")
        val dailyTotals = values[3] as List<DailyStudyTotal>
        val activeTimer = values[4] as ActiveTimerSnapshot
        val prefs = values[5] as AppPreferencesState
        val banner = values[6] as String?

        StudyTrackerUiState(
            subjects = subjects,
            subjectSummaries = summaries,
            sessions = sessions,
            dailyTotals = dailyTotals,
            activeTimer = activeTimer,
            prefs = prefs,
            statusBanner = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudyTrackerUiState()
    )

    fun addSubject(code: String, name: String, targetHours: Float, colorHex: String) {
        viewModelScope.launch {
            dao.insertSubject(
                SubjectEntity(
                    code = code.trim(),
                    name = name.trim(),
                    colorHex = colorHex,
                    targetHoursPerWeek = targetHours.coerceAtLeast(0.5f)
                )
            )
            StudyTrackerWidgetProvider.updateAllWidgets(getApplication())
            statusMessage.value = "Added subject \"$name\" with ${targetHours}h target."
        }
    }

    fun updateSubjectTargetAndName(subjectId: Long, newCode: String, newName: String, newTargetHours: Float, colorHex: String) {
        viewModelScope.launch {
            val existing = uiState.value.subjects.find { it.id == subjectId } ?: return@launch
            dao.updateSubject(
                existing.copy(
                    code = newCode.trim(),
                    name = newName.trim(),
                    targetHoursPerWeek = newTargetHours.coerceAtLeast(0.5f),
                    colorHex = colorHex
                )
            )
            StudyTrackerWidgetProvider.updateAllWidgets(getApplication())
            statusMessage.value = "Updated target for \"$newName\" to ${newTargetHours}h."
        }
    }

    fun deleteSubject(subjectId: Long) {
        viewModelScope.launch {
            val existing = uiState.value.subjects.find { it.id == subjectId } ?: return@launch
            dao.deleteSubject(existing)
            StudyTrackerWidgetProvider.updateAllWidgets(getApplication())
            statusMessage.value = "Deleted subject \"${existing.name}\"."
        }
    }

    fun resetSubjectProgress(subjectId: Long) {
        viewModelScope.launch {
            dao.clearSessionsForSubject(subjectId)
            StudyTrackerWidgetProvider.updateAllWidgets(getApplication())
            statusMessage.value = "Reset studied hours for subject."
        }
    }

    fun startSubjectTimer(
        summary: SubjectStudySummary,
        mode: TimerMode,
        countdownMinutes: Int,
        noteOrTopic: String = ""
    ) {
        val ctx = getApplication<Application>()
        val displayTitle = if (summary.code.isNotBlank()) "${summary.code} • ${summary.name}" else summary.name
        val targetSec = if (mode == TimerMode.POMODORO) {
            countdownMinutes.coerceAtLeast(1) * 60L
        } else {
            if (prefsFlow.value.autoStopAtTarget && summary.remainingSeconds > 0L) {
                summary.remainingSeconds
            } else 0L
        }

        val intent = Intent(ctx, StudyTimerService::class.java).apply {
            action = StudyTimerService.ACTION_START
            putExtra(StudyTimerService.EXTRA_SUBJECT_ID, summary.subjectId)
            putExtra(StudyTimerService.EXTRA_SUBJECT_NAME, displayTitle)
            putExtra(StudyTimerService.EXTRA_TOPIC, noteOrTopic)
            putExtra(StudyTimerService.EXTRA_MODE, mode.name)
            putExtra(StudyTimerService.EXTRA_TARGET_SECONDS, targetSec)
        }
        ctx.startForegroundService(intent)
    }

    fun pauseOrResumeTimer(isPaused: Boolean) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, StudyTimerService::class.java).apply {
            action = if (isPaused) StudyTimerService.ACTION_RESUME else StudyTimerService.ACTION_PAUSE
        }
        ctx.startService(intent)
    }

    fun stopAndSaveTimer() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, StudyTimerService::class.java).apply {
            action = StudyTimerService.ACTION_STOP_AND_SAVE
        }
        ctx.startService(intent)
    }

    fun discardActiveTimer() {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, StudyTimerService::class.java).apply {
            action = StudyTimerService.ACTION_DISCARD
        }
        ctx.startService(intent)
    }

    fun logManualSession(subjectId: Long, durationMinutes: Int, topic: String) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val sec = durationMinutes.coerceAtLeast(1) * 60L
            dao.insertSession(
                StudySessionEntity(
                    subjectId = subjectId,
                    startTimeEpochMs = now - sec * 1000L,
                    endTimeEpochMs = now,
                    durationSeconds = sec,
                    mode = TimerMode.STOPWATCH,
                    topicStudied = topic.ifBlank { "Manual Study Log" },
                    focusRating = 5,
                    notes = "Added manually",
                    dateIso = LocalDate.now().toString()
                )
            )
            StudyTrackerWidgetProvider.updateAllWidgets(getApplication())
            statusMessage.value = "Added ${durationMinutes}m to study progress."
        }
    }

    fun updateGeneralSettings(defaultMode: TimerMode, defaultCountdownMinutes: Int, autoStopAtTarget: Boolean) {
        settingsPrefs.edit().apply {
            putString("defaultMode", defaultMode.name)
            putInt("defaultCountdownMinutes", defaultCountdownMinutes.coerceIn(5, 360))
            putBoolean("autoStopAtTarget", autoStopAtTarget)
            apply()
        }
        prefsFlow.value = loadPrefs()
        statusMessage.value = "Saved general settings."
    }

    fun resetAllProgressKeepSubjects() {
        viewModelScope.launch {
            dao.clearSessions()
            StudyTrackerWidgetProvider.updateAllWidgets(getApplication())
            statusMessage.value = "Reset all studied hours to 0h (kept your subjects)."
        }
    }

    fun clearAllSubjectsAndData() {
        viewModelScope.launch {
            dao.clearSessions()
            dao.clearSubjects()
            StudyTrackerWidgetProvider.updateAllWidgets(getApplication())
            statusMessage.value = "Cleared all subjects and study history."
        }
    }

    fun refreshHomeWidgetNow() {
        StudyTrackerWidgetProvider.updateAllWidgets(getApplication())
        statusMessage.value = "Refreshed Android Home Screen Widget."
    }

    fun exportJsonBackup(uri: Uri) {
        viewModelScope.launch {
            val res = SafBackupExportManager.exportFullDatabaseJson(getApplication(), dao, uri)
            statusMessage.value = res.fold(
                onSuccess = { "Backed up $it subjects & progress to JSON." },
                onFailure = { "Backup failed: ${it.localizedMessage}" }
            )
        }
    }

    fun importJsonBackup(uri: Uri) {
        viewModelScope.launch {
            val res = SafBackupExportManager.importFullDatabaseJson(getApplication(), dao, uri)
            StudyTrackerWidgetProvider.updateAllWidgets(getApplication())
            statusMessage.value = res.fold(
                onSuccess = { "Restored $it subjects & study logs from JSON." },
                onFailure = { "Restore failed: ${it.localizedMessage}" }
            )
        }
    }

    fun exportCsvForExcel(uri: Uri) {
        viewModelScope.launch {
            val res = SafBackupExportManager.exportSessionsToExcelCsv(getApplication(), dao, uri)
            statusMessage.value = res.fold(
                onSuccess = { "Exported $it subjects & session logs to CSV." },
                onFailure = { "CSV export failed: ${it.localizedMessage}" }
            )
        }
    }

    fun dismissBanner() {
        statusMessage.value = null
    }
}
