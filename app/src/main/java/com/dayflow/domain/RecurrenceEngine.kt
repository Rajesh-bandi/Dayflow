package com.dayflow.domain

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Pure, independently testable occurrence math: decides whether a task occurs on a
 * date and materializes occurrences with zone-consistent instants.
 */
object RecurrenceEngine {

    fun occursOn(task: Task, date: LocalDate): Boolean {
        val epochDay = date.toEpochDay()
        if (epochDay < task.startEpochDay) return false
        val end = task.recurrence.endEpochDay
        if (end != null && epochDay > end) return false
        return when (task.recurrence.frequency) {
            RecurrenceFrequency.NONE -> epochDay == task.startEpochDay
            RecurrenceFrequency.DAILY -> true
            RecurrenceFrequency.WEEKDAYS -> when (date.dayOfWeek) {
                java.time.DayOfWeek.SATURDAY, java.time.DayOfWeek.SUNDAY -> false
                else -> true
            }
            RecurrenceFrequency.WEEKENDS ->
                date.dayOfWeek == java.time.DayOfWeek.SATURDAY || date.dayOfWeek == java.time.DayOfWeek.SUNDAY
            RecurrenceFrequency.SELECTED_DAYS -> date.dayOfWeek in task.recurrence.daysOfWeek
        }
    }

    fun occurrenceFor(task: Task, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): TaskOccurrence? {
        if (!occursOn(task, date)) return null
        val start = LocalDateTime.of(date, task.startLocalTime())
            .atZone(zone).toInstant().toEpochMilli()
        return TaskOccurrence(task, date, start, start + task.durationMinutes * 60_000L)
    }

    fun occurrencesOn(tasks: List<Task>, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<TaskOccurrence> =
        tasks.mapNotNull { occurrenceFor(it, date, zone) }.sortedBy { it.startMillis }

    fun occurrencesBetween(
        tasks: List<Task>,
        from: LocalDate,
        to: LocalDate,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<TaskOccurrence> {
        if (to.isBefore(from)) return emptyList()
        val result = ArrayList<TaskOccurrence>()
        var day = from
        while (!day.isAfter(to)) {
            result += occurrencesOn(tasks, day, zone)
            day = day.plusDays(1)
        }
        return result
    }

    private fun Task.startLocalTime() = java.time.LocalTime.of(startTimeMinutes / 60, startTimeMinutes % 60)
}
