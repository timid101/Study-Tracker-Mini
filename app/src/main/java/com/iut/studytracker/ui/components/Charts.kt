package com.iut.studytracker.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.ui.graphics.Color

fun parseHexColor(hex: String): Color {
    return runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color(0xFF10B981))
}

fun formatDurationHoursMinutes(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hrs = safe / 3600L
    val mins = (safe % 3600L) / 60L
    return "${hrs}h ${mins}m"
}

fun formatClockHhMmSs(seconds: Long): String {
    val safe = seconds.coerceAtLeast(0L)
    val hrs = safe / 3600L
    val mins = (safe % 3600L) / 60L
    val secs = safe % 60L
    return String.format("%02d:%02d:%02d", hrs, mins, secs)
}
