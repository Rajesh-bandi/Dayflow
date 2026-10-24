package com.dayflow.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.navigation.NavHostController
import com.dayflow.R
import com.dayflow.domain.Task
import com.dayflow.domain.TaskType
import com.dayflow.DayflowApplication
import com.dayflow.data.FocusStatus
import com.dayflow.domain.FocusTimerMath
import com.dayflow.domain.WeekUtils
import com.dayflow.openEditor
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.dayflow.notifications.scheduleFocusCompletion
import com.dayflow.blocking.FocusGuard
import java.time.LocalDate

/** Focus enforcement stays opt-in and reports the actual Android Accessibility service state. */
@Composable
internal fun FocusScreen(nav: NavHostController, requestedTaskId: Long = -1L) {
    val context = LocalContext.current.applicationContext
    val app = context as DayflowApplication
    val sessionRepo = app.focusSessions
    val profileRepo = app.focusProfiles
    val taskRepo = app.repository
    val session by sessionRepo.observeCurrent().collectAsStateWithLifecycle(initialValue = null)
    var scheduledFocusId by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(session?.id, session?.status, session?.expectedEndAtMillis) {
        scheduledFocusId?.let { scheduleFocusCompletion(context, it, 0L, false) }
        scheduledFocusId = session?.takeIf { it.status == FocusStatus.RUNNING.name }?.id
        session?.takeIf { it.status == FocusStatus.RUNNING.name }?.let {
            scheduleFocusCompletion(context, it.id, it.expectedEndAtMillis, true)
        }
    }
    val history by sessionRepo.observeRecent().collectAsStateWithLifecycle(initialValue = emptyList())
    val tasks by taskRepo.observeTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val deepWorkTasks = tasks.filter { it.type == TaskType.DEEP_WORK }
    val blockedApps by app.blockedApps.selected.collectAsStateWithLifecycle()
    // Apps the user deliberately unlocked for this session, refreshed as they open them.
    var permittedApps by remember { mutableStateOf(FocusGuard.permittedPackages()) }
    val packageManager = context.packageManager
    val appLabels = remember(blockedApps) {
        blockedApps.associateWith { packageName ->
            runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(packageName, 0)).toString() }
                .getOrDefault(packageName.substringAfterLast('.'))
        }
    }
    fun openDeliberately(packageName: String) {
        // Unlock before launching, so the guard does not bounce the app the user just chose.
        FocusGuard.permit(packageName)
        permittedApps = FocusGuard.permittedPackages()
        val launch = packageManager.getLaunchIntentForPackage(packageName) ?: return
        runCatching { context.startActivity(launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }
    var accessibilityEnabled by remember(context) { mutableStateOf(isFocusGuardEnabled(context)) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        accessibilityEnabled = isFocusGuardEnabled(context)
    }
    val profiles by profileRepo.observeAll().collectAsStateWithLifecycle(initialValue = emptyList())
    var selectedTaskId by remember(requestedTaskId) { mutableLongStateOf(requestedTaskId) }
    var durationText by remember { mutableStateOf("25") }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmEnd by remember { mutableStateOf(false) }
    var showNewProfile by remember { mutableStateOf(false) }
    var profileNameDraft by remember { mutableStateOf("") }
    var profileFocusDraft by remember { mutableStateOf("25") }
    var profileBreakDraft by remember { mutableStateOf("5") }
    var selectedProfileName by remember { mutableStateOf("Pomodoro") }
    var ambientSound by remember { mutableStateOf(com.dayflow.domain.FocusAmbientSound.current()) }
    var completedSessionMessage by remember { mutableStateOf<String?>(null) }
    var recoveryBreakMinutes by remember { mutableIntStateOf(0) }
    var breakRemainingSeconds by remember { mutableLongStateOf(0L) }
    var breakActive by remember { mutableStateOf(false) }
    var hadActiveSession by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun playCompletionChime() {
        runCatching {
            val mp = android.media.MediaPlayer.create(context, R.raw.dayflow_chime)
            mp?.setAudioAttributes(
                android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            mp?.setOnCompletionListener { player -> player.release() }
            mp?.start()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            com.dayflow.domain.FocusAmbientSound.stop()
        }
    }

    LaunchedEffect(requestedTaskId) {
        if (requestedTaskId > 0L) {
            selectedTaskId = requestedTaskId
            val task = deepWorkTasks.firstOrNull { it.id == requestedTaskId }
            if (task != null && task.durationMinutes > 0) {
                durationText = task.durationMinutes.toString()
            }
        }
    }

    var initializedProfile by remember { mutableStateOf(false) }
    LaunchedEffect(profiles) {
        if (!initializedProfile && profiles.isNotEmpty()) {
            val deepWorkProfile = if (requestedTaskId > 0L) profiles.firstOrNull { it.name.equals("Deep Work", ignoreCase = true) } else null
            val preferred = deepWorkProfile ?: profiles.firstOrNull { it.name == selectedProfileName } ?: profiles.first()
            selectedProfileName = preferred.name
            if (selectedTaskId <= 0L) {
                durationText = preferred.focusMinutes.toString()
            }
            initializedProfile = true
        }
    }

    // Monitor focus session expiration and handle completion transition
    LaunchedEffect(session?.id, session?.status) {
        if (session != null && session?.status == FocusStatus.RUNNING.name) {
            hadActiveSession = true
        }
        while (true) {
            now = System.currentTimeMillis()
            val current = session
            if (current != null && current.status == FocusStatus.RUNNING.name && now >= current.expectedEndAtMillis) {
                sessionRepo.reconcile(now)
                com.dayflow.domain.FocusAmbientSound.stop()
                ambientSound = com.dayflow.domain.FocusAmbientSound.Soundscape.OFF
                playCompletionChime()
                val profile = profiles.firstOrNull { it.name.equals(current.profileName, ignoreCase = true) }
                recoveryBreakMinutes = profile?.breakMinutes ?: 5
                completedSessionMessage = "Focus block complete! Your progress has been saved in the sanctuary."
                hadActiveSession = false
            } else {
                sessionRepo.reconcile(now)
            }
            delay(1_000)
        }
    }

    // Break countdown timer
    LaunchedEffect(breakActive) {
        while (breakActive && breakRemainingSeconds > 0) {
            delay(1_000)
            breakRemainingSeconds--
            if (breakRemainingSeconds <= 0) {
                breakActive = false
                playCompletionChime()
                completedSessionMessage = "Recovery break finished! Ready for your next focus session."
            }
        }
    }

    Page("Focus") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                com.dayflow.domain.FocusAmbientSound.stop()
                nav.popBackStack()
            }) { Icon(Icons.Default.ArrowBack, "Back", tint = Muted) }
            Column(Modifier.weight(1f)) {
                Text(
                    when {
                        breakActive -> "● RECOVERY IN PROGRESS"
                        session?.status == FocusStatus.RUNNING.name -> "● SESSION IN FLIGHT"
                        session?.status == FocusStatus.PAUSED.name -> "⏸ SESSION PAUSED"
                        else -> "FOCUS SANCTUARY"
                    },
                    fontSize = 10.sp, color = if (breakActive) Amber else Mint, fontWeight = FontWeight.SemiBold, letterSpacing = .7.sp
                )
                Text("Focus Sanctuary", fontSize = 20.sp, color = Text, fontWeight = FontWeight.SemiBold)
            }
            session?.let { Tag(it.profileName, Lavender) }
        }

        // Completion banner
        completedSessionMessage?.let { msg ->
            CardBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Session Complete ✨", color = Mint, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(msg, color = Text, fontSize = 12.sp)
                    }
                    TextButton(onClick = { completedSessionMessage = null }) { Text("Dismiss", color = Muted) }
                }
                if (recoveryBreakMinutes > 0 && !breakActive) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            breakRemainingSeconds = recoveryBreakMinutes * 60L
                            breakActive = true
                            completedSessionMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Amber, contentColor = Ink)
                    ) {
                        Text("Start $recoveryBreakMinutes min Recovery Break")
                    }
                }
            }
        }

        // Live Break / Recovery Mode
        if (breakActive) {
            CardBox {
                Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                    val breakTotalSeconds = (recoveryBreakMinutes * 60).coerceAtLeast(1)
                    val breakProgress = (breakRemainingSeconds.toFloat() / breakTotalSeconds).coerceIn(0f, 1f)
                    CircularProgressIndicator(
                        progress = { breakProgress },
                        modifier = Modifier.size(190.dp),
                        color = Amber,
                        trackColor = Raised,
                        strokeWidth = 7.dp
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("RECOVERY BREAK", color = Amber, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Text(
                            "${(breakRemainingSeconds / 60).toString().padStart(2, '0')}:${(breakRemainingSeconds % 60).toString().padStart(2, '0')}",
                            fontSize = 38.sp, color = Text, fontWeight = FontWeight.SemiBold
                        )
                        Tag("Rest & Recharge", Amber)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("Rest your eyes, stretch, hydrate, and take a deep breath.", color = Muted, fontSize = 12.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = {
                        breakActive = false
                        breakRemainingSeconds = 0
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Conclude break early")
                }
            }
        } else if (session != null) {
            val active = session!!
            val remainingSeconds = FocusTimerMath.remainingMillis(active, now) / 1_000
            val totalMillis = (active.expectedEndAtMillis - active.startedAtMillis - active.accumulatedPauseMillis).coerceAtLeast(1L)
            val progress = (1f - FocusTimerMath.remainingMillis(active, now).toFloat() / totalMillis).coerceIn(0f, 1f)
            val associatedTask = tasks.firstOrNull { it.id == active.taskId }
            CardBox {
                Box(Modifier.fillMaxWidth().height(250.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(progress = { progress }, modifier = Modifier.size(218.dp), color = Mint, trackColor = Raised, strokeWidth = 8.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (active.status == FocusStatus.PAUSED.name) "PAUSED" else "REMAINING TIME", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp)
                        Text("${(remainingSeconds / 60).toString().padStart(2, '0')}:${(remainingSeconds % 60).toString().padStart(2, '0')}", fontSize = 40.sp, color = Text, fontWeight = FontWeight.SemiBold)
                        Tag("Goal · ${WeekUtils.durationLabel((totalMillis / 60_000).toInt())}", Mint)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(associatedTask?.title ?: "Personal focus", color = Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
                Text(associatedTask?.description?.takeIf(String::isNotBlank) ?: "${active.profileName} focus profile", color = Muted, fontSize = 12.sp)

                // Ambient Soundscape selector
                Spacer(Modifier.height(10.dp))
                Text("Ambient soundscape", color = Muted, fontSize = 11.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.dayflow.domain.FocusAmbientSound.Soundscape.entries.forEach { sound ->
                        FilterChip(
                            selected = ambientSound == sound,
                            onClick = {
                                ambientSound = sound
                                com.dayflow.domain.FocusAmbientSound.play(sound)
                            },
                            label = { Text(sound.label, fontSize = 11.sp) }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Deep work", color = Muted, fontSize = 11.sp)
                    Text(if (accessibilityEnabled && blockedApps.isNotEmpty()) "${blockedApps.size} selected apps protected" else "Focus guard off", color = if (accessibilityEnabled && blockedApps.isNotEmpty()) Mint else Muted, fontSize = 11.sp)
                }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    scope.launch {
                        if (active.status == FocusStatus.PAUSED.name) {
                            sessionRepo.resume()
                            if (ambientSound != com.dayflow.domain.FocusAmbientSound.Soundscape.OFF) {
                                com.dayflow.domain.FocusAmbientSound.play(ambientSound)
                            }
                        } else {
                            sessionRepo.pause()
                            com.dayflow.domain.FocusAmbientSound.stop()
                        }
                    }
                }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink)) {
                    Text(if (active.status == FocusStatus.PAUSED.name) "Resume focus" else "Pause focus")
                }
                TextButton(onClick = { confirmEnd = true }, modifier = Modifier.fillMaxWidth()) { Text("Conclude session early", color = Muted) }
            }
            LumiCompanion(completed = 1, total = 1, streak = 0, focusMode = true)
            CardBox {
                SectionTitle("Android focus guard", if (accessibilityEnabled) "Enabled" else "Optional")
                Text(
                    if (accessibilityEnabled && blockedApps.isNotEmpty()) "Opening a blocked app now brings you back here instead of out of Dayflow, so the session stays in reach."
                    else "Selected apps may be sent back to Dayflow during a running session. Android can interrupt the service, and apps can still be reopened.",
                    color = Muted, fontSize = 12.sp
                )
                TextButton(onClick = { nav.navigate("settings") }) { Text("Manage selected apps", color = Lavender) }
            }
            if (accessibilityEnabled && blockedApps.isNotEmpty()) {
                CardBox {
                    SectionTitle("Open something anyway", "Deliberate")
                    Text("These apps are blocked while this session runs. Opening one here unlocks it for the rest of the session, so it is your decision rather than an accident.", color = Muted, fontSize = 12.sp)
                    blockedApps.sorted().forEach { packageName ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(appLabels[packageName] ?: packageName, color = Text, fontSize = 13.sp, modifier = Modifier.weight(1f))
                            TextButton(onClick = { openDeliberately(packageName) }) {
                                Text(if (packageName in permittedApps) "Unlocked · open again" else "Unlock and open", color = Lavender, fontSize = 11.sp)
                            }
                        }
                    }
                    Text("Unlocking lasts until the session ends.", color = Muted, fontSize = 10.sp)
                }
            }
        } else {
            CardBox {
                Text("Start a focus session", color = Text, fontWeight = FontWeight.SemiBold)
                Text("Focus profile", color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    profiles.forEach { profile ->
                        FilterChip(
                            selected = selectedProfileName == profile.name,
                            onClick = {
                                selectedProfileName = profile.name
                                if (selectedTaskId <= 0L) {
                                    durationText = profile.focusMinutes.toString()
                                }
                            },
                            label = { Text("${profile.name} · ${profile.focusMinutes}m") }
                        )
                    }
                    TextButton(onClick = { showNewProfile = true }) { Text("+ Profile", color = Lavender) }
                }
                profiles.firstOrNull { it.name == selectedProfileName }?.let { Text("${it.breakMinutes} minute recovery after this focus block", color = Muted, fontSize = 11.sp) }
                Spacer(Modifier.height(6.dp))
                Text("Duration", color = Muted, fontSize = 12.sp)
                OutlinedTextField(
                    value = durationText,
                    onValueChange = { durationText = it.filter(Char::isDigit).take(4) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    suffix = { Text("minutes") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(Modifier.height(10.dp))
                Text("Associate a Deep Work task", color = Muted, fontSize = 12.sp)
                if (deepWorkTasks.isEmpty()) {
                    Text("No Deep Work tasks scheduled. Starting will create a dedicated focus block.", color = Muted, fontSize = 12.sp)
                } else {
                    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = selectedTaskId <= 0L,
                            onClick = {
                                selectedTaskId = -1L
                                profiles.firstOrNull { it.name == selectedProfileName }?.let { durationText = it.focusMinutes.toString() }
                            },
                            label = { Text("Personal focus") }
                        )
                        deepWorkTasks.forEach { task ->
                            FilterChip(
                                selected = selectedTaskId == task.id,
                                onClick = {
                                    selectedTaskId = task.id
                                    if (task.durationMinutes > 0) durationText = task.durationMinutes.toString()
                                },
                                label = { Text(task.title) }
                            )
                        }
                    }
                }

                // Ambient Soundscape selection for next session
                Spacer(Modifier.height(10.dp))
                Text("Ambient soundscape", color = Muted, fontSize = 12.sp)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    com.dayflow.domain.FocusAmbientSound.Soundscape.entries.forEach { sound ->
                        FilterChip(
                            selected = ambientSound == sound,
                            onClick = { ambientSound = sound },
                            label = { Text(sound.label, fontSize = 11.sp) }
                        )
                    }
                }

                error?.let { Text(it, color = Danger, fontSize = 12.sp) }
                Spacer(Modifier.height(12.dp))
                Button(onClick = {
                    val minutes = durationText.toIntOrNull()
                    if (minutes == null || minutes !in 1..1440) error = "Choose a duration from 1 to 1,440 minutes."
                    else scope.launch {
                        runCatching { sessionRepo.start(selectedTaskId.takeIf { it > 0 }, minutes, selectedProfileName) }
                            .onFailure { error = it.message ?: "Could not start the focus session." }
                            .onSuccess {
                                error = null
                                completedSessionMessage = null
                                if (ambientSound != com.dayflow.domain.FocusAmbientSound.Soundscape.OFF) {
                                    com.dayflow.domain.FocusAmbientSound.play(ambientSound)
                                }
                            }
                    }
                }, colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink), modifier = Modifier.fillMaxWidth()) {
                    Text("Start focus sanctuary")
                }
            }
            Button(onClick = { nav.openEditor(type = TaskType.DEEP_WORK.name) }, colors = ButtonDefaults.buttonColors(containerColor = Raised, contentColor = Text), modifier = Modifier.fillMaxWidth()) {
                Text("Schedule deep work ritual")
            }
        }
        if (session == null && !breakActive) CardBox {
            Text(if (accessibilityEnabled && blockedApps.isNotEmpty()) "Focus guard ready · ${blockedApps.size} apps selected" else "Optional focus guard", color = Text, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(if (accessibilityEnabled && blockedApps.isNotEmpty()) "Selected apps are sent to Home while this focus session runs. Android can interrupt the service, and apps can be reopened." else "Choose apps and explicitly activate Dayflow’s Accessibility service in Settings to send them to Home during focus sessions. Android can interrupt this service; essential apps remain available.", color = Muted, fontSize = 12.sp)
            TextButton(onClick = { nav.navigate("settings") }) { Text("Configure focus guard", color = Lavender) }
        }
        if (history.isNotEmpty()) {
            CardBox {
                SectionTitle("Recent sessions")
                history.take(10).forEach { item ->
                    val actualMinutes = (item.actualDurationMillis ?: 0L) / 60_000
                    TaskRow(
                        title = tasks.firstOrNull { it.id == item.taskId }?.title ?: "Personal focus",
                        meta = "${item.status.lowercase().replaceFirstChar { it.uppercase() }} · ${item.profileName}",
                        time = "${actualMinutes}m",
                        accent = if (item.status == FocusStatus.COMPLETED.name) Mint else Muted
                    )
                }
            }
        }
    }
    if (confirmEnd) AlertDialog(
        onDismissRequest = { confirmEnd = false },
        title = { Text("End this session?", color = Text) },
        text = { Text("It will be saved as interrupted with the actual elapsed time.", color = Muted) },
        confirmButton = {
            TextButton(onClick = {
                confirmEnd = false
                com.dayflow.domain.FocusAmbientSound.stop()
                scope.launch { sessionRepo.finishEarly() }
            }) { Text("End session", color = Danger) }
        },
        dismissButton = { TextButton(onClick = { confirmEnd = false }) { Text("Keep focusing", color = Muted) } },
        containerColor = Surface
    )
    if (showNewProfile) AlertDialog(
        onDismissRequest = { showNewProfile = false }, containerColor = Surface,
        title = { Text("Create focus profile", color = Text) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(profileNameDraft, { profileNameDraft = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(profileFocusDraft, { profileFocusDraft = it.filter(Char::isDigit).take(4) }, label = { Text("Focus minutes") }, singleLine = true)
                OutlinedTextField(profileBreakDraft, { profileBreakDraft = it.filter(Char::isDigit).take(3) }, label = { Text("Recovery minutes") }, singleLine = true)
            }
        },
        confirmButton = { TextButton(onClick = {
            val focus = profileFocusDraft.toIntOrNull(); val rest = profileBreakDraft.toIntOrNull()
            if (profileNameDraft.isNotBlank() && focus != null && rest != null && focus in 1..1440 && rest in 0..180) scope.launch {
                profileRepo.save(profileNameDraft, focus, rest); selectedProfileName = profileNameDraft.trim(); durationText = focus.toString(); profileNameDraft = ""; showNewProfile = false
            }
        }) { Text("Save profile", color = Lavender) } },
        dismissButton = { TextButton(onClick = { showNewProfile = false }) { Text("Cancel", color = Muted) } }
    )
}

private fun isFocusGuardEnabled(context: android.content.Context): Boolean = FocusGuard.isEnabled(context)

@Composable
internal fun ActionsScreen(nav: NavHostController) {
    val repository = rememberRepository()
    val tasks by repository.observeTasks().collectAsStateWithLifecycle(initialValue = emptyList())
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var pending by remember { mutableStateOf<Task?>(null) }
    var httpPending by remember { mutableStateOf<Task?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    val actions = tasks.filter { it.type == TaskType.QUICK_ACTION }
    Page("Quick Actions", "Your saved links and actions", null) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Button(onClick = { nav.openEditor(type = TaskType.QUICK_ACTION.name) }, colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink)) { Text("Add action") }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { nav.navigate("routines") }, modifier = Modifier.weight(1f)) { Text("Routines") }
            OutlinedButton(onClick = { nav.navigate("settings") }, modifier = Modifier.weight(1f)) { Text("Settings") }
        }
        if (actions.isEmpty()) {
            CardBox {
                Text("No quick actions yet", color = Text, fontWeight = FontWeight.SemiBold)
                Text("Add a Quick Action task and give it an HTTPS destination. Your actions stay on this device.", color = Muted, fontSize = 12.sp)
            }
        }
        actions.forEach { task ->
            CardBox {
                Text(task.title, color = Text, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(task.destinationUrl ?: "No destination configured", color = Muted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            val url = task.destinationUrl?.trim()
                            val uri = url?.let(Uri::parse)
                            if (uri == null || uri.scheme !in setOf("https", "http") || uri.host.isNullOrBlank()) {
                                message = "Add a valid web link to this action before launching it."
                            } else if (uri.scheme == "http") {
                                httpPending = task
                            } else {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                                    pending = task
                                    message = null
                                } catch (_: ActivityNotFoundException) {
                                    message = "No installed app can open this link."
                                } catch (_: SecurityException) {
                                    message = "Android did not allow this link to open."
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink)
                    ) { Text("Open link") }
                    OutlinedButton(onClick = { nav.openEditor(taskId = task.id) }) { Text("Edit") }
                }
            }
        }
        message?.let { Text(it, color = Danger, fontSize = 12.sp) }
    }

    pending?.let { task ->
        AlertDialog(
            onDismissRequest = { pending = null },
            title = { Text("Did you complete this action?", color = Text) },
            text = { Text("${task.title} was opened. Confirm only after you return and finish it.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        repository.setCompleted(task.id, LocalDate.now().toEpochDay(), true)
                        pending = null
                    }
                }) { Text("Mark complete", color = Lavender) }
            },
            dismissButton = { TextButton(onClick = { pending = null }) { Text("Not yet", color = Muted) } },
            containerColor = Surface
        )
    }
    httpPending?.let { task ->
        AlertDialog(
            onDismissRequest = { httpPending = null },
            title = { Text("Open an unencrypted site?", color = Text) },
            text = { Text("${task.destinationUrl} uses HTTP. Continue only if you trust this site.", color = Muted) },
            confirmButton = {
                TextButton(onClick = {
                    val uri = Uri.parse(task.destinationUrl)
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
                        pending = task
                        message = null
                    } catch (_: ActivityNotFoundException) {
                        message = "No installed app can open this link."
                    } catch (_: SecurityException) {
                        message = "Android did not allow this link to open."
                    }
                    httpPending = null
                }) { Text("Continue", color = Lavender) }
            },
            dismissButton = { TextButton(onClick = { httpPending = null }) { Text("Cancel", color = Muted) } },
            containerColor = Surface
        )
    }
}

@Composable
internal fun SetupScreen(nav: NavHostController) {
    val app = LocalContext.current.applicationContext as DayflowApplication
    val finishSetup = {
        app.preferences.completeOnboarding()
        if (!nav.popBackStack()) nav.navigate("today") { popUpTo("setup") { inclusive = true }; launchSingleTop = true }
    }
    Page("Permissions Setup", "STEP 2 OF 3 · VOICE AND SHIELD SETUP", null, footer = {
        Button(onClick = finishSetup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink)) { Text("Continue to Today") }
        TextButton(onClick = finishSetup, modifier = Modifier.fillMaxWidth()) { Text("Configure permissions later", color = Muted) }
    }) {
    CardBox {
        Text("Calm focus, powered by Android", color = Text, fontWeight = FontWeight.SemiBold, fontSize = 18.sp)
        Text("Dayflow never silently blocks apps or reads private data. Choose only the optional access you need; planning works without permissions.", color = Muted, fontSize = 13.sp)
    }
    NaturalVoiceSetupCard()
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Tag("✓  1. Plan your week", Mint)
        Tag("◉  2. Protect focus", Lavender)
        Tag("⌛  3. Build consistency", Muted)
    }
    SectionTitle("Android system disclosures", "Optional")
    CardBox {
        Text("Ritual reminders", color = Text, fontWeight = FontWeight.SemiBold)
        Text("Reminders use Android notifications and may be delayed by battery settings. Manage notification access any time in Dayflow settings.", color = Muted, fontSize = 12.sp)
        TextButton(onClick = { nav.navigate("settings") }) { Text("Notification settings", color = Lavender) }
    }
    CardBox {
        Text("Accessibility service · optional", color = Text, fontWeight = FontWeight.SemiBold)
        Text("During a focus session, selected launchable apps can be sent back to Home. Android can interrupt this service, and apps can still be reopened. Dayflow does not read screen content.", color = Muted, fontSize = 12.sp)
        TextButton(onClick = { nav.navigate("settings") }) { Text("Configure focus guard", color = Lavender) }
    }
    CardBox {
        Text("Private by design", color = Text, fontWeight = FontWeight.SemiBold)
        Text("Tasks, schedules, and progress are stored locally on this device. No account or cloud sync is required.", color = Muted, fontSize = 12.sp)
    }
    }
}
