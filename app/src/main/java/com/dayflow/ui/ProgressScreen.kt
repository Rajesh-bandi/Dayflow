package com.dayflow.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.dayflow.domain.TaskType
import com.dayflow.domain.WeekUtils
import com.dayflow.DayflowApplication
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun ProgressScreen(nav: NavHostController) {
    val repo = rememberRepository()
    val app = LocalContext.current.applicationContext as DayflowApplication
    val vm: ProgressViewModel = viewModel(factory = viewModelFactory { initializer { ProgressViewModel(repo, app.focusSessions, app.preferences) } })
    val state by vm.uiState.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()

    Page("Today", "Consistency", null) {
        Text("Mindful review", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .8.sp)
        Text("Consistency", color = Text, fontSize = 21.sp, fontWeight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(ProgressPeriod.WEEK, ProgressPeriod.MONTH, ProgressPeriod.QUARTER).forEach { option ->
                FilterChip(
                    selected = period == option,
                    onClick = { vm.selectPeriod(option) },
                    label = { Text(when (option) { ProgressPeriod.WEEK -> "Week"; ProgressPeriod.MONTH -> "Month"; else -> "Quarter" }, fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Lavender.copy(alpha = .2f),
                        selectedLabelColor = Lavender
                    )
                )
            }
            FilterChip(selected = period == ProgressPeriod.CUSTOM, onClick = { vm.selectPeriod(ProgressPeriod.CUSTOM) }, label = { Text("Custom", fontSize = 11.sp) })
        }
        if (period == ProgressPeriod.CUSTOM) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf(7, 30, 90, 365).forEach { days ->
                    FilterChip(selected = state.customDays == days, onClick = { vm.selectCustomDays(days) }, label = { Text("$days days", fontSize = 10.sp) })
                }
            }
        }

        if (!state.hasData) {
            CardBox {
                Text("No records yet", color = Text, fontWeight = FontWeight.SemiBold)
                Text("Complete a ritual on Today or Week and your consistency stats will appear here.", color = Muted, fontSize = 12.sp)
            }
        } else {
            CardBox {
                Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("HARMONY INDEX", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = .6.sp)
                        Row(verticalAlignment = androidx.compose.ui.Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("${state.summary.percentLabel}%", fontSize = 32.sp, color = Text, fontWeight = FontWeight.SemiBold)
                            val delta = state.summary.percentLabel - state.previousSummary.percentLabel
                            Tag("${if (delta >= 0) "+" else ""}$delta% vs last period", if (delta >= 0) Mint else Amber)
                        }
                    }
                    Box(Modifier.size(44.dp), contentAlignment = androidx.compose.ui.Alignment.Center) {
                        CircularProgressIndicator(progress = { state.summary.fraction }, modifier = Modifier.fillMaxSize(), color = Lavender, trackColor = Raised, strokeWidth = 4.dp)
                        Text("✿", color = Mint, fontSize = 17.sp)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(state.rangeLabel, color = Muted, fontSize = 10.sp)
            }

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("${state.summary.completed} / ${state.summary.total}", "Completed tasks")
                    StatCard(WeekUtils.durationLabel(state.deepFocusMinutes), "Deep focus")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("${state.streakDays} ${if (state.streakDays == 1) "day" else "days"}", "Active streak")
                    StatCard(if (state.habitLabel == "—") "—" else "${state.habitAdherence.times(100).toInt()}%", "Habit adherence")
                }
            }

            CardBox {
                SectionTitle("Consistency Heatmap", "Past 4 Wks")
                Text("Daily rhythm and intentional hours", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    listOf("M", "T", "W", "T", "F", "S", "S").forEach { Text(it, color = Muted, fontSize = 9.sp, modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                }
                Spacer(Modifier.height(5.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.heatDays.chunked(7).forEach { week ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            week.forEach { day ->
                                val cellColor = when {
                                    day.total == 0 -> Raised
                                    day.fraction >= 1f -> Mint
                                    else -> Lavender.copy(alpha = .35f + .6f * day.fraction)
                                }
                                Box(Modifier.weight(1f).padding(horizontal = 2.dp).aspectRatio(1f).clip(RoundedCornerShape(5.dp)).background(cellColor))
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Tag("Today · ${state.todayLabel} · Peak balance ${state.bestDayLabel}", Mint)
                Spacer(Modifier.height(6.dp))
                Text("Rest / recovery", color = Muted, fontSize = 9.sp)
            }

            if (state.typeStats.isNotEmpty()) {
                CardBox {
                    SectionTitle("Time allocation")
                    Text("Planned intentions vs completed schedule", color = Muted, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    state.typeStats.forEach { stat ->
                        val fraction = if (stat.totalMinutes == 0) 0f else stat.completedMinutes.toFloat() / stat.totalMinutes
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(stat.type.label, color = Text, fontSize = 12.sp)
                            Text(
                                "${WeekUtils.durationLabel(stat.completedMinutes)} / ${WeekUtils.durationLabel(stat.totalMinutes)} (${(fraction * 100).toInt()}%)",
                                color = Muted, fontSize = 11.sp
                            )
                        }
                        LinearProgressIndicator(
                            progress = { fraction },
                            modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
                            color = accentFor(stat.type), trackColor = Raised
                        )
                    }
                }
            }

            CardBox {
                SectionTitle("Gentle observations", "Calm analytics")
                val delta = state.summary.percentLabel - state.previousSummary.percentLabel
                Text(
                    when {
                        state.summary.total == 0 -> "There are no scheduled rituals in this period yet. Add a small anchor to start seeing your rhythm."
                        delta > 0 -> "You completed ${state.summary.percentLabel}% of this period’s plan, up $delta points from the previous period. Keep the pace sustainable."
                        delta < 0 -> "This period is ${-delta} points below the previous one. A lighter plan can make room for recovery."
                        else -> "Your completion stayed steady at ${state.summary.percentLabel}% compared with the previous period."
                    },
                    color = Muted, fontSize = 12.sp
                )
            }

            if (state.habits.isNotEmpty()) {
                CardBox {
                    SectionTitle("Habit Anchors", "${state.habits.size} Active")
                    state.habits.forEach { habit ->
                        TaskRow(
                            title = habit.task.title,
                            meta = "${habit.task.recurrenceDescription()} • ${WeekUtils.durationLabel(habit.task.durationMinutes)} • ${habit.completedCount} completed",
                            time = "",
                            accent = Lavender
                        )
                    }
                    Text("Consistent progress over exhaustion.", color = Text, fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun RowScope.StatCard(value: String, label: String) {
    CardBox(Modifier.weight(1f)) {
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Text)
        Text(label, fontSize = 9.sp, color = Muted)
    }
}
