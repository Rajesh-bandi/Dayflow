package com.dayflow.notifications

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.dayflow.MainActivity
import com.dayflow.R
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.Task
import com.dayflow.domain.TaskCompletion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

// The version suffix is deliberate: a channel's sound is fixed when it is first created, so an existing
// install can only pick up the Dayflow chime under a new channel id.
private const val CHANNEL_REMINDERS = "dayflow_reminders_v2"
private const val CHANNEL_FOCUS = "dayflow_focus_v2"
private const val EXTRA_ID = "notification_id"
private const val EXTRA_TITLE = "notification_title"
private const val EXTRA_BODY = "notification_body"
private const val EXTRA_FOCUS = "focus_expiration"
private const val EXTRA_URL = "notification_action_url"

/** Where tapping a notification lands, and the ids that keep reminders stable across reschedules. */
object ReminderTarget {

    /**
     * An action task's link is opened straight from the notification, but only over https. An http link
     * still opens the app, where the task's own confirmation step can ask before loading the page.
     */
    fun actionUrl(destinationUrl: String?): String? {
        val url = destinationUrl?.trim().orEmpty()
        return url.takeIf { it.startsWith("https://", ignoreCase = true) && it.length > "https://".length }
    }

    /** Derived from task and day so rescheduling a reminder replaces the same alarm instead of adding one. */
    fun reminderId(taskId: Long, epochDay: Long): Int =
        (taskId * 31 + epochDay).hashCode().and(0x7fffffff).coerceAtLeast(1)
}

internal object ReminderNotifications {
    fun schedule(
        context: Context,
        id: Int,
        atMillis: Long,
        title: String,
        body: String,
        focus: Boolean = false,
        actionUrl: String? = null
    ) {
        val alarm = context.getSystemService(AlarmManager::class.java)
        val operation = pending(context, id, title, body, focus, actionUrl)
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMillis, operation)
    }

    fun cancel(context: Context, id: Int) {
        context.getSystemService(AlarmManager::class.java).cancel(pending(context, id, "", "", false, null))
    }

    private fun pending(context: Context, id: Int, title: String, body: String, focus: Boolean, actionUrl: String?): PendingIntent {
        val intent = Intent(context, DayflowReminderReceiver::class.java)
            .putExtra(EXTRA_ID, id).putExtra(EXTRA_TITLE, title).putExtra(EXTRA_BODY, body)
            .putExtra(EXTRA_FOCUS, focus).putExtra(EXTRA_URL, actionUrl)
        return PendingIntent.getBroadcast(context, id, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    fun show(context: Context, id: Int, title: String, body: String, focus: Boolean = false, actionUrl: String? = null) {
        val manager = context.getSystemService(NotificationManager::class.java)
        val channelId = if (focus) CHANNEL_FOCUS else CHANNEL_REMINDERS
        if (Build.VERSION.SDK_INT >= 26) {
            // A focus session ending is worth a heads-up; a task reminder is not.
            val importance = if (focus) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val name = if (focus) "Focus session finished" else "Task reminders"
            val description = if (focus) {
                "Tells you a focus session has ended and marks the task done."
            } else {
                "Reminds you ten minutes before a scheduled task."
            }
            manager.createNotificationChannel(NotificationSound.channel(context, channelId, name, description, importance))
        }
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return

        val contentIntent = when {
            focus -> PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java).setAction(MainActivity.ACTION_FOCUS_RETURN),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            actionUrl != null -> PendingIntent.getActivity(
                context, id,
                Intent(Intent.ACTION_VIEW, Uri.parse(actionUrl)),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            else -> PendingIntent.getActivity(
                context, id,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_dayflow_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setContentIntent(contentIntent)
            .setSound(NotificationSound.uri(context))
            .setVibrate(NotificationSound.VIBRATION)
            .setCategory(if (focus) NotificationCompat.CATEGORY_ALARM else NotificationCompat.CATEGORY_REMINDER)
            .setPriority(if (focus) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(id, notification) }
    }
}

class DayflowReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getIntExtra(EXTRA_ID, 1)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Dayflow"
        val body = intent.getStringExtra(EXTRA_BODY) ?: "A moment for your plan."
        val focus = intent.getBooleanExtra(EXTRA_FOCUS, false)
        val actionUrl = intent.getStringExtra(EXTRA_URL)
        val show = { ReminderNotifications.show(context, id, title, body, focus, actionUrl) }
        if (focus) {
            val pendingResult = goAsync()
            val app = context.applicationContext as com.dayflow.DayflowApplication
            app.backgroundScope.launch {
                // Reconciling here is what turns a finished session into a completed task.
                runCatching { app.focusSessions.reconcile() }
                show()
                pendingResult.finish()
            }
        } else show()
    }
}

/** Refreshes the next two weeks whenever tasks/preferences change and when Android restarts. */
class ReminderCoordinator(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val registry = context.getSharedPreferences("dayflow_alarm_registry", Context.MODE_PRIVATE)

    fun observe(tasks: kotlinx.coroutines.flow.Flow<List<Task>>, completions: kotlinx.coroutines.flow.Flow<List<TaskCompletion>>, enabled: kotlinx.coroutines.flow.Flow<Boolean>) {
        scope.launch {
            kotlinx.coroutines.flow.combine(tasks, completions, enabled) { allTasks, allCompletions, isEnabled -> Triple(allTasks, allCompletions, isEnabled) }
                .collect { (allTasks, allCompletions, isEnabled) -> sync(allTasks, allCompletions, isEnabled) }
        }
    }

    suspend fun syncFromRepository(tasks: kotlinx.coroutines.flow.Flow<List<Task>>, completions: kotlinx.coroutines.flow.Flow<List<TaskCompletion>>, enabled: Boolean) = sync(tasks.first(), completions.first(), enabled)

    private fun sync(tasks: List<Task>, completions: List<TaskCompletion>, enabled: Boolean) {
        val previous = registry.getStringSet("ids", emptySet()).orEmpty().mapNotNull(String::toIntOrNull).toSet()
        val now = System.currentTimeMillis()
        val nextIds = mutableSetOf<Int>()
        if (enabled && NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            val start = LocalDate.now()
            val completedOrSkipped = completions.filter { it.completed || it.status in setOf("COMPLETED", "SKIPPED") }
                .map { it.taskId to it.dateEpochDay }.toSet()
            RecurrenceEngine.occurrencesBetween(tasks, start, start.plusDays(13))
                .forEach { occurrence ->
                    if ((occurrence.taskId to occurrence.date.toEpochDay()) in completedOrSkipped) return@forEach
                    val fireAt = occurrence.startMillis - 10 * 60_000L
                    if (fireAt <= now) return@forEach
                    val id = ReminderTarget.reminderId(occurrence.taskId, occurrence.date.toEpochDay())
                    nextIds += id
                    ReminderNotifications.schedule(
                        context, id, fireAt,
                        "Coming up: ${occurrence.task.title}",
                        if (occurrence.task.destinationUrl != null) {
                            "Starts in 10 minutes · tap to open the action"
                        } else {
                            "Starts in 10 minutes"
                        },
                        actionUrl = ReminderTarget.actionUrl(occurrence.task.destinationUrl)
                    )
                }
        }
        (previous - nextIds).forEach { ReminderNotifications.cancel(context, it) }
        registry.edit().putStringSet("ids", nextIds.map(Int::toString).toSet()).apply()
    }
}

class RestoreRemindersReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val app = context.applicationContext as com.dayflow.DayflowApplication
        app.backgroundScope.launch {
            runCatching {
                app.reminderCoordinator.syncFromRepository(app.repository.observeTasks(), app.repository.observeAllCompletions(), app.preferences.state.value.remindersEnabled)
                app.focusSessions.reconcile()
                app.focusSessions.observeCurrent().first()?.takeIf { it.status == com.dayflow.data.FocusStatus.RUNNING.name }?.let {
                    scheduleFocusCompletion(context, it.id, it.expectedEndAtMillis, true)
                }
            }
            pendingResult.finish()
        }
    }
}

fun scheduleFocusCompletion(context: Context, id: Long, expectedEndAtMillis: Long, enabled: Boolean) {
    val intId = (id xor (id ushr 32)).toInt().and(0x7fffffff).coerceAtLeast(1)
    if (enabled) {
        ReminderNotifications.schedule(
            context, intId, expectedEndAtMillis,
            "Focus session complete",
            "Your planned focus time is finished. Any task linked to this session is marked done.",
            focus = true
        )
    } else {
        ReminderNotifications.cancel(context, intId)
    }
}
