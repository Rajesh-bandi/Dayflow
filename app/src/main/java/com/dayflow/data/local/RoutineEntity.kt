package com.dayflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A reusable, local schedule block. Applying it creates a dated task without changing the template. */
@Entity(tableName = "routines")
data class RoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val title: String,
    val description: String,
    val type: String,
    val startTimeMinutes: Int,
    val durationMinutes: Int,
    val priority: String,
    val destinationUrl: String?,
    val focusProfile: String?,
    val createdAtMillis: Long
)
