package com.dayflow

import com.dayflow.data.FocusStatus
import com.dayflow.domain.FocusCompletionRules
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime

/**
 * Auto-completing a task is only correct for a session that ran its whole length. Ending early must leave
 * the schedule alone, or "I stopped after two minutes" would count as done.
 */
class FocusCompletionRulesTest {

    @Test
    fun onlyAFullSessionWithATaskClosesIt() {
        assertTrue(FocusCompletionRules.closesTask(FocusStatus.COMPLETED, 7L))
        assertFalse("a session with no task has nothing to close", FocusCompletionRules.closesTask(FocusStatus.COMPLETED, null))
    }

    @Test
    fun endingEarlyCancellingOrExpiringNeverClosesTheTask() {
        listOf(FocusStatus.INTERRUPTED, FocusStatus.CANCELLED, FocusStatus.PAUSED, FocusStatus.RUNNING, FocusStatus.EXPIRED).forEach { outcome ->
            assertFalse("$outcome must not mark the task done", FocusCompletionRules.closesTask(outcome, 7L))
        }
    }

    @Test
    fun theTaskIsClosedForTheDayTheSessionStartedOn() {
        val zone = ZoneId.of("Asia/Kolkata")
        val lateNight = ZonedDateTime.of(LocalDate.of(2026, 3, 4), LocalTime.of(23, 50), zone).toInstant().toEpochMilli()
        assertEquals(
            "a session started at 23:50 belongs to the day the user sat down",
            LocalDate.of(2026, 3, 4).toEpochDay(),
            FocusCompletionRules.completionEpochDay(lateNight, zone)
        )
    }

    @Test
    fun theDayDependsOnTheDeviceZoneNotOnUtc() {
        val instant = ZonedDateTime.of(LocalDate.of(2026, 3, 4), LocalTime.of(23, 50), ZoneId.of("Asia/Kolkata")).toInstant().toEpochMilli()
        assertEquals(LocalDate.of(2026, 3, 4).toEpochDay(), FocusCompletionRules.completionEpochDay(instant, ZoneId.of("Asia/Kolkata")))
        // The same moment is still 4 March in UTC, and already the 4th too, but an earlier zone differs.
        assertEquals(LocalDate.of(2026, 3, 4).toEpochDay(), FocusCompletionRules.completionEpochDay(instant, ZoneOffset.UTC))
    }
}
