package com.dayflow

import com.dayflow.domain.OverlapDetector
import com.dayflow.domain.Recurrence
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.Task
import com.dayflow.domain.TaskType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OverlapDetectorTest {

    private val day = LocalDate.of(2026, 10, 7)

    private fun task(id: Long, startMinutes: Int, duration: Int) = Task(
        id = id,
        title = "Task $id",
        type = TaskType.REGULAR,
        startEpochDay = day.toEpochDay(),
        startTimeMinutes = startMinutes,
        durationMinutes = duration,
        recurrence = Recurrence.daily()
    )

    private fun occurrence(task: Task) = RecurrenceEngine.occurrenceFor(task, day)!!

    @Test
    fun overlappingIntervalsAreDetected() {
        val a = task(1, 9 * 60, 120) // 09:00 - 11:00
        val b = task(2, 10 * 60, 60) // 10:00 - 11:00
        val overlaps = OverlapDetector.findOverlaps(listOf(occurrence(a), occurrence(b)))
        assertEquals(1, overlaps.size)
        assertEquals(1L, overlaps.first().taskIdA)
        assertEquals(2L, overlaps.first().taskIdB)
    }

    @Test
    fun touchingBoundariesAreNotOverlaps() {
        val a = task(1, 9 * 60, 60) // ends 10:00
        val b = task(2, 10 * 60, 60) // starts 10:00
        assertTrue(OverlapDetector.findOverlaps(listOf(occurrence(a), occurrence(b))).isEmpty())
    }

    @Test
    fun taskCannotOverlapItself() {
        val a = task(1, 9 * 60, 60)
        assertTrue(OverlapDetector.findOverlaps(listOf(occurrence(a), occurrence(a))).isEmpty())
    }

    @Test
    fun threeWayOverlapFindsAllPairs() {
        val a = task(1, 9 * 60, 180)
        val b = task(2, 10 * 60, 60)
        val c = task(3, 10 * 60 + 30, 60)
        assertEquals(3, OverlapDetector.findOverlaps(listOf(a, b, c).map { occurrence(it) }).size)
    }

    @Test
    fun draftTaskOverlapCheckIgnoresItself() {
        val existing = task(1, 9 * 60, 60)
        val draft = existing.copy(id = 0) // same slot, new task
        val overlaps = OverlapDetector.findOverlapsFor(draft, day, listOf(existing))
        assertEquals(1, overlaps.size)
        // Editing the same task against its own slot produces no conflict
        assertTrue(OverlapDetector.findOverlapsFor(existing, day, listOf(existing)).isEmpty())
    }

    @Test
    fun freeSlotProducesNoConflict() {
        val existing = task(1, 9 * 60, 60)
        val draft = task(0, 12 * 60, 60)
        assertTrue(OverlapDetector.findOverlapsFor(draft, day, listOf(existing)).isEmpty())
    }
}
