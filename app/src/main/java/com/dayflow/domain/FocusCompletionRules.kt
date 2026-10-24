package com.dayflow.domain

import com.dayflow.data.FocusStatus
import java.time.Instant
import java.time.ZoneId

/**
 * When a focus session runs its whole length for a task, that task is done — it is what the session was
 * started for, and asking the user to confirm it afterwards is busywork.
 *
 * The rules are deliberately narrow. Only a session that reached its end closes a task: ending early,
 * cancelling, or running without a task must never touch the schedule, or "I stopped after two minutes"
 * would silently count as finished.
 */
object FocusCompletionRules {

    fun closesTask(outcome: FocusStatus, taskId: Long?): Boolean =
        outcome == FocusStatus.COMPLETED && taskId != null

    /**
     * The occurrence to close is the day the session was started for. A session that begins at 23:50 and
     * runs past midnight belongs to the day the user sat down, not the next one.
     */
    fun completionEpochDay(startedAtMillis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(startedAtMillis).atZone(zone).toLocalDate().toEpochDay()
}
