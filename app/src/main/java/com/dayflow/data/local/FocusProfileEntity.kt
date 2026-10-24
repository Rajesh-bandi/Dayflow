package com.dayflow.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "focus_profiles")
data class FocusProfileEntity(
    @PrimaryKey val name: String,
    val focusMinutes: Int,
    val breakMinutes: Int,
    val createdAtMillis: Long
)
