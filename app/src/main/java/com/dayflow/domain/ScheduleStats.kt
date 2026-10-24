package com.dayflow.domain

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class DaySummary(val total: Int, val completed: Int) {
    val fraction: Float get() = ScheduleStats.completionFraction(completed, total)
    val percentLabel: Int get() = (fraction * 100).toInt()
    val allComplete: Boolean get() = total > 0 && completed == total
}

data class TypeStat(val type: TaskType, val totalMinutes: Int, val completedMinutes: Int)

/** Completion percentage, streak and aggregation math derived from real records. */
object ScheduleStats {

    fun completionFraction(completed: Int, total: Int): Float =
        if (total <= 0) 0f else (completed.toFloat() / total).coerceIn(0f, 1f)

    /** Occurrence counts as completed only when a completed=true record exists for (taskId, date). */
    fun summarize(occurrences: List<TaskOccurrence>, completions: List<TaskCompletion>): DaySummary {
        val skipped = completions.filter { it.status == "SKIPPED" }.map { it.taskId to it.dateEpochDay }.toSet()
        val completedKeys = completions.filter { it.completed || it.status == "COMPLETED" }.map { it.taskId to it.dateEpochDay }.toSet()
        val active = occurrences.filterNot { (it.taskId to it.date.toEpochDay()) in skipped }
        val completed = active.count { (it.taskId to it.date.toEpochDay()) in completedKeys }
        return DaySummary(active.size, completed)
    }

    fun isCompleted(occurrence: TaskOccurrence, completions: List<TaskCompletion>): Boolean =
        completions.any { it.taskId == occurrence.taskId && it.dateEpochDay == occurrence.date.toEpochDay() && (it.completed || it.status == "COMPLETED") }

    /** Consecutive days ending at [today] (or yesterday, if today has no completions yet) with at least one completion. */
    fun streakDays(completedDates: Set<LocalDate>, today: LocalDate): Int {
        var cursor = if (completedDates.contains(today)) today else today.minusDays(1)
        var streak = 0
        while (completedDates.contains(cursor)) {
            streak++
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    /** Counts only days on which at least one scheduled habit was due; unscheduled days are neutral. */
    fun habitStreakDays(tasks: List<com.dayflow.domain.Task>, completions: List<TaskCompletion>, today: LocalDate): Int {
        val habits = tasks.filter { it.type == TaskType.HABIT }
        if (habits.isEmpty()) {
            return streakDays(completions.filter { it.completed }.map { LocalDate.ofEpochDay(it.dateEpochDay) }.toSet(), today)
        }
        val completed = completions.filter { it.completed || it.status == "COMPLETED" }.map { it.taskId to it.dateEpochDay }.toSet()
        var cursor = if (habits.any { RecurrenceEngine.occursOn(it, today) } && habits.filter { RecurrenceEngine.occursOn(it, today) }.all { (it.id to today.toEpochDay()) in completed }) today else today.minusDays(1)
        var streak = 0
        repeat(366) {
            val due = habits.filter { RecurrenceEngine.occursOn(it, cursor) }
            if (due.isNotEmpty()) {
                if (!due.all { (it.id to cursor.toEpochDay()) in completed }) return streak
                streak++
            }
            cursor = cursor.minusDays(1)
        }
        return streak
    }

    fun byType(occurrences: List<TaskOccurrence>, completions: List<TaskCompletion>): List<TypeStat> =
        occurrences.filterNot { occurrence -> completions.any { it.status == "SKIPPED" && it.taskId == occurrence.taskId && it.dateEpochDay == occurrence.date.toEpochDay() } }
            .groupBy { it.task.type }.map { (type, list) ->
            TypeStat(
                type = type,
                totalMinutes = list.sumOf { it.durationMinutes },
                completedMinutes = list.filter { isCompleted(it, completions) }.sumOf { it.durationMinutes }
            )
        }.sortedByDescending { it.totalMinutes }
}
