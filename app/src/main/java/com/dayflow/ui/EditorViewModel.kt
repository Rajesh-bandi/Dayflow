package com.dayflow.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dayflow.data.TaskRepository
import com.dayflow.domain.OverlapDetector
import com.dayflow.domain.Priority
import com.dayflow.domain.Recurrence
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.RecurrenceFrequency
import com.dayflow.domain.Task
import com.dayflow.domain.TaskField
import com.dayflow.domain.TaskType
import com.dayflow.domain.TaskValidator
import com.dayflow.domain.ValidationError
import com.dayflow.domain.WeekUtils
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate

data class EditorDraft(
    val id: Long = 0L,
    val title: String = "",
    val description: String = "",
    val type: TaskType = TaskType.REGULAR,
    val startEpochDay: Long = LocalDate.now().toEpochDay(),
    val startTimeMinutes: Int = 9 * 60,
    val durationMinutes: Int = 60,
    val priority: Priority = Priority.NORMAL,
    val destinationUrl: String = "",
    val focusProfile: String? = null,
    val frequency: RecurrenceFrequency = RecurrenceFrequency.NONE,
    val daysOfWeek: Set<DayOfWeek> = emptySet(),
    val createdAtMillis: Long = 0L
) {
    val isNew: Boolean get() = id == 0L
    val startDate: LocalDate get() = LocalDate.ofEpochDay(startEpochDay)
    val recurrenceDescription: String
        get() = when (frequency) {
            RecurrenceFrequency.NONE -> "One-time"
            RecurrenceFrequency.DAILY -> "Daily"
            RecurrenceFrequency.WEEKDAYS -> "Weekdays"
            RecurrenceFrequency.WEEKENDS -> "Weekends"
            RecurrenceFrequency.SELECTED_DAYS ->
                if (daysOfWeek.isEmpty()) "Custom"
                else daysOfWeek.sortedBy { it.value }.joinToString(", ") { TaskValidator.dayOfWeekLabel(it) }
        }
}

data class EditorUiState(
    val draft: EditorDraft = EditorDraft(),
    val errors: Map<TaskField, String> = emptyMap(),
    val overlapConflicts: List<String> = emptyList(),
    val isSaving: Boolean = false
)

sealed class EditorEvent {
    data object Saved : EditorEvent()
    data object Deleted : EditorEvent()
}

data class EditorArgs(
    val taskId: Long = -1L,
    val initialDateEpochDay: Long = -1L,
    val presetTitle: String = "",
    val presetType: String = "",
    val presetMinutes: Int = -1,
    val editOccurrenceOfTaskId: Long = -1L
)

class EditorViewModel(
    private val repository: TaskRepository,
    private val args: EditorArgs
) : ViewModel() {

    private val _state = MutableStateFlow(EditorUiState())
    val state: StateFlow<EditorUiState> = _state

    private val _events = Channel<EditorEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        viewModelScope.launch {
            val existing = if (args.taskId > 0) repository.getTask(args.taskId) else null
            val draft = if (existing != null && args.editOccurrenceOfTaskId > 0) {
                existing.toDraft().copy(id = 0L, startEpochDay = args.initialDateEpochDay, frequency = RecurrenceFrequency.NONE, daysOfWeek = emptySet())
            } else existing?.toDraft() ?: initialDraft()
            _state.update { it.copy(draft = draft) }
        }
    }

    private fun initialDraft(): EditorDraft {
        val type = args.presetType.takeIf { it.isNotBlank() }
            ?.let { name -> TaskType.entries.firstOrNull { it.name == name } }
        return EditorDraft(
            type = type ?: TaskType.REGULAR,
            startEpochDay = if (args.initialDateEpochDay >= 0) args.initialDateEpochDay
            else LocalDate.now().toEpochDay(),
            title = args.presetTitle,
            durationMinutes = if (args.presetMinutes > 0) args.presetMinutes else 60
        )
    }

    private fun Task.toDraft() = EditorDraft(
        id = id,
        title = title,
        description = description,
        type = type,
        startEpochDay = startEpochDay,
        startTimeMinutes = startTimeMinutes,
        durationMinutes = durationMinutes,
        priority = priority,
        destinationUrl = destinationUrl ?: "",
        focusProfile = focusProfile,
        frequency = recurrence.frequency,
        daysOfWeek = recurrence.daysOfWeek,
        createdAtMillis = createdAtMillis
    )

    fun onTitle(value: String) = update { it.copy(title = value) }
    fun onDescription(value: String) = update { it.copy(description = value) }
    fun onType(value: TaskType) = update { it.copy(type = value) }
    fun onDate(value: LocalDate) = update { it.copy(startEpochDay = value.toEpochDay()) }
    fun onStartTime(minutes: Int) = update { it.copy(startTimeMinutes = minutes.coerceIn(0, 23 * 60 + 59)) }
    fun onDuration(minutes: Int) = update { it.copy(durationMinutes = minutes) }
    fun onPriority(value: Priority) = update { it.copy(priority = value) }
    fun onUrl(value: String) = update { it.copy(destinationUrl = value) }
    fun onFrequency(value: RecurrenceFrequency) = update { draft ->
        when (value) {
            RecurrenceFrequency.WEEKDAYS -> draft.copy(
                frequency = value,
                daysOfWeek = DayOfWeek.values().filter { it.value <= 5 }.toSet()
            )
            RecurrenceFrequency.WEEKENDS -> draft.copy(
                frequency = value,
                daysOfWeek = setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
            )
            else -> draft.copy(frequency = value)
        }
    }

    fun onToggleDay(day: DayOfWeek) = update { draft ->
        val days = if (day in draft.daysOfWeek) draft.daysOfWeek - day else draft.daysOfWeek + day
        draft.copy(daysOfWeek = days)
    }

    private fun update(transform: (EditorDraft) -> EditorDraft) {
        _state.update { current ->
            val draft = transform(current.draft)
            // Keep only the errors that still apply to the edited draft.
            val failing = TaskValidator.validate(draft.toTask()).map { it.field }.toSet()
            val errors = current.errors.filterKeys { it in failing }
            current.copy(draft = draft, errors = errors, overlapConflicts = emptyList())
        }
    }

    fun save(confirmedOverlaps: Boolean = false) {
        val task = _state.value.draft.toTask()
        val errors = TaskValidator.validate(task)
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors.toFieldMap(), overlapConflicts = emptyList()) }
            return
        }
        viewModelScope.launch {
            if (!confirmedOverlaps) {
                val conflicts = findConflicts(task)
                if (conflicts.isNotEmpty()) {
                    _state.update { it.copy(overlapConflicts = conflicts) }
                    return@launch
                }
            }
            _state.update { it.copy(isSaving = true) }
            repository.saveTask(task)
            if (args.editOccurrenceOfTaskId > 0) repository.setSkipped(args.editOccurrenceOfTaskId, task.startEpochDay)
            _state.update { it.copy(isSaving = false) }
            _events.send(EditorEvent.Saved)
        }
    }

    fun dismissOverlaps() {
        _state.update { it.copy(overlapConflicts = emptyList()) }
    }

    fun delete() {
        val id = _state.value.draft.id
        if (id == 0L) return
        viewModelScope.launch {
            repository.deleteTask(id)
            _events.send(EditorEvent.Deleted)
        }
    }

    private suspend fun findConflicts(task: Task): List<String> {
        val all = repository.observeTasks().first()
        val start = task.startDate()
        val ignored = setOf(task.id, args.editOccurrenceOfTaskId)
        val existing = RecurrenceEngine.occurrencesBetween(all.filter { it.id !in ignored }, start, start.plusDays(6))
        val drafts = RecurrenceEngine.occurrencesBetween(listOf(task), start, start.plusDays(6))
        return OverlapDetector.findOverlaps(existing + drafts)
            .map { (a, b) ->
                val clash = if (a.taskId == task.id) b else a
                "${clash.task.title} • ${WeekUtils.timeLabel(clash.startMinuteOfDay)}–" +
                    "${WeekUtils.timeLabel(clash.startMinuteOfDay + clash.durationMinutes)}"
            }.distinct()
    }

    private fun List<ValidationError>.toFieldMap(): Map<TaskField, String> =
        associate { it.field to it.message }

    private fun EditorDraft.toTask() = Task(
        id = id,
        title = title.trim(),
        description = description.trim(),
        type = type,
        category = type.label,
        startEpochDay = startEpochDay,
        startTimeMinutes = startTimeMinutes,
        durationMinutes = durationMinutes,
        priority = priority,
        destinationUrl = destinationUrl.trim().ifBlank { null },
        focusProfile = focusProfile,
        recurrence = when (frequency) {
            RecurrenceFrequency.NONE -> Recurrence.ONE_TIME
            RecurrenceFrequency.DAILY -> Recurrence.daily()
            RecurrenceFrequency.WEEKDAYS -> Recurrence.weekdays()
            RecurrenceFrequency.WEEKENDS -> Recurrence.weekends()
            RecurrenceFrequency.SELECTED_DAYS -> Recurrence.selected(daysOfWeek)
        },
        createdAtMillis = createdAtMillis
    )
}
