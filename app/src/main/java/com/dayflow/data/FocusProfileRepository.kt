package com.dayflow.data

import com.dayflow.data.local.DayflowDatabase
import com.dayflow.data.local.FocusProfileEntity
import kotlinx.coroutines.flow.Flow

class FocusProfileRepository(private val database: DayflowDatabase) {
    fun observeAll(): Flow<List<FocusProfileEntity>> = database.focusProfileDao().observeAll()
    suspend fun save(name: String, focusMinutes: Int, breakMinutes: Int) {
        require(name.isNotBlank() && focusMinutes in 1..1440 && breakMinutes in 0..180)
        database.focusProfileDao().upsert(FocusProfileEntity(name.trim(), focusMinutes, breakMinutes, System.currentTimeMillis()))
    }
    suspend fun delete(name: String) = database.focusProfileDao().delete(name)
}
