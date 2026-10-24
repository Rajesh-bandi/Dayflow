package com.dayflow

import com.dayflow.domain.Recurrence
import com.dayflow.domain.Task
import com.dayflow.domain.TaskType
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TaskRepositoryTest {

    private val monday = LocalDate.of(2026, 10, 5)
    private val repository = InMemoryTaskRepository()

    private fun sampleTask(recurrence: Recurrence = Recurrence.ONE_TIME) = Task(
        title = "Spring Boot Microservices",
        description = "Deep architecture",
        type = TaskType.DEEP_WORK,
        category = "Deep Work",
        startEpochDay = monday.toEpochDay(),
        startTimeMinutes = 18 * 60,
        durationMinutes = 60,
        recurrence = recurrence
    )

    @Test
    fun createTaskPersistsAndIsObservable() = runBlocking {
        val id = repository.saveTask(sampleTask())
        assertTrue(id > 0)
        val loaded = repository.getTask(id)
        assertNotNull(loaded)
        assertEquals("Spring Boot Microservices", loaded!!.title)
        assertEquals(monday.toEpochDay(), loaded.startEpochDay)
        assertEquals(18 * 60, loaded.startTimeMinutes)
    }

    @Test
    fun updatesArePersisted() = runBlocking {
        val id = repository.saveTask(sampleTask())
        repository.saveTask(repository.getTask(id)!!.copy(title = "Renamed", durationMinutes = 90))
        val updated = repository.getTask(id)!!
        assertEquals("Renamed", updated.title)
        assertEquals(90, updated.durationMinutes)
        assertEquals(1, repository.countTasks())
    }

    @Test
    fun completingAndReopeningTogglesRecord() = runBlocking {
        val id = repository.saveTask(sampleTask())
        repository.setCompleted(id, monday.toEpochDay(), true)
        var completion = repository.getCompletion(id, monday.toEpochDay())
        assertTrue(completion!!.completed)
        assertNotNull(completion.completedAtMillis)

        repository.setCompleted(id, monday.toEpochDay(), false)
        completion = repository.getCompletion(id, monday.toEpochDay())
        assertFalse(completion!!.completed)
        assertNull(completion.completedAtMillis)
    }

    @Test
    fun recurringOccurrencesHaveIndependentCompletionHistory() = runBlocking {
        val id = repository.saveTask(sampleTask(Recurrence.weekdays()))
        val tuesday = monday.plusDays(1)

        repository.setCompleted(id, monday.toEpochDay(), true)
        repository.setCompleted(id, tuesday.toEpochDay(), true)
        repository.setCompleted(id, tuesday.toEpochDay(), false) // reopen Tuesday only

        assertTrue(repository.getCompletion(id, monday.toEpochDay())!!.completed)
        assertFalse(repository.getCompletion(id, tuesday.toEpochDay())!!.completed)

        val all = repository.observeAllCompletions().first()
        assertEquals(2, all.size)
    }

    @Test
    fun deletingTaskRemovesItsCompletionHistory() = runBlocking {
        val id = repository.saveTask(sampleTask())
        repository.setCompleted(id, monday.toEpochDay(), true)

        repository.deleteTask(id)

        assertNull(repository.getTask(id))
        assertNull(repository.getCompletion(id, monday.toEpochDay()))
        assertTrue(repository.observeAllCompletions().first().isEmpty())
    }

}
