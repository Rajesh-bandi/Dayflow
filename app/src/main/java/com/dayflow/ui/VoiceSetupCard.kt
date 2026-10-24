package com.dayflow.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.dayflow.DayflowApplication
import com.dayflow.data.AgentGender
import com.dayflow.data.NeuralVoice
import com.dayflow.data.VoiceEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * First-run identity and voice setup.
 *
 * Without this a fresh install speaks with Android's built-in synthesiser — precisely the robotic voice
 * that makes an assistant feel like a machine — and the natural voice stays hidden in Settings. The name
 * and the voice are asked for here, before the first reply, and both are skippable.
 */
@Composable
internal fun NaturalVoiceSetupCard() {
    val context = LocalContext.current
    val app = context.applicationContext as DayflowApplication
    val prefs by app.preferences.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var draftName by remember { mutableStateOf(prefs.agentName) }
    var gender by remember { mutableStateOf(prefs.agentGender) }
    var selectedId by remember { mutableStateOf(NeuralVoice.recommendedModel(prefs.agentGender) { id -> NeuralVoice.isInstalled(context, id) }) }
    val selected = remember(selectedId) { NeuralVoice.model(selectedId) ?: NeuralVoice.MODELS.first() }
    var installTick by remember { mutableStateOf(0) }
    val installed = remember(selectedId, installTick) { NeuralVoice.isInstalled(context, selected.id) }
    var busy by remember { mutableStateOf(false) }
    var fraction by remember { mutableStateOf(0f) }
    var progressText by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    val player = remember { mutableStateOf<android.media.MediaPlayer?>(null) }

    fun playSample() {
        status = "Preparing a sample on this phone…"
        scope.launch {
            val file = withContext(Dispatchers.IO) {
                runCatching {
                    NeuralVoice.synthesize(
                        context, selected.id,
                        "Hi, I'm ${draftName.ifBlank { "your assistant" }}. This is how I'll sound from now on.",
                        gender = gender
                    )
                }.getOrNull()
            }
            if (file == null) {
                status = "The sample could not be generated. Try downloading the voice again."
                return@launch
            }
            val played = runCatching {
                player.value?.let { runCatching { it.stop() }; runCatching { it.release() } }
                player.value = android.media.MediaPlayer().apply {
                    setAudioAttributes(
                        android.media.AudioAttributes.Builder()
                            .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                            .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                            .build()
                    )
                    setDataSource(file.absolutePath)
                    setOnCompletionListener { done -> done.release(); file.delete(); player.value = null }
                    setOnErrorListener { err, _, _ -> err.release(); file.delete(); player.value = null; true }
                    prepare()
                    start()
                }
            }.isSuccess
            status = if (played) "Playing the sample." else "This device could not play the sample."
        }
    }

    fun setUpVoice() {
        busy = true
        status = ""
        progressText = "Connecting…"
        scope.launch {
            app.preferences.setAgentName(draftName)
            app.preferences.setAgentGender(gender)
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    NeuralVoice.install(context, selected) { copied, total ->
                        fraction = if (total > 0) (copied.toFloat() / total).coerceIn(0f, 1f) else 0f
                        progressText = if (total > 0) {
                            "Downloading ${copied / 1_048_576} of ${total / 1_048_576} MB"
                        } else {
                            "Downloading ${copied / 1_048_576} MB"
                        }
                    }
                }
            }
            result.onSuccess {
                app.preferences.setNeuralVoiceModel(selected.id)
                app.preferences.setVoiceEngine(VoiceEngine.NEURAL_ON_DEVICE)
                // Load the model now so the first spoken reply is instant rather than the slowest one.
                withContext(Dispatchers.IO) { NeuralVoice.warmUp(context, selected.id) }
                installTick++
                status = "Ready. ${app.preferences.state.value.agentName} will speak with this voice from the first reply."
            }
            result.onFailure { failure ->
                status = failure.message ?: "The download failed. Check your connection and try again."
            }
            busy = false
            progressText = ""
        }
    }

    CardBox {
        Text("Name your assistant", color = Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
        Text("It answers as whoever you name it, and presents with the voice you pick.", color = Muted, fontSize = 12.sp)
        OutlinedTextField(
            draftName, { draftName = it },
            label = { Text("Assistant name") },
            placeholder = { Text(com.dayflow.data.DEFAULT_AGENT_NAME) },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AgentGender.entries.forEach { option ->
                TextButton(onClick = {
                    gender = option
                    app.preferences.setAgentGender(option)
                    selectedId = NeuralVoice.recommendedModel(option) { id -> NeuralVoice.isInstalled(context, id) }
                    status = ""
                }) {
                    Text(
                        "${option.label} voice",
                        color = if (option == gender) Lavender else Muted,
                        fontWeight = if (option == gender) FontWeight.SemiBold else FontWeight.Normal,
                        fontSize = 12.sp
                    )
                }
            }
        }
        Text(
            "Android's built-in voice is a machine reading words. A neural voice runs on this phone, works offline, needs no account or key, and sounds like a person.",
            color = Muted, fontSize = 12.sp
        )
        Spacer(Modifier.height(4.dp))
        when {
            busy -> {
                LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth(), color = Lavender, trackColor = Raised)
                Text(progressText.ifBlank { "Starting…" }, color = Lavender, fontSize = 11.sp)
            }
            installed -> {
                Column {
                    Text("Installed · speaks offline on this device", color = Mint, fontSize = 12.sp)
                    Text(selected.label, color = Text, fontSize = 12.sp)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { playSample() }, colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink)) { Text("Hear a sample") }
                    TextButton(onClick = { app.preferences.setVoiceEngine(VoiceEngine.SYSTEM); status = "Android's voice will be used. You can change this in Settings." }) {
                        Text("Use Android's voice", color = Muted, fontSize = 11.sp)
                    }
                }
            }
            else -> {
                Text("Voices that speak as a ${gender.label.lowercase()} voice", color = Text, fontSize = 12.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NeuralVoice.modelsFor(gender).forEach { option ->
                        TextButton(onClick = { selectedId = option.id; status = "" }) {
                            Text(
                                "${option.label.split("·").first().trim()} · ${option.archiveMegabytes} MB",
                                color = if (option.id == selectedId) Lavender else Muted,
                                fontWeight = if (option.id == selectedId) FontWeight.SemiBold else FontWeight.Normal,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
                Text(selected.detail, color = Muted, fontSize = 11.sp)
                Button(
                    onClick = { setUpVoice() },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Lavender, contentColor = Ink)
                ) { Text("Set up my natural voice") }
                Text("You can skip this and set it up any time in Settings.", color = Muted, fontSize = 11.sp)
            }
        }
        if (status.isNotBlank()) Text(status, color = if (status.contains("could not") || status.contains("failed")) Amber else Mint, fontSize = 11.sp)
    }
}
