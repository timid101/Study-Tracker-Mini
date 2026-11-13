package com.iut.studytracker.ui.screens

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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iut.studytracker.data.SubjectStudySummary
import com.iut.studytracker.data.TimerMode
import com.iut.studytracker.ui.components.formatClockHhMmSs
import com.iut.studytracker.ui.components.formatDurationHoursMinutes
import com.iut.studytracker.ui.components.parseHexColor
import com.iut.studytracker.viewmodel.StudyTrackerUiState
import com.iut.studytracker.viewmodel.StudyTrackerViewModel
import java.time.LocalDate
import kotlin.math.roundToInt

private val SUBJECT_COLOR_PALETTE = listOf(
    "#10B981", "#3B82F6", "#F59E0B", "#8B5CF6", "#EC4899", "#06B6D4", "#EF4444", "#84CC16"
)

@Composable
fun MainTrackerScreen(viewModel: StudyTrackerViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

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
                    icon = { Icon(Icons.Default.Settings, contentDescription = "General Settings") },
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
            when (selectedTab) {
                0 -> SubjectsFirstInterfaceTab(
                    state = state,
                    onAddSubjectClick = { showAddSubjectDialog = true },
                    onEditSubjectClick = { editingSummary = it },
                    onLogManualClick = { loggingSummary = it },
                    onDeleteSubjectClick = { deletingSummary = it },
                    onJumpToTimerClick = { summary ->
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
                    onAddSubjectClick = { showAddSubjectDialog = true }
                )
                2 -> GeneralSettingsTab(
                    state = state,
                    viewModel = viewModel,
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
                Text("Are you sure you want to delete "${item.name}" and all of its logged study hours? This cannot be undone.")
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
                        "This will reset completed hours to 0h 0m across all subjects while keeping your subject list and target hours."
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
// TAB 1: FIRST INTERFACE — SUBJECTS LIST, TARGETS, COMPLETED & REMAINING
// =========================================================================
@Composable
private fun SubjectsFirstInterfaceTab(
    state: StudyTrackerUiState,
    onAddSubjectClick: () -> Unit,
    onEditSubjectClick: (SubjectStudySummary) -> Unit,
    onLogManualClick: (SubjectStudySummary) -> Unit,
    onDeleteSubjectClick: (SubjectStudySummary) -> Unit,
    onJumpToTimerClick: (SubjectStudySummary) -> Unit
) {
    val totalStudiedSec = state.subjectSummaries.sumOf { it.totalSeconds }
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
                // Modifier.weight(1f) prevents long subtitle text from pushing the Add button off-screen!
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 10.dp)
                ) {
                    Text(
                        text = "My Subjects (${state.subjectSummaries.size})",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Text(
                        text = "Target hours, completed & remaining time",
                        fontSize = 12.sp,
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

        // Overall Target vs Completed Summary Banner
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
                        Text("Overall Target Completion", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
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
                        SummaryStatColumn("Completed", formatDurationHoursMinutes(totalStudiedSec), Color(0xFF10B981))
                        SummaryStatColumn("Total Target", formatDurationHoursMinutes(totalTargetSec), Color(0xFF38BDF8))
                        SummaryStatColumn("Remaining", formatDurationHoursMinutes(totalRemainingSec), Color(0xFFF59E0B))
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
                            text = "Add as many subjects as you want and set a target study hour limit for each one.",
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
                val pctInt = item.completionPercent.roundToInt().coerceIn(0, 100)
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
                            progress = { (item.completionPercent / 100f).coerceIn(0f, 1f) },
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
                                label = "Completed",
                                value = formatDurationHoursMinutes(item.totalSeconds),
                                valueColor = Color(0xFF10B981)
                            )
                            SummaryStatColumn(
                                label = "Target Limit",
                                value = formatDurationHoursMinutes(item.targetSeconds),
                                valueColor = Color.White
                            )
                            SummaryStatColumn(
                                label = "Remaining",
                                value = if (item.remainingSeconds > 0L) formatDurationHoursMinutes(item.remainingSeconds) else "Goal Met!",
                                valueColor = if (item.remainingSeconds > 0L) Color(0xFFF59E0B) else Color(0xFF10B981)
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
                                    Text("Start Timer", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                }
                                OutlinedButton(
                                    onClick = { onLogManualClick(item) },
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("+ Log Hours", fontSize = 12.sp, color = Color.White)
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

            // Full-width button right below the list so adding more subjects is always obvious
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
                        text = "Run a dedicated timer for any subject below",
                        fontSize = 12.sp,
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
                    Text("Timer Mode for Starting a Subject", fontSize = 12.sp, color = Color(0xFF9CA3AF))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == TimerMode.STOPWATCH,
                            onClick = { selectedMode = TimerMode.STOPWATCH },
                            label = { Text("Stopwatch (Count Up)") }
                        )
                        FilterChip(
                            selected = selectedMode == TimerMode.POMODORO,
                            onClick = { selectedMode = TimerMode.POMODORO },
                            label = { Text("Countdown Limit") }
                        )
                    }
                    if (selectedMode == TimerMode.POMODORO) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            listOf(25, 45, 60, 90, 120).forEach { mins ->
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
                val liveStudiedSec = (activeSummary?.totalSeconds ?: 0L) + active.elapsedSeconds
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
                            text = if (active.isPaused) "PAUSED • ${active.subjectName}" else "ACTIVE TIMER • ${active.subjectName}",
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
                                text = "Live Studied: ${formatDurationHoursMinutes(liveStudiedSec)}  •  Remaining: ${formatDurationHoursMinutes(liveRemainingSec)}",
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
                                onClick = { viewModel.stopAndSaveTimer() },
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
                val liveSeconds = item.totalSeconds + (if (isThisRunning) active.elapsedSeconds else 0L)
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
                                    text = "Studied: ${formatDurationHoursMinutes(liveSeconds)} / Target: ${formatDurationHoursMinutes(item.targetSeconds)} (${formatDurationHoursMinutes(liveRemaining)} left)",
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
                                        onClick = { viewModel.stopAndSaveTimer() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black)
                                    ) {
                                        Text("Save", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.startSubjectTimer(
                                        summary = item,
                                        mode = selectedMode,
                                        countdownMinutes = countdownMins
                                    )
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = Color.Black),
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (selectedMode == TimerMode.STOPWATCH) {
                                        "Start Stopwatch for ${item.name}"
                                    } else {
                                        "Start ${countdownMins}m Countdown for ${item.name}"
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
// TAB 3: GENERAL SETTINGS, WIDGET CONTROLS & BACKUP/EXPORT
// =========================================================================
@Composable
private fun GeneralSettingsTab(
    state: StudyTrackerUiState,
    viewModel: StudyTrackerViewModel,
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
                text = "Configure default timers, home screen widgets, and offline data backups",
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
                    Text("Android Home Screen Widget", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    Text(
                        text = "Your APK includes a native Home Screen Widget showing each course name and the exact percentage of your target study time completed. Long-press your phone's home screen -> tap Widgets -> select Study Tracker Progress.",
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

        // Timer Preferences Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Default Timer Preferences", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

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
                        label = { Text("Default Countdown Duration (Minutes)") },
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
                            Text("Auto-stop at 100% Subject Target", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text("Automatically finish stopwatch when a course reaches its target hours", color = Color(0xFF9CA3AF), fontSize = 11.sp)
                        }
                        Switch(checked = autoStop, onCheckedChange = { autoStop = it })
                    }

                    Button(
                        onClick = {
                            val mins = defaultMinsText.toIntOrNull() ?: 45
                            viewModel.updateGeneralSettings(defaultMode, mins, autoStop)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Timer Settings", fontWeight = FontWeight.Bold)
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
                        Text("Export Subjects & Progress for Excel (.csv)")
                    }
                    OutlinedButton(onClick = onBackupJson, modifier = Modifier.fillMaxWidth()) {
                        Text("Backup All Courses & Progress (.json)")
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
                        Text("Reset Studied Hours to 0 (Keep Subjects)", color = Color(0xFFFBBF24))
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
// DIALOGS: ADD / EDIT SUBJECT & TARGET HOURS, MANUAL TIME LOG
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
        mutableStateOf(initialSummary?.targetHoursPerWeek?.toString() ?: "10")
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
                text = if (initialSummary == null) "Add Subject / Course" else "Edit Subject & Target",
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
                    label = { Text("Target Study Hours (Limit / Goal) *") },
                    placeholder = { Text("e.g. 12.5") },
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
                                val hrs = targetHoursText.toFloatOrNull()?.coerceAtLeast(0.5f) ?: 10f
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
                            val hrs = targetHoursText.toFloatOrNull()?.coerceAtLeast(0.5f) ?: 10f
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
        title = { Text("Adjust Study Time — ${summary.name}", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Currently Completed: ${formatDurationHoursMinutes(summary.totalSeconds)}",
                    fontSize = 12.sp,
                    color = Color(0xFF10B981)
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it },
                    label = { Text("Minutes (e.g. 30 or 60)") },
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
                    Text("Reset This Subject to 0h 0m", color = Color(0xFFF87171), fontSize = 12.sp)
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
