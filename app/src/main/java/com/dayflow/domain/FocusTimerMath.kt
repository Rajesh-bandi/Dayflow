package com.dayflow.domain

import com.dayflow.data.local.FocusSessionEntity
import com.dayflow.data.FocusStatus

/** Timestamp-based focus clock math; recomposition frequency never changes recorded duration. */
object FocusTimerMath {
    fun remainingMillis(session: FocusSessionEntity, nowMillis: Long): Long {
        val remaining = if (session.status == FocusStatus.PAUSED.name) {
            session.expectedEndAtMillis - (session.pausedAtMillis ?: nowMillis)
        } else session.expectedEndAtMillis - nowMillis
        return remaining.coerceAtLeast(0)
    }

    fun actualElapsedMillis(session: FocusSessionEntity, nowMillis: Long): Long {
        if (session.actualDurationMillis != null) return session.actualDurationMillis
        val pauseInProgress = if (session.status == FocusStatus.PAUSED.name) {
            (nowMillis - (session.pausedAtMillis ?: nowMillis)).coerceAtLeast(0)
        } else 0L
        return (nowMillis - session.startedAtMillis - session.accumulatedPauseMillis - pauseInProgress).coerceAtLeast(0)
    }
}
