package com.iut.studytracker.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface StudyTrackerDao {

    @Query("SELECT * FROM subjects WHERE isArchived = 0 ORDER BY code ASC")
    fun observeActiveSubjects(): Flow<List<SubjectEntity>>

    @Query("SELECT * FROM subjects ORDER BY id ASC")
    suspend fun getAllSubjectsOnce(): List<SubjectEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubject(subject: SubjectEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSubjects(subjects: List<SubjectEntity>)

    @Update
    suspend fun updateSubject(subject: SubjectEntity)

    @Delete
    suspend fun deleteSubject(subject: SubjectEntity)

    @Query("SELECT * FROM study_sessions ORDER BY startTimeEpochMs DESC")
    fun observeAllSessions(): Flow<List<StudySessionEntity>>

    @Query("SELECT * FROM study_sessions ORDER BY startTimeEpochMs DESC")
    suspend fun getAllSessionsOnce(): List<StudySessionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: StudySessionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSessions(sessions: List<StudySessionEntity>)

    @Delete
    suspend fun deleteSession(session: StudySessionEntity)

    @Query(
        """
        SELECT s.id AS subjectId,
               s.code AS code,
               s.name AS name,
               s.colorHex AS colorHex,
               s.targetHoursPerWeek AS targetHoursPerWeek,
               COALESCE(SUM(ss.durationSeconds), 0) AS totalSeconds,
               COUNT(ss.id) AS sessionCount
        FROM subjects s
        LEFT JOIN study_sessions ss ON s.id = ss.subjectId
        WHERE s.isArchived = 0
        GROUP BY s.id
        ORDER BY totalSeconds DESC, s.code ASC
        """
    )
    fun observeSubjectSummaries(): Flow<List<SubjectStudySummary>>

    @Query(
        """
        SELECT dateIso,
               COALESCE(SUM(durationSeconds), 0) AS totalSeconds,
               COUNT(id) AS sessionCount
        FROM study_sessions
        GROUP BY dateIso
        ORDER BY dateIso ASC
        """
    )
    fun observeDailyTotals(): Flow<List<DailyStudyTotal>>

    @Query("SELECT * FROM tasks ORDER BY isCompleted ASC, dueDateIso ASC")
    fun observeTasks(): Flow<List<TaskItemEntity>>

    @Query("SELECT * FROM tasks ORDER BY id ASC")
    suspend fun getAllTasksOnce(): List<TaskItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTasks(tasks: List<TaskItemEntity>)

    @Update
    suspend fun updateTask(task: TaskItemEntity)

    @Delete
    suspend fun deleteTask(task: TaskItemEntity)

    @Query("SELECT * FROM exams ORDER BY examDateIso ASC")
    fun observeExams(): Flow<List<ExamScheduleEntity>>

    @Query("SELECT * FROM exams ORDER BY id ASC")
    suspend fun getAllExamsOnce(): List<ExamScheduleEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExam(exam: ExamScheduleEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExams(exams: List<ExamScheduleEntity>)

    @Delete
    suspend fun deleteExam(exam: ExamScheduleEntity)

    @Query("SELECT * FROM study_notes ORDER BY updatedAtEpochMs DESC")
    fun observeNotes(): Flow<List<StudyNoteEntity>>

    @Query("SELECT * FROM study_notes ORDER BY id ASC")
    suspend fun getAllNotesOnce(): List<StudyNoteEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNote(note: StudyNoteEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertNotes(notes: List<StudyNoteEntity>)

    @Delete
    suspend fun deleteNote(note: StudyNoteEntity)

    @Query("SELECT * FROM timer_presets ORDER BY id ASC")
    fun observePresets(): Flow<List<TimerPresetEntity>>

    @Query("SELECT * FROM timer_presets ORDER BY id ASC")
    suspend fun getAllPresetsOnce(): List<TimerPresetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPresets(presets: List<TimerPresetEntity>)

    @Query("SELECT * FROM daily_goals ORDER BY dateIso DESC")
    fun observeDailyGoals(): Flow<List<DailyGoalEntity>>

    @Query("SELECT * FROM daily_goals ORDER BY dateIso ASC")
    suspend fun getAllDailyGoalsOnce(): List<DailyGoalEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDailyGoal(goal: DailyGoalEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyGoals(goals: List<DailyGoalEntity>)

    @Query("DELETE FROM study_sessions")
    suspend fun clearSessions()

    @Query("DELETE FROM tasks")
    suspend fun clearTasks()

    @Query("DELETE FROM exams")
    suspend fun clearExams()

    @Query("DELETE FROM study_notes")
    suspend fun clearNotes()

    @Query("DELETE FROM daily_goals")
    suspend fun clearDailyGoals()

    @Query("DELETE FROM timer_presets")
    suspend fun clearPresets()

    @Query("DELETE FROM subjects")
    suspend fun clearSubjects()

    @Transaction
    suspend fun restoreFullSnapshot(
        subjects: List<SubjectEntity>,
        sessions: List<StudySessionEntity>,
        tasks: List<TaskItemEntity>,
        exams: List<ExamScheduleEntity>,
        notes: List<StudyNoteEntity>,
        goals: List<DailyGoalEntity>,
        presets: List<TimerPresetEntity>
    ) {
        clearSessions()
        clearTasks()
        clearExams()
        clearNotes()
        clearDailyGoals()
        clearPresets()
        clearSubjects()
        insertSubjects(subjects)
        insertSessions(sessions)
        insertTasks(tasks)
        insertExams(exams)
        insertNotes(notes)
        insertDailyGoals(goals)
        insertPresets(presets)
    }
}
