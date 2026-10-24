package com.dayflow.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayflow.domain.TaskOccurrence
import com.dayflow.domain.TaskValidator
import com.dayflow.domain.WeekUtils

/**
 * Task details bottom sheet with complete/incomplete, edit, duplicate and delete actions.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TaskDetailsSheet(
    occurrence: TaskOccurrence,
    completed: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onEditOccurrence: (() -> Unit)? = null,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
    onArchive: (() -> Unit)? = null,
    onSkip: (() -> Unit)? = null,
    onDismiss: () -> Unit
) {
    val task = occurrence.task
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Surface) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(task.title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, color = Text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag(task.type.label, accentFor(task.type))
                Tag(task.recurrenceDescription(), if (task.isRecurring) Lavender else Muted)
                Tag(task.priority.name.lowercase().replaceFirstChar { it.uppercase() }, if (task.priority.name == "HIGH") Amber else Blue)
            }
            if (task.description.isNotBlank()) Text(task.description, color = Muted, fontSize = 12.sp)

            val start = displayTime(task.startTimeMinutes)
            val end = displayTime(task.startTimeMinutes + task.durationMinutes)
            Text(
                "${WeekUtils.dayTitle(occurrence.date)} • $start–$end • ${WeekUtils.durationLabel(task.durationMinutes)}",
                color = Text, fontSize = 12.sp
            )
            if (task.category.isNotBlank()) Text("Category • ${task.category}", color = Muted, fontSize = 11.sp)
            task.destinationUrl?.let { url ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(url, color = Blue, fontSize = 11.sp, modifier = Modifier.weight(1f))
                    TextButton(onClick = {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url)))
                        }
                    }) { Icon(Icons.Default.OpenInNew, null, tint = Blue, modifier = Modifier.size(14.dp)); Text("Open", color = Blue, fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.height(4.dp))
            Button(
                onClick = onToggle,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (completed) Raised else Mint,
                    contentColor = if (completed) Text else Ink
                )
            ) { Text(if (completed) "Reopen for this day" else "Mark complete") }
            if (task.isRecurring && onEditOccurrence != null) {
                TextButton(onClick = onEditOccurrence, modifier = Modifier.fillMaxWidth()) { Text("Edit this occurrence only", color = Lavender) }
            }
            if (task.isRecurring && onSkip != null) TextButton(onClick = onSkip, modifier = Modifier.fillMaxWidth()) { Text("Skip this occurrence", color = Muted) }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Lavender)) {
                    Icon(Icons.Default.Edit, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text(if (task.isRecurring) "Edit series" else "Edit")
                }
                OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Blue)) {
                    Text("Duplicate")
                }
                OutlinedButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f), colors = ButtonDefaults.outlinedButtonColors(contentColor = Danger)) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Delete")
                }
            }
            if (onArchive != null) TextButton(onClick = onArchive, modifier = Modifier.fillMaxWidth()) { Text("Archive task", color = Muted) }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface,
            title = { Text("Delete this ritual?", color = Text) },
            text = {
                Text(
                    if (task.isRecurring) "\"${task.title}\" and its completion history will be removed for all days."
                    else "\"${task.title}\" and its completion history will be removed.",
                    color = Muted, fontSize = 13.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete() }) { Text("Delete", color = Danger) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = Muted) } }
        )
    }
}

internal fun com.dayflow.domain.Task.recurrenceDescription(): String = when (recurrence.frequency) {
    com.dayflow.domain.RecurrenceFrequency.NONE -> "One-time"
    com.dayflow.domain.RecurrenceFrequency.DAILY -> "Daily"
    com.dayflow.domain.RecurrenceFrequency.WEEKDAYS -> "Weekdays"
    com.dayflow.domain.RecurrenceFrequency.WEEKENDS -> "Weekends"
    com.dayflow.domain.RecurrenceFrequency.SELECTED_DAYS ->
        if (recurrence.daysOfWeek.isEmpty()) "Custom"
        else recurrence.daysOfWeek.sortedBy { it.value }
            .joinToString(", ") { TaskValidator.dayOfWeekLabel(it) }
}
