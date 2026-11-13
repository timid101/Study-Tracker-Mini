package com.iut.studytracker.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromTimerMode(value: TimerMode): String = value.name

    @TypeConverter
    fun toTimerMode(value: String): TimerMode =
        runCatching { TimerMode.valueOf(value) }.getOrDefault(TimerMode.STOPWATCH)

    @TypeConverter
    fun fromTaskPriority(value: TaskPriority): String = value.name

    @TypeConverter
    fun toTaskPriority(value: String): TaskPriority =
        runCatching { TaskPriority.valueOf(value) }.getOrDefault(TaskPriority.MEDIUM)

    @TypeConverter
    fun fromTaskCategory(value: TaskCategory): String = value.name

    @TypeConverter
    fun toTaskCategory(value: String): TaskCategory =
        runCatching { TaskCategory.valueOf(value) }.getOrDefault(TaskCategory.ASSIGNMENT)
}
