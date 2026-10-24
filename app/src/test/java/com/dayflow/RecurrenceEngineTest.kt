package com.dayflow

import com.dayflow.domain.Recurrence
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.Task
import com.dayflow.domain.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class RecurrenceEngineTest {

    // Monday, 2026-10-05
    private val monday = LocalDate.of(2026, 10, 5)
    private val friday = LocalDate.of(2026, 10, 9)
    private val saturday = monday.plusDays(5)
    private val sunday = monday.plusDays(6)

    private fun task(recurrence: Recurrence, start: LocalDate = monday, minutes: Int = 9 * 60) = Task(
        title = "Test",
        type = TaskType.REGULAR,
        startEpochDay = start.toEpochDay(),
        startTimeMinutes = minutes,
        durationMinutes = 60,
        recurrence = recurrence
    )

    @Test
    fun oneTimeOccursOnlyOnStartDate() {
        val task = task(Recurrence.ONE_TIME)
        assertTrue(RecurrenceEngine.occursOn(task, monday))
        assertFalse(RecurrenceEngine.occursOn(task, monday.plusDays(1)))
        assertFalse(RecurrenceEngine.occursOn(task, monday.minusDays(1)))
    }

    @Test
    fun dailyOccursEveryDayFromStart() {
        val task = task(Recurrence.daily())
        assertTrue(RecurrenceEngine.occursOn(task, monday))
        assertTrue(RecurrenceEngine.occursOn(task, sunday))
        assertTrue(RecurrenceEngine.occursOn(task, monday.plusDays(365)))
        assertFalse(RecurrenceEngine.occursOn(task, monday.minusDays(1)))
    }

    @Test
    fun weekdaysSkipWeekends() {
        val task = task(Recurrence.weekdays())
        assertTrue(RecurrenceEngine.occursOn(task, monday))
        assertTrue(RecurrenceEngine.occursOn(task, friday))
        assertFalse(RecurrenceEngine.occursOn(task, saturday))
        assertFalse(RecurrenceEngine.occursOn(task, sunday))
    }

    @Test
    fun weekendsOnlyOccurOnSatSun() {
        val task = task(Recurrence.weekends())
        assertFalse(RecurrenceEngine.occursOn(task, monday))
        assertTrue(RecurrenceEngine.occursOn(task, saturday))
        assertTrue(RecurrenceEngine.occursOn(task, sunday))
    }

    @Test
    fun selectedWeekdaysOccurOnlyOnChosenDays() {
        val task = task(Recurrence.selected(setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY)))
        assertTrue(RecurrenceEngine.occursOn(task, monday))
        assertFalse(RecurrenceEngine.occursOn(task, monday.plusDays(1))) // Tuesday
        assertTrue(RecurrenceEngine.occursOn(task, monday.plusDays(2))) // Wednesday
        assertFalse(RecurrenceEngine.occursOn(task, monday.plusDays(3))) // Thursday
        assertTrue(RecurrenceEngine.occursOn(task, friday))
        // Repeats on the same weekdays the following week
        assertTrue(RecurrenceEngine.occursOn(task, monday.plusDays(7)))
        assertFalse(RecurrenceEngine.occursOn(task, monday.plusDays(8)))
    }

    @Test
    fun selectedDaysRespectInclusiveEndDate() {
        val task = task(Recurrence.daily(endEpochDay = monday.plusDays(2).toEpochDay()))
        assertTrue(RecurrenceEngine.occursOn(task, monday.plusDays(2)))
        assertFalse(RecurrenceEngine.occursOn(task, monday.plusDays(3)))
    }

    @Test
    fun occurrenceUsesStartTimeAndDuration() {
        val task = task(Recurrence.daily(), minutes = 18 * 60)
        val occurrence = RecurrenceEngine.occurrenceFor(task, monday)!!
        assertEquals(monday, occurrence.date)
        assertEquals(18 * 60, occurrence.startMinuteOfDay)
        assertEquals(60, occurrence.durationMinutes)
        assertEquals(occurrence.endMillis - occurrence.startMillis, 60 * 60_000L)
    }

    @Test
    fun occurrenceForNonMatchingDayIsNull() {
        val task = task(Recurrence.weekdays())
        assertNull(RecurrenceEngine.occurrenceFor(task, saturday))
    }

    @Test
    fun occurrencesOnIsSortedByStartTime() {
        val early = task(Recurrence.daily(), minutes = 6 * 60)
        val later = task(Recurrence.daily(), minutes = 20 * 60).copy(id = 2L)
        val noon = task(Recurrence.daily(), minutes = 12 * 60).copy(id = 3L)
        val occurrences = RecurrenceEngine.occurrencesOn(listOf(later, early, noon), monday)
        assertEquals(listOf(6 * 60L, 12 * 60L, 20 * 60L), occurrences.map { it.startMinuteOfDay.toLong() })
    }
}
