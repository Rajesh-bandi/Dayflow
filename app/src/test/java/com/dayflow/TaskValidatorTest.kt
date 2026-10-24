package com.dayflow

import com.dayflow.domain.Priority
import com.dayflow.domain.Recurrence
import com.dayflow.domain.Task
import com.dayflow.domain.TaskField
import com.dayflow.domain.TaskType
import com.dayflow.domain.TaskValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class TaskValidatorTest {

    private val valid = Task(
        title = "Deep work",
        type = TaskType.DEEP_WORK,
        startEpochDay = LocalDate.of(2026, 10, 7).toEpochDay(),
        startTimeMinutes = 9 * 60,
        durationMinutes = 90,
        priority = Priority.HIGH
    )

    private fun errorsFor(task: Task) = TaskValidator.validate(task).map { it.field }

    @Test
    fun validTaskHasNoErrors() {
        assertTrue(TaskValidator.validate(valid).isEmpty())
    }

    @Test
    fun blankTitleIsRejected() {
        assertTrue(TaskField.TITLE in errorsFor(valid.copy(title = "   ")))
    }

    @Test
    fun nonPositiveDurationIsRejected() {
        assertTrue(TaskField.DURATION in errorsFor(valid.copy(durationMinutes = 0)))
        assertTrue(TaskField.DURATION in errorsFor(valid.copy(durationMinutes = -30)))
    }

    @Test
    fun excessiveDurationIsRejected() {
        assertTrue(TaskField.DURATION in errorsFor(valid.copy(durationMinutes = 24 * 60 + 1)))
    }

    @Test
    fun invalidStartTimeIsRejected() {
        assertTrue(TaskField.START_TIME in errorsFor(valid.copy(startTimeMinutes = 24 * 60)))
        assertTrue(TaskField.START_TIME in errorsFor(valid.copy(startTimeMinutes = -1)))
    }

    @Test
    fun customRecurrenceRequiresSelectedDays() {
        val task = valid.copy(recurrence = Recurrence.selected(emptySet()))
        assertTrue(TaskField.RECURRENCE_DAYS in errorsFor(task))
        val fixed = valid.copy(recurrence = Recurrence.selected(setOf(DayOfWeek.MONDAY)))
        assertTrue(TaskValidator.validate(fixed).isEmpty())
    }

    @Test
    fun malformedUrlIsRejected() {
        assertTrue(TaskField.URL in errorsFor(valid.copy(destinationUrl = "not a link")))
        assertTrue(TaskField.URL in errorsFor(valid.copy(destinationUrl = "https:///missing-host")))
        assertTrue(TaskField.URL in errorsFor(valid.copy(destinationUrl = "javascript:alert(1)")))
        assertTrue(TaskField.URL in errorsFor(valid.copy(destinationUrl = "https://user:pass@example.com")))
        assertTrue(TaskValidator.validate(valid.copy(destinationUrl = "https://builder.aws.com")).isEmpty())
        assertTrue(TaskValidator.validate(valid.copy(destinationUrl = "http://example.com/path")).isEmpty())
        assertTrue(TaskValidator.validate(valid.copy(destinationUrl = null)).isEmpty())
    }

    @Test
    fun multipleErrorsAreAllReported() {
        val broken = valid.copy(title = "", durationMinutes = -1)
        assertEquals(setOf(TaskField.TITLE, TaskField.DURATION), errorsFor(broken).toSet())
    }
}
