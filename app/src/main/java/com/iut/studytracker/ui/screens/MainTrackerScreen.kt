package com.iut.studytracker.ui.screens

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.Ringtone
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
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

data class AppThemeColors(
    val isDark: Boolean,
    val bg: Color,
    val card: Color,
    val surface: Color,
    val navBar: Color,
    val textPrimary: Color,
    val textMuted: Color,
    val divider: Color
)

private fun getThemePalette(isDark: Boolean): AppThemeColors {
    return if (isDark) {
        AppThemeColors(
            isDark = true,
            bg = Color(0xFF000000),
            card = Color(0xFF0B0F17),
            surface = Color(0xFF111827),
            navBar = Color(0xFF070A10),
            textPrimary = Color(0xFFF9FAFB),
            textMuted = Color(0xFF9CA3AF),
            divider = Color(0xFF1F2937)
        )
    } else {
        AppThemeColors(
            isDark = false,
            bg = Color(0xFFF3F4F6),
            card = Color(0xFFFFFFFF),
            surface = Color(0xFFE5E7EB),
            navBar = Color(0xFFFFFFFF),
            textPrimary = Color(0xFF111827),
            textMuted = Color(0xFF4B5563),
            divider = Color(0xFFD1D5DB)
        )
    }
}

enum class SirenSoundOption(val id: String, val label: String) {
    EMERGENCY_SIREN("SIREN", "Emergency Police/Ambulance Siren"),
    RAPID_BEEP("BEEP", "Rapid Digital Alarm Beep"),
    SYSTEM_ALARM("SYSTEM", "Phone Default Alarm Ringtone"),
    GENTLE_CHIME("CHIME", "Melodic Completion Chime"),
    CUSTOM_MUSIC("CUSTOM", "Custom Music / Audio File from Phone")
}

@Composable
fun MainTrackerScreen(viewModel: StudyTrackerViewModel) {
    val rawState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiPrefs = remember {
        context.getSharedPreferences("study_tracker_ui_extra_prefs", Context.MODE_PRIVATE)
    }

    var selectedTab by remember { mutableIntStateOf(0) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Extra UI & Focus Preferences (Theme, Siren Sound Type, Custom Audio URI, 15s Strict Focus Auto-Stop)
    var isDarkTheme by remember { mutableStateOf(uiPrefs.getBoolean("is_dark_theme", true)) }
    var selectedSirenType by remember {
        val saved = uiPrefs.getString("siren_sound_type", SirenSoundOption.EMERGENCY_SIREN.id)
        mutableStateOf(
            SirenSoundOption.values().find { it.id == saved } ?: SirenSoundOption.EMERGENCY_SIREN
        )
    }
    var customMusicUriStr by remember { mutableStateOf(uiPrefs.getString("custom_music_uri", "") ?: "") }
    var customMusicName by remember { mutableStateOf(uiPrefs.getString("custom_music_name", "No music file selected") ?: "No music file selected") }
    var strictFocus15sEnabled by remember { mutableStateOf(uiPrefs.getBoolean("strict_focus_15s", true)) }

    val theme = remember(isDarkTheme) { getThemePalette(isDarkTheme) }

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

    // Track whether the app is currently visible in the foreground for the 15-Second Strict Focus Auto-Stop
    var isAppInForeground by remember { mutableStateOf(true) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START, Lifecycle.Event.ON_RESUME -> {
                    isAppInForeground = true
                }
                Lifecycle.Event.ON_STOP -> {
                    isAppInForeground = false
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            runCatching { activeRingtone?.stop() }
        }
    }

    // Strict Focus Mode: If timer is running and user leaves the app (while screen is ON/unlocked) for >15 seconds, auto-save & stop!
    LaunchedEffect(state.activeTimer.isRunning, isAppInForeground, strictFocus15sEnabled) {
        if (strictFocus15sEnabled && state.activeTimer.isRunning && !isAppInForeground) {
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            var secondsOutsideWithScreenOn = 0
            while (state.activeTimer.isRunning && !isAppInForeground && strictFocus15sEnabled) {
                delay(1000L)
                val isScreenInteractive = powerManager?.isInteractive ?: true
                if (isScreenInteractive) {
                    secondsOutsideWithScreenOn++
                    if (secondsOutsideWithScreenOn >= 15) {
                        saveAndStopCurrentTimer("Auto-stopped (left app for >15s)")
                        runCatching {
                            val tg = ToneGenerator(AudioManager.STREAM_ALARM, 90)
                            tg.startTone(ToneGenerator.TONE_PROP_NACK, 600)
                        }
                        break
                    }
                } else {
                    // Screen is locked/off — user is studying with phone locked, so reset counter
                    secondsOutsideWithScreenOn = 0
                }
            }
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
            if (active.elapsedSeconds > 0L && active.elapsedSeconds % 20L == 0L) {
                viewModel.refreshHomeWidgetNow()
            }
            val subSummary = todaySummaries.find { it.subjectId == active.subjectId }
            val subTargetSec = subSummary?.targetSeconds ?: 0L
            val alreadyStudiedTodaySec = subSummary?.totalSeconds ?: 0L
            val totalTodayWithLive = alreadyStudiedTodaySec + active.elapsedSeconds

            val hitCountdown = active.mode == TimerMode.POMODORO &&
                active.targetSeconds > 0L &&
                active.elapsedSeconds >= active.targetSeconds

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

    // Play the user's chosen Siren / Alarm / Custom Music while isSirenRinging is true
    LaunchedEffect(isSirenRinging, selectedSirenType, customMusicUriStr) {
        if (isSirenRinging) {
            val useRingtoneUri: Uri? = when (selectedSirenType) {
                SirenSoundOption.CUSTOM_MUSIC -> {
                    if (customMusicUriStr.isNotBlank()) runCatching { Uri.parse(customMusicUriStr) }.getOrNull()
                    else RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                }
                SirenSoundOption.SYSTEM_ALARM -> {
                    RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                        ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                }
                else -> null
            }

            if (useRingtoneUri != null) {
                runCatching {
                    activeRingtone?.stop()
                    val r = RingtoneManager.getRingtone(context, useRingtoneUri)
                    r.audioAttributes = AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                    r.play()
                    activeRingtone = r
                }
            }

            val toneGen = runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 100) }.getOrNull()
            @Suppress("DEPRECATION")
            val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator

            try {
                for (cycle in 0 until 30) {
                    if (!isSirenRinging) break
                    runCatching {
                        vibrator?.vibrate(VibrationEffect.createOneShot(400L, VibrationEffect.DEFAULT_AMPLITUDE))
                    }
                    when (selectedSirenType) {
                        SirenSoundOption.EMERGENCY_SIREN -> {
                            runCatching {
                                val tone = if (cycle % 2 == 0) {
                                    ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK
                                } else {
                                    ToneGenerator.TONE_CDMA_HIGH_L
                                }
                                toneGen?.startTone(tone, 450)
                            }
                            delay(520L)
                        }
                        SirenSoundOption.RAPID_BEEP -> {
                            runCatching {
                                toneGen?.startTone(ToneGenerator.TONE_CDMA_ALERT_CALL_GUARD, 300)
                            }
                            delay(380L)
                        }
                        SirenSoundOption.GENTLE_CHIME -> {
                            runCatching {
                                val tone = if (cycle % 2 == 0) {
                                    ToneGenerator.TONE_PROP_ACK
                                } else {
                                    ToneGenerator.TONE_CDMA_MED_L
                                }
                                toneGen?.startTone(tone, 350)
                            }
                            delay(650L)
                        }
                        SirenSoundOption.SYSTEM_ALARM, SirenSoundOption.CUSTOM_MUSIC -> {
                            // Ringtone is playing; if ringtone failed to play, fallback to emergency tone
                            if (activeRingtone == null || activeRingtone?.isPlaying == false) {
                                runCatching {
                                    toneGen?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, 450)
                                }
                            }
                            delay(600L)
                        }
                    }
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

    // Custom Music / Audio Picker Launcher for Alarm Sound
    val pickAudioLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            val uriString = uri.toString()
            val label = uri.lastPathSegment?.substringAfterLast('/') ?: "Custom Audio File"
            customMusicUriStr = uriString
            customMusicName = label
            selectedSirenType = SirenSoundOption.CUSTOM_MUSIC
            uiPrefs.edit()
                .putString("custom_music_uri", uriString)
                .putString("custom_music_name", label)
                .putString("siren_sound_type", SirenSoundOption.CUSTOM_MUSIC.id)
                .apply()
        }
    }

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
            NavigationBar(containerColor = theme.navBar) {
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
                .background(theme.bg)
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Loud Siren Alert Banner
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
                                    text = "Alarm (${selectedSirenType.label}) is ringing • Session saved",
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
                            Text("STOP ALARM", fontWeight = FontWeight.ExtraBold, fontSize = 12.sp)
                        }
                    }
                }
            }

            when (selectedTab) {
                0 -> SubjectsFirstInterfaceTab(
                    theme = theme,
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
                    theme = theme,
                    state = state,
                    viewModel = viewModel,
                    strictFocus15sEnabled = strictFocus15sEnabled,
                    onSaveAndStopTimer = { saveAndStopCurrentTimer("Timer Study Session") },
                    onStartSubjectTimerWithAutoSave = { summary, mode, mins ->
                        if (state.activeTimer.isRunning && state.activeTimer.subjectId != summary.subjectId) {
                            saveAndStopCurrentTimer("Saved before switching subject")
                        }
                        viewModel.startSubjectTimer(summary, mode, mins)
                    },
                    onAddSubjectClick = { showAddSubjectDialog = true }
                )
                2 -> StudyCalendarWithGraphsTab(
                    theme = theme,
                    state = state
                )
                3 -> GeneralSettingsTab(
                    theme = theme,
                    state = state,
                    viewModel = viewModel,
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = { dark ->
                        isDarkTheme = dark
                        uiPrefs.edit().putBoolean("is_dark_theme", dark).apply()
                    },
                    selectedSirenType = selectedSirenType,
                    customMusicName = customMusicName,
                    onSelectSirenType = { option ->
                        selectedSirenType = option
                        uiPrefs.edit().putString("siren_sound_type", option.id).apply()
                    },
                    onPickCustomMusicFile = {
                        pickAudioLauncher.launch(arrayOf("audio/*"))
                    },
                    strictFocus15sEnabled = strictFocus15sEnabled,
                    onToggleStrictFocus15s = { enabled ->
                        strictFocus15sEnabled = enabled
                        uiPrefs.edit().putBoolean("strict_focus_15s", enabled).apply()
                    },
                    onTestSirenClick = {
                        sirenSubjectTitle = "Alarm Sound Preview"
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
    theme: AppThemeColors,
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
                        color = theme.textPrimary
                    )
                    Text(
                        text = "Starts at 0% each new day • History & graphs in Calendar",
                        fontSize = 11.sp,
                        color = theme.textMuted
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
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Today's Target Completion", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = theme.textPrimary)
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
                        trackColor = theme.divider
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        SummaryStatColumn("Studied Today", formatDurationHoursMinutes(totalStudiedSec), Color(0xFF10B981), theme.textMuted)
                        SummaryStatColumn("Daily Target", formatDurationHoursMinutes(totalTargetSec), Color(0xFF0284C7), theme.textMuted)
                        SummaryStatColumn("Remaining Today", formatDurationHoursMinutes(totalRemainingSec), Color(0xFFD97706), theme.textMuted)
                    }
                }
            }
        }

        if (state.subjectSummaries.isEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = theme.card),
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
                            color = theme.textPrimary
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Add your subjects and set a daily study hour target for each one.",
                            fontSize = 13.sp,
                            color = theme.textMuted
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
                    colors = CardDefaults.cardColors(containerColor = theme.card),
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
                                    color = theme.textPrimary
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
                            trackColor = theme.divider
                        )

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            SummaryStatColumn(
                                label = "Studied Today",
                                value = formatDurationHoursMinutes(liveStudiedSec),
                                valueColor = Color(0xFF10B981),
                                labelColor = theme.textMuted
                            )
                            SummaryStatColumn(
                                label = "Daily Target",
                                value = formatDurationHoursMinutes(item.targetSeconds),
                                valueColor = theme.textPrimary,
                                labelColor = theme.textMuted
                            )
                            SummaryStatColumn(
                                label = "Remaining Today",
                                value = if (liveRemSec > 0L) formatDurationHoursMinutes(liveRemSec) else "Goal Met!",
                                valueColor = if (liveRemSec > 0L) Color(0xFFD97706) else Color(0xFF10B981),
                                labelColor = theme.textMuted
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
                                    Text("+/- Time", fontSize = 12.sp, color = theme.textPrimary)
                                }
                            }

                            Row {
                                IconButton(onClick = { onEditSubjectClick(item) }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Edit Target", tint = theme.textMuted)
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
private fun SummaryStatColumn(label: String, value: String, valueColor: Color, labelColor: Color) {
    Column {
        Text(text = label, fontSize = 11.sp, color = labelColor)
        Spacer(modifier = Modifier.height(2.dp))
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = valueColor)
    }
}

// =========================================================================
// TAB 2: HEAD OF SUBJECTS & TIMERS OF EACH SUBJECT
// =========================================================================
@Composable
private fun SubjectTimersTab(
    theme: AppThemeColors,
    state: StudyTrackerUiState,
    viewModel: StudyTrackerViewModel,
    strictFocus15sEnabled: Boolean,
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
                        color = theme.textPrimary
                    )
                    Text(
                        text = if (strictFocus15sEnabled) {
                            "Siren at target • Auto-stops if you leave app for >15s"
                        } else {
                            "Siren rings automatically when your subject target is reached"
                        },
                        fontSize = 11.sp,
                        color = theme.textMuted
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
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Timer Mode (Alarm rings when limit or daily target is reached)", fontSize = 12.sp, color = theme.textMuted)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedMode == TimerMode.STOPWATCH,
                            onClick = { selectedMode = TimerMode.STOPWATCH },
                            label = { Text("Stopwatch (Alarm at Subject Target)") }
                        )
                        FilterChip(
                            selected = selectedMode == TimerMode.POMODORO,
                            onClick = { selectedMode = TimerMode.POMODORO },
                            label = { Text("Custom Countdown Alarm") }
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
                    colors = CardDefaults.cardColors(containerColor = theme.card),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No Subjects Available", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Add your courses first so each subject gets its own dedicated timer.", fontSize = 12.sp, color = theme.textMuted)
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
                    colors = CardDefaults.cardColors(containerColor = theme.card),
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
                                Text(text = titleText, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "Today: ${formatDurationHoursMinutes(liveSeconds)} / Target: ${formatDurationHoursMinutes(item.targetSeconds)} (${formatDurationHoursMinutes(liveRemaining)} left)",
                                    fontSize = 12.sp,
                                    color = theme.textMuted
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
                            trackColor = theme.divider
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
                                        Text(if (active.isPaused) "Resume" else "Pause", fontSize = 12.sp, color = theme.textPrimary)
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
                                        "Start ${countdownMins}m Alarm Timer for ${item.name}"
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
// TAB 3: CALENDAR + GRAPH 1 (TIME BY SUBJECT) + GRAPH 2 (TIME VS DAY)
// =========================================================================
@Composable
private fun StudyCalendarWithGraphsTab(
    theme: AppThemeColors,
    state: StudyTrackerUiState
) {
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

    // For Graph 1 ("Time by Subject" on Selected Date): show every active subject on X-axis and its hours studied on selectedDateIso!
    val subjectHoursForSelectedDate = remember(state.subjects, selectedDaySessions) {
        val secBySub = selectedDaySessions
            .groupBy { it.subjectId }
            .mapValues { (_, list) -> list.sumOf { it.durationSeconds }.coerceAtLeast(0L) }

        state.subjects.map { sub ->
            val label = if (sub.code.isNotBlank()) sub.code else sub.name
            val sec = secBySub[sub.id] ?: 0L
            Triple(label, sub.colorHex, sec)
        }
    }

    val monthPrefix = displayedMonth.toString() // YYYY-MM
    val daysInMonth = displayedMonth.lengthOfMonth()

    // For Graph 2 ("Time vs Day" across the whole displayed month): days 1..daysInMonth
    val dailySecondsForMonth = remember(totalsByDate, displayedMonth, daysInMonth) {
        (1..daysInMonth).map { dayNum ->
            val dateStr = displayedMonth.atDay(dayNum).toString()
            totalsByDate[dateStr] ?: 0L
        }
    }

    val monthTotalSeconds = remember(dailySecondsForMonth) {
        dailySecondsForMonth.sum()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = "Study Calendar & Analytics",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = theme.textPrimary
            )
            Text(
                text = "Tap any date to inspect daily study hours, subject graph, and monthly trend",
                fontSize = 12.sp,
                color = theme.textMuted
            )
        }

        // 1. Interactive Monthly Calendar Grid
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
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
                            Text("Prev", fontSize = 12.sp, color = theme.textPrimary)
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            val monthTitle = "${displayedMonth.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${displayedMonth.year}"
                            Text(monthTitle, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
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
                            Text("Next", fontSize = 12.sp, color = theme.textPrimary)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(modifier = Modifier.fillMaxWidth()) {
                        listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat").forEach { dow ->
                            Text(
                                text = dow,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = theme.textMuted,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    val firstDayOfMonth = displayedMonth.atDay(1)
                    val startOffset = firstDayOfMonth.dayOfWeek.value % 7
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
                                            studiedSec >= 4 * 3600L -> Color(0xFF047857)
                                            studiedSec > 0L -> if (theme.isDark) Color(0xFF112A22) else Color(0xFFD1FAE5)
                                            else -> theme.surface
                                        }
                                        val borderColor = when {
                                            isSelected -> Color(0xFF10B981)
                                            isToday -> Color(0xFF0284C7)
                                            else -> Color.Transparent
                                        }
                                        val numColor = if (isSelected || (studiedSec >= 4 * 3600L)) Color.White else theme.textPrimary

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
                                                    color = numColor
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
                                                        color = if (isSelected) Color(0xFFA7F3D0) else Color(0xFF10B981)
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

        // 2. GRAPH 1: TIME BY SUBJECT (X-Axis = Subjects, Y-Axis = Hours Studied on Selected Date)
        item {
            val selectedDayTotalSec = totalsByDate[selectedDateIso] ?: 0L
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Graph 1: Time by Subject ($selectedDateIso)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "Hours studied per subject on the selected day (X-axis = Subject)",
                                fontSize = 11.sp,
                                color = theme.textMuted
                            )
                        }
                        Text(
                            text = formatDurationHoursMinutes(selectedDayTotalSec),
                            fontSize = 15.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF10B981)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (subjectHoursForSelectedDate.isEmpty()) {
                        Text(
                            text = "Add subjects on the Subjects tab to view the Time by Subject graph.",
                            fontSize = 12.sp,
                            color = theme.textMuted,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        val maxSubSec = subjectHoursForSelectedDate.maxOf { it.third }.coerceAtLeast(3600L)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            subjectHoursForSelectedDate.forEach { (subLabel, hex, sec) ->
                                val barColor = parseHexColor(hex)
                                val heightFraction = (sec.toFloat() / maxSubSec.toFloat()).coerceIn(0.04f, 1f)
                                val timeText = if (sec >= 3600L) {
                                    String.format("%.1fh", sec / 3600.0)
                                } else if (sec > 0L) {
                                    "${sec / 60L}m"
                                } else {
                                    "0m"
                                }

                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .padding(horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Bottom
                                ) {
                                    Text(
                                        text = timeText,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (sec > 0L) barColor else theme.textMuted
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(0.68f)
                                            .fillMaxHeight(heightFraction * 0.72f)
                                            .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                            .background(if (sec > 0L) barColor else theme.divider)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = subLabel,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = theme.textPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 3. GRAPH 2: TIME VS. DAY (Monthly Progression Graph for the Whole Month)
        item {
            val monthName = "${displayedMonth.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())} ${displayedMonth.year}"
            val maxDaySec = (dailySecondsForMonth.maxOrNull() ?: 0L).coerceAtLeast(3600L)
            val maxDayHoursLabel = String.format("%.1fh", maxDaySec / 3600.0)
            val midDayHoursLabel = String.format("%.1fh", (maxDaySec / 2.0) / 3600.0)
            val activeDaysCount = dailySecondsForMonth.count { it > 0L }

            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Graph 2: Time vs. Day ($monthName)",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = theme.textPrimary
                            )
                            Text(
                                text = "Daily study hours across the whole month ($activeDaysCount active days)",
                                fontSize = 11.sp,
                                color = theme.textMuted
                            )
                        }
                        Text(
                            text = formatDurationHoursMinutes(monthTotalSeconds),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF06B6D4)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(155.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Y-Axis Labels
                        Column(
                            modifier = Modifier
                                .fillMaxHeight()
                                .padding(end = 6.dp, bottom = 18.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.End
                        ) {
                            Text(maxDayHoursLabel, fontSize = 9.sp, color = theme.textMuted)
                            Text(midDayHoursLabel, fontSize = 9.sp, color = theme.textMuted)
                            Text("0h", fontSize = 9.sp, color = theme.textMuted)
                        }

                        // Monthly Bar + Trend Line Canvas
                        Column(modifier = Modifier.weight(1f)) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                val w = size.width
                                val h = size.height
                                val n = dailySecondsForMonth.size.coerceAtLeast(1)
                                val slotW = w / n.toFloat()
                                val barW = (slotW * 0.62f).coerceAtLeast(3f)

                                // Horizontal reference lines
                                listOf(0f, 0.5f, 1f).forEach { frac ->
                                    val y = h * frac
                                    drawLine(
                                        color = theme.divider,
                                        start = Offset(0f, y),
                                        end = Offset(w, y),
                                        strokeWidth = 1f
                                    )
                                }

                                val linePath = Path()
                                var firstPoint = true

                                dailySecondsForMonth.forEachIndexed { idx, sec ->
                                    val ratio = (sec.toFloat() / maxDaySec.toFloat()).coerceIn(0f, 1f)
                                    val barH = ratio * (h - 6f)
                                    val cx = idx * slotW + slotW / 2f
                                    val topY = h - barH

                                    if (sec > 0L) {
                                        drawRoundRect(
                                            color = Color(0xFF10B981).copy(alpha = 0.45f),
                                            topLeft = Offset(cx - barW / 2f, topY),
                                            size = Size(barW, barH),
                                            cornerRadius = CornerRadius(3f, 3f)
                                        )
                                    }

                                    if (firstPoint) {
                                        linePath.moveTo(cx, topY)
                                        firstPoint = false
                                    } else {
                                        linePath.lineTo(cx, topY)
                                    }
                                }

                                drawPath(
                                    path = linePath,
                                    color = Color(0xFF06B6D4),
                                    style = Stroke(width = 3f, cap = StrokeCap.Round)
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            // X-Axis Day Markers
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf("Day 1", "Day 8", "Day 15", "Day 22", "Day $daysInMonth").forEach { lbl ->
                                    Text(lbl, fontSize = 9.sp, color = theme.textMuted)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// =========================================================================
// TAB 4: GENERAL SETTINGS — THEME, ALARM SOUND, 15s STRICT FOCUS & BACKUP
// =========================================================================
@Composable
private fun GeneralSettingsTab(
    theme: AppThemeColors,
    state: StudyTrackerUiState,
    viewModel: StudyTrackerViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: (Boolean) -> Unit,
    selectedSirenType: SirenSoundOption,
    customMusicName: String,
    onSelectSirenType: (SirenSoundOption) -> Unit,
    onPickCustomMusicFile: () -> Unit,
    strictFocus15sEnabled: Boolean,
    onToggleStrictFocus15s: (Boolean) -> Unit,
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
                color = theme.textPrimary
            )
            Text(
                text = "Theme mode, custom alarm sound, 15s focus guard, and backups",
                fontSize = 12.sp,
                color = theme.textMuted
            )
        }

        // 1. Theme Mode Card (Dark vs. Light)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("App Theme (Dark / Light)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                    Text(
                        text = "Switch between True AMOLED Dark Mode and Daylight Light Mode.",
                        fontSize = 12.sp,
                        color = theme.textMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FilterChip(
                            selected = isDarkTheme,
                            onClick = { onToggleTheme(true) },
                            label = { Text("Dark Mode (AMOLED)") }
                        )
                        FilterChip(
                            selected = !isDarkTheme,
                            onClick = { onToggleTheme(false) },
                            label = { Text("Light Mode") }
                        )
                    }
                }
            }
        }

        // 2. Alarm & Siren Sound Selector Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Alarm & Siren Sound", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                    Text(
                        text = "Choose which siren or custom music plays when your study timer finishes.",
                        fontSize = 12.sp,
                        color = theme.textMuted
                    )

                    SirenSoundOption.values().forEach { option ->
                        val isSelected = selectedSirenType == option
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) Color(0xFF10B981).copy(alpha = 0.15f) else theme.surface)
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) Color(0xFF10B981) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { onSelectSirenType(option) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = option.label,
                                    fontSize = 13.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = theme.textPrimary
                                )
                                if (option == SirenSoundOption.CUSTOM_MUSIC) {
                                    Text(
                                        text = "Selected file: $customMusicName",
                                        fontSize = 11.sp,
                                        color = theme.textMuted
                                    )
                                }
                            }
                            if (isSelected) {
                                Text("Active", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = onPickCustomMusicFile,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Pick Music from Phone", fontSize = 12.sp, color = theme.textPrimary)
                        }
                        Button(
                            onClick = onTestSirenClick,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Alarm Sound", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // 3. Strict Focus (15-Second Leave App Auto-Stop) & Timer Defaults Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Focus Guard & Timer Settings", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)

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
                            Text("Auto-Stop If You Leave App (>15 Seconds)", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Automatically saves and stops the timer if you switch to another app with the screen unlocked for more than 15 seconds (locking your screen keeps the timer running)", color = theme.textMuted, fontSize = 11.sp)
                        }
                        Switch(checked = strictFocus15sEnabled, onCheckedChange = onToggleStrictFocus15s)
                    }

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
                            Text("Ring Alarm When Daily Subject Target Reached", color = theme.textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text("Automatically sounds your chosen alarm when a subject reaches 100% of its daily target hours", color = theme.textMuted, fontSize = 11.sp)
                        }
                        Switch(checked = autoStop, onCheckedChange = { autoStop = it })
                    }

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
                        label = { Text("Default Countdown Limit (Minutes)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            val mins = defaultMinsText.toIntOrNull() ?: 60
                            viewModel.updateGeneralSettings(defaultMode, mins, autoStop)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Timer Preferences", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Home Screen Widget Sync Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Daily Home Screen & Lock Screen Widget", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                    Text(
                        text = "Resets to 0% each new day and updates your Lock Screen and Home Screen progress bars.",
                        fontSize = 12.sp,
                        color = theme.textMuted
                    )
                    Button(
                        onClick = { viewModel.refreshHomeWidgetNow() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981), contentColor = Color.Black),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Sync & Refresh Widget Now", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 5. Offline Backup & Excel CSV Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Offline Backup & Excel Export", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = theme.textPrimary)
                    Button(onClick = onExportCsv, modifier = Modifier.fillMaxWidth()) {
                        Text("Export Subjects & Calendar History for Excel (.csv)")
                    }
                    OutlinedButton(onClick = onBackupJson, modifier = Modifier.fillMaxWidth()) {
                        Text("Backup All Courses & History (.json)", color = theme.textPrimary)
                    }
                    OutlinedButton(onClick = onRestoreJson, modifier = Modifier.fillMaxWidth()) {
                        Text("Restore Backup from File (.json)", color = theme.textPrimary)
                    }
                }
            }
        }

        // 6. Reset Controls Card
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = theme.card),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Reset & Data Controls", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFFEF4444))
                    OutlinedButton(
                        onClick = onRequestResetHours,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Reset All Studied Hours to 0 (Keep Subjects)", color = Color(0xFFD97706))
                    }
                    OutlinedButton(
                        onClick = onRequestDeleteAll,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Delete All Subjects & Start Fresh", color = Color(0xFFEF4444))
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
