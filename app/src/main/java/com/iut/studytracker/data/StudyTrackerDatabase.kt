package com.iut.studytracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        SubjectEntity::class,
        StudySessionEntity::class,
        TaskItemEntity::class,
        ExamScheduleEntity::class,
        DailyGoalEntity::class,
        StudyNoteEntity::class,
        TimerPresetEntity::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class StudyTrackerDatabase : RoomDatabase() {

    abstract fun dao(): StudyTrackerDao

    companion object {
        @Volatile
        private var INSTANCE: StudyTrackerDatabase? = null

        fun getInstance(context: Context): StudyTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyTrackerDatabase::class.java,
                    "study_tracker_offline.db"
                )
                    .addCallback(object : Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.dao()?.let { dao ->
                                    seedInitialData(dao)
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        private suspend fun seedInitialData(dao: StudyTrackerDao) {
            val defaultSubjects = listOf(
                SubjectEntity(code = "CEE 4311", name = "Structural Analysis & Design I", colorHex = "#10B981", targetHoursPerWeek = 8f, credits = 3.0f),
                SubjectEntity(code = "CEE 4313", name = "Reinforced Concrete Structures I", colorHex = "#3B82F6", targetHoursPerWeek = 8f, credits = 3.0f),
                SubjectEntity(code = "CEE 4351", name = "Transportation Engineering I", colorHex = "#F59E0B", targetHoursPerWeek = 6f, credits = 3.0f),
                SubjectEntity(code = "CEE 4341", name = "Geotechnical Engineering I", colorHex = "#8B5CF6", targetHoursPerWeek = 6f, credits = 3.0f),
                SubjectEntity(code = "CEE 4331", name = "Environmental Engineering I", colorHex = "#EC4899", targetHoursPerWeek = 5f, credits = 3.0f)
            )
            dao.insertSubjects(defaultSubjects)

            val defaultPresets = listOf(
                TimerPresetEntity(name = "Classic Pomodoro (25/5)", focusMinutes = 25, shortBreakMinutes = 5, longBreakMinutes = 15, roundsBeforeLongBreak = 4),
                TimerPresetEntity(name = "Deep Engineering Block (50/10)", focusMinutes = 50, shortBreakMinutes = 10, longBreakMinutes = 20, roundsBeforeLongBreak = 3),
                TimerPresetEntity(name = "Exam Problem Set (90/15)", focusMinutes = 90, shortBreakMinutes = 15, longBreakMinutes = 30, roundsBeforeLongBreak = 2)
            )
            dao.insertPresets(defaultPresets)
        }
    }
}
