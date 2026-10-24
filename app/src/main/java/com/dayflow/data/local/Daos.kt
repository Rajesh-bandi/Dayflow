package com.dayflow.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE archivedAtMillis IS NULL ORDER BY startEpochDay, startTimeMinutes, title")
    fun observeAll(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE archivedAtMillis IS NOT NULL ORDER BY archivedAtMillis DESC")
    fun observeArchived(): Flow<List<TaskEntity>>

    @Query("SELECT * FROM tasks WHERE id = :id")
    fun observeById(id: Long): Flow<TaskEntity?>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: Long): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity): Long

    @Update
    suspend fun update(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("UPDATE tasks SET archivedAtMillis = :atMillis WHERE id = :id")
    suspend fun archive(id: Long, atMillis: Long)

    @Query("UPDATE tasks SET archivedAtMillis = NULL WHERE id = :id")
    suspend fun restore(id: Long)
}

@Dao
interface TaskOccurrenceDao {
    @Query("SELECT * FROM task_occurrences WHERE dateEpochDay BETWEEN :fromEpochDay AND :toEpochDay")
    fun observeBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TaskOccurrenceEntity>>

    @Query("SELECT * FROM task_occurrences")
    fun observeAll(): Flow<List<TaskOccurrenceEntity>>

    @Query("SELECT * FROM task_occurrences WHERE taskId = :taskId AND dateEpochDay = :dateEpochDay")
    suspend fun get(taskId: Long, dateEpochDay: Long): TaskOccurrenceEntity?

    @Upsert
    suspend fun upsert(occurrence: TaskOccurrenceEntity)

    @Query("DELETE FROM task_occurrences WHERE taskId = :taskId")
    suspend fun deleteForTask(taskId: Long)
}

@Dao
interface FocusSessionDao {
    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY startedAtMillis DESC LIMIT 1")
    fun observeCurrent(): Flow<FocusSessionEntity?>

    @Query("SELECT * FROM focus_sessions WHERE status IN ('RUNNING', 'PAUSED') ORDER BY startedAtMillis DESC LIMIT 1")
    suspend fun getCurrent(): FocusSessionEntity?

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMillis DESC LIMIT :limit")
    fun observeRecent(limit: Int = 30): Flow<List<FocusSessionEntity>>

    @Query("SELECT * FROM focus_sessions ORDER BY startedAtMillis")
    fun observeAllHistory(): Flow<List<FocusSessionEntity>>

    @Insert
    suspend fun insert(session: FocusSessionEntity): Long

    @Update
    suspend fun update(session: FocusSessionEntity)
}

@Dao
interface RoutineDao {
    @Query("SELECT * FROM routines ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<RoutineEntity>>

    @Insert
    suspend fun insert(routine: RoutineEntity): Long

    @Query("DELETE FROM routines WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface FocusProfileDao {
    @Query("SELECT * FROM focus_profiles ORDER BY focusMinutes")
    fun observeAll(): Flow<List<FocusProfileEntity>>
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: FocusProfileEntity)
    @Query("DELETE FROM focus_profiles WHERE name = :name")
    suspend fun delete(name: String)
}

@Dao
interface AiDao {
    @Query("SELECT * FROM ai_messages WHERE mode = :mode ORDER BY createdAtMillis DESC LIMIT 100") fun observeMessages(mode: String): Flow<List<AiMessageEntity>>
    @Query("SELECT * FROM ai_messages WHERE mode = :mode ORDER BY createdAtMillis DESC LIMIT :limit") suspend fun recentMessages(mode: String, limit: Int): List<AiMessageEntity>
    @Insert suspend fun insertMessage(message: AiMessageEntity): Long
    @Query("DELETE FROM ai_messages WHERE mode = :mode") suspend fun clearMessages(mode: String)
    @Query("DELETE FROM ai_messages") suspend fun clearAllMessages()
    @Query("SELECT * FROM ai_memories ORDER BY updatedAtMillis DESC") fun observeMemories(): Flow<List<AiMemoryEntity>>
    @Query("SELECT * FROM ai_memories ORDER BY updatedAtMillis DESC LIMIT :limit") suspend fun recentMemories(limit: Int): List<AiMemoryEntity>
    @Query("SELECT * FROM ai_memories WHERE content = :content COLLATE NOCASE LIMIT 1") suspend fun findMemory(content: String): AiMemoryEntity?
    @Insert suspend fun insertMemory(memory: AiMemoryEntity): Long
    @Query("DELETE FROM ai_memories WHERE id = :id") suspend fun deleteMemory(id: Long)
    @Query("UPDATE ai_memories SET content = :content, updatedAtMillis = :now WHERE id = :id") suspend fun updateMemory(id: Long, content: String, now: Long = System.currentTimeMillis())
    @Query("UPDATE ai_memories SET explicit = 1 WHERE id = :id") suspend fun markExplicit(id: Long)
    /** Keeps growth bounded by dropping the oldest facts that were only inferred from chat. */
    @Query("DELETE FROM ai_memories WHERE explicit = 0 AND id NOT IN (SELECT id FROM ai_memories ORDER BY updatedAtMillis DESC LIMIT :keep)") suspend fun pruneImplicit(keep: Int)
    @Query("SELECT * FROM ai_user_profile WHERE id = 1") suspend fun getProfile(): AiUserProfileEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun saveProfile(profile: AiUserProfileEntity)
    @Insert suspend fun insertReschedule(record: TaskRescheduleEntity)
}
