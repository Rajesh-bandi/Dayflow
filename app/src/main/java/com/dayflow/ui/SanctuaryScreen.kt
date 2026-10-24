package com.dayflow.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.dayflow.DayflowApplication
import com.dayflow.domain.RecurrenceEngine
import com.dayflow.domain.ScheduleStats
import java.time.LocalDate

@Composable
internal fun SanctuaryScreen(nav: NavHostController) {
    val app = LocalContext.current.applicationContext as DayflowApplication
    val tasks by app.repository.observeTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val completions by app.repository.observeAllCompletions().collectAsStateWithLifecycle(initialValue = emptyList())
    val focusSessions by app.focusSessions.observeAllHistory().collectAsStateWithLifecycle(initialValue = emptyList())
    val today = LocalDate.now()
    val summary = ScheduleStats.summarize(RecurrenceEngine.occurrencesOn(tasks, today), completions)
    val dates = completions.filter { it.completed }.map { LocalDate.ofEpochDay(it.dateEpochDay) }.toSet()
    val streak = ScheduleStats.streakDays(dates, today)
    val completedCount = completions.count { it.completed || it.status == "COMPLETED" }
    val completedFocusCount = focusSessions.count { it.status == com.dayflow.data.FocusStatus.COMPLETED.name }
    val totalFocusMinutes = focusSessions
        .filter { it.status == com.dayflow.data.FocusStatus.COMPLETED.name }
        .sumOf { ((it.endedAtMillis ?: it.expectedEndAtMillis) - it.startedAtMillis).coerceAtLeast(0L) / 60_000L }

    val totalAccomplished = completedCount + completedFocusCount
    val level = (1 + totalAccomplished / 10).coerceAtMost(20)
    val xp = totalAccomplished % 10
    val stage = when { totalAccomplished < 10 -> "New sprout"; totalAccomplished < 120 -> "Growing light"; else -> "Flourishing" }
    Page("${LocalAgentName.current}’s sanctuary", "A companion for your own pace") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = Muted) }
            Text("Your rhythm shapes ${LocalAgentName.current}’s growth", color = Muted, fontSize = 12.sp)
        }
        CardBox {
            val companionMint = Mint
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Canvas(Modifier.size(190.dp)) {
                    val scale = size.minDimension / 180f
                    drawCircle(companionMint.copy(alpha = .12f), size.minDimension * .48f, center)
                    drawOval(Color(0xFFA78BFA), topLeft = androidx.compose.ui.geometry.Offset(size.width*.19f,size.height*.04f), size = androidx.compose.ui.geometry.Size(size.width*.2f,size.height*.45f))
                    drawOval(Color(0xFFA78BFA), topLeft = androidx.compose.ui.geometry.Offset(size.width*.61f,size.height*.04f), size = androidx.compose.ui.geometry.Size(size.width*.2f,size.height*.45f))
                    drawCircle(Color(0xFFA78BFA), 55*scale, androidx.compose.ui.geometry.Offset(size.width/2,size.height*.52f))
                    drawOval(Color(0xFFEDE9FE), topLeft = androidx.compose.ui.geometry.Offset(size.width*.36f,size.height*.56f), size = androidx.compose.ui.geometry.Size(size.width*.28f,size.height*.24f))
                    drawCircle(Color(0xFF251646), 5*scale, androidx.compose.ui.geometry.Offset(size.width*.42f,size.height*.48f))
                    drawCircle(Color(0xFF251646), 5*scale, androidx.compose.ui.geometry.Offset(size.width*.58f,size.height*.48f))
                    drawCircle(companionMint, 6*scale, androidx.compose.ui.geometry.Offset(size.width*.5f,size.height*.12f))
                }
            }
            Text(LocalAgentName.current, color = Text, fontSize = 25.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Text("Level $level · $stage", color = Lavender, fontSize = 13.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
            Spacer(Modifier.height(12.dp))
            SectionTitle("Next little milestone", "$xp / 10 rituals & focus hours")
            LinearProgressIndicator(progress = { xp / 10f }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp), color = Mint, trackColor = Raised)
        }
        CardBox {
            SectionTitle("Life stages", "Based on rituals & focus sessions")
            listOf("New sprout" to "1–9 milestones", "Growing light" to "10–119 milestones", "Flourishing" to "120+ milestones").forEachIndexed { index, (title, detail) ->
                val active = when (index) { 0 -> totalAccomplished < 10; 1 -> totalAccomplished in 10 until 120; else -> totalAccomplished >= 120 }
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(9.dp).background(if (active) Mint else Raised, CircleShape))
                    Spacer(Modifier.width(10.dp)); Column { Text(title, color = if (active) Mint else Text, fontWeight = FontWeight.Medium); Text(detail, color = Muted, fontSize = 11.sp) }
                }
            }
        }
        CardBox {
            SectionTitle("Deep Focus Sanctuary")
            val hoursFocused = totalFocusMinutes / 60
            val minsFocused = totalFocusMinutes % 60
            val focusTimeText = if (hoursFocused > 0) "${hoursFocused}h ${minsFocused}m" else "${minsFocused}m"
            Text("$completedFocusCount completed session${if (completedFocusCount == 1) "" else "s"} · $focusTimeText total focus immersion", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = { nav.navigate("focus") { launchSingleTop = true } },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Mint)
            ) {
                Text("Enter Deep Focus", fontSize = 12.sp)
            }
        }
        CardBox {
            SectionTitle("Today’s rhythm")
            Text("${summary.completed} of ${summary.total} planned rituals completed · $streak day${if (streak == 1) "" else "s"} in rhythm", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(8.dp))
            Text("${LocalAgentName.current}’s mood follows your progress, but rest days don’t erase growth. This companion is a gentle reflection, not a score.", color = Text, fontSize = 12.sp)
        }
    }
}
