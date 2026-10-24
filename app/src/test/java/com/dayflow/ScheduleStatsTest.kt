package com.dayflow

import com.dayflow.domain.Recurrence
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.ScheduleStats
import com.dayflow.domain.Task
import com.dayflow.domain.TaskCompletion
import com.dayflow.domain.TaskType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ScheduleStatsTest {

    private val monday = LocalDate.of(2026, 10, 5)

    private fun task(id: Long, type: TaskType = TaskType.REGULAR, minutes: Int = 60) = Task(
        id = id,
        title = "Task $id",
        type = type,
        startEpochDay = monday.toEpochDay(),
        startTimeMinutes = 9 * 60,
        durationMinutes = minutes,
        recurrence = Recurrence.daily()
    )

    private fun completion(taskId: Long, day: LocalDate) =
        TaskCompletion(taskId, day.toEpochDay(), completed = true, completedAtMillis = 1L)

    @Test
    fun completionFractionMatchesPercentage() {
        assertEquals(0.68f, ScheduleStats.completionFraction(68, 100), 0.001f)
        assertEquals(0f, ScheduleStats.completionFraction(0, 0), 0.001f)
        assertEquals(1f, ScheduleStats.completionFraction(8, 8), 0.001f)
        assertEquals(0f, ScheduleStats.completionFraction(3, 0), 0.001f)
    }

    private fun occurrencesOf(vararg tasks: Task) =
        tasks.map { RecurrenceEngine.occurrenceFor(it, monday)!! }

    @Test
    fun summarizeCountsOnlyRecordedCompletions() {
        val occurrences = occurrencesOf(task(1), task(2), task(3))
        val completions = listOf(completion(1, monday), completion(2, monday))
        val summary = ScheduleStats.summarize(occurrences, completions)
        assertEquals(3, summary.total)
        assertEquals(2, summary.completed)
        assertEquals(66, summary.percentLabel)
    }

    @Test
    fun completionRecordsFromOtherDaysDoNotCount() {
        val occurrences = occurrencesOf(task(1))
        val otherDay = monday.plusDays(1)
        val summary = ScheduleStats.summarize(occurrences, listOf(completion(1, otherDay)))
        assertEquals(0, summary.completed)
    }

    @Test
    fun streakCountsConsecutiveDaysEndingToday() {
        val completed = setOf(monday, monday.plusDays(1), monday.plusDays(2))
        assertEquals(3, ScheduleStats.streakDays(completed, monday.plusDays(2)))
        // Today not finished yet: streak still counts through yesterday
        assertEquals(3, ScheduleStats.streakDays(completed, monday.plusDays(3)))
        // Gap breaks the streak
        assertEquals(0, ScheduleStats.streakDays(completed, monday.plusDays(5)))
    }

    @Test
    fun byTypeAggregatesPlannedAndCompletedMinutes() {
        val occurrences = occurrencesOf(
            task(1, TaskType.DEEP_WORK, 120),
            task(2, TaskType.DEEP_WORK, 60),
            task(3, TaskType.HABIT, 30)
        )
        val stats = ScheduleStats.byType(occurrences, listOf(completion(1, monday), completion(3, monday)))
        val deep = stats.first { it.type == TaskType.DEEP_WORK }
        assertEquals(180, deep.totalMinutes)
        assertEquals(120, deep.completedMinutes)
        val habit = stats.first { it.type == TaskType.HABIT }
        assertEquals(30, habit.completedMinutes)
    }
}
