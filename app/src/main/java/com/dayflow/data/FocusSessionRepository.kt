package com.dayflow.data

import androidx.room.withTransaction
import com.dayflow.data.TaskMappers.toEntity
import com.dayflow.data.local.DayflowDatabase
import com.dayflow.data.local.FocusSessionEntity
import com.dayflow.domain.FocusCompletionRules
import com.dayflow.domain.TaskCompletion
import kotlinx.coroutines.flow.Flow

enum class FocusStatus { RUNNING, PAUSED, COMPLETED, INTERRUPTED, CANCELLED, EXPIRED }

class FocusSessionRepository(private val database: DayflowDatabase) {
    private val dao = database.focusSessionDao()
    private val occurrenceDao = database.occurrenceDao()

    /**
     * A session that reached its planned end closes the task it was started for, so the schedule reflects
     * the work actually done. Called only from the paths that expire a session at full length.
     */
    private suspend fun completeLinkedTask(session: FocusSessionEntity, now: Long) {
        val taskId = session.taskId ?: return
        if (!FocusCompletionRules.closesTask(FocusStatus.COMPLETED, taskId)) return
        occurrenceDao.upsert(
            TaskCompletion(
                taskId = taskId,
                dateEpochDay = FocusCompletionRules.completionEpochDay(session.startedAtMillis),
                completed = true,
                completedAtMillis = now,
                status = "COMPLETED"
            ).toEntity()
        )
    }

    fun observeCurrent(): Flow<FocusSessionEntity?> = dao.observeCurrent()
    fun observeRecent(): Flow<List<FocusSessionEntity>> = dao.observeRecent()
    fun observeAllHistory(): Flow<List<FocusSessionEntity>> = dao.observeAllHistory()

    suspend fun start(taskId: Long?, durationMinutes: Int, profileName: String = "Personal", now: Long = System.currentTimeMillis()): Long {
        require(durationMinutes in 1..24 * 60)
        return database.withTransaction {
            val current = dao.getCurrent()
            if (current != null) {
                if (current.status == FocusStatus.RUNNING.name && now >= current.expectedEndAtMillis) {
                    dao.update(current.expire())
                    completeLinkedTask(current, now)
                } else {
                    throw IllegalStateException("A focus session is already active")
                }
            }
            dao.insert(
                FocusSessionEntity(
                    taskId = taskId,
                    profileName = profileName,
                    startedAtMillis = now,
                    expectedEndAtMillis = now + durationMinutes * 60_000L,
                    status = FocusStatus.RUNNING.name
                )
            )
        }
    }

    suspend fun pause(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val session = dao.getCurrent() ?: return@withTransaction
        if (session.status != FocusStatus.RUNNING.name) return@withTransaction
        if (now >= session.expectedEndAtMillis) {
            dao.update(session.expire())
            completeLinkedTask(session, now)
        } else {
            dao.update(session.copy(status = FocusStatus.PAUSED.name, pausedAtMillis = now))
        }
    }

    suspend fun resume(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val session = dao.getCurrent() ?: return@withTransaction
        if (session.status != FocusStatus.PAUSED.name) return@withTransaction
        val pausedAt = session.pausedAtMillis ?: now
        val pauseDuration = (now - pausedAt).coerceAtLeast(0)
        dao.update(
            session.copy(
                status = FocusStatus.RUNNING.name,
                expectedEndAtMillis = session.expectedEndAtMillis + pauseDuration,
                pausedAtMillis = null,
                accumulatedPauseMillis = session.accumulatedPauseMillis + pauseDuration
            )
        )
    }

    suspend fun finishEarly(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val session = dao.getCurrent() ?: return@withTransaction
        if (session.status == FocusStatus.RUNNING.name && now >= session.expectedEndAtMillis) {
            dao.update(session.expire())
            completeLinkedTask(session, now)
        } else {
            val pausedNow = if (session.status == FocusStatus.PAUSED.name) (now - (session.pausedAtMillis ?: now)).coerceAtLeast(0) else 0L
            val actual = (now - session.startedAtMillis - session.accumulatedPauseMillis - pausedNow).coerceAtLeast(0)
            // Ending early is not completion, so the linked task is left exactly as it was.
            dao.update(session.copy(status = FocusStatus.INTERRUPTED.name, endedAtMillis = now, actualDurationMillis = actual))
        }
    }

    suspend fun cancel(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val session = dao.getCurrent() ?: return@withTransaction
        val pausedNow = if (session.status == FocusStatus.PAUSED.name) (now - (session.pausedAtMillis ?: now)).coerceAtLeast(0) else 0L
        val actual = (now - session.startedAtMillis - session.accumulatedPauseMillis - pausedNow).coerceAtLeast(0)
        dao.update(session.copy(status = FocusStatus.CANCELLED.name, endedAtMillis = now, actualDurationMillis = actual))
    }

    suspend fun reconcile(now: Long = System.currentTimeMillis()) = database.withTransaction {
        val session = dao.getCurrent() ?: return@withTransaction
        if (session.status == FocusStatus.RUNNING.name && now >= session.expectedEndAtMillis) {
            dao.update(session.expire())
            completeLinkedTask(session, now)
        }
    }

    private fun FocusSessionEntity.expire(): FocusSessionEntity {
        val actual = (expectedEndAtMillis - startedAtMillis - accumulatedPauseMillis).coerceAtLeast(0)
        return copy(status = FocusStatus.COMPLETED.name, endedAtMillis = expectedEndAtMillis, actualDurationMillis = actual)
    }
}
