package com.dayflow.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "focus_sessions", indices = [Index("startedAtMillis"), Index("status")])
data class FocusSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Long?,
    val profileName: String,
    val startedAtMillis: Long,
    val expectedEndAtMillis: Long,
    val pausedAtMillis: Long? = null,
    val accumulatedPauseMillis: Long = 0,
    val endedAtMillis: Long? = null,
    val actualDurationMillis: Long? = null,
    val status: String
)
