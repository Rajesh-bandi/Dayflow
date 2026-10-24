package com.dayflow.domain

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.ZoneId

/** The task archetypes already represented in the Stitch UI. */
enum class TaskType(val label: String) {
    REGULAR("Regular"),
    DEEP_WORK("Deep Work"),
    QUICK_ACTION("Quick Action"),
    HABIT("Habit"),
    BREAK("Break");
}

enum class Priority { LOW, NORMAL, HIGH }

/** Proper recurrence model (not a boolean): supports one-time, daily, weekdays, weekends, selected weekdays. */
enum class RecurrenceFrequency { NONE, DAILY, WEEKDAYS, WEEKENDS, SELECTED_DAYS }

data class Recurrence(
    val frequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val endEpochDay: Long? = null
) {
    companion object {
        val ONE_TIME = Recurrence()
        fun daily(endEpochDay: Long? = null) = Recurrence(RecurrenceFrequency.DAILY, endEpochDay = endEpochDay)
        fun weekdays(endEpochDay: Long? = null) = Recurrence(RecurrenceFrequency.WEEKDAYS, endEpochDay = endEpochDay)
        fun weekends(endEpochDay: Long? = null) = Recurrence(RecurrenceFrequency.WEEKENDS, endEpochDay = endEpochDay)
        fun selected(days: Set<DayOfWeek>, endEpochDay: Long? = null) =
            Recurrence(RecurrenceFrequency.SELECTED_DAYS, days, endEpochDay)
    }
}

data class Task(
    val id: Long = 0L,
    val title: String = "",
    val description: String = "",
    val type: TaskType = TaskType.REGULAR,
    val category: String = "",
    val startEpochDay: Long = 0L,
    val startTimeMinutes: Int = 0,
    val durationMinutes: Int = 60,
    val priority: Priority = Priority.NORMAL,
    val destinationUrl: String? = null,
    val focusProfile: String? = null,
    val recurrence: Recurrence = Recurrence.ONE_TIME,
    val createdAtMillis: Long = 0L,
    val modifiedAtMillis: Long = 0L,
    val archivedAtMillis: Long? = null
) {
    fun startDate(): LocalDate = LocalDate.ofEpochDay(startEpochDay)

    fun startLocalDateTime(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()) =
        date.atTime(startTimeMinutes / 60, startTimeMinutes % 60).atZone(zone).toLocalDateTime()

    val isRecurring: Boolean get() = recurrence.frequency != RecurrenceFrequency.NONE
}

/** A single materialized instance of a task on a local date. */
data class TaskOccurrence(
    val task: Task,
    val date: LocalDate,
    val startMillis: Long,
    val endMillis: Long
) {
    val taskId: Long get() = task.id
    val startMinuteOfDay: Int get() = task.startTimeMinutes
    val durationMinutes: Int get() = task.durationMinutes
}

/** One row of completion history, keyed by (taskId, dateEpochDay) so recurring occurrences stay independent. */
data class TaskCompletion(
    val taskId: Long,
    val dateEpochDay: Long,
    val completed: Boolean,
    val completedAtMillis: Long?,
    val status: String = if (completed) "COMPLETED" else "PLANNED"
)
