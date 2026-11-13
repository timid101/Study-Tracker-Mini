package com.iut.studytracker.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val AmoledBlack = Color(0xFF000000)
val AmoledSurface = Color(0xFF0B0F17)
val AmoledCard = Color(0xFF111827)
val EmeraldAccent = Color(0xFF10B981)
val CyanAccent = Color(0xFF06B6D4)
val AmberAccent = Color(0xFFF59E0B)
val TextPrimary = Color(0xFFF9FAFB)
val TextMuted = Color(0xFF9CA3AF)

private val AmoledColorScheme = darkColorScheme(
    primary = EmeraldAccent,
    onPrimary = Color.Black,
    secondary = CyanAccent,
    onSecondary = Color.Black,
    tertiary = AmberAccent,
    background = AmoledBlack,
    onBackground = TextPrimary,
    surface = AmoledSurface,
    onSurface = TextPrimary,
    surfaceVariant = AmoledCard,
    onSurfaceVariant = TextMuted
)

@Composable
fun StudyTrackerAmoledTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AmoledColorScheme,
        content = content
    )
}
