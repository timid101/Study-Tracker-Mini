package com.iut.studytracker.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

enum class TimerMode { STOPWATCH, POMODORO }
enum class TaskPriority { HIGH, MEDIUM, LOW }
enum class TaskCategory { ASSIGNMENT, LAB_REPORT, CLASS_TEST, EXAM_PREP, PROJECT }

// 1. Subject / Course Entity (User defines their own courses and target study hours)
@Entity(tableName = "subjects")
data class SubjectEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val code: String,
    val name: String,
    val colorHex: String,
    val targetHoursPerWeek: Float = 10.0f,
    val credits: Float = 3.0f,
    val isArchived: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

// 2. Study Session Log Entity
@Entity(
    tableName = "study_sessions",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId"), Index("dateIso")]
)
data class StudySessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val startTimeEpochMs: Long,
    val endTimeEpochMs: Long,
    val durationSeconds: Long,
    val mode: TimerMode = TimerMode.STOPWATCH,
    val topicStudied: String = "",
    val focusRating: Int = 5,
    val notes: String = "",
    val dateIso: String
)

// 3. Task Entity
@Entity(
    tableName = "tasks",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class TaskItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val category: TaskCategory = TaskCategory.ASSIGNMENT,
    val priority: TaskPriority = TaskPriority.MEDIUM,
    val dueDateIso: String,
    val isCompleted: Boolean = false,
    val completedAtEpochMs: Long? = null
)

// 4. Exam Schedule Entity
@Entity(
    tableName = "exams",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class ExamScheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val examTitle: String,
    val examDateIso: String,
    val roomOrVenue: String = "",
    val syllabusTopics: String = "",
    val targetMarks: String = ""
)

// 5. Daily Study Goal Entity
@Entity(tableName = "daily_goals")
data class DailyGoalEntity(
    @PrimaryKey val dateIso: String,
    val targetMinutes: Int = 240,
    val notes: String = ""
)

// 6. Study Note Entity
@Entity(
    tableName = "study_notes",
    foreignKeys = [
        ForeignKey(
            entity = SubjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["subjectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("subjectId")]
)
data class StudyNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val subjectId: Long,
    val title: String,
    val formulaOrSummary: String,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

// 7. Timer Preset Entity
@Entity(tableName = "timer_presets")
data class TimerPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val focusMinutes: Int,
    val shortBreakMinutes: Int,
    val longBreakMinutes: Int,
    val roundsBeforeLongBreak: Int = 4
)

// Projections
data class SubjectStudySummary(
    val subjectId: Long,
    val code: String,
    val name: String,
    val colorHex: String,
    val targetHoursPerWeek: Float,
    val totalSeconds: Long,
    val sessionCount: Int
) {
    val targetSeconds: Long
        get() = (targetHoursPerWeek.coerceAtLeast(0.1f) * 3600f).toLong()

    val remainingSeconds: Long
        get() = (targetSeconds - totalSeconds).coerceAtLeast(0L)

    val completionPercent: Float
        get() = if (targetSeconds > 0L) {
            ((totalSeconds.toFloat() / targetSeconds.toFloat()) * 100f).coerceIn(0f, 100f)
        } else 0f
}

data class DailyStudyTotal(
    val dateIso: String,
    val totalSeconds: Long,
    val sessionCount: Int
)

data class SubjectWithRelations(
    @Embedded val subject: SubjectEntity,
    @Relation(parentColumn = "id", entityColumn = "subjectId")
    val tasks: List<TaskItemEntity>,
    @Relation(parentColumn = "id", entityColumn = "subjectId")
    val exams: List<ExamScheduleEntity>
)
