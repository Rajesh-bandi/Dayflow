package com.dayflow

import com.dayflow.data.FocusStatus
import com.dayflow.data.local.FocusSessionEntity
import com.dayflow.domain.FocusTimerMath
import org.junit.Assert.assertEquals
import org.junit.Test

class FocusTimerMathTest {
    @Test fun remainingUsesWallClockAfterRecreation() {
        val session = FocusSessionEntity(taskId = 4, profileName = "Personal", startedAtMillis = 1_000, expectedEndAtMillis = 61_000, status = FocusStatus.RUNNING.name)
        assertEquals(31_000, FocusTimerMath.remainingMillis(session, 30_000))
        assertEquals(0, FocusTimerMath.remainingMillis(session, 70_000))
    }

    @Test fun pausedSessionRetainsRemainingTime() {
        val session = FocusSessionEntity(taskId = null, profileName = "Personal", startedAtMillis = 1_000, expectedEndAtMillis = 61_000, pausedAtMillis = 21_000, status = FocusStatus.PAUSED.name)
        assertEquals(40_000, FocusTimerMath.remainingMillis(session, 40_000))
        assertEquals(20_000, FocusTimerMath.actualElapsedMillis(session, 41_000))
    }

    @Test fun actualTimeExcludesAccumulatedPauses() {
        val session = FocusSessionEntity(taskId = null, profileName = "Personal", startedAtMillis = 1_000, expectedEndAtMillis = 91_000, accumulatedPauseMillis = 30_000, status = FocusStatus.RUNNING.name)
        assertEquals(30_000, FocusTimerMath.actualElapsedMillis(session, 61_000))
    }
}
