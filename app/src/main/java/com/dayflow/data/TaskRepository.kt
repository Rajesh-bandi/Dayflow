package com.dayflow.data

import androidx.room.withTransaction
import com.dayflow.data.local.DayflowDatabase
import com.dayflow.data.TaskMappers.toDomain
import com.dayflow.data.TaskMappers.toEntity
import com.dayflow.domain.Task
import com.dayflow.domain.TaskCompletion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface TaskRepository {
    fun observeTasks(): Flow<List<Task>>
    fun observeArchivedTasks(): Flow<List<Task>> = kotlinx.coroutines.flow.flowOf(emptyList())
    fun observeTask(id: Long): Flow<Task?>
    fun observeCompletionsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TaskCompletion>>
    fun observeAllCompletions(): Flow<List<TaskCompletion>>
    suspend fun getTask(id: Long): Task?
    suspend fun countTasks(): Int
    suspend fun saveTask(task: Task): Long
    suspend fun deleteTask(id: Long)
    suspend fun archiveTask(id: Long) { deleteTask(id) }
    suspend fun restoreTask(id: Long) {}
    suspend fun getCompletion(taskId: Long, dateEpochDay: Long): TaskCompletion?
    suspend fun setCompleted(taskId: Long, dateEpochDay: Long, completed: Boolean)
    suspend fun setSkipped(taskId: Long, dateEpochDay: Long) { setCompleted(taskId, dateEpochDay, false) }
}

class RoomTaskRepository(private val database: DayflowDatabase) : TaskRepository {

    private val taskDao = database.taskDao()
    private val occurrenceDao = database.occurrenceDao()

    override fun observeTasks(): Flow<List<Task>> =
        taskDao.observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeArchivedTasks(): Flow<List<Task>> = taskDao.observeArchived().map { list -> list.map { it.toDomain() } }

    override fun observeTask(id: Long): Flow<Task?> =
        taskDao.observeById(id).map { it?.toDomain() }

    override fun observeCompletionsBetween(fromEpochDay: Long, toEpochDay: Long): Flow<List<TaskCompletion>> =
        occurrenceDao.observeBetween(fromEpochDay, toEpochDay).map { list -> list.map { it.toDomain() } }

    override fun observeAllCompletions(): Flow<List<TaskCompletion>> =
        occurrenceDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getTask(id: Long): Task? = taskDao.getById(id)?.toDomain()

    override suspend fun countTasks(): Int = taskDao.count()

    override suspend fun saveTask(task: Task): Long = database.withTransaction {
        val now = System.currentTimeMillis()
        if (task.id == 0L) {
            taskDao.insert(task.copy(createdAtMillis = now, modifiedAtMillis = now).toEntity())
        } else {
            taskDao.update(task.copy(modifiedAtMillis = now).toEntity())
            task.id
        }
    }

    override suspend fun deleteTask(id: Long) = database.withTransaction {
        occurrenceDao.deleteForTask(id)
        taskDao.deleteById(id)
    }

    override suspend fun archiveTask(id: Long) { taskDao.archive(id, System.currentTimeMillis()) }
    override suspend fun restoreTask(id: Long) { taskDao.restore(id) }

    override suspend fun getCompletion(taskId: Long, dateEpochDay: Long): TaskCompletion? =
        occurrenceDao.get(taskId, dateEpochDay)?.toDomain()

    override suspend fun setCompleted(taskId: Long, dateEpochDay: Long, completed: Boolean) {
        occurrenceDao.upsert(
            TaskCompletion(
                taskId = taskId,
                dateEpochDay = dateEpochDay,
                completed = completed,
                completedAtMillis = if (completed) System.currentTimeMillis() else null,
                status = if (completed) "COMPLETED" else "PLANNED"
            ).toEntity()
        )
    }

    override suspend fun setSkipped(taskId: Long, dateEpochDay: Long) {
        occurrenceDao.upsert(TaskCompletion(taskId, dateEpochDay, false, null, "SKIPPED").toEntity())
    }

}
