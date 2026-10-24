package com.dayflow.domain

import java.time.DayOfWeek
import java.net.URI

enum class TaskField { TITLE, START_DATE, START_TIME, DURATION, RECURRENCE_DAYS, URL }

data class ValidationError(val field: TaskField, val message: String)

/** Validates task input before persistence. Pure and unit tested. */
object TaskValidator {

    const val MAX_DURATION_MINUTES = 24 * 60

    fun validate(task: Task): List<ValidationError> {
        val errors = ArrayList<ValidationError>()
        if (task.title.isBlank()) errors += ValidationError(TaskField.TITLE, "Ritual name is required")
        if (task.title.length > 120) errors += ValidationError(TaskField.TITLE, "Keep the name under 120 characters")
        if (task.startEpochDay < 0) errors += ValidationError(TaskField.START_DATE, "Choose a valid date")
        if (task.startTimeMinutes !in 0 until 24 * 60) errors += ValidationError(TaskField.START_TIME, "Choose a valid start time")
        when {
            task.durationMinutes <= 0 ->
                errors += ValidationError(TaskField.DURATION, "Duration must be at least 1 minute")
            task.durationMinutes > MAX_DURATION_MINUTES ->
                errors += ValidationError(TaskField.DURATION, "Duration can't exceed 24 hours")
        }
        if (task.recurrence.frequency == RecurrenceFrequency.SELECTED_DAYS && task.recurrence.daysOfWeek.isEmpty()) {
            errors += ValidationError(TaskField.RECURRENCE_DAYS, "Choose at least one weekday")
        }
        task.destinationUrl?.let { url ->
            if (url.isNotBlank() && !isValidUrl(url)) {
                errors += ValidationError(TaskField.URL, "Link must start with http:// or https://")
            }
        }
        return errors
    }

    private fun isValidUrl(url: String): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        return uri.scheme?.lowercase() in setOf("http", "https") &&
            !uri.host.isNullOrBlank() && uri.userInfo == null
    }

    fun dayOfWeekLabel(day: DayOfWeek): String = when (day) {
        DayOfWeek.MONDAY -> "Mon"
        DayOfWeek.TUESDAY -> "Tue"
        DayOfWeek.WEDNESDAY -> "Wed"
        DayOfWeek.THURSDAY -> "Thu"
        DayOfWeek.FRIDAY -> "Fri"
        DayOfWeek.SATURDAY -> "Sat"
        DayOfWeek.SUNDAY -> "Sun"
    }
}
