package com.dayflow.ui

import android.content.Intent
import android.content.ActivityNotFoundException
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dayflow.domain.Priority
import com.dayflow.domain.RecurrenceFrequency
import com.dayflow.domain.TaskField
import com.dayflow.domain.TaskType
import com.dayflow.domain.TaskValidator
import com.dayflow.domain.WeekUtils
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun EditorScreen(
    nav: NavHostController,
    taskId: Long = -1L,
    dateEpochDay: Long = -1L,
    presetTitle: String = "",
    presetType: String = "",
    presetMinutes: Int = -1,
    editOccurrenceOfTaskId: Long = -1L
) {
    val repo = rememberRepository()
    val args = remember(taskId, dateEpochDay, presetTitle, presetType, presetMinutes, editOccurrenceOfTaskId) {
        com.dayflow.ui.EditorArgs(taskId, dateEpochDay, presetTitle, presetType, presetMinutes, editOccurrenceOfTaskId)
    }
    val vm: EditorViewModel = viewModel(key = "editor-$taskId-$editOccurrenceOfTaskId", factory = viewModelFactory {
        initializer { EditorViewModel(repo, args) }
    })
    val state by vm.state.collectAsStateWithLifecycle()
    val draft = state.draft
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var linkMessage by remember { mutableStateOf<String?>(null) }
    val lifecycleOwner = LocalLifecycleOwner.current

    // One-shot navigation events (save/delete).
    LaunchedEffect(vm, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            vm.events.collect { event ->
                when (event) {
                    EditorEvent.Saved, EditorEvent.Deleted ->
                        if (!nav.popBackStack()) nav.navigate("today") { launchSingleTop = true }
                }
            }
        }
    }

    Page(
        if (editOccurrenceOfTaskId > 0) "Edit occurrence" else "New Task Flow",
        if (editOccurrenceOfTaskId > 0) "Only this date changes" else if (draft.isNew) "New Ritual" else "Edit Ritual",
        "Draft • ${draft.recurrenceDescription}",
        footer = {
            Button(
                onClick = { vm.save() },
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink, disabledContainerColor = Raised, disabledContentColor = Muted)
            ) { Icon(Icons.Default.CheckCircle, null); Text(if (draft.isNew) "Save Ritual" else "Update Ritual") }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!draft.isNew) TextButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.Delete, null, tint = Danger); Text("Delete", color = Danger)
                }
                TextButton(onClick = { if (!nav.popBackStack()) nav.navigate("today") { launchSingleTop = true } }, modifier = Modifier.weight(1f)) { Text("Discard", color = Muted) }
            }
        }
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(if (draft.isNew) "New Ritual" else "Edit Ritual", color = Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("●  Draft · ${draft.recurrenceDescription}", color = Mint, fontSize = 11.sp)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(onClick = { if (!nav.popBackStack()) nav.navigate("today") { launchSingleTop = true } }) {
                    Text("×", color = Muted, fontSize = 24.sp)
                }
            }
        }
        // Name + intent
        CardBox {
            ErrorText(state.errors[TaskField.TITLE])
            Text("Ritual Name", fontSize = 12.sp, color = Muted)
            OutlinedTextField(
                draft.title, vm::onTitle,
                modifier = Modifier.fillMaxWidth(), singleLine = true,
                colors = editorFieldColors(), isError = state.errors.containsKey(TaskField.TITLE)
            )
            Spacer(Modifier.height(12.dp))
            Text("Intent & Micro-goals", fontSize = 12.sp, color = Muted)
            OutlinedTextField(
                draft.description, vm::onDescription,
                modifier = Modifier.fillMaxWidth(), minLines = 2,
                colors = editorFieldColors()
            )
            Text("Markdown supported", fontSize = 10.sp, color = Muted)
        }

        // Archetype
        CardBox {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Ritual Archetype", color = Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("Tap to morph canvas", color = Lavender, fontSize = 10.sp)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                TaskType.entries.forEach { type ->
                    val selected = draft.type == type
                    Column(
                        Modifier.weight(1f).heightIn(min = 60.dp).clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Lavender else Raised)
                            .clickable { vm.onType(type) }.padding(horizontal = 2.dp, vertical = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(type.editorGlyph(), color = if (selected) Ink else Muted, fontSize = 15.sp)
                        Text(type.editorLabel(), color = if (selected) Ink else Text, fontSize = 9.sp, maxLines = 1)
                    }
                }
            }
        }

        // Target dispatch
        if (draft.type == TaskType.QUICK_ACTION) {
            CardBox {
                SectionTitle("Target Dispatch")
                Text("Destination URL / Deep Link", color = Muted, fontSize = 12.sp)
                ErrorText(state.errors[TaskField.URL])
                OutlinedTextField(
                    draft.destinationUrl, vm::onUrl,
                    modifier = Modifier.fillMaxWidth(), singleLine = true,
                    colors = editorFieldColors(), isError = state.errors.containsKey(TaskField.URL)
                )
                TextButton(onClick = {
                    val url = draft.destinationUrl.trim()
                    val uri = runCatching { android.net.Uri.parse(url) }.getOrNull()
                    if (uri == null || uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) {
                        linkMessage = "Enter a valid HTTPS or HTTP address first."
                    } else try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                    } catch (_: ActivityNotFoundException) {
                        linkMessage = "No installed app can open this link."
                    } catch (_: SecurityException) {
                        linkMessage = "Android did not allow this link to open."
                    }
                }) { Icon(Icons.Default.OpenInNew, null); Text("Test Link") }
            }
        }

        // Priority
        CardBox {
            SectionTitle("Priority")
            Text("Highlights this ritual in your lists", color = Muted, fontSize = 11.sp)
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(Priority.entries.size) { index ->
                    val priority = Priority.entries[index]
                    val label = priority.name.lowercase().replaceFirstChar { it.uppercase() }
                    FilterChip(
                        selected = draft.priority == priority, onClick = { vm.onPriority(priority) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender.copy(alpha = .2f), selectedLabelColor = Lavender
                        )
                    )
                }
            }
        }

        // Timing & cadence
        CardBox {
            SectionTitle("Timing & Cadence", draft.recurrenceDescription)
            Text("Repeats", color = Muted, fontSize = 11.sp)
            ErrorText(state.errors[TaskField.RECURRENCE_DAYS])
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val options = listOf(
                    RecurrenceFrequency.NONE to "One-time",
                    RecurrenceFrequency.DAILY to "Daily",
                    RecurrenceFrequency.WEEKDAYS to "Weekdays",
                    RecurrenceFrequency.WEEKENDS to "Weekends",
                    RecurrenceFrequency.SELECTED_DAYS to "Custom"
                )
                items(options.size) { index ->
                    val (frequency, label) = options[index]
                    FilterChip(
                        selected = draft.frequency == frequency, onClick = { vm.onFrequency(frequency) },
                        label = { Text(label, fontSize = 11.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Lavender.copy(alpha = .2f), selectedLabelColor = Lavender
                        )
                    )
                }
            }
            if (draft.frequency == RecurrenceFrequency.SELECTED_DAYS) {
                Spacer(Modifier.height(8.dp))
                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(DayOfWeek.entries.size) { index ->
                        val day = DayOfWeek.entries[index]
                        FilterChip(
                            selected = day in draft.daysOfWeek, onClick = { vm.onToggleDay(day) },
                            label = { Text(TaskValidator.dayOfWeekLabel(day), fontSize = 10.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Lavender.copy(alpha = .2f), selectedLabelColor = Lavender
                            )
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))
            ErrorText(state.errors[TaskField.START_DATE])
            ErrorText(state.errors[TaskField.START_TIME])
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Start Date", color = Muted, fontSize = 11.sp)
                    Button(
                        onClick = { showDatePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Text)
                    ) { Text(WeekUtils.dayTitle(draft.startDate)) }
                }
                Column(Modifier.weight(1f)) {
                    Text("Start Time", color = Muted, fontSize = 11.sp)
                    Button(
                        onClick = { showTimePicker = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Ink, contentColor = Text)
                    ) { Text(displayTime(draft.startTimeMinutes)) }
                }
            }
            Spacer(Modifier.height(10.dp))
            ErrorText(state.errors[TaskField.DURATION])
            Text("Duration", color = Muted, fontSize = 11.sp)
            OutlinedTextField(
                value = draft.durationMinutes.toString(),
                onValueChange = { text ->
                    text.filter { it.isDigit() }.toIntOrNull()?.let { vm.onDuration(it) }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                suffix = { Text("min") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = editorFieldColors(),
                isError = state.errors.containsKey(TaskField.DURATION)
            )
        }

        if (state.overlapConflicts.isNotEmpty()) {
            CardBox {
                SectionTitle("Schedule Conflict")
                state.overlapConflicts.forEach { Text("⚠ $it", color = Danger, fontSize = 12.sp) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { vm.save(confirmedOverlaps = true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink)
                    ) { Text("Save Anyway") }
                    OutlinedButton(onClick = { vm.dismissOverlaps() }, colors = ButtonDefaults.outlinedButtonColors(contentColor = Muted)) { Text("Cancel") }
                }
            }
        }

    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = draft.startDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        vm.onDate(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    showDatePicker = false
                }) { Text("OK", color = Lavender) }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel", color = Muted) } },
            colors = DatePickerDefaults.colors(containerColor = Surface)
        ) { DatePicker(state = datePickerState, colors = DatePickerDefaults.colors(containerColor = Surface, selectedDayContainerColor = Lavender, todayDateBorderColor = Raised)) }
    }

    if (showTimePicker) {
        val hour24 = draft.startTimeMinutes / 60
        var hourText by remember(showTimePicker, draft.startTimeMinutes) { mutableStateOf(((hour24 + 11) % 12 + 1).toString()) }
        var minuteText by remember(showTimePicker, draft.startTimeMinutes) { mutableStateOf((draft.startTimeMinutes % 60).toString().padStart(2, '0')) }
        var isPm by remember(showTimePicker, draft.startTimeMinutes) { mutableStateOf(hour24 >= 12) }
        var timeError by remember(showTimePicker) { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            containerColor = Surface,
            title = { Text("Start Time", color = Text) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Choose a 12-hour time", color = Muted, fontSize = 12.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = hourText,
                            onValueChange = { hourText = it.filter(Char::isDigit).take(2); timeError = null },
                            label = { Text("Hour") },
                            modifier = Modifier.width(112.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next)
                        )
                        Text(":", color = Text, fontSize = 24.sp)
                        OutlinedTextField(
                            value = minuteText,
                            onValueChange = { minuteText = it.filter(Char::isDigit).take(2); timeError = null },
                            label = { Text("Minute") },
                            modifier = Modifier.width(112.dp),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done)
                        )
                    }
                    SingleChoiceSegmentedButtonRow {
                        listOf(false to "AM", true to "PM").forEachIndexed { index, (pm, label) ->
                            SegmentedButton(selected = isPm == pm, onClick = { isPm = pm }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
                        }
                    }
                    timeError?.let { Text(it, color = Danger, fontSize = 12.sp) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val hour = hourText.toIntOrNull()
                    val minute = minuteText.toIntOrNull()
                    if (hour == null || hour !in 1..12 || minute == null || minute !in 0..59) timeError = "Enter an hour from 1–12 and minutes from 00–59."
                    else {
                        val hourOfDay = (hour!! % 12) + if (isPm) 12 else 0
                        vm.onStartTime(hourOfDay * 60 + minute!!)
                        showTimePicker = false
                    }
                }) { Text("OK", color = Lavender) }
            },
            dismissButton = { TextButton(onClick = { showTimePicker = false }) { Text("Cancel", color = Muted) } }
        )
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = Surface,
            title = { Text("Delete this ritual?", color = Text) },
            text = { Text("This removes the task and its completion history.", color = Muted, fontSize = 13.sp) },
            confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete", color = Danger) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep", color = Muted) } }
        )
    }

    linkMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { linkMessage = null },
            title = { Text("Can't open link", color = Text) },
            text = { Text(message, color = Muted) },
            confirmButton = { TextButton(onClick = { linkMessage = null }) { Text("OK", color = Lavender) } },
            containerColor = Surface
        )
    }
}

private fun TaskType.editorLabel(): String = when (this) {
    TaskType.REGULAR -> "Regular"
    TaskType.DEEP_WORK -> "Deep"
    TaskType.QUICK_ACTION -> "Action"
    TaskType.HABIT -> "Habit"
    TaskType.BREAK -> "Break"
}

private fun TaskType.editorGlyph(): String = when (this) {
    TaskType.REGULAR -> "✓"
    TaskType.DEEP_WORK -> "♙"
    TaskType.QUICK_ACTION -> "↗"
    TaskType.HABIT -> "♨"
    TaskType.BREAK -> "♧"
}

@Composable
private fun ErrorText(message: String?) {
    if (message != null) {
        Text(message, color = Danger, fontSize = 11.sp)
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun editorFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = Text,
    unfocusedTextColor = Text,
    focusedBorderColor = Lavender,
    unfocusedBorderColor = Edge,
    focusedContainerColor = Ink,
    unfocusedContainerColor = Ink
)
