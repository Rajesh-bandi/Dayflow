package com.dayflow.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "ai_messages", indices = [Index(value = ["mode", "createdAtMillis"])])
data class AiMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val role: String,
    val content: String,
    val createdAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_memories", indices = [Index(value = ["category"])])
data class AiMemoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val category: String,
    val content: String,
    val explicit: Boolean = true,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

@Entity(tableName = "ai_user_profile")
data class AiUserProfileEntity(
    @PrimaryKey val id: Int = 1,
    val name: String = "",
    val wakeTime: String = "06:00",
    val windDownTime: String = "22:30",
    val preferences: String = "",
    val goals: String = "",
    val updatedAtMillis: Long = System.currentTimeMillis()
)

/** Records a reschedule as a new occurrence while preserving the source task and its completion history. */
@Entity(tableName = "task_reschedules", indices = [Index(value = ["taskId", "fromEpochDay"])])
data class TaskRescheduleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long,
    val newTaskId: Long,
    val fromEpochDay: Long,
    val toEpochDay: Long,
    val createdAtMillis: Long = System.currentTimeMillis()
)
