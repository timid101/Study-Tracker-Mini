package com.iut.studytracker.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iut.studytracker.data.TaskCategory
import com.iut.studytracker.data.TaskPriority
import com.iut.studytracker.data.TimerMode
import com.iut.studytracker.ui.components.ActivityHeatmap365
import com.iut.studytracker.ui.components.SubjectDonutChart
import com.iut.studytracker.viewmodel.StudyTrackerUiState
import com.iut.studytracker.viewmodel.StudyTrackerViewModel
import java.time.LocalDate

@Composable
fun MainTrackerScreen(viewModel: StudyTrackerViewModel) {
    val state by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

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
        bottomBar = {
            NavigationBar(containerColor = Color(0xFF080B11)) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Overview") },
                    label = { Text("Dashboard") }
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = { Icon(Icons.Default.Timer, contentDescription = "Focus Timer") },
                    label = { Text("Timer") }
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.CheckCircle, contentDescription = "Tasks") },
                    label = { Text("Coursework") }
                )
                NavigationBarItem(
                    selected = selectedTab == 3,
                    onClick = { selectedTab = 3 },
                    icon = { Icon(Icons.Default.Analytics, contentDescription = "Analytics") },
                    label = { Text("Analytics") }
                )
                NavigationBarItem(
                    selected = selectedTab == 4,
                    onClick = { selectedTab = 4 },
                    icon = { Icon(Icons.Default.CloudDownload, contentDescription = "Backup") },
                    label = { Text("Backup") }
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
                0 -> DashboardTab(state, viewModel)
                1 -> FocusTimerTab(state, viewModel)
                2 -> CourseworkTab(state, viewModel)
                3 -> AnalyticsTab(state)
                4 -> BackupExportTab(
                    onBackupJson = { createJsonLauncher.launch("study_tracker_backup_${LocalDate.now()}.json") },
                    onRestoreJson = { openJsonLauncher.launch(arrayOf("application/json", "*/*")) },
                    onExportCsv = { createCsvLauncher.launch("study_sessions_${LocalDate.now()}.csv") }
                )
            }
        }
    }
}

@Composable
private fun DashboardTab(state: StudyTrackerUiState, viewModel: StudyTrackerViewModel) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text(
                text = "CEE Academic Study Tracker",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "100% Offline • True AMOLED • Room SQLite",
                fontSize = 13.sp,
                color = Color(0xFF9CA3AF)
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                MetricCard(
                    label = "Today's Focus",
                    value = String.format("%.1f hrs", state.todaySeconds / 3600.0),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Active Streak",
                    value = "${state.currentStreakDays} days",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = "Pending Tasks",
                    value = "${state.tasks.count { !it.isCompleted }}",
                    modifier = Modifier.weight(1f)
                )
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Subject Distribution", fontWeight = FontWeight.SemiBold, color = Color.White)
                    SubjectDonutChart(summaries = state.subjectSummaries)
                }
            }
        }

        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    ActivityHeatmap365(dailyTotals = state.dailyTotals)
                }
            }
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = label, fontSize = 11.sp, color = Color(0xFF9CA3AF))
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
        }
    }
}

@Composable
private fun FocusTimerTab(state: StudyTrackerUiState, viewModel: StudyTrackerViewModel) {
    var selectedSubjectIndex by remember { mutableIntStateOf(0) }
    var selectedMode by remember { mutableStateOf(TimerMode.POMODORO) }
    var focusMinutes by remember { mutableIntStateOf(25) }
    var topicText by remember { mutableStateOf("") }

    val active = state.activeTimer
    val displaySec = if (active.isRunning) {
        if (active.mode == TimerMode.POMODORO) (active.targetSeconds - active.elapsedSeconds).coerceAtLeast(0L)
        else active.elapsedSeconds
    } else {
        focusMinutes * 60L
    }
    val mm = (displaySec / 60).toString().padStart(2, '0')
    val ss = (displaySec % 60).toString().padStart(2, '0')

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("Foreground Focus Timer", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (active.isRunning) active.subjectName else "Ready to Focus",
                    color = Color(0xFF10B981),
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$mm:$ss",
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(16.dp))

                if (!active.isRunning) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedMode == TimerMode.POMODORO,
                            onClick = { selectedMode = TimerMode.POMODORO },
                            label = { Text("Pomodoro") }
                        )
                        FilterChip(
                            selected = selectedMode == TimerMode.STOPWATCH,
                            onClick = { selectedMode = TimerMode.STOPWATCH },
                            label = { Text("Stopwatch") }
                        )
                        FilterChip(
                            selected = focusMinutes == 25,
                            onClick = { focusMinutes = 25 },
                            label = { Text("25m") }
                        )
                        FilterChip(
                            selected = focusMinutes == 50,
                            onClick = { focusMinutes = 50 },
                            label = { Text("50m") }
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = topicText,
                        onValueChange = { topicText = it },
                        label = { Text("Topic / Problem Set (e.g. Truss Indeterminacy)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            val sub = state.subjects.getOrElse(selectedSubjectIndex) { state.subjects.firstOrNull() }
                            if (sub != null) {
                                viewModel.startForegroundTimer(sub, topicText, selectedMode, focusMinutes)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Start Lock-Screen Foreground Timer")
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(onClick = { viewModel.pauseOrResumeTimer(active.isPaused) }) {
                            Text(if (active.isPaused) "Resume" else "Pause")
                        }
                        Button(onClick = { viewModel.stopAndSaveTimer() }) {
                            Text("Finish & Save Session")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseworkTab(state: StudyTrackerUiState, viewModel: StudyTrackerViewModel) {
    var taskTitle by remember { mutableStateOf("") }
    var dueDate by remember { mutableStateOf(LocalDate.now().plusDays(3).toString()) }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Assignments, Lab Reports & Class Tests", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text("New Task / Lab Report / CT") },
                    modifier = Modifier.weight(1f)
                )
                Button(
                    onClick = {
                        val firstSub = state.subjects.firstOrNull()?.id ?: 1L
                        if (taskTitle.isNotBlank()) {
                            viewModel.addTask(firstSub, taskTitle, TaskCategory.ASSIGNMENT, TaskPriority.HIGH, dueDate)
                            taskTitle = ""
                        }
                    }
                ) {
                    Text("Add")
                }
            }
        }

        items(state.tasks) { task ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = task.isCompleted,
                            onCheckedChange = { viewModel.toggleTaskCompleted(task) }
                        )
                        Column {
                            Text(text = task.title, color = Color.White, fontWeight = FontWeight.Medium)
                            Text(text = "Due: ${task.dueDateIso} • ${task.priority.name}", color = Color(0xFF9CA3AF), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AnalyticsTab(state: StudyTrackerUiState) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Recent Study Sessions", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.White)
        }
        items(state.sessions) { s ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0F17)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "${s.topicStudied} (${s.durationSeconds / 60} min)",
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "${s.dateIso} • Mode: ${s.mode.name} • Focus: ${s.focusRating}/5",
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun BackupExportTab(
    onBackupJson: () -> Unit,
    onRestoreJson: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text(
            text = "Offline Storage Access Framework (SAF)",
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Text(
            text = "Zero internet permissions. Export your entire SQLite database to a local .json snapshot or export study logs as a UTF-8 .csv file ready for Microsoft Excel.",
            color = Color(0xFF9CA3AF),
            fontSize = 13.sp
        )
        Button(onClick = onBackupJson, modifier = Modifier.fillMaxWidth()) {
            Text("Export Full Database Backup (.json)")
        }
        OutlinedButton(onClick = onRestoreJson, modifier = Modifier.fillMaxWidth()) {
            Text("Restore Database from Backup (.json)")
        }
        Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
            Text("Export Study Sessions for Excel (.csv)")
        }
    }
}
