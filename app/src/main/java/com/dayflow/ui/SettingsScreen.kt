package com.dayflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.dayflow.data.AgentGender
import com.dayflow.data.AiConfig
import com.dayflow.data.LumiAiClient
import com.dayflow.data.LumiMemoryService
import com.dayflow.data.local.AiMemoryEntity
import kotlinx.coroutines.flow.collectLatest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.dayflow.DayflowApplication
import java.time.DayOfWeek
import android.content.Intent
import android.provider.Settings
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.compose.material3.Checkbox
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.dayflow.data.CloudVoice
import com.dayflow.data.NeuralVoice
import com.dayflow.data.NeuralVoiceModel as NeuralVoiceModelOption
import com.dayflow.data.VoiceEngine

@Composable
internal fun SettingsScreen(nav: NavHostController) {
    val app = LocalContext.current.applicationContext as DayflowApplication
    val prefs by app.preferences.state.collectAsStateWithLifecycle()
    // Captured for the click lambdas below, which cannot read a CompositionLocal themselves.
    val agentName = LocalAgentName.current
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> app.preferences.setReminders(granted) }
    var aiUrl by remember { mutableStateOf(app.aiConfiguration.read().baseUrl) }
    var aiKey by remember { mutableStateOf(app.aiConfiguration.read().apiKey) }
    var aiModel by remember { mutableStateOf(app.aiConfiguration.read().model) }
    var aiSearchKey by remember { mutableStateOf(app.aiConfiguration.read().searchApiKey) }
    var aiTtsModel by remember { mutableStateOf(app.aiConfiguration.read().ttsModel) }
    var aiTtsVoice by remember { mutableStateOf(app.aiConfiguration.read().ttsVoice) }
    var speechModels by remember { mutableStateOf<List<String>>(emptyList()) }
    var voiceStatus by remember { mutableStateOf("Not tested") }
    var voiceBusy by remember { mutableStateOf(false) }
    val voicePlayer = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val playVoice: (ByteArray) -> Unit = { bytes ->
        runCatching {
            voicePlayer.value?.release()
            val file = java.io.File(app.cacheDir, "lumi_voice_sample.mp3")
            file.writeBytes(bytes)
            voicePlayer.value = android.media.MediaPlayer().apply {
                setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build()
                )
                setDataSource(file.absolutePath)
                setOnCompletionListener { it.release(); file.delete(); voicePlayer.value = null }
                prepare()
                start()
            }
        }
    }
    var aiStatus by remember { mutableStateOf("Not tested") }
    var aiBusy by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var memoryDraft by remember { mutableStateOf("") }
    var memories by remember { mutableStateOf(emptyList<AiMemoryEntity>()) }
    var profileName by remember { mutableStateOf("") }
    var profilePreferences by remember { mutableStateOf("") }
    var profileGoals by remember { mutableStateOf("") }
    var editingMemoryId by remember { mutableStateOf<Long?>(null) }
    var editingMemoryText by remember { mutableStateOf("") }
    var memoryFeedback by remember { mutableStateOf<String?>(null) }
    var showClearHistory by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        app.aiDao.observeMemories().collectLatest { memories = it }
        // Profile is loaded independently below when this screen is created.
    }
    LaunchedEffect(Unit) {
        val saved = app.aiDao.getProfile()
        profileName = saved?.name.orEmpty(); profilePreferences = saved?.preferences.orEmpty(); profileGoals = saved?.goals.orEmpty()
    }
    Page("Settings", "Shape Dayflow around your day") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = Muted) }
            Text("Saved on this device", color = Muted, fontSize = 12.sp)
        }
        CardBox {
            SectionTitle("AI gateway · OmniRoute")
            Text("Credentials are stored encrypted on this device. Use your OmniRoute server's OpenAI-compatible base URL.", color = Muted, fontSize = 12.sp)
            OutlinedTextField(aiUrl, { aiUrl = it }, label = { Text("Base URL") }, placeholder = { Text("https://your-server/v1") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedButton(enabled = !aiBusy, onClick = {
                aiBusy = true; aiStatus = "Looking for a gateway on this phone…"
                scope.launch {
                    val found = withContext(Dispatchers.IO) { com.dayflow.data.LocalGatewayDiscovery.discover(aiKey) }
                    if (found != null) {
                        aiUrl = "$found/v1"
                        aiStatus = "Found one at $found · tap Test connection to confirm"
                    } else {
                        aiStatus = "Nothing answered on this phone. Enter your OmniRoute URL and port above."
                    }
                    aiBusy = false
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Find the gateway running on this phone") }
            Text("OmniRoute in Termux on this same phone answers on the loopback address, so http://127.0.0.1:<port>/v1 works with no Wi-Fi, LAN address or router involved.", color = Muted, fontSize = 11.sp)
            OutlinedTextField(aiKey, { aiKey = it }, label = { Text("API key") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            OutlinedTextField(aiModel, { aiModel = it }, label = { Text("Model name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(aiSearchKey, { aiSearchKey = it }, label = { Text("Web search key (optional)") }, placeholder = { Text("Leave empty to use free keyless search") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password), modifier = Modifier.fillMaxWidth())
            Text("Optional. Search works without a key using free public sources. Pasting an Exa key from exa.ai gives cleaner, more relevant results; its free tier needs no card.", color = Muted, fontSize = 11.sp)
            OutlinedTextField(aiTtsModel, { aiTtsModel = it }, label = { Text("Natural voice model (optional)") }, placeholder = { Text("Leave empty for the on-device voice") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(aiTtsVoice, { aiTtsVoice = it }, label = { Text("Voice name") }, placeholder = { Text("alloy") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            Text("Speech uses this gateway's /v1/audio/speech route, so a natural voice needs no extra account. Leave the model empty to use the phone's built-in voice. If the gateway cannot synthesize speech, Lumi falls back to the on-device voice automatically.", color = Muted, fontSize = 11.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(enabled = !voiceBusy, onClick = {
                    voiceBusy = true; voiceStatus = "Asking the gateway for speech models…"
                    val draft = AiConfig(aiUrl, aiKey, aiModel, aiSearchKey, aiTtsModel, aiTtsVoice)
                    scope.launch {
                        val result = runCatching {
                            withContext(Dispatchers.IO) { app.aiConfiguration.save(draft); CloudVoice.speechModels(draft) }
                        }
                        speechModels = result.getOrElse { emptyList() }
                        voiceStatus = result.fold(
                            { found -> if (found.isEmpty()) "This gateway advertises no speech models, so $agentName keeps the on-device voice." else "Found ${found.size} speech models · tap one to use it" },
                            { error -> error.message ?: "Could not reach the gateway." }
                        )
                        voiceBusy = false
                    }
                }, modifier = Modifier.weight(1f)) { Text("Find voices") }
                OutlinedButton(enabled = !voiceBusy, onClick = {
                    voiceBusy = true; voiceStatus = "Synthesising a sample…"
                    val draft = AiConfig(aiUrl, aiKey, aiModel, aiSearchKey, aiTtsModel, aiTtsVoice)
                    scope.launch {
                        val result = runCatching {
                            app.aiConfiguration.save(draft)
                            withContext(Dispatchers.IO) { CloudVoice.synthesize(draft, "Hi, I'm Lumi. Your next focus block starts soon.") }
                        }
                        result.onSuccess(playVoice)
                        voiceStatus = result.fold(
                            { "Playing the gateway voice." },
                            { error -> "Gateway voice unavailable, $agentName keeps the on-device voice. ${error.message}" }
                        )
                        voiceBusy = false
                    }
                }, modifier = Modifier.weight(1f)) { Text("Test voice") }
            }
            Text("Voice: $voiceStatus", color = if (voiceStatus.contains("unavailable")) Amber else Muted, fontSize = 12.sp)
            if (speechModels.isNotEmpty()) Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                speechModels.forEach { model ->
                    TextButton(onClick = { aiTtsModel = model }) { Text(model, color = Lavender, fontSize = 11.sp) }
                }
            }
            Text("Connection: $aiStatus", color = if (aiStatus == "Connected") Mint else Muted, fontSize = 12.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(enabled = !aiBusy, onClick = {
                    aiBusy = true; aiStatus = "Connecting…"
                    scope.launch {
                        val result = runCatching { withContext(Dispatchers.IO) { app.aiConfiguration.save(AiConfig(aiUrl, aiKey, aiModel, aiSearchKey, aiTtsModel, aiTtsVoice)); LumiAiClient(app.aiConfiguration).testConnection() } }
                        aiStatus = if (result.isSuccess) "Connected" else result.exceptionOrNull()?.message ?: "Connection error"
                        aiBusy = false
                    }
                }, modifier = Modifier.weight(1f)) { Text("Test connection") }
                Button(enabled = !aiBusy, onClick = {
                    val result = runCatching { app.aiConfiguration.save(AiConfig(aiUrl, aiKey, aiModel, aiSearchKey, aiTtsModel, aiTtsVoice)) }
                    aiStatus = if (result.isSuccess) "Saved · not tested" else result.exceptionOrNull()?.message ?: "Could not save"
                }, modifier = Modifier.weight(1f)) { Text("Save") }
            }
            TextButton(onClick = { nav.navigate("lumi") }) { Text("Open Lumi chat", color = Lavender) }
        }
        AgentIdentityCard()
        VoiceEngineCard()
        CardBox {
            SectionTitle("Week starts on")
            listOf(DayOfWeek.MONDAY to "Monday", DayOfWeek.SUNDAY to "Sunday").forEach { (day, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = prefs.firstDayOfWeek == day, onClick = { app.preferences.setFirstDay(day) })
                    Text(label, color = Text)
                }
            }
            Text("The week planner updates immediately to match.", color = Muted, fontSize = 11.sp)
        }
        CardBox {
            SectionTitle("${LocalAgentName.current} profile and memories")
            Text("${LocalAgentName.current} keeps chat history separately from a small set of useful memories. It can save clearly stated preferences, goals, and facts; routine patterns are calculated from your actual completion records. Review, edit, or delete memories here.", color = Muted, fontSize = 12.sp)
            OutlinedTextField(profileName, { profileName = it }, label = { Text("Your name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(profilePreferences, { profilePreferences = it }, label = { Text("Preferences") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            OutlinedTextField(profileGoals, { profileGoals = it }, label = { Text("Ongoing goals") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            Button(onClick = {
                scope.launch {
                    val previous = app.aiDao.getProfile() ?: com.dayflow.data.local.AiUserProfileEntity()
                    app.aiDao.saveProfile(previous.copy(name = profileName.trim(), preferences = profilePreferences.trim(), goals = profileGoals.trim(), updatedAtMillis = System.currentTimeMillis()))
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save profile") }
            OutlinedTextField(memoryDraft, { memoryDraft = it }, label = { Text("Add a memory") }, modifier = Modifier.fillMaxWidth())
            OutlinedButton(onClick = {
                val value = memoryDraft.trim()
                if (value.isNotEmpty()) scope.launch {
                    if (LumiMemoryService(app.aiDao).save(value) == null) memoryFeedback = "That memory looks temporary or sensitive, so it was not saved."
                    else { memoryDraft = ""; memoryFeedback = "Memory saved on this device." }
                }
            }, modifier = Modifier.fillMaxWidth()) { Text("Save memory") }
            memoryFeedback?.let { Text(it, color = Muted, fontSize = 11.sp) }
            memories.forEach { memory ->
                Column(Modifier.fillMaxWidth()) {
                    if (editingMemoryId == memory.id) {
                        OutlinedTextField(editingMemoryText, { editingMemoryText = it }, label = { Text("Edit memory") }, modifier = Modifier.fillMaxWidth())
                        Row {
                            TextButton(onClick = {
                                val updated = editingMemoryText.trim()
                                if (updated.isNotBlank()) scope.launch { app.aiDao.updateMemory(memory.id, updated); editingMemoryId = null }
                            }) { Text("Save") }
                            TextButton(onClick = { editingMemoryId = null }) { Text("Cancel") }
                        }
                    } else {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(memory.category.replaceFirstChar(Char::uppercase), color = Lavender, fontSize = 10.sp)
                                Text(memory.content, color = Text, fontSize = 12.sp)
                                Text(if (memory.explicit) "Saved from your statement or Settings" else "Learned from conversation · review this" , color = Muted, fontSize = 10.sp)
                            }
                            TextButton(onClick = { editingMemoryId = memory.id; editingMemoryText = memory.content }) { Text("Edit") }
                            TextButton(onClick = { scope.launch { app.aiDao.deleteMemory(memory.id) } }) { Text("Delete", color = Danger) }
                        }
                    }
                }
            }
            TextButton(onClick = { showClearHistory = true }, modifier = Modifier.fillMaxWidth()) { Text("Delete all chat history", color = Danger) }
        }
        CardBox {
            SectionTitle("Notifications")
            Text("Daily schedule reminders", color = Text)
            Text("Reminder scheduling needs notification permission. Turn it on when you’re ready.", color = Muted, fontSize = 12.sp)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(if (prefs.remindersEnabled) "Enabled" else "Off", color = if (prefs.remindersEnabled) Mint else Muted)
                Switch(checked = prefs.remindersEnabled, onCheckedChange = { enabled ->
                    if (!enabled) app.preferences.setReminders(false)
                    else if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    else app.preferences.setReminders(true)
                })
            }
            if (prefs.remindersEnabled) Text("Dayflow schedules a reminder 10 minutes before upcoming rituals. Android may delay reminders while battery saver is active.", color = Muted, fontSize = 11.sp)
        }
        CardBox {
            SectionTitle("Display")
            listOf(false to "Dark (Stitch default)", true to "Light").forEach { (light, label) ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = prefs.lightTheme == light, onClick = { app.preferences.setLightTheme(light) })
                    Text(label, color = Text)
                }
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)) {
                    Text("24-hour time", color = Text)
                    Text(if (prefs.use24HourTime) "09:30" else "9:30 AM", color = Muted, fontSize = 11.sp)
                }
                Switch(checked = prefs.use24HourTime, onCheckedChange = app.preferences::set24Hour)
            }
            Text("Theme changes apply across Dayflow immediately.", color = Muted, fontSize = 11.sp)
        }
        CardBox {
            SectionTitle("Privacy and platform access")
            Text("Schedules, routines, focus history, and progress stay on this device. The optional focus guard runs only after you enable its Accessibility service and while a focus session is active.", color = Muted, fontSize = 12.sp)
            TextButton(onClick = { nav.navigate("setup") }) { Text("Review platform access", color = Lavender) }
        }
        FocusGuardSetupCard()
        OutlinedButton(onClick = { nav.navigate("archive") }, modifier = Modifier.fillMaxWidth()) { Text("View archived tasks") }
    }
    if (showClearHistory) AlertDialog(
        onDismissRequest = { showClearHistory = false },
        title = { Text("Delete Lumi chat history?") },
        text = { Text("This removes saved conversations from this device. Your profile and saved memories stay intact.") },
        confirmButton = { TextButton(onClick = { scope.launch { app.aiDao.clearAllMessages(); showClearHistory = false } }) { Text("Delete history", color = Danger) } },
        dismissButton = { TextButton(onClick = { showClearHistory = false }) { Text("Cancel") } }
    )
}

private const val FRESH_EVENT_WINDOW_MILLIS = 10 * 60 * 1000L

@Composable
internal fun FocusGuardSetupCard() {
    val context = LocalContext.current
    val app = context.applicationContext as DayflowApplication
    val selected by app.blockedApps.selected.collectAsStateWithLifecycle()
    val guard by com.dayflow.blocking.FocusGuard.status.collectAsStateWithLifecycle()
    var enabled by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        // Ask Android directly; the settings string format differs between OEM builds.
        enabled = com.dayflow.blocking.FocusGuard.isEnabled(context)
    }
    val defaultHome = remember {
        context.packageManager.resolveActivity(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), 0)?.activityInfo?.packageName
    }
    val launchableApps = remember {
        context.packageManager.queryIntentActivities(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER), 0)
            .mapNotNull { resolve -> resolve.activityInfo?.applicationInfo?.let { info ->
                if (info.packageName == context.packageName || info.packageName == defaultHome || (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0) null
                else info.packageName to context.packageManager.getApplicationLabel(info).toString()
            } }.distinctBy { it.first }.sortedBy { it.second.lowercase() }
    }
    val eligiblePackages = remember(launchableApps) { launchableApps.map { it.first }.toSet() }
    val allEligibleSelected = eligiblePackages.isNotEmpty() && selected.containsAll(eligiblePackages)
    CardBox {
        SectionTitle("Optional app guard")
        Text("Choose launchable apps to send back to Home while a focus session is running. This does not monitor screen content. Android can interrupt the service, and selected apps can still be reopened.", color = Muted, fontSize = 12.sp)
        Spacer(Modifier.height(8.dp))
        val declared = remember { com.dayflow.blocking.FocusGuard.declared(context) }
        if (!declared) Text(
            "This install does not declare the guard service, so Android has nothing to list under Accessibility. Reinstall the current Dayflow APK.",
            color = Danger, fontSize = 11.sp
        )
        if (!enabled && declared) {
            Text(
                "Android hides the switch until you find the right page. It is never in the first Accessibility list — open the last step below, or look for Downloaded apps / Installed services / Installed apps, then Dayflow Focus Guard.",
                color = Amber, fontSize = 11.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "If Android says the setting is restricted: Dayflow App info ⋮ → Allow restricted settings.",
                color = Amber, fontSize = 11.sp
            )
            Spacer(Modifier.height(8.dp))
        }
        // Android unbinds and rebinds accessibility services on its own schedule, so a momentary
        // "disconnected" does not mean the guard is off. A recent event is the honest signal.
        val listening = guard.connected || (guard.lastEventAtMillis > 0L && System.currentTimeMillis() - guard.lastEventAtMillis < FRESH_EVENT_WINDOW_MILLIS)
        val lastEventAge = if (guard.lastEventAtMillis == 0L) null else System.currentTimeMillis() - guard.lastEventAtMillis
        Text(
            when {
                !enabled -> "Android reports the guard as off, so selected apps are never sent Home."
                !listening -> "Guard is switched on but no events have arrived yet. Open another app, then come back to Dayflow to confirm it is live."
                guard.enforcing -> "Guard is running and enforcing a focus session right now."
                else -> "Guard is enabled and listening. Android reconnects it on its own after battery optimisations, so enforcement only holds while the service is connected and a focus session is running."
            },
            color = when { enabled && listening -> Mint; enabled -> Amber; else -> Muted },
            fontSize = 11.sp
        )
        if (enabled && listening) Text(
            "Android events seen: ${if (guard.lastEventAtMillis == 0L) "none yet" else lastEventAge?.let { "${it / 1000}s ago" } ?: "ok"}" +
                (guard.lastRedirectPackage?.let { " · last app sent Home: ${it.substringAfterLast('.')}" } ?: " · nothing sent Home yet"),
            color = Muted, fontSize = 10.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Guard service: ${if (declared) "declared by this install" else "missing from this install"} · " +
                "switch: ${if (enabled) "on" else "off"} · " +
                "engine: ${if (guard.connected) "connected" else "not connected right now"}",
            color = Muted, fontSize = 10.sp
        )
        Button(
            onClick = {
                context.startActivity(
                    Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                        .setData(android.net.Uri.parse("package:${context.packageName}"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = if (enabled) Raised else Lavender, contentColor = if (enabled) Text else Ink)
        ) { Text(if (enabled) "1 · Dayflow App info" else "1 · Dayflow App info · allow restricted settings") }
        OutlinedButton(
            onClick = {
                // Opens this exact service's own toggle page, which works even when a ROM buries the entry.
                val direct = Intent("android.settings.ACCESSIBILITY_DETAILS_SETTINGS")
                    .putExtra("android.intent.extra.COMPONENT_NAME", com.dayflow.blocking.FocusGuard.componentName(context))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val opened = runCatching { context.startActivity(direct); true }.getOrDefault(false)
                if (!opened) runCatching {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Lavender)
        ) { Text(if (enabled) "2 · Dayflow Focus Guard switch is on" else "2 · Open the Dayflow Focus Guard switch") }
        OutlinedButton(
            onClick = { app.blockedApps.setAllBlocked(eligiblePackages, !allEligibleSelected) },
            enabled = eligiblePackages.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Lavender)
        ) {
            Text(if (allEligibleSelected) "Clear app selection" else "Select all ${eligiblePackages.size} eligible apps")
        }
        if (launchableApps.isEmpty()) Text("No eligible user apps were found.", color = Muted, fontSize = 11.sp)
        else launchableApps.forEach { (packageName, label) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = packageName in selected, onCheckedChange = { app.blockedApps.setBlocked(packageName, it) })
                Column {
                    Text(label, color = Text, fontSize = 13.sp)
                    Text(packageName, color = Muted, fontSize = 9.sp)
                }
            }
        }
        Text("${selected.size} selected · Dayflow, Android settings, the system UI, and your default launcher are excluded.", color = Muted, fontSize = 10.sp)
    }
}

/**
 * Voice picker. The phone's own TextToSpeech is a formant synthesiser from a different era, and the
 * user's gateway may have no speech provider at all, so the natural option is a neural model that runs
 * on the device itself: offline, free, and no account.
 */
@Composable
private fun VoiceEngineCard() {
    val context = LocalContext.current
    val app = context.applicationContext as DayflowApplication
    val prefs by app.preferences.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val agentName = LocalAgentName.current
    var status by remember { mutableStateOf("") }
    var busyModel by remember { mutableStateOf<String?>(null) }
    var progressText by remember { mutableStateOf("") }
    var installedTick by remember { mutableStateOf(0) }
    val samplePlayer = remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    val installed = remember(installedTick) { NeuralVoice.MODELS.associate { it.id to NeuralVoice.isInstalled(context, it.id) } }

    val playSample: (java.io.File) -> Unit = { file ->
        runCatching {
            samplePlayer.value?.let { runCatching { it.stop() }; runCatching { it.release() } }
            samplePlayer.value = android.media.MediaPlayer().apply {
                setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                setDataSource(file.absolutePath)
                setOnCompletionListener { done -> done.release(); file.delete(); samplePlayer.value = null }
                setOnErrorListener { err, _, _ -> err.release(); file.delete(); samplePlayer.value = null; true }
                prepare(); start()
            }
        }.onFailure { status = "That sample could not be played on this device." }
    }
    fun playModelSample(modelId: String) {
        busyModel = modelId
        status = "Rendering a sample on this phone…"
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    NeuralVoice.synthesize(
                        context, modelId,
                        "Hi, I'm $agentName. This is the voice I'll use to talk with you.",
                        gender = app.preferences.state.value.agentGender
                    )
                }.getOrNull()
            }
            if (file != null) { playSample(file); status = "Playing the sample." }
            else status = "The sample could not be generated. The model files may be incomplete; download again."
            busyModel = null
        }
    }
    fun installModel(model: NeuralVoiceModelOption) {
        busyModel = model.id
        progressText = "Starting the ${model.archiveMegabytes} MB download…"
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    NeuralVoice.install(context, model) { copied, total ->
                        progressText = if (total > 0) "Downloading ${copied / 1_048_576} of ${total / 1_048_576} MB…"
                        else "Downloading ${copied / 1_048_576} MB…"
                    }
                }
            }
            result.onSuccess {
                installedTick++
                app.preferences.setNeuralVoiceModel(model.id)
                if (app.preferences.state.value.voiceEngine == VoiceEngine.SYSTEM) app.preferences.setVoiceEngine(VoiceEngine.NEURAL_ON_DEVICE)
                status = "${model.label.split("·").first().trim()} is ready. $agentName will speak with it offline."
            }
            result.onFailure { status = it.message ?: "The download failed. Check your connection and try again." }
            busyModel = null
            progressText = ""
        }
    }

    CardBox {
        SectionTitle("Natural voice")
        Text("Android's built-in voice reads as robotic. A neural voice runs on this phone, offline, with no account and no key, and sounds like a person. Your gateway is still used when it has a speech model.", color = Muted, fontSize = 12.sp)
        listOf(
            Triple(VoiceEngine.AUTOMATIC, "Automatic", "Neural voice when one is installed, then the gateway, then Android's voice."),
            Triple(VoiceEngine.NEURAL_ON_DEVICE, "Neural voice on this phone", "Always prefer the installed neural voice. Works offline, never leaves the device."),
            Triple(VoiceEngine.GATEWAY, "Gateway voice only", "Use the speech model configured in your OmniRoute gateway."),
            Triple(VoiceEngine.SYSTEM, "Android's voice only", "The built-in synthesiser. Robotic, but instant and needs nothing.")
        ).forEach { (engine, label, description) ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = prefs.voiceEngine == engine, onClick = { app.preferences.setVoiceEngine(engine) })
                Column(Modifier.weight(1f)) {
                    Text(label, color = Text, fontSize = 13.sp)
                    Text(description, color = Muted, fontSize = 11.sp)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Neural voice models (one-time download, stored on this device)", color = Text, fontSize = 12.sp)
        NeuralVoice.MODELS.forEach { model ->
            val ready = installed[model.id] == true
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(model.label, color = Text, fontSize = 13.sp)
                    Text(if (ready) "Installed · speaks offline" else model.detail, color = if (ready) Mint else Muted, fontSize = 11.sp)
                    if (prefs.neuralVoiceModel == model.id) Text("Selected voice", color = Lavender, fontSize = 10.sp)
                }
                when {
                    busyModel == model.id -> Text(progressText.ifBlank { "Working…" }, color = Lavender, fontSize = 10.sp)
                    ready -> Row {
                        TextButton(onClick = { app.preferences.setNeuralVoiceModel(model.id) }) {
                            Text(if (prefs.neuralVoiceModel == model.id) "Selected" else "Use", color = Lavender, fontSize = 11.sp)
                        }
                        TextButton(enabled = busyModel == null, onClick = { playModelSample(model.id) }) { Text("Sample", color = Lavender, fontSize = 11.sp) }
                    }
                    else -> Button(enabled = busyModel == null, onClick = { installModel(model) }) { Text("Download", fontSize = 11.sp) }
                }
            }
        }
        Text(
            if (busyModel != null) progressText else "A two-minute sample length is enough to hear the difference. Android's voice stays as the last fallback so Lumi never goes silent.",
            color = if (busyModel != null) Lavender else Muted, fontSize = 11.sp
        )
        if (status.isNotBlank()) Text(status, color = if (status.contains("could not") || status.contains("failed")) Amber else Mint, fontSize = 11.sp)
        Text("Speech is rendered on the device. No text leaves the phone for the neural voice.", color = Muted, fontSize = 10.sp)
    }
}

/**
 * The assistant's name and the voice it presents with. Both belong to the user: the name is used across
 * Dayflow and in how the assistant refers to itself, and the gender moves the selected voice to one that
 * matches instead of leaving a mismatched voice chosen.
 */
@Composable
private fun AgentIdentityCard() {
    val context = LocalContext.current
    val app = context.applicationContext as DayflowApplication
    val prefs by app.preferences.state.collectAsStateWithLifecycle()
    var draftName by remember(prefs.agentName) { mutableStateOf(prefs.agentName) }
    var status by remember { mutableStateOf("") }
    CardBox {
        SectionTitle("Assistant name and voice")
        Text("Give your assistant a name and choose the voice it presents with. The name appears throughout Dayflow and is how it refers to itself.", color = Muted, fontSize = 12.sp)
        OutlinedTextField(
            draftName, { draftName = it },
            label = { Text("Assistant name") },
            placeholder = { Text("For example, ${com.dayflow.data.DEFAULT_AGENT_NAME}") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        OutlinedButton(onClick = {
            app.preferences.setAgentName(draftName)
            draftName = app.preferences.state.value.agentName
            status = "Name saved. Dayflow now calls it $draftName."
        }, modifier = Modifier.fillMaxWidth()) { Text("Save name") }
        AgentGender.entries.forEach { option ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = prefs.agentGender == option, onClick = {
                    app.preferences.setAgentGender(option)
                    val target = NeuralVoice.recommendedModel(option) { id -> NeuralVoice.isInstalled(context, id) }
                    app.preferences.setNeuralVoiceModel(target)
                    val label = NeuralVoice.model(target)?.label?.split("·")?.first()?.trim().orEmpty()
                    status = if (NeuralVoice.isInstalled(context, target)) {
                        "Voice switched to $label."
                    } else {
                        "$label is the matching voice. Download it below to hear it; Android's voice speaks until then."
                    }
                })
                Column(Modifier.weight(1f)) {
                    Text("${option.label} voice", color = Text, fontSize = 13.sp)
                    Text(
                        if (option == AgentGender.FEMININE) "Selects a feminine neural voice." else "Selects a masculine neural voice.",
                        color = Muted, fontSize = 11.sp
                    )
                }
            }
        }
        if (status.isNotBlank()) Text(status, color = Mint, fontSize = 11.sp)
    }
}
