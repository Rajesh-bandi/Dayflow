package com.dayflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.data.TaskRepository
import com.dayflow.data.FocusSessionRepository
import com.dayflow.data.local.FocusSessionEntity
import com.dayflow.data.AppPreferences
import com.dayflow.domain.DaySummary
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.ScheduleStats
import com.dayflow.domain.Task
import com.dayflow.domain.TaskCompletion
import com.dayflow.domain.TaskType
import com.dayflow.domain.TypeStat
import com.dayflow.domain.WeekUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

enum class ProgressPeriod { WEEK, MONTH, QUARTER, CUSTOM }

data class HeatDay(val date: LocalDate, val fraction: Float, val total: Int, val completed: Int)

data class HabitStat(val task: Task, val completedCount: Int, val total: Int)

data class ProgressUiState(
    val period: ProgressPeriod = ProgressPeriod.WEEK,
    val rangeLabel: String = "",
    val summary: DaySummary = DaySummary(0, 0),
    val previousSummary: DaySummary = DaySummary(0, 0),
    val deepFocusMinutes: Int = 0,
    val previousFocusMinutes: Int = 0,
    val streakDays: Int = 0,
    val habitAdherence: Float = 0f,
    val habitLabel: String = "—",
    val heatDays: List<HeatDay> = emptyList(),
    val typeStats: List<TypeStat> = emptyList(),
    val habits: List<HabitStat> = emptyList(),
    val bestDayLabel: String = "",
    val todayLabel: String = "",
    val customDays: Int = 30
) {
    val hasData: Boolean get() = summary.total > 0 || heatDays.any { it.total > 0 } || deepFocusMinutes > 0
}

class ProgressViewModel(private val repository: TaskRepository, private val focusRepository: FocusSessionRepository, private val preferences: AppPreferences) : ViewModel() {

    private val _period = MutableStateFlow(ProgressPeriod.WEEK)
    val period: StateFlow<ProgressPeriod> = _period
    private val _customDays = MutableStateFlow(30)
    private val periodSettings = combine(_period, _customDays) { period, days -> period to days }

    val uiState: StateFlow<ProgressUiState> = combine(
        repository.observeTasks(),
        repository.observeAllCompletions(),
        focusRepository.observeAllHistory(),
        preferences.state,
        periodSettings
    ) { tasks, completions, sessions, prefs, selected -> buildState(tasks, completions, sessions, selected.first, selected.second, prefs.firstDayOfWeek) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            buildState(emptyList(), emptyList(), emptyList(), _period.value, _customDays.value, java.time.DayOfWeek.MONDAY)
        )

    fun selectPeriod(period: ProgressPeriod) {
        _period.value = period
    }
    fun selectCustomDays(days: Int) { _customDays.value = days.coerceIn(7, 365) }

    private fun buildState(
        tasks: List<Task>,
        completions: List<TaskCompletion>,
        sessions: List<FocusSessionEntity>,
        period: ProgressPeriod,
        customDays: Int,
        firstDay: java.time.DayOfWeek
    ): ProgressUiState {
        val today = LocalDate.now()
        val start = when (period) {
            ProgressPeriod.WEEK -> WeekUtils.weekStart(today, firstDay)
            ProgressPeriod.MONTH -> today.withDayOfMonth(1)
            ProgressPeriod.QUARTER -> today.minusDays(89)
            ProgressPeriod.CUSTOM -> today.minusDays(customDays - 1L)
        }
        val rangeOccurrences = RecurrenceEngine.occurrencesBetween(tasks, start, today)
        val summary = ScheduleStats.summarize(rangeOccurrences, completions)
        val previousLength = java.time.temporal.ChronoUnit.DAYS.between(start, today).toInt() + 1
        val previousStart = start.minusDays(previousLength.toLong())
        val previousOccurrences = RecurrenceEngine.occurrencesBetween(tasks, previousStart, start.minusDays(1))
        val previousSummary = ScheduleStats.summarize(previousOccurrences, completions)
        val periodStartMillis = start.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val todayEndMillis = today.plusDays(1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
        val completedFocus = sessions.filter { it.startedAtMillis in periodStartMillis until todayEndMillis }
            .sumOf { it.actualDurationMillis ?: 0L }.div(60_000).toInt()
        val previousFocus = sessions.filter { it.startedAtMillis >= previousStart.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli() && it.startedAtMillis < periodStartMillis }
            .sumOf { it.actualDurationMillis ?: 0L }.div(60_000).toInt()

        val streak = ScheduleStats.habitStreakDays(tasks, completions, today)

        val habitOccurrences = rangeOccurrences.filter { it.task.type == TaskType.HABIT }
        val habitSummary = ScheduleStats.summarize(habitOccurrences, completions)

        val heatStart = today.minusDays(27)
        val heatOccurrences = RecurrenceEngine.occurrencesBetween(tasks, heatStart, today)
        val heatDays = (0L..27L).map { offset ->
            val date = heatStart.plusDays(offset)
            val dayOccurrences = heatOccurrences.filter { it.date == date }
            val daySummary = ScheduleStats.summarize(dayOccurrences, completions)
            HeatDay(date, daySummary.fraction, daySummary.total, daySummary.completed)
        }

        val habits = tasks.filter { it.type == TaskType.HABIT && RecurrenceEngine.occursOn(it, today) }
            .map { task ->
                HabitStat(
                    task = task,
                    completedCount = rangeOccurrences.count {
                        it.taskId == task.id && ScheduleStats.isCompleted(it, completions)
                    },
                    total = rangeOccurrences.count { it.taskId == task.id }
                )
            }

        val bestDay = heatDays.filter { it.total > 0 }
            .maxWithOrNull(compareBy({ it.fraction }, { it.completed }))
        val todaySummary = ScheduleStats.summarize(
            RecurrenceEngine.occurrencesOn(tasks, today),
            completions
        )

        return ProgressUiState(
            period = period,
            rangeLabel = when (period) {
                ProgressPeriod.WEEK -> "${WeekUtils.weekRangeLabel(start)} • Week ${weekOfYear(today)}"
                ProgressPeriod.MONTH -> "${WeekUtils.monthYearLabel(start)} • ${start.dayOfMonth}–${today.dayOfMonth}"
                ProgressPeriod.QUARTER -> "${start} – ${today} • 90 days"
                ProgressPeriod.CUSTOM -> "${start} – ${today} • $customDays days"
            },
            summary = summary,
            previousSummary = previousSummary,
            deepFocusMinutes = completedFocus,
            previousFocusMinutes = previousFocus,
            streakDays = streak,
            habitAdherence = habitSummary.fraction,
            habitLabel = if (habitSummary.total == 0) "—" else "${habitSummary.completed}/${habitSummary.total}",
            heatDays = heatDays,
            typeStats = ScheduleStats.byType(rangeOccurrences, completions),
            habits = habits,
            bestDayLabel = bestDay?.let { "${WeekUtils.dayTitle(it.date)} • ${it.completed}/${it.total}" } ?: "—",
            todayLabel = "${todaySummary.completed} of ${todaySummary.total} tasks completed",
            customDays = customDays
        )
    }

    private fun weekOfYear(date: LocalDate): Int =
        date.get(java.time.temporal.WeekFields.ISO.weekOfWeekBasedYear())
}
