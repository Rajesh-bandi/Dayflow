package com.dayflow.domain

import java.time.LocalDate
import java.time.ZoneId

/** Detects overlapping scheduled occurrences so the UI can communicate conflicts clearly. */
object OverlapDetector {

    data class Overlap(val first: TaskOccurrence, val second: TaskOccurrence) {
        val taskIdA: Long get() = first.taskId
        val taskIdB: Long get() = second.taskId
    }

    /** Pairwise overlaps between distinct tasks; touching boundaries (end == start) are not overlaps. */
    fun findOverlaps(occurrences: List<TaskOccurrence>): List<Overlap> {
        val sorted = occurrences.sortedBy { it.startMillis }
        val overlaps = ArrayList<Overlap>()
        for (i in sorted.indices) {
            for (j in i + 1 until sorted.size) {
                val a = sorted[i]
                val b = sorted[j]
                if (a.taskId == b.taskId) continue
                if (a.startMillis < b.endMillis && b.startMillis < a.endMillis) {
                    overlaps += Overlap(a, b)
                }
            }
        }
        return overlaps
    }

    /** Overlaps a draft [task] would cause on [date] against already scheduled [tasks]. */
    fun findOverlapsFor(
        task: Task,
        date: LocalDate,
        tasks: List<Task>,
        zone: ZoneId = ZoneId.systemDefault()
    ): List<Overlap> {
        val draft = RecurrenceEngine.occurrenceFor(task, date, zone) ?: return emptyList()
        val others = tasks.filter { it.id != task.id }
            .mapNotNull { RecurrenceEngine.occurrenceFor(it, date, zone) }
        return findOverlaps(others + draft)
    }
}
