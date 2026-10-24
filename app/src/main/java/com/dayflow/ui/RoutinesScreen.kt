package com.dayflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.dayflow.DayflowApplication
import com.dayflow.data.local.RoutineEntity
import com.dayflow.domain.Task
import java.time.LocalDate
import kotlinx.coroutines.launch

@Composable
internal fun RoutinesScreen(nav: NavHostController) {
    val app = LocalContext.current.applicationContext as DayflowApplication
    val routines by app.routines.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by app.repository.observeTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    var showSave by remember { mutableStateOf(false) }
    var chosen by remember { mutableStateOf<Task?>(null) }
    var routineName by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(LocalDate.now()) }
    var conflictRoutine by remember { mutableStateOf<RoutineEntity?>(null) }
    var conflictMessages by remember { mutableStateOf<List<String>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    Page("Routines", "Reusable blocks for a calmer day") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = Muted) }
            Text("Build a routine from a task you already use.", color = Muted, fontSize = 12.sp)
        }
        CardBox {
            SectionTitle("Add a routine block")
            Text("Choose an existing task as a reusable template. Applying it makes a dated copy and keeps the routine intact.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            if (tasks.isEmpty()) Text("Create a task first, then save it here as a routine.", color = Muted, fontSize = 12.sp)
            else Button(onClick = { chosen = tasks.firstOrNull(); routineName = chosen?.title.orEmpty(); showSave = true }, colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink)) { Text("Save a task as routine") }
        }
        if (routines.isEmpty()) CardBox { Text("No routine blocks saved", color = Text, fontWeight = FontWeight.SemiBold); Text("Routines keep your repeatable schedule pieces close at hand.", color = Muted, fontSize = 12.sp) }
        routines.forEach { routine -> RoutineCard(routine, date, onApply = { scope.launch {
            val result = app.routines.apply(routine, date.toEpochDay())
            if (result.conflicts.isNotEmpty()) { conflictRoutine = routine; conflictMessages = result.conflicts }
            else if (result.alreadyPresent) message = "${routine.title} is already on this day."
        } }, onDelete = { scope.launch { app.routines.delete(routine.id) } }) }
        message?.let { Text(it, color = Mint, fontSize = 12.sp) }
        CardBox {
            SectionTitle("Apply to a day")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(date.toString(), color = Text, fontWeight = FontWeight.Medium)
                Row { TextButton(onClick = { date = date.minusDays(1) }) { Text("−1 day", color = Lavender) }; TextButton(onClick = { date = date.plusDays(1) }) { Text("+1 day", color = Lavender) } }
            }
        }
    }
    if (showSave) AlertDialog(
        onDismissRequest = { showSave = false }, containerColor = Surface,
        title = { Text("Save routine block", color = Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(routineName, { routineName = it }, label = { Text("Routine name") }, singleLine = true)
                tasks.forEach { task ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = chosen?.id == task.id, onClick = { chosen = task })
                        Column { Text(task.title, color = Text); Text("${task.type.label} · ${task.durationMinutes} min", color = Muted, fontSize = 11.sp) }
                    }
                }
            }
        },
        confirmButton = { TextButton(enabled = chosen != null && routineName.isNotBlank(), onClick = { val task = chosen; if (task != null) scope.launch { app.routines.save(routineName, task); showSave = false } }) { Text("Save routine", color = Lavender) } },
        dismissButton = { TextButton(onClick = { showSave = false }) { Text("Cancel", color = Muted) } }
    )
    conflictRoutine?.let { routine -> AlertDialog(
        onDismissRequest = { conflictRoutine = null }, containerColor = Surface,
        title = { Text("This overlaps another plan", color = Text) },
        text = { Text("${conflictMessages.joinToString("\n")}\n\nAdd ${routine.title} anyway?", color = Muted) },
        confirmButton = { TextButton(onClick = { scope.launch { app.routines.apply(routine, date.toEpochDay(), confirmedConflicts = true); conflictRoutine = null } }) { Text("Add anyway", color = Lavender) } },
        dismissButton = { TextButton(onClick = { conflictRoutine = null }) { Text("Choose another time", color = Muted) } }
    ) }
}

@Composable
private fun RoutineCard(routine: RoutineEntity, date: LocalDate, onApply: () -> Unit, onDelete: () -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(routine.name, color = Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                Text("${routine.title} · ${routine.durationMinutes} min · ${routine.type.lowercase().replace('_', ' ')}", color = Muted, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete routine", tint = Muted) }
        }
        Button(onClick = onApply, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Raised, contentColor = Lavender)) { Text("Add to ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}") }
    }
}
