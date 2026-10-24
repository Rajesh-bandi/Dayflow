package com.dayflow.data

import com.dayflow.data.local.DayflowDatabase
import com.dayflow.data.local.RoutineEntity
import com.dayflow.domain.Task
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.OverlapDetector
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

data class RoutineApplyResult(val taskId: Long?, val conflicts: List<String> = emptyList(), val alreadyPresent: Boolean = false)

class RoutineRepository(private val db: DayflowDatabase, private val tasks: TaskRepository) {
    fun observeAll(): Flow<List<RoutineEntity>> = db.routineDao().observeAll()
    suspend fun save(name: String, task: Task): Long = db.routineDao().insert(
        RoutineEntity(name = name.trim(), title = task.title, description = task.description, type = task.type.name,
            startTimeMinutes = task.startTimeMinutes, durationMinutes = task.durationMinutes, priority = task.priority.name,
            destinationUrl = task.destinationUrl, focusProfile = task.focusProfile, createdAtMillis = System.currentTimeMillis())
    )
    suspend fun apply(routine: RoutineEntity, dateEpochDay: Long, confirmedConflicts: Boolean = false): RoutineApplyResult {
        val existing = tasks.observeTasks().first().firstOrNull {
            it.title == routine.title && it.startEpochDay == dateEpochDay && it.startTimeMinutes == routine.startTimeMinutes && it.durationMinutes == routine.durationMinutes && it.type.name == routine.type
        }
        if (existing != null) return RoutineApplyResult(existing.id, alreadyPresent = true)
        val date = LocalDate.ofEpochDay(dateEpochDay)
        val template = Task(title = routine.title, description = routine.description,
            type = runCatching { com.dayflow.domain.TaskType.valueOf(routine.type) }.getOrDefault(com.dayflow.domain.TaskType.REGULAR),
            category = "Routine", startEpochDay = dateEpochDay, startTimeMinutes = routine.startTimeMinutes,
            durationMinutes = routine.durationMinutes,
            priority = runCatching { com.dayflow.domain.Priority.valueOf(routine.priority) }.getOrDefault(com.dayflow.domain.Priority.NORMAL),
            destinationUrl = routine.destinationUrl, focusProfile = routine.focusProfile)
        val scheduled = RecurrenceEngine.occurrencesOn(tasks.observeTasks().first(), date)
        val candidate = RecurrenceEngine.occurrenceFor(template, date)!!
        val conflicts = OverlapDetector.findOverlaps(scheduled + candidate).map { (a, b) ->
            val other = if (a.taskId == candidate.taskId) b else a
            "${other.task.title} · ${other.task.startTimeMinutes}"
        }.distinct()
        if (conflicts.isNotEmpty() && !confirmedConflicts) return RoutineApplyResult(null, conflicts)
        return RoutineApplyResult(tasks.saveTask(template), conflicts)
    }
    suspend fun delete(id: Long) = db.routineDao().delete(id)
}
