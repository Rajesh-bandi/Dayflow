package com.dayflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dayflow.domain.TaskOccurrence
import com.dayflow.openEditor
import com.dayflow.domain.WeekUtils
import com.dayflow.DayflowApplication
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun WeekScreen(nav: NavHostController) {
    val repo = rememberRepository()
    val vm: WeekViewModel = viewModel(factory = viewModelFactory { initializer { WeekViewModel(repo) } })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val preferences = (LocalContext.current.applicationContext as DayflowApplication).preferences
    val prefs by preferences.state.collectAsStateWithLifecycle()
    LaunchedEffect(prefs.firstDayOfWeek) { vm.setFirstDay(prefs.firstDayOfWeek) }
    val nowMillis = System.currentTimeMillis()
    var sheetOccurrence by remember { mutableStateOf<TaskOccurrence?>(null) }
    var skippedOccurrence by remember { mutableStateOf<TaskOccurrence?>(null) }
    var archivedTaskId by remember { mutableStateOf<Long?>(null) }

    Page("Week") {
        skippedOccurrence?.let { skipped ->
            CardBox { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${skipped.task.title} skipped for this day", color = Muted, fontSize = 11.sp, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.restoreOccurrence(skipped); skippedOccurrence = null }) { Text("Undo", color = Lavender) }
            } }
        }
        archivedTaskId?.let { id ->
            CardBox { Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Task archived", color = Muted, modifier = Modifier.weight(1f))
                TextButton(onClick = { vm.restoreTask(id); archivedTaskId = null }) { Text("Undo", color = Lavender) }
            } }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = { vm.previousWeek() }) { Icon(Icons.Default.ChevronLeft, "Previous week", tint = Muted) }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(state.monthLabel, color = Text, fontWeight = FontWeight.SemiBold)
                Text(if (state.isCurrentWeek) "This Week" else state.rangeLabel, color = Lavender, fontSize = 11.sp)
            }
            IconButton(onClick = { vm.nextWeek() }) { Icon(Icons.Default.ChevronRight, "Next week", tint = Muted) }
        }

        CardBox {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                state.days.forEach { day ->
                    Column(
                        Modifier.clip(RoundedCornerShape(14.dp))
                            .background(if (day.isSelected) Lavender else Color.Transparent)
                            .clickable { vm.selectDate(day.date) }
                            .padding(7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(day.dayLabel.take(3).uppercase(), fontSize = 8.sp, color = if (day.isSelected) Ink else Muted)
                        Text(day.dayNumber, fontSize = 13.sp, color = if (day.isSelected) Ink else Text, fontWeight = FontWeight.Bold)
                        Box(
                            Modifier.padding(top = 3.dp).size(4.dp).clip(CircleShape)
                                .background(
                                    when {
                                        day.allComplete -> Mint
                                        day.total > 0 -> if (day.isSelected) Ink else Lavender
                                        day.isToday -> if (day.isSelected) Ink else Muted
                                        else -> androidx.compose.ui.graphics.Color.Transparent
                                    }
                                )
                        )
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatBox(WeekUtils.durationLabel(state.weekPlannedMinutes), "Planned")
            StatBox("${state.weekSummary.completed}/${state.weekSummary.total}", "Tasks Done")
            StatBox("${state.weekSummary.percentLabel}%", "On Track")
        }

        CardBox {
            SectionTitle("Schedule", WeekUtils.dayTitle(state.selectedDate))
            state.overlapLabels.forEach { label ->
                Spacer(Modifier.height(4.dp))
                Tag("⚠ Overlap • $label", Danger)
            }
            Spacer(Modifier.height(8.dp))
            if (state.occurrences.isEmpty()) {
                Text("Nothing planned for this day.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(4.dp))
            }
            state.occurrences.forEach { occurrence ->
                val completed = state.isCompleted(occurrence)
                val status = when { completed -> "Done"; occurrence.endMillis < nowMillis -> "Missed"; occurrence.startMillis <= nowMillis -> "In progress"; else -> "" }
                TaskRow(
                    title = occurrence.task.title,
                    meta = "${WeekUtils.durationLabel(occurrence.durationMinutes)} • ${occurrence.task.type.label}" +
                        (if (status.isNotEmpty()) " • $status" else ""),
                    time = displayTime(occurrence.startMinuteOfDay),
                    accent = accentFor(occurrence.task.type),
                    checked = state.isCompleted(occurrence),
                    onClick = { sheetOccurrence = occurrence },
                    onToggle = { vm.toggleCompletion(occurrence) }
                )
            }
            AddRow("Tap to plan focus or rest") {
                nav.openEditor(dateEpochDay = state.selectedDate.toEpochDay())
            }
        }

        CardBox {
            SectionTitle("Week at a Glance", state.rangeLabel)
            state.days.forEach { day ->
                TaskRow(
                    title = WeekUtils.dayTitle(day.date),
                    meta = if (day.total == 0) "No rituals planned" else "${day.total} rituals • ${day.completed} completed",
                    time = if (day.allComplete) "✓" else if (day.total > 0) "${day.completed}/${day.total}" else "",
                    accent = if (day.allComplete) Mint else if (day.total > 0) Lavender else Muted,
                    checked = false,
                    onClick = { vm.selectDate(day.date) }
                )
            }
        }
    }

    sheetOccurrence?.let { occurrence ->
        TaskDetailsSheet(
            occurrence = occurrence,
            completed = state.isCompleted(occurrence),
            onToggle = { vm.toggleCompletion(occurrence) },
            onEdit = { sheetOccurrence = null; nav.openEditor(taskId = occurrence.taskId) },
            onEditOccurrence = { sheetOccurrence = null; nav.openEditor(taskId = occurrence.taskId, dateEpochDay = occurrence.date.toEpochDay(), editOccurrenceOfTaskId = occurrence.taskId) },
            onDuplicate = { vm.duplicateTask(occurrence); sheetOccurrence = null },
            onDelete = { vm.deleteTask(occurrence.taskId); sheetOccurrence = null },
            onArchive = { vm.archiveTask(occurrence.taskId); archivedTaskId = occurrence.taskId; sheetOccurrence = null },
            onSkip = { vm.skipOccurrence(occurrence); skippedOccurrence = occurrence; sheetOccurrence = null },
            onDismiss = { sheetOccurrence = null }
        )
    }
}

@Composable
private fun RowScope.StatBox(value: String, label: String) {
    CardBox(Modifier.weight(1f)) {
        Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Lavender)
        Text(label, fontSize = 10.sp, color = Muted)
    }
}
