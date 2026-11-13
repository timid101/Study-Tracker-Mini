package com.iut.studytracker.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.iut.studytracker.StudyTrackerApp
import com.iut.studytracker.data.DailyStudyTotal
import com.iut.studytracker.data.ExamScheduleEntity
import com.iut.studytracker.data.StudySessionEntity
import com.iut.studytracker.data.SubjectEntity
import com.iut.studytracker.data.SubjectStudySummary
import com.iut.studytracker.data.TaskCategory
import com.iut.studytracker.data.TaskItemEntity
import com.iut.studytracker.data.TaskPriority
import com.iut.studytracker.data.TimerMode
import com.iut.studytracker.data.TimerPresetEntity
import com.iut.studytracker.service.ActiveTimerSnapshot
import com.iut.studytracker.service.StudyTimerService
import com.iut.studytracker.util.SafBackupExportManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class StudyTrackerUiState(
    val subjects: List<SubjectEntity> = emptyList(),
    val subjectSummaries: List<SubjectStudySummary> = emptyList(),
    val sessions: List<StudySessionEntity> = emptyList(),
    val dailyTotals: List<DailyStudyTotal> = emptyList(),
    val tasks: List<TaskItemEntity> = emptyList(),
    val exams: List<ExamScheduleEntity> = emptyList(),
    val presets: List<TimerPresetEntity> = emptyList(),
    val activeTimer: ActiveTimerSnapshot = ActiveTimerSnapshot(),
    val currentStreakDays: Int = 0,
    val todaySeconds: Long = 0L,
    val statusBanner: String? = null
)

class StudyTrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val dao = (application as StudyTrackerApp).database.dao()
    private val statusMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<StudyTrackerUiState> = combine(
        dao.observeActiveSubjects(),
        dao.observeSubjectSummaries(),
        dao.observeAllSessions(),
        dao.observeDailyTotals(),
        dao.observeTasks(),
        dao.observeExams(),
        dao.observePresets(),
        StudyTimerService.timerState,
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
        @Suppress("UNCHECKED_CAST")
        val tasks = values[4] as List<TaskItemEntity>
        @Suppress("UNCHECKED_CAST")
        val exams = values[5] as List<ExamScheduleEntity>
        @Suppress("UNCHECKED_CAST")
        val presets = values[6] as List<TimerPresetEntity>
        val activeTimer = values[7] as ActiveTimerSnapshot
        val banner = values[8] as String?

        val todayStr = LocalDate.now().toString()
        val todayTotal = dailyTotals.find { it.dateIso == todayStr }?.totalSeconds ?: 0L
        val streak = calculateStreak(dailyTotals)

        StudyTrackerUiState(
            subjects = subjects,
            subjectSummaries = summaries,
            sessions = sessions,
            dailyTotals = dailyTotals,
            tasks = tasks,
            exams = exams,
            presets = presets,
            activeTimer = activeTimer,
            currentStreakDays = streak,
            todaySeconds = todayTotal,
            statusBanner = banner
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = StudyTrackerUiState()
    )

    private fun calculateStreak(totals: List<DailyStudyTotal>): Int {
        val activeDates = totals.filter { it.totalSeconds >= 600L }.map { it.dateIso }.toHashSet()
        var streak = 0
        var cursor = LocalDate.now()
        if (!activeDates.contains(cursor.toString())) {
            cursor = cursor.minusDays(1)
        }
        while (activeDates.contains(cursor.toString())) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun startForegroundTimer(
        subject: SubjectEntity,
        topic: String,
        mode: TimerMode,
        focusMinutes: Int
    ) {
        val ctx = getApplication<Application>()
        val intent = Intent(ctx, StudyTimerService::class.java).apply {
            action = StudyTimerService.ACTION_START
            putExtra(StudyTimerService.EXTRA_SUBJECT_ID, subject.id)
            putExtra(StudyTimerService.EXTRA_SUBJECT_NAME, "${subject.code} • ${subject.name}")
            putExtra(StudyTimerService.EXTRA_TOPIC, topic)
            putExtra(StudyTimerService.EXTRA_MODE, mode.name)
            putExtra(StudyTimerService.EXTRA_TARGET_SECONDS, focusMinutes * 60L)
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

    fun logManualSession(subjectId: Long, durationMinutes: Int, topic: String, focusRating: Int) {
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
                    focusRating = focusRating.coerceIn(1, 5),
                    notes = "Logged manually",
                    dateIso = LocalDate.now().toString()
                )
            )
            statusMessage.value = "Saved ${durationMinutes}m study session."
        }
    }

    fun addSubject(code: String, name: String, colorHex: String, targetWeeklyHours: Float) {
        viewModelScope.launch {
            dao.insertSubject(
                SubjectEntity(
                    code = code.trim(),
                    name = name.trim(),
                    colorHex = colorHex,
                    targetHoursPerWeek = targetWeeklyHours
                )
            )
            statusMessage.value = "Added course $code."
        }
    }

    fun addTask(subjectId: Long, title: String, category: TaskCategory, priority: TaskPriority, dueDateIso: String) {
        viewModelScope.launch {
            dao.insertTask(
                TaskItemEntity(
                    subjectId = subjectId,
                    title = title.trim(),
                    category = category,
                    priority = priority,
                    dueDateIso = dueDateIso
                )
            )
        }
    }

    fun toggleTaskCompleted(task: TaskItemEntity) {
        viewModelScope.launch {
            val next = !task.isCompleted
            dao.updateTask(
                task.copy(
                    isCompleted = next,
                    completedAtEpochMs = if (next) System.currentTimeMillis() else null
                )
            )
        }
    }

    fun addExam(subjectId: Long, title: String, dateIso: String, venue: String, syllabus: String) {
        viewModelScope.launch {
            dao.insertExam(
                ExamScheduleEntity(
                    subjectId = subjectId,
                    examTitle = title.trim(),
                    examDateIso = dateIso,
                    roomOrVenue = venue.trim(),
                    syllabusTopics = syllabus.trim()
                )
            )
        }
    }

    fun exportJsonBackup(uri: Uri) {
        viewModelScope.launch {
            val res = SafBackupExportManager.exportFullDatabaseJson(getApplication(), dao, uri)
            statusMessage.value = res.fold(
                onSuccess = { "Backed up full database ($it sessions) to JSON." },
                onFailure = { "Backup failed: ${it.localizedMessage}" }
            )
        }
    }

    fun importJsonBackup(uri: Uri) {
        viewModelScope.launch {
            val res = SafBackupExportManager.importFullDatabaseJson(getApplication(), dao, uri)
            statusMessage.value = res.fold(
                onSuccess = { "Restored database ($it sessions) from JSON." },
                onFailure = { "Restore failed: ${it.localizedMessage}" }
            )
        }
    }

    fun exportCsvForExcel(uri: Uri) {
        viewModelScope.launch {
            val res = SafBackupExportManager.exportSessionsToExcelCsv(getApplication(), dao, uri)
            statusMessage.value = res.fold(
                onSuccess = { "Exported $it sessions to Excel-ready CSV." },
                onFailure = { "CSV export failed: ${it.localizedMessage}" }
            )
        }
    }

    fun dismissBanner() {
        statusMessage.value = null
    }
}
