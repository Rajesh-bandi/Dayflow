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
import java.time.DayOfWeek

data class WeekDayCell(
    val date: LocalDate,
    val dayLabel: String,
    val dayNumber: String,
    val total: Int,
    val completed: Int,
    val isSelected: Boolean,
    val isToday: Boolean
) {
    val allComplete: Boolean get() = total > 0 && completed == total
}

data class WeekUiState(
    val weekStart: LocalDate = WeekUtils.weekStart(LocalDate.now()),
    val monthLabel: String = "",
    val rangeLabel: String = "",
    val isCurrentWeek: Boolean = true,
    val days: List<WeekDayCell> = emptyList(),
    val selectedDate: LocalDate = LocalDate.now(),
    val occurrences: List<TaskOccurrence> = emptyList(),
    val summary: DaySummary = DaySummary(0, 0),
    val weekSummary: DaySummary = DaySummary(0, 0),
    val weekPlannedMinutes: Int = 0,
    val overlapLabels: List<String> = emptyList(),
    val completions: List<TaskCompletion> = emptyList(),
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY
) {
    fun isCompleted(occurrence: TaskOccurrence): Boolean =
        ScheduleStats.isCompleted(occurrence, completions)
}

class WeekViewModel(private val repository: TaskRepository) : ViewModel() {

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    private val _firstDay = MutableStateFlow(DayOfWeek.MONDAY)
    val selectedDate: StateFlow<LocalDate> = _selectedDate

    val uiState: StateFlow<WeekUiState> = combine(
        repository.observeTasks(),
        repository.observeAllCompletions(),
        _selectedDate,
        _firstDay
    ) { tasks, completions, date, firstDay -> buildState(tasks, completions, date, firstDay) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildState(emptyList(), emptyList(), _selectedDate.value, _firstDay.value)
        )

    fun selectDate(date: LocalDate) {
        _selectedDate.value = date
    }

    fun previousWeek() {
        _selectedDate.value = _selectedDate.value.minusWeeks(1)
    }

    fun nextWeek() {
        _selectedDate.value = _selectedDate.value.plusWeeks(1)
    }

    fun goToToday() {
        _selectedDate.value = LocalDate.now()
    }

    fun setFirstDay(day: DayOfWeek) { _firstDay.value = day }

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
        selected: LocalDate,
        firstDay: DayOfWeek
    ): WeekUiState {
        val weekStart = WeekUtils.weekStart(selected, firstDay)
        val weekEnd = WeekUtils.weekEnd(weekStart)
        val today = LocalDate.now()

        val skipped = completions.filter { it.status == "SKIPPED" }.map { it.taskId to it.dateEpochDay }.toSet()
        val weekOccurrences = RecurrenceEngine.occurrencesBetween(tasks, weekStart, weekEnd).filterNot { (it.taskId to it.date.toEpochDay()) in skipped }
        val dayCells = WeekUtils.weekDates(weekStart).map { date ->
            val dayOccurrences = weekOccurrences.filter { it.date == date }
            val summary = ScheduleStats.summarize(dayOccurrences, completions)
            WeekDayCell(
                date = date,
                dayLabel = date.dayOfWeek.name.take(3).lowercase().replaceFirstChar { it.uppercase() },
                dayNumber = date.dayOfMonth.toString(),
                total = summary.total,
                completed = summary.completed,
                isSelected = date == selected,
                isToday = date == today
            )
        }
        val selectedOccurrences = weekOccurrences.filter { it.date == selected }
        val selectedSummary = ScheduleStats.summarize(selectedOccurrences, completions)
        val weekSummary = ScheduleStats.summarize(weekOccurrences, completions)

        return WeekUiState(
            weekStart = weekStart,
            monthLabel = WeekUtils.monthYearLabel(selected),
            rangeLabel = WeekUtils.weekRangeLabel(weekStart),
            isCurrentWeek = weekStart == WeekUtils.weekStart(today, firstDay),
            days = dayCells,
            selectedDate = selected,
            occurrences = selectedOccurrences,
            summary = selectedSummary,
            weekSummary = weekSummary,
            weekPlannedMinutes = weekOccurrences.sumOf { it.durationMinutes },
            overlapLabels = OverlapDetector.findOverlaps(selectedOccurrences).map { (a, b) ->
                "${a.task.title} ↔ ${b.task.title} • ${WeekUtils.timeLabel(b.startMinuteOfDay)}"
            },
            completions = completions,
            firstDayOfWeek = firstDay
        )
    }
}
