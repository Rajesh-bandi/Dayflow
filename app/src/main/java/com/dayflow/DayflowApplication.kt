package com.dayflow

import android.app.Application
import com.dayflow.data.RoomTaskRepository
import com.dayflow.data.FocusSessionRepository
import com.dayflow.data.RoutineRepository
import com.dayflow.data.AppPreferences
import com.dayflow.data.FocusProfileRepository
import com.dayflow.data.TaskRepository
import com.dayflow.data.local.DayflowDatabase
import com.dayflow.notifications.ReminderCoordinator
import com.dayflow.blocking.BlockedAppPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.map

class DayflowApplication : Application() {

    val backgroundScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val repository: TaskRepository by lazy {
        RoomTaskRepository(DayflowDatabase.get(this))
    }

    val focusSessions: FocusSessionRepository by lazy {
        FocusSessionRepository(DayflowDatabase.get(this))
    }

    val routines: RoutineRepository by lazy { RoutineRepository(DayflowDatabase.get(this), repository) }
    val preferences: AppPreferences by lazy { AppPreferences(this) }
    val reminderCoordinator: ReminderCoordinator by lazy { ReminderCoordinator(this) }
    val focusProfiles: FocusProfileRepository by lazy { FocusProfileRepository(DayflowDatabase.get(this)) }
    val blockedApps: BlockedAppPreferences by lazy { BlockedAppPreferences(this) }
    val aiConfiguration: com.dayflow.data.AiConfiguration by lazy { com.dayflow.data.AiConfiguration(this) }
    val aiDao by lazy { DayflowDatabase.get(this).aiDao() }

    override fun onCreate() {
        super.onCreate()
        reminderCoordinator.observe(repository.observeTasks(), repository.observeAllCompletions(), preferences.state.map { it.remindersEnabled })
    }

}
