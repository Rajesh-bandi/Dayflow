package com.dayflow.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dayflow.domain.TaskType
import com.dayflow.domain.WeekUtils
import com.dayflow.R

internal data class DayflowPalette(val ink: Color, val surface: Color, val raised: Color, val edge: Color, val text: Color, val muted: Color, val lavender: Color, val mint: Color, val blue: Color, val amber: Color, val danger: Color)
internal val DarkDayflowPalette = DayflowPalette(Color(0xFF121316), Color(0xFF191B20), Color(0xFF22252C), Color(0xFF272A33), Color(0xFFF4F4F6), Color(0xFF999DA8), Color(0xFFA78BFA), Color(0xFF73E2B3), Color(0xFF82B7FF), Color(0xFFF6BE68), Color(0xFFFF7777))
internal val LightDayflowPalette = DayflowPalette(Color(0xFFF4F2F8), Color(0xFFFFFFFF), Color(0xFFE9E5F0), Color(0xFFD5D0DE), Color(0xFF24212B), Color(0xFF6F6B78), Color(0xFF6941C6), Color(0xFF087952), Color(0xFF2D67A5), Color(0xFF9A6200), Color(0xFFB42318))
internal val LocalDayflowPalette = staticCompositionLocalOf { DarkDayflowPalette }
internal val Local24HourTime = staticCompositionLocalOf { true }

/** The assistant's chosen name, so every screen says it rather than a hard-coded "Lumi". */
internal val LocalAgentName = staticCompositionLocalOf { com.dayflow.data.DEFAULT_AGENT_NAME }
@Composable internal fun displayTime(minutes: Int): String = WeekUtils.timeLabel(minutes, Local24HourTime.current)
internal val Ink: Color @Composable get() = LocalDayflowPalette.current.ink

/**
 * The profile entry point, shown on the home screen: the user's initial once a name is saved, otherwise
 * a neutral person mark. It opens Settings, which holds the profile, the assistant's identity and voice.
 */
@Composable internal fun ProfileAvatarButton(name: String?, onClick: () -> Unit) {
    val initial = name?.trim()?.takeIf(String::isNotEmpty)?.first()?.uppercase()
    Box(
        Modifier.size(34.dp).clip(CircleShape).background(Raised)
            .clickable(onClickLabel = "Profile and settings", onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (initial == null) Icon(Icons.Default.Person, null, tint = Lavender, modifier = Modifier.size(18.dp))
        else Text(initial.toString(), color = Lavender, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
    }
}
internal val Surface: Color @Composable get() = LocalDayflowPalette.current.surface
internal val Raised: Color @Composable get() = LocalDayflowPalette.current.raised
internal val Edge: Color @Composable get() = LocalDayflowPalette.current.edge
internal val Text: Color @Composable get() = LocalDayflowPalette.current.text
internal val Muted: Color @Composable get() = LocalDayflowPalette.current.muted
internal val Lavender: Color @Composable get() = LocalDayflowPalette.current.lavender
internal val Mint: Color @Composable get() = LocalDayflowPalette.current.mint
internal val Blue: Color @Composable get() = LocalDayflowPalette.current.blue
internal val Amber: Color @Composable get() = LocalDayflowPalette.current.amber
internal val Danger: Color @Composable get() = LocalDayflowPalette.current.danger

@Composable
internal fun accentFor(type: TaskType): Color = when (type) {
    TaskType.DEEP_WORK -> Lavender
    TaskType.HABIT -> Mint
    TaskType.QUICK_ACTION -> Amber
    TaskType.BREAK -> Mint
    TaskType.REGULAR -> Blue
}

@Composable
internal fun Page(
    title: String,
    subtitle: String? = null,
    trailing: String? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(Modifier.fillMaxSize().background(Ink)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Image(painterResource(R.drawable.dayflow_logo), contentDescription = "Dayflow", modifier = Modifier.size(24.dp))
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text("Dayflow", fontSize = 9.sp, letterSpacing = .8.sp, color = Muted, fontWeight = FontWeight.SemiBold)
                Text(title, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = Text)
            }
            if (trailing != null) {
                Text(trailing, color = Lavender, fontSize = 13.sp)
            }
        }
        LazyColumn(
            Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 0.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
        }
        footer?.let {
            Column(
                Modifier.fillMaxWidth().background(Ink).padding(horizontal = 14.dp, vertical = 8.dp),
                content = it
            )
        }
    }
}

@Composable
internal fun CardBox(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(Surface)
            .border(1.dp, Edge.copy(alpha = .55f), RoundedCornerShape(20.dp)).padding(16.dp),
        content = content
    )
}

@Composable
internal fun SectionTitle(title: String, action: String? = null) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Text)
        if (action != null) Text(action, fontSize = 12.sp, color = Lavender)
    }
}

@Composable
internal fun Tag(label: String, color: Color = Lavender) {
    Text(
        label,
        Modifier.clip(CircleShape).background(color.copy(alpha = .13f)).padding(horizontal = 10.dp, vertical = 5.dp),
        fontSize = 10.sp, color = color, fontWeight = FontWeight.Medium
    )
}

@Composable
internal fun TaskRow(
    title: String,
    meta: String,
    time: String,
    accent: Color = Lavender,
    checked: Boolean = false,
    onClick: () -> Unit = {},
    onToggle: (() -> Unit)? = null
) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(22.dp).clip(CircleShape)
                .then(if (onToggle != null) Modifier.clickable(onClick = onToggle) else Modifier)
                .border(1.5.dp, if (checked) Mint else Muted, CircleShape)
                .background(if (checked) Mint else Color.Transparent),
            contentAlignment = Alignment.Center
        ) { if (checked) Icon(Icons.Default.Check, null, tint = Ink, modifier = Modifier.size(14.dp)) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                title,
                color = if (checked) Muted else Text,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
            )
            if (meta.isNotEmpty()) Text(meta, color = Muted, fontSize = 11.sp)
        }
        Column(horizontalAlignment = Alignment.End) {
            if (time.isNotEmpty()) Text(time, color = Muted, fontSize = 10.sp)
            Box(Modifier.padding(top = 5.dp).size(6.dp).clip(CircleShape).background(accent))
        }
    }
}

@Composable
internal fun TimelineTaskRow(
    occurrence: com.dayflow.domain.TaskOccurrence,
    checked: Boolean,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    nowMillis: Long = System.currentTimeMillis(),
    isLast: Boolean = false
) {
    Row(Modifier.fillMaxWidth().heightIn(min = 68.dp), verticalAlignment = Alignment.Top) {
        Column(Modifier.width(52.dp).padding(top = 10.dp), horizontalAlignment = Alignment.End) {
            Text(displayTime(occurrence.startMinuteOfDay), color = Lavender, fontSize = 10.sp, fontWeight = FontWeight.Medium)
            Text(displayTime((occurrence.startMinuteOfDay + occurrence.durationMinutes).coerceAtMost(1439)), color = Muted, fontSize = 9.sp)
        }
        Box(Modifier.width(24.dp).fillMaxHeight(), contentAlignment = Alignment.TopCenter) {
            if (!isLast) Box(Modifier.padding(top = 14.dp).width(1.dp).fillMaxHeight().background(Edge))
            Box(Modifier.padding(top = 13.dp).size(9.dp).clip(CircleShape).background(accentFor(occurrence.task.type)).border(2.dp, Surface, CircleShape))
        }
        Column(Modifier.weight(1f).padding(bottom = 7.dp)) {
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(Surface).clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(occurrence.task.title, color = if (checked) Muted else Text, fontSize = 13.sp, fontWeight = FontWeight.Medium, textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None)
                    val status = when { checked -> "Completed"; occurrence.endMillis < nowMillis -> "Missed"; occurrence.startMillis <= nowMillis -> "In progress"; else -> "" }
                    Text("${occurrence.task.type.label} · ${WeekUtils.durationLabel(occurrence.durationMinutes)}${if (status.isNotEmpty()) " · $status" else ""}", color = if (status == "Missed") Amber else Muted, fontSize = 10.sp)
                }
                Box(Modifier.size(22.dp).clip(CircleShape).clickable(onClick = onToggle).border(1.5.dp, if (checked) Mint else Muted, CircleShape).background(if (checked) Mint else Color.Transparent), contentAlignment = Alignment.Center) {
                    if (checked) Icon(Icons.Default.Check, "Completed", tint = Ink, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

@Composable
internal fun AddRow(label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Default.Add, null, tint = Lavender)
        Spacer(Modifier.width(8.dp))
        Text(label, color = Lavender, fontSize = 12.sp)
    }
}

/** Lumi reflects saved schedule completion and the current active-day streak. */
@Composable
internal fun LumiCompanion(completed: Int, total: Int, streak: Int, onVisit: (() -> Unit)? = null, focusMode: Boolean = false) {
    val ratio = if (total == 0) 0f else completed.toFloat() / total
    val dormant = total == 0
    val celebrating = !focusMode && !dormant && ratio >= 1f
    val thriving = !dormant && (ratio >= .65f || streak >= 3)
    val aura = when { focusMode -> Lavender; dormant -> Muted; thriving -> Mint; else -> Lavender }
    val expression = when { focusMode -> "Deep focus"; dormant -> "Resting"; celebrating -> "Celebrating"; thriving -> "Thriving"; ratio < .35f -> "Gentle reset"; else -> "Growing" }
    val message = when {
        focusMode -> "${LocalAgentName.current} is focusing alongside you. One calm interval at a time."
        dormant -> "I’m taking a little rest. Add a ritual and we can grow together."
        celebrating -> "You completed today’s plan. Let’s celebrate the care you put into it! ✨"
        thriving -> "Look at us go! Your steady effort is helping our sanctuary bloom. ✨"
        ratio < .35f -> "A quiet day is okay. One small step is enough; I’m here with you."
        else -> "Nice steady progress. Let’s keep a kind, comfortable pace."
    }
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(Modifier.size(72.dp)) {
                val scale = size.minDimension / 160f
                fun p(x: Float, y: Float) = androidx.compose.ui.geometry.Offset(x * scale, y * scale)
                drawCircle(aura.copy(alpha = .14f), 75 * scale, p(80f, 80f))
                val body = if (dormant) Color(0xFF9388A8) else Color(0xFFA78BFA)
                // Soft ears and rounded body
                rotate(-12f, pivot = p(50f, 42f)) { drawRoundRect(body, p(35f, 10f), androidx.compose.ui.geometry.Size(31*scale, 55*scale), androidx.compose.ui.geometry.CornerRadius(18*scale)) }
                rotate(12f, pivot = p(110f, 42f)) { drawRoundRect(body, p(94f, 10f), androidx.compose.ui.geometry.Size(31*scale, 55*scale), androidx.compose.ui.geometry.CornerRadius(18*scale)) }
                drawCircle(body, 48*scale, p(80f, 87f))
                drawOval(Color(0xFFEDE9FE), topLeft = p(56f, 96f), size = androidx.compose.ui.geometry.Size(48*scale, 42*scale))
                if (dormant) {
                    drawLine(Color(0xFF33245A), p(52f, 79f), p(68f, 79f), 3*scale)
                    drawLine(Color(0xFF33245A), p(92f, 79f), p(108f, 79f), 3*scale)
                } else if (focusMode) {
                    drawLine(Color(0xFF33245A), p(52f, 80f), p(68f, 84f), 3*scale)
                    drawLine(Color(0xFF33245A), p(92f, 84f), p(108f, 80f), 3*scale)
                } else if (thriving) {
                    drawCircle(Color(0xFF251646), 7*scale, p(60f, 77f)); drawCircle(Color(0xFF251646), 7*scale, p(100f, 77f))
                    drawCircle(Color.White, 2.4f*scale, p(62f, 74f)); drawCircle(Color.White, 2.4f*scale, p(102f, 74f))
                } else {
                    drawLine(Color(0xFF33245A), p(52f, 81f), p(68f, 75f), 3*scale)
                    drawLine(Color(0xFF33245A), p(92f, 75f), p(108f, 81f), 3*scale)
                }
                drawCircle(Color(0xFFF472B6).copy(alpha = .6f), 5*scale, p(48f, 91f)); drawCircle(Color(0xFFF472B6).copy(alpha = .6f), 5*scale, p(112f, 91f))
                drawCircle(Color(0xFF6D28D9), 2.5f*scale, p(80f, 89f))
                val smile = Path().apply { moveTo(73*scale, 96*scale); quadraticTo(80*scale, 103*scale, 87*scale, 96*scale) }
                drawPath(smile, Color(0xFF6D28D9), style = Stroke(2*scale))
                drawCircle(Color(0xFF73E2B3), 5*scale, p(82f, 25f))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(LocalAgentName.current, color = Text, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                    Spacer(Modifier.width(8.dp))
                    Tag(expression, aura)
                }
                Text(if (focusMode) "Here for this focus interval" else if (dormant) "Your companion is resting" else "${streak} day${if (streak == 1) "" else "s"} in rhythm", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(5.dp))
                Text(if (focusMode) "Quiet focus" else "${(ratio * 100).toInt()}% vitality", color = aura, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(10.dp))
        Text("“$message”", color = Text, fontSize = 12.sp, lineHeight = 17.sp)
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(progress = { if (focusMode) 1f else ratio }, modifier = Modifier.fillMaxWidth().height(5.dp).clip(CircleShape), color = aura, trackColor = Raised)
        if (onVisit != null) {
            Spacer(Modifier.height(5.dp))
            Text("Visit ${LocalAgentName.current}’s sanctuary  →", color = Lavender, fontSize = 12.sp, modifier = Modifier.clickable(onClick = onVisit).padding(vertical = 6.dp))
        }
    }
}
