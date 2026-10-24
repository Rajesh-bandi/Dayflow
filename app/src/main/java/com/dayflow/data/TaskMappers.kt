package com.dayflow.data

import com.dayflow.data.local.TaskEntity
import com.dayflow.data.local.TaskOccurrenceEntity
import com.dayflow.domain.Priority
import com.dayflow.domain.Recurrence
import com.dayflow.domain.RecurrenceFrequency
import com.dayflow.domain.Task
import com.dayflow.domain.TaskCompletion
import com.dayflow.domain.TaskType
import java.time.DayOfWeek

/** Entity <-> domain mapping. Weekday bitmask uses bit 0 = Monday .. bit 6 = Sunday (ISO order). */
object TaskMappers {

    fun TaskEntity.toDomain() = Task(
        id = id,
        title = title,
        description = description,
        type = runCatching { TaskType.valueOf(type) }.getOrDefault(TaskType.REGULAR),
        category = category,
        startEpochDay = startEpochDay,
        startTimeMinutes = startTimeMinutes,
        durationMinutes = durationMinutes,
        priority = runCatching { Priority.valueOf(priority) }.getOrDefault(Priority.NORMAL),
        destinationUrl = destinationUrl,
        focusProfile = focusProfile,
        recurrence = Recurrence(
            frequency = runCatching { RecurrenceFrequency.valueOf(recurrenceFrequency) }
                .getOrDefault(RecurrenceFrequency.NONE),
            daysOfWeek = daysFromMask(recurrenceDaysMask),
            endEpochDay = recurrenceEndEpochDay
        ),
        createdAtMillis = createdAtMillis,
        modifiedAtMillis = modifiedAtMillis,
        archivedAtMillis = archivedAtMillis
    )

    fun Task.toEntity() = TaskEntity(
        id = id,
        title = title,
        description = description,
        type = type.name,
        category = category,
        startEpochDay = startEpochDay,
        startTimeMinutes = startTimeMinutes,
        durationMinutes = durationMinutes,
        priority = priority.name,
        destinationUrl = destinationUrl,
        focusProfile = focusProfile,
        recurrenceFrequency = recurrence.frequency.name,
        recurrenceDaysMask = maskFromDays(recurrence.daysOfWeek),
        recurrenceEndEpochDay = recurrence.endEpochDay,
        createdAtMillis = createdAtMillis,
        modifiedAtMillis = modifiedAtMillis,
        archivedAtMillis = archivedAtMillis
    )

    fun TaskOccurrenceEntity.toDomain() = TaskCompletion(
        taskId = taskId,
        dateEpochDay = dateEpochDay,
        completed = completed,
        completedAtMillis = completedAtMillis,
        status = status
    )

    fun TaskCompletion.toEntity() = TaskOccurrenceEntity(
        taskId = taskId,
        dateEpochDay = dateEpochDay,
        completed = completed,
        completedAtMillis = completedAtMillis,
        status = status
    )

    fun maskFromDays(days: Set<DayOfWeek>): Int =
        days.fold(0) { mask, day -> mask or (1 shl (day.value - 1)) }

    fun daysFromMask(mask: Int): Set<DayOfWeek> =
        DayOfWeek.values().filterTo(LinkedHashSet()) { (mask and (1 shl (it.value - 1))) != 0 }
}
