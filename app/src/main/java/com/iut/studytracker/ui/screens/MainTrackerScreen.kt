package com.iut.studytracker.ui.screens

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iut.studytracker.data.SubjectStudySummary
import com.iut.studytracker.data.TimerMode
import com.iut.studytracker.ui.components.formatClockHhMmSs
import com.iut.studytracker.ui.components.formatDurationHoursMinutes
import com.iut.studytracker.ui.components.parseHexColor
import com.iut.studytracker.viewmodel.StudyTrackerUiState
import com.iut.studytracker.viewmodel.StudyTrackerViewModel
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

private val SUBJECT_COLOR_PALETTE = listOf(
    "#10B981", "#3B82F6", "#F59E0B", "#8B5CF6", "#EC4899", "#06B6D4", "#EF4444", "#84CC16"
)

@Composable
fun MainTrackerScreen(viewModel: StudyTrackerViewModel) {
    val rawState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Track today's date (YYYY-MM-DD) and automatically roll over at midnight so each new day starts at 0m (0%)
    var todayIso by remember { mutableStateOf(LocalDate.now().toString()) }
    LaunchedEffect(Unit) {
        while (true) {
            val currentDay = LocalDate.now().toString()
            if (todayIso != currentDay) {
                todayIso = currentDay
                viewModel.refreshHomeWidgetNow()
            }
            delay(10_000L)
        }
    }

    // Compute TODAY'S subject summaries strictly from sessions recorded today (dateIso == todayIso)
    val todaySummaries = remember(rawState.subjects, rawState.sessions, todayIso) {
        val todaySessionsBySub = rawState.sessions
            .filter { it.dateIso == todayIso }
            .groupBy { it.subjectId }

        rawState.subjects.map { sub ->
            val subSessions = todaySessionsBySub[sub.id].orEmpty()
            val todaySec = subSessions.sumOf { it.durationSeconds }.coerceAtLeast(0L)
            SubjectStudySummary(
                subjectId = sub.id,
                code = sub.code,
                name = sub.name,
                colorHex = sub.colorHex,
                targetHoursPerWeek = sub.targetHoursPerWeek,
                totalSeconds = todaySec,
                sessionCount = subSessions.size
            )
        }
    }

    val state = remember(rawState, todaySummaries) {
        rawState.copy(subjectSummaries = todaySummaries)
    }

    // Siren Alarm state
    var isSirenRinging by remember { mutableStateOf(false) }
    var sirenSubjectTitle by remember { mutableStateOf("") }
    var activeRingtone by remember { mutableStateOf<Ringtone?>(null) }

    // Helper that reliably saves active timer session using logManualSession + discardActiveTimer
    val saveAndStopCurrentTimer: (String) -> Unit = { noteLabel ->
        val active = state.activeTimer
        if (active.isRunning && active.subjectId > 0L) {
            val minsToSave = if (active.elapsedSeconds < 60L) 1 else (active.elapsedSeconds / 60L).toInt()
            viewModel.logManualSession(active.subjectId, minsToSave, noteLabel)
            viewModel.discardActiveTimer()
            viewModel.refreshHomeWidgetNow()
        } else {
            viewModel.stopAndSaveTimer()
        }
    }

    // Monitor running timer: Refresh widget regularly AND trigger Siren when subject's daily target or countdown is reached!
    LaunchedEffect(
        state.activeTimer.isRunning,
        state.activeTimer.elapsedSeconds,
        state.activeTimer.subjectId,
        todaySummaries
    ) {
        val active = state.activeTimer
        if (active.isRunning && active.subjectId > 0L) {
            if (active.elapsedSeconds > 0L && active.elapsedSeconds % 30L == 0L) {
                viewModel.refreshHomeWidgetNow()
            }
            val subSummary = todaySummaries.find { it.subjectId == active.subjectId }
            val subTargetSec = subSummary?.targetSeconds ?: 0L
            val alreadyStudiedTodaySec = subSummary?.totalSeconds ?: 0L
            val totalTodayWithLive = alreadyStudiedTodaySec + active.elapsedSeconds

            val hitCountdown = active.mode == TimerMode.POMODORO &&
                active.targetSeconds > 0L &&
                active.elapsedSeconds >= active.targetSeconds

            // Trigger siren when total study time today for this subject reaches its daily target (e.g. 1 hour)
            val hitDailySubjectTarget = subTargetSec > 0L &&
                alreadyStudiedTodaySec < subTargetSec &&
                totalTodayWithLive >= subTargetSec

            if (hitCountdown || hitDailySubjectTarget) {
                sirenSubjectTitle = active.subjectName.ifBlank { subSummary?.name ?: "Subject" }
                saveAndStopCurrentTimer("Completed Daily Target (Siren)")
                isSirenRinging = true
            }
        }
    }

    // Play loud alternating Siren + Alarm Ringtone + Vibration while isSirenRinging is true
    LaunchedEffect(isSirenRinging) {
        if (isSirenRinging) {
            runCatching {
                activeRingtone?.stop()
                val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                val r = RingtoneManager.getRingtone(context, uri)
                r.audioAttributes = AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
                r.play()
                activeRingtone = r
            }

            val toneGen = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

            try {
                for (cycle in 0 until 30) {
                    if (!isSirenRinging) break
                    runCatching {
                        vibrator?.vibrate(VibrationEffect.createOneShot(450L, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    runCatching {
                        val tone = if (cycle % 2 == 0) {
                            ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
                        } else {
                            ToneGenerator.TONE_CDMA_HIGH_L
                        }
                        toneGen?.startTone(tone, 450)
                    }
                    delay(550L)
                }
            } finally {
                runCatching { toneGen?.release() }
                runCatching {
                    activeRingtone?.stop()
                    activeRingtone = null
                }
                isSirenRinging = false
            }
        } else {
            runCatching {
                activeRingtone?.stop()
                activeRingtone = null
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { activeRingtone?.stop() }
        }
    }

    // Dialog states
    var showAddSubjectDialog by remember { mutableStateOf(false) }
    var editingSummary by remember { mutableStateOf<SubjectStudySummary?>(null) }
    var loggingSummary by remember { mutableStateOf<SubjectStudySummary?>(null) }
    var deletingSummary by remember { mutableStateOf<SubjectStudySummary?>(null) }
    var confirmResetMode by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.statusBanner) {
        state.statusBanner?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.dismissBanner()
        }
    }

    val createJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri -> uri?.let { viewModel.exportJsonBackup(it) } }

    val openJsonLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> uri?.let { viewModel.importJsonBackup(it) } }

    val createCsvLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv")
    ) { uri -> uri?.let { viewModel.exportCsvForExcel(it) } }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selectedTab == 0 || selectedTab == 1) {
                ExtendedFloatingActionButton(
                    onClick = { showAddSubjectDialog = true },
                    containerColor = Color(0xFF10B981),
                    contentColor = Color.Black,
                    icon = { Icon(Icons.Default.Add, contentDescription = "Add Subject") },
                    text = { Text("Add Subject", fontWeight = FontWeight.Bold) }
                )
            }
        },
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF070A10)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Book, contentDescription = "Subjects") },
                    label = { Text("Subjects") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Subject Timers") },
                    label = { Text("Timers") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.DateRange, contentDescription = "Study Calendar") },
                    label = { Text("Calendar") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                    label = { Text("Settings") }
                )
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Loud Siren Alert Banner (visible on any tab when target time is reached)
            if (isSirenRinging) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF7F1D1D)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                        .border(2.dp, Color(0xFFEF4444), RoundedCornerShape(16.dp))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.NotificationsActive,
                                contentDescription = null,
                                tint = Color.White
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "TARGET REACHED • $sirenSubjectTitle",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    text = "Siren alarm is ringing • Session saved to today's log",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFECACA)
                                )
                            }
                        }
                        Button(
                            onClick = { isSirenRinging = false },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color.White,
                                contentColor = Color(0xFF991B1B)
                            )
                        ) {
                            Text("STOP SIREN", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> SubjectsFirstInterfaceTab(
                    todayIso = todayIso,
                    state = state,
                    onAddSubjectClick = { showAddSubjectDialog = true },
                    onEditSubjectClick = { editingSummary = it },
                    onLogManualClick = { loggingSummary = it },
                    onDeleteSubjectClick = { deletingSummary = it },
                    onJumpToTimerClick = { summary ->
                        if (state.activeTimer.isRunning && state.activeTimer.subjectId != summary.subjectId) {
                            saveAndStopCurrentTimer("Saved before switching subject")
                        }
                        viewModel.startSubjectTimer(
                            summary = summary,
                            mode = state.prefs.defaultMode,
                            countdownMinutes = state.prefs.defaultCountdownMinutes
                        )
                        selectedTab = 1
                    }
                )
                1 -> SubjectTimersTab(
                    state = state,
                    viewModel = viewModel,
                    onSaveAndStopTimer = { saveAndStopCurrentTimer("Timer Study Session") },
                    onStartSubjectTimerWithAutoSave = { summary, mode, mins ->
                        if (state.activeTimer.isRunning && state.activeTimer.subjectId != summary.subjectId) {
                            saveAndStopCurrentTimer("Saved before switching subject")
                        }
                        viewModel.startSubjectTimer(summary, mode, mins)
                    },
                    onAddSubjectClick = { showAddSubjectDialog = true }
                )
                2 -> StudyCalendarTab(state = state)
                3 -> GeneralSettingsTab(
                    state = state,
                    viewModel = viewModel,
                    onTestSirenClick = {
                        sirenSubjectTitle = "Siren Alarm Test"
                        isSirenRinging = true
                    },
                    onBackupJson = { createJsonLauncher.launch("study_tracker_backup_${LocalDate.now()}.json") },
                    onRestoreJson = { openJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                    onExportCsv = { createCsvLauncher.launch("study_progress_${LocalDate.now()}.csv") },
                    onRequestResetHours = { confirmResetMode = "RESET_HOURS" },
                    onRequestDeleteAll = { confirmResetMode = "DELETE_ALL" }
                )
            }
        }
    }

    if (showAddSubjectDialog) {
        AddOrEditSubjectDialog(
            initialSummary = null,
            onDismiss = { showAddSubjectDialog = false },
            onConfirmAndClose = { code, name, targetHrs, colorHex ->
                viewModel.addSubject(code, name, targetHrs, colorHex)
                showAddSubjectDialog = false
            },
            onConfirmAndAddAnother = { code, name, targetHrs, colorHex ->
                viewModel.addSubject(code, name, targetHrs, colorHex)
            }
        )
    }

    editingSummary?.let { item ->
        AddOrEditSubjectDialog(
            initialSummary = item,
            onDismiss = { editingSummary = null },
            onConfirmAndClose = { code, name, targetHrs, colorHex ->
                viewModel.updateSubjectTargetAndName(item.subjectId, code, name, targetHrs, colorHex)
                editingSummary = null
            },
            onConfirmAndAddAnother = null
        )
    }

    loggingSummary?.let { item ->
        ManualLogDialog(
            summary = item,
            onDismiss = { loggingSummary = null },
            onAddMinutes = { mins, note ->
                viewModel.logManualSession(item.subjectId, mins, note)
                loggingSummary = null
            },
            onSubtractMinutes = { mins ->
                val currentMins = (item.totalSeconds / 60L).toInt()
                val newTotalMins = (currentMins - mins).coerceAtLeast(0)
                viewModel.resetSubjectProgress(item.subjectId)
                if (newTotalMins > 0) {
                    viewModel.logManualSession(item.subjectId, newTotalMins, "Adjusted study time (-${mins}m)")
                }
                loggingSummary = null
            },
            onResetSubjectZero = {
                viewModel.resetSubjectProgress(item.subjectId)
                loggingSummary = null
            }
        )
    }

    deletingSummary?.let { item ->
        AlertDialog(
            onDismissRequest = { deletingSummary = null },
            title = { Text("Delete ${item.name}?", fontWeight = FontWeight.Bold) },
            text = {
                Text("Are you sure you want to delete '${item.name}' and all of its logged study hours? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSubject(item.subjectId)
                        deletingSummary = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                ) {
                    Text("Delete Subject", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { deletingSummary = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    confirmResetMode?.let { mode ->
        val isDeleteAll = mode == "DELETE_ALL"
        AlertDialog(
            onDismissRequest = { confirmResetMode = null },
            title = {
                Text(
                    text = if (isDeleteAll) "Delete All Subjects?" else "Reset All Studied Hours to 0?",
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = if (isDeleteAll) {
                        "This will permanently delete all your courses and study history."
                    } else {
                        "This will reset completed hours to 0h 0m across all subjects while keeping your subjects and target hours."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (isDeleteAll) viewModel.clearAllSubjectsAndData()
                        else viewModel.resetAllProgressKeepSubjects()
                        confirmResetMode = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEF4444), contentColor = Color.White)
                ) {
                    Text("Confirm", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetMode = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// =========================================================================
// TAB 1: FIRST INTERFACE — TODAY'S SUBJECTS, TARGETS, COMPLETED & REMAINING
// =========================================================================
@Composable
private fun SubjectsFirstInterfaceTab(
    todayIso: String,
    state: StudyTrackerUiState,
    onAddSubjectClick: () -> Unit,
    onEditSubjectClick: (SubjectStudySummary) -> Unit,
    onLogManualClick: (SubjectStudySummary) -> Unit,
    onDeleteSubjectClick: (SubjectStudySummary) -> Unit,
    onJumpToTimerClick: (SubjectStudySummary) -> Unit
) {
    val active = state.activeTimer
    val totalStudiedSec = state.subjectSummaries.sumOf {
        val liveExtra = if (active.isRunning && active.subjectId == it.subjectId) active.elapsedSeconds else 0L
        (it.totalSeconds + liveExtra).coerceAtLeast(0L)
    }
    val totalTargetSec = state.subjectSummaries.sumOf { it.targetSeconds }
    val totalRemainingSec = (totalTargetSec - totalStudiedSec).coerceAtLeast(0L)
    val overallPct = if (totalTargetSec > 0L) {
        ((totalStudiedSec.toDouble() / totalTargetSec.toDouble()) * 100.0).roundToInt().coerceIn(0, 100)
    } else 0

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = "Today's Study ($todayIso)",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Starts at 0% each new day • History saved in Calendar",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
                Button(
                    onClick = onAddSubjectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Today's Overall Target vs Completed Summary Banner
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Today's Target Completion", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                        Text("$overallPct%", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF10B981))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { (overallPct / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF10B981),
                        trackColor = Color(0xFF1F2937)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStatColumn("Studied Today", formatDurationHoursMinutes(totalStudiedSec), Color(0xFF10B981))
                        SummaryStatColumn("Daily Target", formatDurationHoursMinutes(totalTargetSec), Color(0xFF38BDF8))
                        SummaryStatColumn("Remaining Today", formatDurationHoursMinutes(totalRemainingSec), Color(0xFFF59E0B))
                    }
                }
            }
        }

        if (state.subjectSummaries.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No Courses or Subjects Added Yet",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add your subjects and set a daily study hour target for each one.",
                            fontSize = 13.sp,
                            color = Color(0xFF9CA3AF)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = onAddSubjectClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add Your First Subject", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(state.subjectSummaries, key = { it.subjectId }) { item ->
                val accent = parseHexColor(item.colorHex)
                val isRunningThis = active.isRunning && active.subjectId == item.subjectId
                val liveStudiedSec = (item.totalSeconds + (if (isRunningThis) active.elapsedSeconds else 0L)).coerceAtLeast(0L)
                val liveRemSec = (item.targetSeconds - liveStudiedSec).coerceAtLeast(0L)
                val pctFloat = if (item.targetSeconds > 0L) {
                    ((liveStudiedSec.toFloat() / item.targetSeconds.toFloat()) * 100f).coerceIn(0f, 100f)
                } else 0f
                val pctInt = pctFloat.roundToInt().coerceIn(0, 100)
                val titleText = if (item.code.isNotBlank()) "${item.code} — ${item.name}" else item.name

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(accent, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = titleText,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "$pctInt%",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accent
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        LinearProgressIndicator(
                            progress = { (pctFloat / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = accent,
                            trackColor = Color(0xFF1F2937)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryStatColumn(
                                label = "Studied Today",
                                value = formatDurationHoursMinutes(liveStudiedSec),
                                valueColor = Color(0xFF10B981)
                            )
                            SummaryStatColumn(
                                label = "Daily Target",
                                value = formatDurationHoursMinutes(item.targetSeconds),
                                valueColor = Color.White
                            )
                            SummaryStatColumn(
                                label = "Remaining Today",
                                value = if (liveRemSec > 0L) formatDurationHoursMinutes(liveRemSec) else "Goal Met!",
                                valueColor = if (liveRemSec > 0L) Color(0xFFF59E0B) else Color(0xFF10B981)
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(
                                    onClick = { onJumpToTimerClick(item) },
                                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(if (isRunningThis) "View Timer" else "Start Timer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onLogManualClick(item) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("+/- Time", fontSize = 12.sp, color = Color.White)
                                }
                            }

                            Row {
                                IconButton(onClick = { onEditSubjectClick(item) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Target", tint = Color(0xFF9CA3AF))
                                }
                                IconButton(onClick = { onDeleteSubjectClick(item) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete Subject", tint = Color(0xFFEF4444))
                                }
                            }
                        }
                    }
                }
            }

            item {
                OutlinedButton(
                    onClick = onAddSubjectClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF10B981))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Add Another Subject / Course",
                        color = Color(0xFF10B981),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SummaryStatColumn(label: String, value: String, valueColor: Color) {
    Column {
        Text(text = label, fontSize = 11.sp, color = Color(0xFF9CA3AF))
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// =========================================================================
// TAB 2: HEAD OF SUBJECTS & TIMERS OF EACH SUBJECT
// =========================================================================
@Composable
private fun SubjectTimersTab(
    state: StudyTrackerUiState,
    viewModel: StudyTrackerViewModel,
    onSaveAndStopTimer: () -> Unit,
    onStartSubjectTimerWithAutoSave: (SubjectStudySummary, TimerMode, Int) -> Unit,
    onAddSubjectClick: () -> Unit
) {
    val active = state.activeTimer
    var selectedMode by remember { mutableStateOf(state.prefs.defaultMode) }
    var countdownMins by remember { mutableIntStateOf(state.prefs.defaultCountdownMinutes) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = "Subject Timers (${state.subjectSummaries.size})",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Siren rings automatically when your subject target or limit is reached",
                        fontSize = 11.sp,
                        color = Color(0xFF9CA3AF)
                    )
                }
                Button(
                    onClick = onAddSubjectClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF10B981),
                        contentColor = Color.Black
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            }
        }

        // Mode & Countdown Duration Selector Bar
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Timer Mode (Siren rings when limit or daily target is reached)", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == TimerMode.STOPWATCH,
                            onClick = { selectedMode = TimerMode.STOPWATCH },
                            label = { Text("Stopwatch (Siren at Subject Target)") }
                        )
                        FilterChip(
                            selected = selectedMode == TimerMode.POMODORO,
                            onClick = { selectedMode = TimerMode.POMODORO },
                            label = { Text("Custom Countdown Siren") }
                        )
                    }
                    if (selectedMode == TimerMode.POMODORO) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(15, 30, 45, 60, 90, 120).forEach { mins ->
                                FilterChip(
                                    selected = countdownMins == mins,
                                    onClick = { countdownMins = mins },
                                    label = { Text("${mins}m") }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Active Running Timer Card
        if (active.isRunning) {
            item {
                val activeSummary = state.subjectSummaries.find { it.subjectId == active.subjectId }
                val liveStudiedSec = ((activeSummary?.totalSeconds ?: 0L) + active.elapsedSeconds).coerceAtLeast(0L)
                val targetSec = activeSummary?.targetSeconds ?: 0L
                val liveRemainingSec = (targetSec - liveStudiedSec).coerceAtLeast(0L)
                val displaySec = if (active.mode == TimerMode.POMODORO && active.targetSeconds > 0L) {
                    (active.targetSeconds - active.elapsedSeconds).coerceAtLeast(0L)
                } else {
                    active.elapsedSeconds
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF06281E)),
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color(0xFF10B981), RoundedCornerShape(20.dp))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = if (active.isPaused) "PAUSED • ${active.subjectName}" else "STUDYING • ${active.subjectName}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF10B981)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatClockHhMmSs(displaySec),
                            fontSize = 48.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        if (activeSummary != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Today Studied: ${formatDurationHoursMinutes(liveStudiedSec)}  •  Remaining Today: ${formatDurationHoursMinutes(liveRemainingSec)}",
                                fontSize = 12.sp,
                                color = Color(0xFFD1FAE5)
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = { viewModel.pauseOrResumeTimer(active.isPaused) }
                            ) {
                                Text(if (active.isPaused) "Resume" else "Pause", color = Color.White)
                            }
                            Button(
                                onClick = onSaveAndStopTimer,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                            ) {
                                Text("Save & Stop", fontWeight = FontWeight.Bold)
                            }
                            TextButton(onClick = { viewModel.discardActiveTimer() }) {
                                Text("Discard", color = Color(0xFFF87171))
                            }
                        }
                    }
                }
            }
        }

        if (state.subjectSummaries.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No Subjects Available", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Add your courses first so each subject gets its own dedicated timer.", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(
                            onClick = onAddSubjectClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                        ) {
                            Text("+ Add Subject", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        } else {
            items(state.subjectSummaries, key = { it.subjectId }) { item ->
                val isThisRunning = active.isRunning && active.subjectId == item.subjectId
                val accent = parseHexColor(item.colorHex)
                val titleText = if (item.code.isNotBlank()) "${item.code} — ${item.name}" else item.name
                val liveSeconds = (item.totalSeconds + (if (isThisRunning) active.elapsedSeconds else 0L)).coerceAtLeast(0L)
                val liveRemaining = (item.targetSeconds - liveSeconds).coerceAtLeast(0L)
                val livePct = if (item.targetSeconds > 0L) {
                    ((liveSeconds.toFloat() / item.targetSeconds.toFloat()) * 100f).coerceIn(0f, 100f)
                } else 0f

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp)
                            ) {
                                Text(text = titleText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Today: ${formatDurationHoursMinutes(liveSeconds)} / Target: ${formatDurationHoursMinutes(item.targetSeconds)} (${formatDurationHoursMinutes(liveRemaining)} left)",
                                    fontSize = 12.sp,
                                    color = Color(0xFF9CA3AF)
                                )
                            }
                            Text(
                                text = "${livePct.roundToInt()}%",
                                fontSize = 17.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = accent
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { (livePct / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = accent,
                            trackColor = Color(0xFF1F2937)
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        if (isThisRunning) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Running: ${formatClockHhMmSs(active.elapsedSeconds)}",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF10B981)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(onClick = { viewModel.pauseOrResumeTimer(active.isPaused) }) {
                                        Text(if (active.isPaused) "Resume" else "Pause", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = onSaveAndStopTimer,
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                                    ) {
                                        Text("Save & Stop", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    onStartSubjectTimerWithAutoSave(item, selectedMode, countdownMins)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedMode == TimerMode.STOPWATCH) {
                                        "Start Timer for ${item.name}"
                                    } else {
                                        "Start ${countdownMins}m Siren Timer for ${item.name}"
                                    },
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 3: CALENDAR — DAILY STUDY HISTORY BY DATE & SUBJECT
// =========================================================================
@Composable
private fun StudyCalendarTab(state: StudyTrackerUiState) {
    var displayedMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDateIso by remember { mutableStateOf(LocalDate.now().toString()) }

    val totalsByDate = remember(state.sessions) {
        state.sessions
            .groupBy { it.dateIso }
            .mapValues { (_, list) -> list.sumOf { it.durationSeconds }.coerceAtLeast(0L) }
    }

    val subjectsById = remember(state.subjects) {
        state.subjects.associateBy { it.id }
    }

    val selectedDaySessions = remember(state.sessions, selectedDateIso) {
        state.sessions.filter { it.dateIso == selectedDateIso }
    }

    val selectedDayBySubject = remember(selectedDaySessions, subjectsById) {
        selectedDaySessions
            .groupBy { it.subjectId }
            .mapNotNull { (subId, list) ->
                val totalSec = list.sumOf { it.durationSeconds }.coerceAtLeast(0L)
                if (totalSec <= 0L) null
                else {
                    val sub = subjectsById[subId]
                    Triple(
                        sub?.let { if (it.code.isNotBlank()) "${it.code} — ${it.name}" else it.name } ?: "Deleted Subject",
                        sub?.colorHex ?: "#10B981",
                        totalSec
                    )
                }
            }
    }

    val monthPrefix = displayedMonth.toString() // YYYY-MM
    val monthTotalSeconds = remember(totalsByDate, monthPrefix) {
        totalsByDate.entries
            .filter { it.key.startsWith(monthPrefix) }
            .sumOf { it.value }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Study Calendar",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Tap any date to see how much you studied on that day",
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        // Month Header & Monthly Grid Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(
                            onClick = { displayedMonth = displayedMonth.minusMonths(1) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Prev", fontSize = 12.sp, color = Color.White)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val monthTitle = "${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayedMonth.year}"
                            Text(monthTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text(
                                text = "Month Total: ${formatDurationHoursMinutes(monthTotalSeconds)}",
                                fontSize = 11.sp,
                                color = Color(0xFF10B981)
                            )
                        }

                        OutlinedButton(
                            onClick = { displayedMonth = displayedMonth.plusMonths(1) },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("Next", fontSize = 12.sp, color = Color.White)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Days of week header (Sun..Sat)
                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dow ->
                            Text(
                                text = dow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF9CA3AF),
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val firstDayOfMonth = displayedMonth.atDay(1)
                    val startOffset = firstDayOfMonth.dayOfWeek.value % 7 // Sunday = 0
                    val daysInMonth = displayedMonth.lengthOfMonth()
                    val totalCells = ((startOffset + daysInMonth + 6) / 7) * 7
                    val weeksCount = totalCells / 7
                    val todayStr = LocalDate.now().toString()

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        for (week in 0 until weeksCount) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                for (dow in 0 until 7) {
                                    val cellIndex = week * 7 + dow
                                    val dayNumber = cellIndex - startOffset + 1
                                    if (dayNumber in 1..daysInMonth) {
                                        val dateIso = displayedMonth.atDay(dayNumber).toString()
                                        val studiedSec = totalsByDate[dateIso] ?: 0L
                                        val isSelected = dateIso == selectedDateIso
                                        val isToday = dateIso == todayStr

                                        val cellBg = when {
                                            isSelected -> Color(0xFF065F46)
                                            studiedSec >= 4 * 3600L -> Color(0xFF064E3B)
                                            studiedSec > 0L -> Color(0xFF11221C)
                                            else -> Color(0xFF111827)
                                        }
                                        val borderColor = when {
                                            isSelected -> Color(0xFF10B981)
                                            isToday -> Color(0xFF38BDF8)
                                            else -> Color.Transparent
                                        }

                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .height(48.dp)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(cellBg)
                                                .border(
                                                    width = if (isSelected || isToday) 1.5.dp else 0.dp,
                                                    color = borderColor,
                                                    shape = RoundedCornerShape(8.dp)
                                                )
                                                .clickable { selectedDateIso = dateIso },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(
                                                    text = dayNumber.toString(),
                                                    fontSize = 12.sp,
                                                    fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                                    color = Color.White
                                                )
                                                if (studiedSec > 0L) {
                                                    val shortLabel = if (studiedSec >= 3600L) {
                                                        String.format("%.1fh", studiedSec / 3600.0)
                                                    } else {
                                                        "${(studiedSec / 60L).coerceAtLeast(1L)}m"
                                                    }
                                                    Text(
                                                        text = shortLabel,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color(0xFF10B981)
                                                    )
                                                }
                                            }
                                        }
                                    } else {
                                        Spacer(modifier = Modifier.weight(1f).height(48.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Selected Date Detailed Breakdown Card
        item {
            val selectedDayTotalSec = totalsByDate[selectedDateIso] ?: 0L
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Date: $selectedDateIso",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Text(
                                text = "Subject breakdown for this day",
                                fontSize = 11.sp,
                                color = Color(0xFF9CA3AF)
                            )
                        }
                        Text(
                            text = formatDurationHoursMinutes(selectedDayTotalSec),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF10B981)
                        )
                    }

                    if (selectedDayBySubject.isEmpty()) {
                        Text(
                            text = "No study sessions recorded on $selectedDateIso.",
                            fontSize = 13.sp,
                            color = Color(0xFF9CA3AF),
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    } else {
                        selectedDayBySubject.forEach { (subTitle, hex, sec) ->
                            val c = parseHexColor(hex)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF111827))
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(end = 8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .background(c, CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = subTitle,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White
                                    )
                                }
                                Text(
                                    text = formatDurationHoursMinutes(sec),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = c
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: GENERAL SETTINGS, SIREN ALARM, WIDGET CONTROLS & BACKUP/EXPORT
// =========================================================================
@Composable
private fun GeneralSettingsTab(
    state: StudyTrackerUiState,
    viewModel: StudyTrackerViewModel,
    onTestSirenClick: () -> Unit,
    onBackupJson: () -> Unit,
    onRestoreJson: () -> Unit,
    onExportCsv: () -> Unit,
    onRequestResetHours: () -> Unit,
    onRequestDeleteAll: () -> Unit
) {
    var defaultMode by remember(state.prefs.defaultMode) { mutableStateOf(state.prefs.defaultMode) }
    var defaultMinsText by remember(state.prefs.defaultCountdownMinutes) {
        mutableStateOf(state.prefs.defaultCountdownMinutes.toString())
    }
    var autoStop by remember(state.prefs.autoStopAtTarget) { mutableStateOf(state.prefs.autoStopAtTarget) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "General Settings",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Configure alarm siren, daily widget updates, and offline backups",
                fontSize = 12.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        // Home Screen Widget Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily Home Screen Widget", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    Text(
                        text = "The Home Screen Widget resets to 0% each new day and updates regularly as you study.",
                        fontSize = 12.sp,
                        color = Color(0xFF9CA3AF)
                    )
                    Button(
                        onClick = { viewModel.refreshHomeWidgetNow() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sync & Refresh Home Screen Widget Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Timer & Alarm Siren Preferences Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Timer & Siren Alarm Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = defaultMode == TimerMode.STOPWATCH,
                            onClick = { defaultMode = TimerMode.STOPWATCH },
                            label = { Text("Default: Stopwatch") }
                        )
                        FilterChip(
                            selected = defaultMode == TimerMode.POMODORO,
                            onClick = { defaultMode = TimerMode.POMODORO },
                            label = { Text("Default: Countdown") }
                        )
                    }

                    OutlinedTextField(
                        value = defaultMinsText,
                        onValueChange = { defaultMinsText = it },
                        label = { Text("Default Countdown Siren Limit (Minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp)
                        ) {
                            Text("Ring Siren When Daily Subject Target Reached", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Automatically saves session and sounds alarm siren when a subject reaches 100% of its daily target hours", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                        }
                        Switch(checked = autoStop, onCheckedChange = { autoStop = it })
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val mins = defaultMinsText.toIntOrNull() ?: 60
                                viewModel.updateGeneralSettings(defaultMode, mins, autoStop)
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Settings", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = onTestSirenClick,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Siren Alarm", color = Color(0xFFF87171))
                        }
                    }
                }
            }
        }

        // Offline Backup & Excel CSV Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Offline Backup & Excel Export", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                        Text("Export Subjects & Calendar History for Excel (.csv)")
                    }
                    OutlinedButton(onClick = onBackupJson, modifier = Modifier.fillMaxWidth()) {
                        Text("Backup All Courses & History (.json)")
                    }
                    OutlinedButton(onClick = onRestoreJson, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore Backup from File (.json)")
                    }
                }
            }
        }

        // Reset Controls Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reset & Data Controls", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF87171))
                    OutlinedButton(
                        onClick = onRequestResetHours,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset All Studied Hours to 0 (Keep Subjects)", color = Color(0xFFFBBF24))
                    }
                    OutlinedButton(
                        onClick = onRequestDeleteAll,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete All Subjects & Start Fresh", color = Color(0xFFF87171))
                    }
                }
            }
        }
    }
}

// =========================================================================
// DIALOGS: ADD / EDIT SUBJECT & DAILY TARGET HOURS, MANUAL TIME LOG
// =========================================================================
@Composable
private fun AddOrEditSubjectDialog(
    initialSummary: SubjectStudySummary?,
    onDismiss: () -> Unit,
    onConfirmAndClose: (code: String, name: String, targetHours: Float, colorHex: String) -> Unit,
    onConfirmAndAddAnother: ((code: String, name: String, targetHours: Float, colorHex: String) -> Unit)? = null
) {
    var name by remember { mutableStateOf(initialSummary?.name ?: "") }
    var code by remember { mutableStateOf(initialSummary?.code ?: "") }
    var targetHoursText by remember {
        mutableStateOf(initialSummary?.targetHoursPerWeek?.toString() ?: "2")
    }
    var selectedColorIndex by remember {
        val idx = SUBJECT_COLOR_PALETTE.indexOfFirst {
            it.equals(initialSummary?.colorHex ?: "", ignoreCase = true)
        }
        mutableIntStateOf(if (idx >= 0) idx else 0)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialSummary == null) "Add Subject & Daily Target" else "Edit Subject & Daily Target",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Subject / Course Name *") },
                    placeholder = { Text("e.g. Structural Analysis / Physics") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Course Code (Optional)") },
                    placeholder = { Text("e.g. CEE 4311") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = targetHoursText,
                    onValueChange = { targetHoursText = it },
                    label = { Text("Daily Target Study Hours (Resets Each Day) *") },
                    placeholder = { Text("e.g. 2.0") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Text("Pick Subject Color", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SUBJECT_COLOR_PALETTE.forEachIndexed { index, hex ->
                        val c = parseHexColor(hex)
                        val isSelected = selectedColorIndex == index
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(c)
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) Color.White else Color.Transparent,
                                    shape = CircleShape
                                )
                                .clickable { selectedColorIndex = index }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (onConfirmAndAddAnother != null) {
                    OutlinedButton(
                        onClick = {
                            if (name.isNotBlank()) {
                                val hrs = targetHoursText.toFloatOrNull()?.coerceAtLeast(0.1f) ?: 2f
                                val color = SUBJECT_COLOR_PALETTE[selectedColorIndex]
                                onConfirmAndAddAnother(code, name, hrs, color)
                                name = ""
                                code = ""
                                selectedColorIndex = (selectedColorIndex + 1) % SUBJECT_COLOR_PALETTE.size
                            }
                        }
                    ) {
                        Text("+ Save & Add Next", fontSize = 12.sp)
                    }
                }
                Button(
                    onClick = {
                        if (name.isNotBlank()) {
                            val hrs = targetHoursText.toFloatOrNull()?.coerceAtLeast(0.1f) ?: 2f
                            val color = SUBJECT_COLOR_PALETTE[selectedColorIndex]
                            onConfirmAndClose(code, name, hrs, color)
                        }
                    }
                ) {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
private fun ManualLogDialog(
    summary: SubjectStudySummary,
    onDismiss: () -> Unit,
    onAddMinutes: (minutes: Int, note: String) -> Unit,
    onSubtractMinutes: (minutes: Int) -> Unit,
    onResetSubjectZero: () -> Unit
) {
    var minutesText by remember { mutableStateOf("30") }
    var noteText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Today's Time — ${summary.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Studied Today: ${formatDurationHoursMinutes(summary.totalSeconds)}",
                    fontSize = 12.sp,
                    color = Color(0xFF10B981)
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it },
                    label = { Text("Minutes (e.g. 20 or 60)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = noteText,
                    onValueChange = { noteText = it },
                    label = { Text("Topic / Note (Optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedButton(
                    onClick = onResetSubjectZero,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Reset This Subject to 0m", color = Color(0xFFF87171), fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(
                    onClick = {
                        val mins = minutesText.toIntOrNull()?.coerceAtLeast(1) ?: 30
                        onSubtractMinutes(mins)
                    }
                ) {
                    Text("- Subtract", color = Color(0xFFFBBF24), fontSize = 12.sp)
                }
                Button(
                    onClick = {
                        val mins = minutesText.toIntOrNull()?.coerceAtLeast(1) ?: 30
                        onAddMinutes(mins, noteText)
                    }
                ) {
                    Text("+ Add Time", fontSize = 12.sp)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
