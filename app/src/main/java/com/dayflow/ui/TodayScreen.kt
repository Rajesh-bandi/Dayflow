package com.dayflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dayflow.domain.TaskOccurrence
import com.dayflow.openEditor
import com.dayflow.domain.TaskType
import com.dayflow.domain.WeekUtils

@Composable
internal fun TodayScreen(nav: NavHostController) {
    val repo = rememberRepository()
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(repo) } })
    val state by vm.uiState.collectAsStateWithLifecycle()
    var sheetOccurrence by remember { mutableStateOf<TaskOccurrence?>(null) }
    var skippedOccurrence by remember { mutableStateOf<TaskOccurrence?>(null) }
    var archivedTaskId by remember { mutableStateOf<Long?>(null) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(60_000) } }
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as com.dayflow.DayflowApplication
    var profileName by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        profileName = runCatching { app.aiDao.getProfile()?.name }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    Page("Today", "Your day, your pace.") {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { vm.shiftDay(-1) }) { Icon(Icons.Default.ChevronLeft, "Previous day", tint = Muted) }
            Text(state.dateLabel.uppercase(), color = Muted, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            IconButton(onClick = { vm.shiftDay(1) }) { Icon(Icons.Default.ChevronRight, "Next day", tint = Muted) }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(state.greeting, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = Text, modifier = Modifier.weight(1f))
            Tag("🔥  ${state.streakDays} ${if (state.streakDays == 1) "day" else "days"}", Lavender)
            Spacer(Modifier.width(8.dp))
            ProfileAvatarButton(name = profileName, onClick = { nav.navigate("settings") })
        }
        TextButton(onClick = { nav.navigate("lumi") }) {
            Text("Chat with Lumi  →", color = Lavender, fontSize = 13.sp)
        }

        CardBox {
            val focusTarget = state.occurrences.firstOrNull {
                it.task.type == TaskType.DEEP_WORK && it.endMillis > now && !state.isCompleted(it)
            } ?: state.occurrences.firstOrNull {
                it.task.type == TaskType.DEEP_WORK && !state.isCompleted(it)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("DAILY RHYTHM", color = Lavender, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.1.sp)
                    Spacer(Modifier.height(5.dp))
                    Text("Your day, your pace.", color = Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        if (state.summary.total == 0) "Plan a few rituals to give your day a gentle rhythm."
                        else "${state.summary.completed} of ${state.summary.total} tasks complete · ${state.summary.percentLabel}% of today's plan.",
                        color = Muted, fontSize = 12.sp
                    )
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    CircularProgressIndicator(progress = { state.summary.fraction }, modifier = Modifier.fillMaxSize(), color = Lavender, trackColor = Raised, strokeWidth = 4.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("${state.summary.percentLabel}%", color = Text, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${state.summary.completed} of ${state.summary.total}", color = Muted, fontSize = 8.sp)
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = { nav.navigate("focus?taskId=${focusTarget?.task?.id ?: -1L}") },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Lavender, contentColor = Ink
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (focusTarget != null) "Start Focus • ${focusTarget.task.title}" else "Enter Focus Sanctuary")
            }
        }

        LumiCompanion(
            completed = state.summary.completed,
            total = state.summary.total,
            streak = state.streakDays,
            onVisit = { nav.navigate("sanctuary") }
        )

        val quickActions = state.occurrences.filter { it.task.type == TaskType.QUICK_ACTION }
        if (quickActions.isNotEmpty()) {
            CardBox {
                SectionTitle("Fast rituals", "${quickActions.size} saved")
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    quickActions.forEach { occurrence ->
                        CardBox(Modifier.width(220.dp).clickable { nav.navigate("actions") }) {
                            Tag("${WeekUtils.durationLabel(occurrence.durationMinutes)} · ${if (state.isCompleted(occurrence)) "Done today" else "Quick action"}", if (state.isCompleted(occurrence)) Mint else Lavender)
                            Spacer(Modifier.height(7.dp))
                            Text(occurrence.task.title, color = Text, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                            Text(occurrence.task.destinationUrl ?: "No destination configured", color = Muted, fontSize = 10.sp, maxLines = 1)
                        }
                    }
                }
                TextButton(onClick = { nav.navigate("actions") }) { Text("Open action hub", color = Lavender) }
            }
        }

        val freeWindows = buildList {
            var cursor = 8 * 60
            state.occurrences.forEach { item ->
                val start = item.startMinuteOfDay.coerceIn(8 * 60, 22 * 60)
                val end = (item.startMinuteOfDay + item.durationMinutes).coerceIn(8 * 60, 22 * 60)
                if (start - cursor >= 30) add(cursor to start)
                cursor = maxOf(cursor, end)
            }
            if (22 * 60 - cursor >= 30) add(cursor to 22 * 60)
        }
        if (freeWindows.isNotEmpty()) CardBox {
            SectionTitle("Open windows", "8:00–22:00")
            Text("Room to breathe between planned rituals", color = Muted, fontSize = 11.sp)
            freeWindows.take(3).forEach { (start, end) ->
                Row(Modifier.fillMaxWidth().padding(top = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                    Tag("${displayTime(start)} – ${displayTime(end)}", Blue)
                    Spacer(Modifier.width(8.dp)); Text("${WeekUtils.durationLabel(end - start)} free", color = Muted, fontSize = 11.sp)
                }
            }
        }

        CardBox {
            SectionTitle("Chronological Schedule", "${state.summary.total} planned")
            state.overlapLabels.forEach { label ->
                Spacer(Modifier.height(4.dp))
                Tag("⚠ Overlap • $label", Danger)
            }
            Spacer(Modifier.height(6.dp))
            if (state.occurrences.isEmpty()) {
                Text("No rituals planned for this day.", color = Muted, fontSize = 12.sp)
                Spacer(Modifier.height(6.dp))
            }
            var nowTagPlaced = false
            state.occurrences.forEach { occurrence ->
                if (occurrence.startMillis > now && !nowTagPlaced) {
                    nowTagPlaced = true
                    Spacer(Modifier.height(8.dp))
                    Tag("NOW • ${displayTime(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), java.time.ZoneId.systemDefault()).toLocalTime().toSecondOfDay() / 60)}", Lavender)
                    Spacer(Modifier.height(8.dp))
                }
                TimelineTaskRow(
                    occurrence = occurrence,
                    checked = state.isCompleted(occurrence),
                    onClick = { sheetOccurrence = occurrence },
                    onToggle = { vm.toggleCompletion(occurrence) },
                    nowMillis = now,
                    isLast = occurrence == state.occurrences.lastOrNull()
                )
            }
            if (!nowTagPlaced) {
                Spacer(Modifier.height(8.dp))
                Tag("NOW • ${displayTime(java.time.LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), java.time.ZoneId.systemDefault()).toLocalTime().toSecondOfDay() / 60)}", Lavender)
            }
            AddRow("Plan a ritual for this day") {
                nav.openEditor(dateEpochDay = state.date.toEpochDay())
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
