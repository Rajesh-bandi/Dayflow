package com.dayflow.data

import com.dayflow.data.local.AiDao
import com.dayflow.data.local.TaskRescheduleEntity
import com.dayflow.domain.OverlapDetector
import com.dayflow.domain.Priority
import com.dayflow.domain.Recurrence
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.Task
import com.dayflow.domain.TaskType
import com.dayflow.domain.TaskValidator
import com.dayflow.domain.WeekUtils
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

sealed interface RoutineActionResult {
    data class Complete(val message: String) : RoutineActionResult
    data class Confirm(val message: String, val call: com.dayflow.data.LumiFunctionCall) : RoutineActionResult
    data class Failure(val message: String) : RoutineActionResult
}

/** Narrow AI tool boundary. All reads and writes go through the existing repository/domain validators. */
class RoutineToolService(private val repository: TaskRepository, private val aiDao: AiDao) {

    /** Planning window used for free-time maths, matching the Today screen. */
    private val dayStartMinutes = 8 * 60
    private val dayEndMinutes = 22 * 60

    fun toolDefinitions(): JSONArray = JSONArray().apply {
        put(tool("get_today_schedule", "Read scheduled tasks and completion statuses for a date. Use this for schedule questions.", objectOf("date" to str("Local date in YYYY-MM-DD format; defaults to today."))))
        put(tool("get_missed_tasks", "List past scheduled tasks that remain incomplete, with each task's id and duration so you can reschedule them. Reported as overdue, never silently marked missed.", objectOf("date" to str("Local date in YYYY-MM-DD format; defaults to yesterday."))))
        put(tool("get_schedule_gaps", "Calculate open gaps between scheduled tasks on a date.", objectOf("date" to str("Local date in YYYY-MM-DD format; defaults to today."))))
        put(tool("find_free_slot", "Find open windows long enough for a task on a date. Use this before proposing a time so the suggestion never clashes with saved tasks.", objectOf(
            "date" to str("Local date YYYY-MM-DD; defaults to today."),
            "minutes" to num("Required length in minutes")
        ), arrayOf("minutes")))
        put(tool("find_tasks", "Search saved tasks by name to get their real ids before editing, completing or deleting. Use this instead of guessing an id.", objectOf(
            "query" to str("Words from the task title; empty lists every task"),
            "include_archived" to bool("Include archived tasks; defaults to false")
        )))
        put(tool("get_weekly_progress", "Summarize actual completion records for the last seven days.", JSONObject()))
        put(tool("create_task", "Add a task of any type. Ask the user for any missing title, date, start time, or duration before calling. Set task_type for habits, deep work, quick actions or breaks. Set recurrence for repeating tasks.", taskProperties(includeId = false), arrayOf("title", "date", "start_time", "duration_minutes")))
        put(tool("update_task", "Edit or reschedule an existing task. Use the real task id from find_tasks or the schedule; include only the fields that change.", taskProperties(includeId = true), arrayOf("task_id")))
        put(tool("create_tasks", "Add several tasks in one call, for example when planning a whole day or setting up a weekly routine. Each entry uses the same fields as create_task. Entries that clash with an existing saved task are reported and not created, so you can suggest another time.", objectOf(
            "tasks" to JSONObject().put("type", "array").put("description", "Tasks to add")
                .put("items", JSONObject().put("type", "object").put("properties", taskProperties(includeId = false))
                    .put("required", JSONArray(listOf("title", "date", "start_time", "duration_minutes"))))
        ), arrayOf("tasks")))
        put(tool("complete_task", "Mark a scheduled task occurrence completed or pending for a date.", objectOf("task_id" to num("Task id"), "date" to str("Local date YYYY-MM-DD; defaults to today."), "completed" to bool("True to complete, false to clear completion")), arrayOf("task_id")))
        put(tool("delete_task", "Remove a task from active schedules. The app asks the user to confirm first.", objectOf("task_id" to num("Task id from the real schedule")), arrayOf("task_id")))
    }

    /** Same field set for creating and editing so habits, actions and repeats are reachable from chat. */
    private fun taskProperties(includeId: Boolean): JSONObject = JSONObject().apply {
        if (includeId) put("task_id", num("Task id from find_tasks"))
        put("title", str("Task title"))
        put("date", str("Local date YYYY-MM-DD"))
        put("start_time", str("24-hour time HH:mm"))
        put("duration_minutes", num("Duration in minutes"))
        put("task_type", enumStr("Task archetype; defaults to regular", listOf("regular", "deep_work", "quick_action", "habit", "break")))
        put("priority", enumStr("Importance; defaults to normal", listOf("low", "normal", "high")))
        put("description", str("Optional short note or detail"))
        put("destination_url", str("Optional link, used by quick actions"))
        put("recurrence", enumStr("Repeat pattern; defaults to none", listOf("none", "daily", "weekdays", "weekends", "selected_days")))
        put("days", JSONObject().put("type", "array").put("description", "Weekdays for selected_days recurrence")
            .put("items", JSONObject().put("type", "string").put("enum", JSONArray(listOf("mon", "tue", "wed", "thu", "fri", "sat", "sun")))))
        put("repeat_until", str("Optional last local date YYYY-MM-DD the repeat continues through"))
    }

    suspend fun execute(call: LumiFunctionCall, confirmed: Boolean = false): RoutineActionResult {
        return try {
            when (call.name) {
                "get_today_schedule" -> RoutineActionResult.Complete(schedule(parseDate(call.arguments.optString("date"), LocalDate.now()) ?: return RoutineActionResult.Failure("Date must use YYYY-MM-DD.")))
                "get_missed_tasks" -> RoutineActionResult.Complete(missed(parseDate(call.arguments.optString("date"), LocalDate.now().minusDays(1)) ?: return RoutineActionResult.Failure("Date must use YYYY-MM-DD.")))
                "get_schedule_gaps" -> RoutineActionResult.Complete(gaps(parseDate(call.arguments.optString("date"), LocalDate.now()) ?: return RoutineActionResult.Failure("Date must use YYYY-MM-DD.")))
                "find_free_slot" -> {
                    val minutes = call.arguments.optInt("minutes", 0)
                    if (minutes <= 0) RoutineActionResult.Failure("Tell me how many minutes the task needs.")
                    else RoutineActionResult.Complete(freeSlots(parseDate(call.arguments.optString("date"), LocalDate.now()) ?: return RoutineActionResult.Failure("Date must use YYYY-MM-DD."), minutes))
                }
                "find_tasks" -> RoutineActionResult.Complete(findTasks(call.arguments.optString("query"), call.arguments.optBoolean("include_archived", false)))
                "get_weekly_progress" -> RoutineActionResult.Complete(weeklyProgress())
                "create_task" -> create(call, confirmed)
                "create_tasks" -> createMany(call)
                "update_task" -> update(call, confirmed)
                "complete_task" -> complete(call)
                "delete_task" -> delete(call, confirmed)
                else -> RoutineActionResult.Failure("Unsupported routine action '${call.name}'.")
            }
        } catch (e: Exception) {
            RoutineActionResult.Failure(e.message ?: "Could not complete the routine action.")
        }
    }

    private suspend fun schedule(date: LocalDate): String {
        val tasks = tasksOn(date)
        val completions = repository.observeCompletionsBetween(date.toEpochDay(), date.toEpochDay()).first().associateBy { it.taskId }
        if (tasks.isEmpty()) return "No saved tasks on $date."
        return tasks.joinToString("\n") { task ->
            val status = completions[task.id]?.status ?: "PLANNED"
            "id=${task.id} · ${WeekUtils.timeLabel(task.startTimeMinutes)}–${WeekUtils.timeLabel((task.startTimeMinutes + task.durationMinutes).coerceAtMost(1439))} · ${task.title} · ${task.type.label} · ${task.durationMinutes} min · $status"
        }
    }

    private suspend fun missed(date: LocalDate): String {
        val tasks = tasksOn(date)
        val records = repository.observeCompletionsBetween(date.toEpochDay(), date.toEpochDay()).first().associateBy { it.taskId }
        val overdue = tasks.filter { records[it.id]?.status != "COMPLETED" && records[it.id]?.status != "SKIPPED" }
        return if (overdue.isEmpty()) "No incomplete scheduled tasks found on $date."
        else "Incomplete scheduled tasks on $date (overdue candidates; not automatically marked missed). Reschedule the ones the user wants and keep their original ids:\n" +
            overdue.joinToString("\n") { "id=${it.id} · ${it.title} · ${it.type.label} · ${it.durationMinutes} min" }
    }

    private suspend fun gaps(date: LocalDate): String {
        val tasks = tasksOn(date).sortedBy { it.startTimeMinutes }
        if (tasks.size < 2) return "Fewer than two scheduled tasks on $date, so there are no between-task gaps to report."
        var occupiedUntil = tasks.first().startTimeMinutes + tasks.first().durationMinutes
        val result = ArrayList<String>()
        tasks.drop(1).forEach { task ->
            if (task.startTimeMinutes > occupiedUntil) result += "${WeekUtils.timeLabel(occupiedUntil)}–${WeekUtils.timeLabel(task.startTimeMinutes)} (${task.startTimeMinutes - occupiedUntil} min)"
            occupiedUntil = maxOf(occupiedUntil, task.startTimeMinutes + task.durationMinutes)
        }
        return result.joinToString("\n").ifBlank { "No free gaps between scheduled tasks on $date." }
    }

    /** Concrete open windows inside the planning day, longest first, so a proposal never clashes. */
    private suspend fun freeSlots(date: LocalDate, minutes: Int): String {
        val busy = tasksOn(date).sortedBy { it.startTimeMinutes }
        var cursor = dayStartMinutes
        val windows = ArrayList<Pair<Int, Int>>()
        busy.forEach { task ->
            val start = task.startTimeMinutes.coerceIn(dayStartMinutes, dayEndMinutes)
            val end = (task.startTimeMinutes + task.durationMinutes).coerceIn(dayStartMinutes, dayEndMinutes)
            if (start - cursor >= minutes) windows += cursor to start
            cursor = maxOf(cursor, end)
        }
        if (dayEndMinutes - cursor >= minutes) windows += cursor to dayEndMinutes
        if (windows.isEmpty()) return "No open window of $minutes minutes or more on $date within ${WeekUtils.timeLabel(dayStartMinutes)}–${WeekUtils.timeLabel(dayEndMinutes)}."
        val today = LocalDate.now()
        val nowMinutes = if (date == today) LocalTime.now().let { it.hour * 60 + it.minute } else -1
        return "Open windows on $date of at least $minutes minutes (planning day ${WeekUtils.timeLabel(dayStartMinutes)}–${WeekUtils.timeLabel(dayEndMinutes)}):\n" +
            windows.sortedByDescending { it.second - it.first }.joinToString("\n") { (start, end) ->
                val length = end - start
                val usable = if (nowMinutes > 0 && start < nowMinutes) maxOf(0, end - maxOf(start, nowMinutes)) else length
                "${WeekUtils.timeLabel(start)}–${WeekUtils.timeLabel(end)} ($length min free" + (if (nowMinutes > 0 && start < nowMinutes) ", $usable min remaining today)" else ")")
            }
    }

    private suspend fun findTasks(query: String, includeArchived: Boolean): String {
        val needle = query.trim().lowercase()
        val all = repository.observeTasks().first() + if (includeArchived) repository.observeArchivedTasks().first() else emptyList()
        val matches = if (needle.isBlank()) all else all.filter { it.title.lowercase().contains(needle) }
        if (matches.isEmpty()) return if (needle.isBlank()) "No saved tasks." else "No saved task title matches \"$query\"."
        val today = LocalDate.now()
        return matches.take(25).joinToString("\n") { task ->
            val next = nextOccurrence(task, today)
            val repeat = task.recurrence.frequency.name.lowercase().replace('_', ' ')
            "id=${task.id} · ${task.title} · ${task.type.label} · $repeat · ${WeekUtils.timeLabel(task.startTimeMinutes)} · ${task.durationMinutes} min" +
                (if (next != null) " · next on $next" else " · no upcoming date") +
                (if (task.archivedAtMillis != null) " · archived" else "")
        }
    }

    private fun nextOccurrence(task: Task, from: LocalDate): LocalDate? {
        if (!task.isRecurring) return task.startDate().takeIf { !it.isBefore(from) }
        var cursor = from
        repeat(400) {
            if (RecurrenceEngine.occursOn(task, cursor)) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }

    private suspend fun weeklyProgress(): String {
        val end = LocalDate.now(); val start = end.minusDays(6)
        val tasks = repository.observeTasks().first()
        val records = repository.observeCompletionsBetween(start.toEpochDay(), end.toEpochDay()).first().associateBy { it.taskId to it.dateEpochDay }
        return (0..6).map { start.plusDays(it.toLong()) }.joinToString("\n") { date ->
            val dayTasks = tasks.filter { RecurrenceEngine.occursOn(it, date) }
            val complete = dayTasks.count { records[it.id to date.toEpochDay()]?.status == "COMPLETED" }
            "$date: $complete/${dayTasks.size} completed"
        }
    }

    private suspend fun create(call: LumiFunctionCall, confirmed: Boolean): RoutineActionResult {
        val args = call.arguments
        val title = args.optString("title").trim()
        val date = parseDate(args.optString("date"), null) ?: return RoutineActionResult.Failure("Please provide a task date in YYYY-MM-DD format.")
        val time = parseTime(args.optString("start_time")) ?: return RoutineActionResult.Failure("Please provide a start time in 24-hour HH:mm format.")
        val duration = args.optInt("duration_minutes", 0)
        val type = parseType(args.optString("task_type"))
        val draft = buildTask(
            id = 0L, title = title, args = args, type = type, date = date, time = time,
            duration = duration, recurrence = parseRecurrence(args),
            createdAtMillis = System.currentTimeMillis(), existing = null
        )
        val errors = TaskValidator.validate(draft)
        if (errors.isNotEmpty()) return RoutineActionResult.Failure(errors.first().message)
        val conflicts = OverlapDetector.findOverlapsFor(draft, date, repository.observeTasks().first())
        if (conflicts.isNotEmpty() && !confirmed) return RoutineActionResult.Confirm("This overlaps ${conflicts.joinToString { it.first.task.title + " / " + it.second.task.title }}. Add it anyway?", call)
        val id = repository.saveTask(draft)
        val saved = repository.getTask(id) ?: return RoutineActionResult.Failure("Dayflow did not confirm that the task was saved.")
        return RoutineActionResult.Complete("Saved ${saved.type.label.lowercase()} '${saved.title}' on $date at ${WeekUtils.timeLabel(saved.startTimeMinutes)} for ${saved.durationMinutes} minutes${recurrenceSuffix(saved)} (task id $id).")
    }

    /**
     * Adds a batch of tasks. Anything that clashes with an already saved task is reported rather than
     * forced in, so the assistant can propose a different time instead of silently double-booking.
     */
    private suspend fun createMany(call: LumiFunctionCall): RoutineActionResult {
        val items = call.arguments.optJSONArray("tasks") ?: return RoutineActionResult.Failure("Provide a list of tasks.")
        if (items.length() == 0) return RoutineActionResult.Failure("Provide at least one task.")
        val scheduled = repository.observeTasks().first().toMutableList()
        val created = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        for (index in 0 until items.length()) {
            val args = items.optJSONObject(index) ?: continue
            val title = args.optString("title").trim()
            val date = parseDate(args.optString("date"), null)
            val time = parseTime(args.optString("start_time"))
            if (title.isBlank() || date == null || time == null) {
                rejected += "${title.ifBlank { "item ${index + 1}" }} (needs a title, date and start time)"
                continue
            }
            val draft = buildTask(0L, title, args, parseType(args.optString("task_type")), date, time, args.optInt("duration_minutes", 0), parseRecurrence(args), System.currentTimeMillis(), null)
            val errors = TaskValidator.validate(draft)
            if (errors.isNotEmpty()) { rejected += "$title (${errors.first().message})"; continue }
            val conflicts = OverlapDetector.findOverlapsFor(draft, date, scheduled)
            if (conflicts.isNotEmpty()) {
                val other = conflicts.map { if (it.first.taskId == draft.id) it.second.task.title else it.first.task.title }.distinct()
                rejected += "$title at ${WeekUtils.timeLabel(draft.startTimeMinutes)} (clashes with ${other.joinToString()})"
                continue
            }
            val saved = repository.getTask(repository.saveTask(draft))
            if (saved == null) { rejected += "$title (Dayflow could not confirm the save)"; continue }
            scheduled += saved
            created += "${saved.title} on $date at ${WeekUtils.timeLabel(saved.startTimeMinutes)} for ${saved.durationMinutes} minutes (id ${saved.id})"
        }
        val summary = buildString {
            if (created.isNotEmpty()) append("Added ${created.size} task${if (created.size == 1) "" else "s"}: ${created.joinToString("; ")}.")
            if (rejected.isNotEmpty()) {
                if (isNotEmpty()) append(' ')
                append("Not added: ${rejected.joinToString("; ")}.")
            }
        }
        return if (created.isEmpty() && rejected.isNotEmpty()) RoutineActionResult.Failure(summary) else RoutineActionResult.Complete(summary)
    }

    private suspend fun update(call: LumiFunctionCall, confirmed: Boolean): RoutineActionResult {
        val args = call.arguments
        val old = repository.getTask(args.optLong("task_id", -1L)) ?: return RoutineActionResult.Failure("No saved task has that id. Use find_tasks to get the real id.")
        val targetDate = if (args.has("date")) parseDate(args.optString("date"), null) ?: return RoutineActionResult.Failure("Date must be YYYY-MM-DD.") else old.startDate()
        val targetTime = if (args.has("start_time")) parseTime(args.optString("start_time")) ?: return RoutineActionResult.Failure("Time must be HH:mm.") else old.startTimeMinutes.let { LocalTime.of(it / 60, it % 60) }
        val targetDuration = if (args.has("duration_minutes")) args.optInt("duration_minutes") else old.durationMinutes
        val dateChanged = targetDate != old.startDate()
        val type = if (args.has("task_type")) parseType(args.optString("task_type")) else old.type
        val recurrence = if (hasRecurrenceEdit(args)) parseRecurrence(args) else old.recurrence
        val draft = buildTask(
            id = if (dateChanged) 0L else old.id, title = args.optString("title", old.title).ifBlank { old.title }.take(120),
            args = args, type = type, date = targetDate, time = targetTime, duration = targetDuration,
            recurrence = if (dateChanged) Recurrence.ONE_TIME else recurrence,
            createdAtMillis = old.createdAtMillis, existing = old
        )
        val errors = TaskValidator.validate(draft)
        if (errors.isNotEmpty()) return RoutineActionResult.Failure(errors.first().message)
        val conflicts = OverlapDetector.findOverlapsFor(draft, targetDate, repository.observeTasks().first())
        if (conflicts.isNotEmpty() && !confirmed) return RoutineActionResult.Confirm("This change overlaps ${conflicts.joinToString { it.first.task.title + " / " + it.second.task.title }}. Apply it anyway?", call)
        if (!dateChanged) {
            repository.saveTask(draft)
            val verified = repository.getTask(old.id) ?: return RoutineActionResult.Failure("Dayflow could not verify the task update.")
            return RoutineActionResult.Complete("Updated '${verified.title}' to ${targetDate} at ${WeekUtils.timeLabel(verified.startTimeMinutes)} for ${verified.durationMinutes} minutes${recurrenceSuffix(verified)}.")
        }
        val newId = repository.saveTask(draft)
        repository.setSkipped(old.id, old.startDate().toEpochDay())
        aiDao.insertReschedule(TaskRescheduleEntity(taskId = old.id, newTaskId = newId, fromEpochDay = old.startDate().toEpochDay(), toEpochDay = targetDate.toEpochDay()))
        val verified = repository.getTask(newId) ?: return RoutineActionResult.Failure("Dayflow could not verify the rescheduled task.")
        return RoutineActionResult.Complete("Rescheduled '${verified.title}' to $targetDate at ${WeekUtils.timeLabel(verified.startTimeMinutes)}; the original occurrence history was preserved.")
    }

    private fun hasRecurrenceEdit(args: JSONObject): Boolean =
        args.has("recurrence") || args.has("repeat_until") || args.has("days")

    /** Single place that turns validated tool arguments into a domain task. */
    private fun buildTask(
        id: Long, title: String, args: JSONObject, type: TaskType, date: LocalDate,
        time: LocalTime, duration: Int, recurrence: Recurrence, createdAtMillis: Long, existing: Task?
    ): Task {
        val url = if (args.has("destination_url")) args.optString("destination_url").trim().ifBlank { null }
        else existing?.destinationUrl
        return Task(
            id = id,
            title = title,
            description = if (args.has("description")) args.optString("description").trim().take(500) else existing?.description.orEmpty(),
            type = type,
            category = type.label,
            startEpochDay = date.toEpochDay(),
            startTimeMinutes = time.hour * 60 + time.minute,
            durationMinutes = duration,
            priority = if (args.has("priority")) parsePriority(args.optString("priority")) else existing?.priority ?: Priority.NORMAL,
            destinationUrl = url,
            focusProfile = existing?.focusProfile,
            recurrence = recurrence,
            createdAtMillis = createdAtMillis
        )
    }

    private fun recurrenceSuffix(task: Task): String = when {
        !task.isRecurring -> ""
        task.recurrence.frequency == com.dayflow.domain.RecurrenceFrequency.SELECTED_DAYS ->
            ", repeating on ${task.recurrence.daysOfWeek.sortedBy { it.value }.joinToString(", ") { TaskValidator.dayOfWeekLabel(it) }}"
        else -> ", repeating ${task.recurrence.frequency.name.lowercase()}"
    }

    private suspend fun complete(call: LumiFunctionCall): RoutineActionResult {
        val taskId = call.arguments.optLong("task_id", -1L)
        val task = repository.getTask(taskId) ?: return RoutineActionResult.Failure("No saved task has that id. Use find_tasks to get the real id.")
        val date = parseDate(call.arguments.optString("date"), LocalDate.now()) ?: return RoutineActionResult.Failure("Date must use YYYY-MM-DD.")
        val complete = call.arguments.optBoolean("completed", true)
        if (!RecurrenceEngine.occursOn(task, date)) return RoutineActionResult.Failure("${task.title} is not scheduled on $date.")
        repository.setCompleted(taskId, date.toEpochDay(), complete)
        val verified = repository.getCompletion(taskId, date.toEpochDay())
        if (verified?.completed != complete) return RoutineActionResult.Failure("Dayflow could not verify the completion update.")
        return RoutineActionResult.Complete("Marked '${task.title}' ${if (complete) "complete" else "pending"} on $date.")
    }

    private suspend fun delete(call: LumiFunctionCall, confirmed: Boolean): RoutineActionResult {
        val task = repository.getTask(call.arguments.optLong("task_id", -1L)) ?: return RoutineActionResult.Failure("No saved task has that id. Use find_tasks to get the real id.")
        if (!confirmed) return RoutineActionResult.Confirm("Remove '${task.title}' from your active schedule? Its history will remain in the archive.", call)
        repository.archiveTask(task.id)
        val archived = repository.getTask(task.id)?.archivedAtMillis != null
        return if (archived) RoutineActionResult.Complete("Removed '${task.title}' from the active schedule. Its completion history is preserved in the archive.") else RoutineActionResult.Failure("Dayflow could not verify that the task was removed.")
    }

    private suspend fun tasksOn(date: LocalDate): List<Task> = repository.observeTasks().first().filter { RecurrenceEngine.occursOn(it, date) }.sortedBy { it.startTimeMinutes }

    private fun parseDate(value: String, fallback: LocalDate?): LocalDate? = if (value.isBlank()) fallback else runCatching { LocalDate.parse(value) }.getOrNull()
    private fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value.trim().let { if (it.length == 4 && it.contains(':')) "0$it" else it }) }.getOrNull()

    private fun parseType(value: String): TaskType = when (value.trim().lowercase().replace(' ', '_').replace('-', '_')) {
        "deep_work", "deep work", "focus", "deep" -> TaskType.DEEP_WORK
        "quick_action", "quick action", "action", "link" -> TaskType.QUICK_ACTION
        "habit", "routine", "ritual" -> TaskType.HABIT
        "break", "rest", "recovery" -> TaskType.BREAK
        else -> TaskType.REGULAR
    }

    private fun parsePriority(value: String): Priority = when (value.trim().lowercase()) {
        "high", "urgent", "important" -> Priority.HIGH
        "low", "whenever" -> Priority.LOW
        else -> Priority.NORMAL
    }

    private fun parseRecurrence(args: JSONObject): Recurrence {
        val until = parseDate(args.optString("repeat_until"), null)?.toEpochDay()
        return when (args.optString("recurrence", "none").trim().lowercase().replace(' ', '_')) {
            "daily", "every_day", "everyday" -> Recurrence.daily(until)
            "weekdays", "every_weekday" -> Recurrence.weekdays(until)
            "weekends", "every_weekend" -> Recurrence.weekends(until)
            "selected_days", "custom", "specific_days" -> Recurrence.selected(parseDays(args.optJSONArray("days")), until)
            else -> Recurrence.ONE_TIME
        }
    }

    private fun parseDays(raw: JSONArray?): Set<DayOfWeek> {
        if (raw == null) return emptySet()
        val days = LinkedHashSet<DayOfWeek>()
        for (index in 0 until raw.length()) {
            when (raw.optString(index).trim().lowercase().take(3)) {
                "mon" -> days += DayOfWeek.MONDAY
                "tue" -> days += DayOfWeek.TUESDAY
                "wed" -> days += DayOfWeek.WEDNESDAY
                "thu" -> days += DayOfWeek.THURSDAY
                "fri" -> days += DayOfWeek.FRIDAY
                "sat" -> days += DayOfWeek.SATURDAY
                "sun" -> days += DayOfWeek.SUNDAY
            }
        }
        return days
    }

    private fun tool(name: String, description: String, properties: JSONObject, required: Array<String> = emptyArray()) = JSONObject()
        .put("type", "function")
        .put("function", JSONObject().put("name", name).put("description", description)
            .put("parameters", JSONObject().put("type", "object").put("properties", properties).put("required", JSONArray(required.toList())).put("additionalProperties", false)))

    private fun objectOf(vararg values: Pair<String, JSONObject>) = JSONObject().apply { values.forEach { put(it.first, it.second) } }
    private fun str(description: String) = JSONObject().put("type", "string").put("description", description)
    private fun num(description: String) = JSONObject().put("type", "integer").put("description", description)
    private fun bool(description: String) = JSONObject().put("type", "boolean").put("description", description)
    private fun enumStr(description: String, values: List<String>) = JSONObject().put("type", "string").put("description", description).put("enum", JSONArray(values))
}
