package com.dayflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.dayflow.DayflowApplication
import kotlinx.coroutines.launch

@Composable
internal fun ArchivedTasksScreen(nav: NavHostController) {
    val repo = (LocalContext.current.applicationContext as DayflowApplication).repository
    val tasks by repo.observeArchivedTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    Page("Archived tasks", "Your plans remain recoverable") {
        IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = Muted) }
        if (tasks.isEmpty()) CardBox { Text("Nothing archived", color = Text); Text("Archived tasks will appear here until you restore or delete them.", color = Muted, fontSize = 12.sp) }
        tasks.forEach { task ->
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(task.title, color = Text)
                        Text("${task.type.label} · ${task.recurrenceDescription()}", color = Muted, fontSize = 11.sp)
                    }
                    IconButton(onClick = { scope.launch { repo.restoreTask(task.id) } }) { Icon(Icons.Default.Restore, "Restore ${task.title}", tint = Mint) }
                }
            }
        }
    }
}
