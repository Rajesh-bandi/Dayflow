package com.dayflow.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String,
    val description: String,
    val type: String,
    val category: String,
    /** Local date of the anchor/first occurrence, stored as epoch days (never a formatted string). */
    val startEpochDay: Long,
    /** Local minutes from midnight of the start time. */
    val startTimeMinutes: Int,
    val durationMinutes: Int,
    val priority: String,
    val destinationUrl: String?,
    val focusProfile: String?,
    /** Structured recurrence: frequency name + ISO weekday bitmask (bit 0 = Monday) + optional inclusive end. */
    val recurrenceFrequency: String,
    val recurrenceDaysMask: Int,
    val recurrenceEndEpochDay: Long?,
    val createdAtMillis: Long,
    val modifiedAtMillis: Long,
    val archivedAtMillis: Long? = null
)

@Entity(
    tableName = "task_occurrences",
    primaryKeys = ["taskId", "dateEpochDay"],
    indices = [Index("dateEpochDay")]
)
data class TaskOccurrenceEntity(
    val taskId: Long,
    val dateEpochDay: Long,
    val completed: Boolean,
    val completedAtMillis: Long?,
    @ColumnInfo(defaultValue = "'PLANNED'") val status: String = "PLANNED"
)
