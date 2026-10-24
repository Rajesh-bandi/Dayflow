package com.dayflow

import com.dayflow.data.TaskRepository
import com.dayflow.domain.Task
import com.dayflow.domain.TaskCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

/** In-memory fake so repository behavior (completion history, persistence) is testable on the JVM. */
class InMemoryTaskRepository : TaskRepository {

    private val tasks = MutableStateFlow<List<Task>>(emptyList())
    private val completions = MutableStateFlow<Map<Pair<Long, Long>, TaskCompletion>>(emptyMap())
    private var nextId = 1L

    override fun observeTasks(): Flow<List<Task>> = tasks

    override fun observeTask(id: Long): Flow<Task?> = tasks.map { list -> list.firstOrNull { it.id == id } }

    override fun observeCompletionsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TaskCompletion>> =
        completions.map { map ->
            map.values.filter { it.dateEpochDay in fromEpochDay..toEpochDay }
                .sortedBy { it.dateEpochDay }
        }

    override fun observeAllCompletions(): Flow<List<TaskCompletion>> =
        completions.map { it.values.sortedBy { c -> c.dateEpochDay } }

    override suspend fun getTask(id: Long): Task? = tasks.value.firstOrNull { it.id == id }

    override suspend fun countTasks(): Int = tasks.value.size

    override suspend fun saveTask(task: Task): Long {
        val (id, now) = if (task.id == 0L) nextId++ to System.currentTimeMillis() else task.id to System.currentTimeMillis()
        val stored = task.copy(id = id, modifiedAtMillis = now, createdAtMillis = task.createdAtMillis.ifZero(now))
        tasks.value = if (tasks.value.any { it.id == id }) {
            tasks.value.map { if (it.id == id) stored else it }
        } else tasks.value + stored
        return id
    }

    override suspend fun deleteTask(id: Long) {
        tasks.value = tasks.value.filterNot { it.id == id }
        completions.value = completions.value.filterKeys { it.first != id }
    }

    override suspend fun getCompletion(taskId: Long, dateEpochDay: Long): TaskCompletion? =
        completions.value[taskId to dateEpochDay]

    override suspend fun setCompleted(taskId: Long, dateEpochDay: Long, completed: Boolean) {
        val key = taskId to dateEpochDay
        completions.value = completions.value + (key to TaskCompletion(
            taskId = taskId,
            dateEpochDay = dateEpochDay,
            completed = completed,
            completedAtMillis = if (completed) System.currentTimeMillis() else null
        ))
    }

    private fun Long.ifZero(fallback: Long) = if (this == 0L) fallback else this
}
