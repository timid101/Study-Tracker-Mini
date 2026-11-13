package com.iut.studytracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

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
    version = 2,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class StudyTrackerDatabase : RoomDatabase() {

    abstract fun dao(): StudyTrackerDao

    companion object {
        @Volatile
        private var INSTANCE: StudyTrackerDatabase? = null

        fun getInstance(context: Context): StudyTrackerDatabase {
            return INSTANCE ?: synchronized(this) {
                // Starts 100% clean with NO pre-populated courses so the user chooses their own subjects & targets
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    StudyTrackerDatabase::class.java,
                    "study_tracker_user_courses.db"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
