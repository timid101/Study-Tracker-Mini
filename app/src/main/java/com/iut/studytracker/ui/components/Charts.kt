package com.iut.studytracker.ui.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.iut.studytracker.data.DailyStudyTotal
import com.iut.studytracker.data.SubjectStudySummary
import java.time.LocalDate

fun parseHexColor(hex: String): Color {
    return runCatching { Color(AndroidColor.parseColor(hex)) }.getOrDefault(Color(0xFF10B981))
}

@Composable
fun SubjectDonutChart(
    summaries: List<SubjectStudySummary>,
    modifier: Modifier = Modifier
) {
    val totalSeconds = summaries.sumOf { it.totalSeconds }.coerceAtLeast(1L)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Box(
            modifier = Modifier.size(150.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.size(130.dp)) {
                val strokeWidth = 28f
                var startAngle = -90f
                if (summaries.all { it.totalSeconds == 0L }) {
                    drawArc(
                        color = Color(0xFF1F2937),
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                    )
                } else {
                    summaries.forEach { item ->
                        val sweep = (item.totalSeconds.toFloat() / totalSeconds.toFloat()) * 360f
                        if (sweep > 0f) {
                            drawArc(
                                color = parseHexColor(item.colorHex),
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt)
                            )
                            startAngle += sweep
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                val totalHours = String.format("%.1f h", summaries.sumOf { it.totalSeconds } / 3600.0)
                Text(
                    text = totalHours,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Total Focus",
                    color = Color(0xFF9CA3AF),
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            summaries.forEach { item ->
                val hours = String.format("%.1fh", item.totalSeconds / 3600.0)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(parseHexColor(item.colorHex), CircleShape)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = item.code,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Text(
                        text = hours,
                        color = Color(0xFF9CA3AF),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ActivityHeatmap365(
    dailyTotals: List<DailyStudyTotal>,
    modifier: Modifier = Modifier
) {
    val mapByDate = dailyTotals.associateBy({ it.dateIso }, { it.totalSeconds })
    val today = LocalDate.now()
    val startDay = today.minusDays(364)
    val scrollState = rememberScrollState()

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "365-Day Study Consistency Heatmap",
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState),
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            for (week in 0 until 53) {
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    for (dayOfWeek in 0 until 7) {
                        val offset = week * 7L + dayOfWeek
                        if (offset <= 364L) {
                            val dateStr = startDay.plusDays(offset).toString()
                            val secs = mapByDate[dateStr] ?: 0L
                            val cellColor = when {
                                secs >= 4 * 3600L -> Color(0xFF10B981)
                                secs >= 2 * 3600L -> Color(0xFF059669)
                                secs >= 45 * 60L -> Color(0xFF047857)
                                secs > 0L -> Color(0xFF064E3B)
                                else -> Color(0xFF161F30)
                            }
                            Box(
                                modifier = Modifier
                                    .size(11.dp)
                                    .background(cellColor, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }
        }
    }
}
