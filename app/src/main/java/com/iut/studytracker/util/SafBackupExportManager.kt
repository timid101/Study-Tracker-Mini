package com.iut.studytracker.util

import android.content.Context
import android.net.Uri
import com.iut.studytracker.data.ExamScheduleEntity
import com.iut.studytracker.data.StudyNoteEntity
import com.iut.studytracker.data.StudySessionEntity
import com.iut.studytracker.data.StudyTrackerDao
import com.iut.studytracker.data.SubjectEntity
import com.iut.studytracker.data.TaskCategory
import com.iut.studytracker.data.TaskItemEntity
import com.iut.studytracker.data.TaskPriority
import com.iut.studytracker.data.TimerMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter

object SafBackupExportManager {

    suspend fun exportFullDatabaseJson(context: Context, dao: StudyTrackerDao, uri: Uri): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val subjects = dao.getAllSubjectsOnce()
                val sessions = dao.getAllSessionsOnce()
                val tasks = dao.getAllTasksOnce()
                val exams = dao.getAllExamsOnce()
                val notes = dao.getAllNotesOnce()

                val root = JSONObject().apply {
                    put("schemaVersion", 2)
                    put("exportedAtEpochMs", System.currentTimeMillis())
                    put("subjects", JSONArray().apply {
                        subjects.forEach { s ->
                            put(JSONObject().apply {
                                put("id", s.id)
                                put("code", s.code)
                                put("name", s.name)
                                put("colorHex", s.colorHex)
                                put("targetHoursPerWeek", s.targetHoursPerWeek.toDouble())
                                put("credits", s.credits.toDouble())
                                put("isArchived", s.isArchived)
                                put("createdAtEpochMs", s.createdAtEpochMs)
                            })
                        }
                    })
                    put("sessions", JSONArray().apply {
                        sessions.forEach { ss ->
                            put(JSONObject().apply {
                                put("id", ss.id)
                                put("subjectId", ss.subjectId)
                                put("startTimeEpochMs", ss.startTimeEpochMs)
                                put("endTimeEpochMs", ss.endTimeEpochMs)
                                put("durationSeconds", ss.durationSeconds)
                                put("mode", ss.mode.name)
                                put("topicStudied", ss.topicStudied)
                                put("focusRating", ss.focusRating)
                                put("notes", ss.notes)
                                put("dateIso", ss.dateIso)
                            })
                        }
                    })
                    put("tasks", JSONArray().apply {
                        tasks.forEach { t ->
                            put(JSONObject().apply {
                                put("id", t.id)
                                put("subjectId", t.subjectId)
                                put("title", t.title)
                                put("category", t.category.name)
                                put("priority", t.priority.name)
                                put("dueDateIso", t.dueDateIso)
                                put("isCompleted", t.isCompleted)
                            })
                        }
                    })
                    put("exams", JSONArray().apply {
                        exams.forEach { e ->
                            put(JSONObject().apply {
                                put("id", e.id)
                                put("subjectId", e.subjectId)
                                put("examTitle", e.examTitle)
                                put("examDateIso", e.examDateIso)
                                put("roomOrVenue", e.roomOrVenue)
                                put("syllabusTopics", e.syllabusTopics)
                                put("targetMarks", e.targetMarks)
                            })
                        }
                    })
                    put("notes", JSONArray().apply {
                        notes.forEach { n ->
                            put(JSONObject().apply {
                                put("id", n.id)
                                put("subjectId", n.subjectId)
                                put("title", n.title)
                                put("formulaOrSummary", n.formulaOrSummary)
                                put("updatedAtEpochMs", n.updatedAtEpochMs)
                            })
                        }
                    })
                }

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                        writer.write(root.toString(2))
                    }
                } ?: error("Unable to open output stream")
                subjects.size
            }
        }

    suspend fun importFullDatabaseJson(context: Context, dao: StudyTrackerDao, uri: Uri): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val rawJson = context.contentResolver.openInputStream(uri)?.use { input ->
                    BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
                } ?: error("Unable to read backup file")

                val root = JSONObject(rawJson)
                val subjectsArr = root.optJSONArray("subjects") ?: JSONArray()
                val sessionsArr = root.optJSONArray("sessions") ?: JSONArray()
                val tasksArr = root.optJSONArray("tasks") ?: JSONArray()
                val examsArr = root.optJSONArray("exams") ?: JSONArray()
                val notesArr = root.optJSONArray("notes") ?: JSONArray()

                val subjects = (0 until subjectsArr.length()).map { i ->
                    val o = subjectsArr.getJSONObject(i)
                    SubjectEntity(
                        id = o.getLong("id"),
                        code = o.optString("code", ""),
                        name = o.getString("name"),
                        colorHex = o.optString("colorHex", "#10B981"),
                        targetHoursPerWeek = o.optDouble("targetHoursPerWeek", 10.0).toFloat(),
                        credits = o.optDouble("credits", 3.0).toFloat(),
                        isArchived = o.optBoolean("isArchived", false),
                        createdAtEpochMs = o.optLong("createdAtEpochMs", System.currentTimeMillis())
                    )
                }

                val sessions = (0 until sessionsArr.length()).map { i ->
                    val o = sessionsArr.getJSONObject(i)
                    StudySessionEntity(
                        id = o.getLong("id"),
                        subjectId = o.getLong("subjectId"),
                        startTimeEpochMs = o.getLong("startTimeEpochMs"),
                        endTimeEpochMs = o.getLong("endTimeEpochMs"),
                        durationSeconds = o.getLong("durationSeconds"),
                        mode = runCatching { TimerMode.valueOf(o.optString("mode", "STOPWATCH")) }.getOrDefault(TimerMode.STOPWATCH),
                        topicStudied = o.optString("topicStudied", ""),
                        focusRating = o.optInt("focusRating", 5),
                        notes = o.optString("notes", ""),
                        dateIso = o.getString("dateIso")
                    )
                }

                val tasks = (0 until tasksArr.length()).map { i ->
                    val o = tasksArr.getJSONObject(i)
                    TaskItemEntity(
                        id = o.getLong("id"),
                        subjectId = o.getLong("subjectId"),
                        title = o.getString("title"),
                        category = runCatching { TaskCategory.valueOf(o.optString("category", "ASSIGNMENT")) }.getOrDefault(TaskCategory.ASSIGNMENT),
                        priority = runCatching { TaskPriority.valueOf(o.optString("priority", "MEDIUM")) }.getOrDefault(TaskPriority.MEDIUM),
                        dueDateIso = o.getString("dueDateIso"),
                        isCompleted = o.optBoolean("isCompleted", false)
                    )
                }

                val exams = (0 until examsArr.length()).map { i ->
                    val o = examsArr.getJSONObject(i)
                    ExamScheduleEntity(
                        id = o.getLong("id"),
                        subjectId = o.getLong("subjectId"),
                        examTitle = o.getString("examTitle"),
                        examDateIso = o.getString("examDateIso"),
                        roomOrVenue = o.optString("roomOrVenue", ""),
                        syllabusTopics = o.optString("syllabusTopics", ""),
                        targetMarks = o.optString("targetMarks", "")
                    )
                }

                val notes = (0 until notesArr.length()).map { i ->
                    val o = notesArr.getJSONObject(i)
                    StudyNoteEntity(
                        id = o.getLong("id"),
                        subjectId = o.getLong("subjectId"),
                        title = o.getString("title"),
                        formulaOrSummary = o.getString("formulaOrSummary"),
                        updatedAtEpochMs = o.optLong("updatedAtEpochMs", System.currentTimeMillis())
                    )
                }

                val presets = dao.getAllPresetsOnce()
                val goals = dao.getAllDailyGoalsOnce()
                dao.restoreFullSnapshot(subjects, sessions, tasks, exams, notes, goals, presets)
                subjects.size
            }
        }

    suspend fun exportSessionsToExcelCsv(context: Context, dao: StudyTrackerDao, uri: Uri): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val summaries = dao.getSubjectSummariesOnce()
                val subjectsById = summaries.associateBy { it.subjectId }
                val sessions = dao.getAllSessionsOnce()

                context.contentResolver.openOutputStream(uri)?.use { out ->
                    OutputStreamWriter(out, Charsets.UTF_8).use { writer ->
                        writer.write("\uFEFF")
                        writer.write("SUBJECT TARGET PROGRESS SUMMARY\n")
                        writer.write("Course Code,Subject Name,Target Hours,Completed Hours,Remaining Hours,Completion %\n")
                        for (sum in summaries) {
                            val safeCode = sum.code.replace("\"", "\"\"")
                            val safeName = sum.name.replace("\"", "\"\"")
                            val targetH = String.format("%.2f", sum.targetHoursPerWeek)
                            val studiedH = String.format("%.2f", sum.totalSeconds / 3600.0)
                            val remH = String.format("%.2f", sum.remainingSeconds / 3600.0)
                            val pct = String.format("%.1f%%", sum.completionPercent)
                            writer.write("\"$safeCode\",\"$safeName\",$targetH,$studiedH,$remH,$pct\n")
                        }
                        writer.write("\nSTUDY SESSION LOGS\n")
                        writer.write("Session ID,Date,Course Code,Subject Name,Timer Mode,Duration (Minutes),Duration (Hours),Notes\n")
                        for (s in sessions) {
                            val sub = subjectsById[s.subjectId]
                            val mins = String.format("%.1f", s.durationSeconds / 60.0)
                            val hrs = String.format("%.2f", s.durationSeconds / 3600.0)
                            val safeTopic = s.topicStudied.replace("\"", "\"\"")
                            val safeName = (sub?.name ?: "Unknown").replace("\"", "\"\"")
                            val safeCode = (sub?.code ?: "").replace("\"", "\"\"")
                            writer.write("${s.id},${s.dateIso},\"$safeCode\",\"$safeName\",${s.mode.name},$mins,$hrs,\"$safeTopic\"\n")
                        }
                    }
                } ?: error("Unable to open CSV output stream")
                summaries.size
            }
        }
}
