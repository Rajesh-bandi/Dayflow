package com.dayflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.data.TaskRepository
import com.dayflow.domain.DaySummary
import com.dayflow.domain.OverlapDetector
import com.dayflow.domain.Recurrence
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.ScheduleStats
import com.dayflow.domain.TaskCompletion
import com.dayflow.domain.TaskOccurrence
import com.dayflow.domain.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val dateLabel: String = "",
    val greeting: String = "",
    val occurrences: List<TaskOccurrence> = emptyList(),
    val completions: List<TaskCompletion> = emptyList(),
    val summary: DaySummary = DaySummary(0, 0),
    val current: TaskOccurrence? = null,
    val next: TaskOccurrence? = null,
    val streakDays: Int = 0,
    val overlapLabels: List<String> = emptyList()
) {
    fun isCompleted(occurrence: TaskOccurrence): Boolean =
        ScheduleStats.isCompleted(occurrence, completions)
}

class TodayViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    val uiState: StateFlow<TodayUiState> = combine(
        repository.observeTasks(),
        repository.observeAllCompletions(),
        _selectedDate
    ) { tasks, completions, date -> buildState(tasks, completions, date) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildState(emptyList(), emptyList(), _selectedDate.value)
        )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun shiftDay(days: Long) {
        _selectedDate.value = _selectedDate.value.plusDays(days)
    }

    fun toggleCompletion(occurrence: TaskOccurrence) {
        val completed = ScheduleStats.isCompleted(occurrence, uiState.value.completions)
        viewModelScope.launch {
            repository.setCompleted(occurrence.taskId, occurrence.date.toEpochDay(), !completed)
        }
    }

    fun skipOccurrence(occurrence: TaskOccurrence) { viewModelScope.launch { repository.setSkipped(occurrence.taskId, occurrence.date.toEpochDay()) } }
    fun restoreOccurrence(occurrence: TaskOccurrence) { viewModelScope.launch { repository.setCompleted(occurrence.taskId, occurrence.date.toEpochDay(), false) } }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { repository.deleteTask(taskId) }
    }
    fun archiveTask(taskId: Long) { viewModelScope.launch { repository.archiveTask(taskId) } }
    fun restoreTask(taskId: Long) { viewModelScope.launch { repository.restoreTask(taskId) } }

    /** Copies an occurrence as a brand-new task (recurring occurrences are copied as one-time on that date). */
    fun duplicateTask(occurrence: TaskOccurrence) {
        viewModelScope.launch {
            repository.saveTask(
                occurrence.task.copy(
                    id = 0L,
                    startEpochDay = occurrence.date.toEpochDay(),
                    recurrence = if (occurrence.task.isRecurring) Recurrence.ONE_TIME else occurrence.task.recurrence
                )
            )
        }
    }

    private fun buildState(
        tasks: List<com.dayflow.domain.Task>,
        completions: List<TaskCompletion>,
        date: LocalDate
    ): TodayUiState {
        val skipped = completions.filter { it.status == "SKIPPED" }.map { it.taskId to it.dateEpochDay }.toSet()
        val occurrences = RecurrenceEngine.occurrencesOn(tasks, date).filterNot { (it.taskId to it.date.toEpochDay()) in skipped }
        val now = System.currentTimeMillis()
        val active = occurrences.filter { it.endMillis > now }
        val current = active.firstOrNull { it.startMillis <= now }
        val next = active.firstOrNull { it.startMillis > now }
        val summary = ScheduleStats.summarize(occurrences, completions)
        val streak = ScheduleStats.habitStreakDays(tasks, completions, LocalDate.now())
        val overlapLabels = OverlapDetector.findOverlaps(occurrences).map { (first, second) ->
            "${first.task.title} ↔ ${second.task.title} • ${WeekUtils.timeLabel(second.startMinuteOfDay)}"
        }
        return TodayUiState(
            date = date,
            dateLabel = WeekUtils.dayTitle(date),
            greeting = greetingFor(now),
            occurrences = occurrences,
            completions = completions,
            summary = summary,
            current = current,
            next = next ?: current,
            streakDays = streak,
            overlapLabels = overlapLabels
        )
    }

    private fun greetingFor(nowMillis: Long): String {
        val hour = java.time.LocalDateTime.ofInstant(
            java.time.Instant.ofEpochMilli(nowMillis),
            java.time.ZoneId.systemDefault()
        ).hour
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..17 -> "Good afternoon"
            else -> "Good evening"
        }
    }
}
