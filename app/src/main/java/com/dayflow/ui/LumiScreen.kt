package com.dayflow.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import androidx.core.content.ContextCompat
import com.dayflow.DayflowApplication
import com.dayflow.data.LumiAiClient
import com.dayflow.data.LumiFunctionCall
import com.dayflow.data.RoutineActionResult
import com.dayflow.data.RoutineToolService
import com.dayflow.data.AssistantToolService
import com.dayflow.data.AssistantActionResult
import com.dayflow.data.AssistantResponseFormatter
import com.dayflow.data.CurrentDateTimeTool
import com.dayflow.data.LumiMemoryService
import com.dayflow.data.local.AiMessageEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import com.dayflow.domain.WeekUtils
import com.dayflow.domain.RecurrenceEngine
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

private const val VOICE_LOG_TAG = "LumiVoice"

@Composable
internal fun LumiScreen(nav: NavHostController, startVoice: Boolean = false) {
    val context = LocalContext.current
    val app = context.applicationContext as DayflowApplication
    fun setVoicePhase(phase: String) {
        Log.i(VOICE_LOG_TAG, "voice.phase.request phase=$phase")
        runCatching {
            context.startService(Intent(context, com.dayflow.LumiVoiceControlService::class.java)
                .setAction(com.dayflow.LumiVoiceControlService.ACTION_SET_VOICE_PHASE)
                .putExtra(com.dayflow.LumiVoiceControlService.EXTRA_VOICE_PHASE, phase))
        }.onFailure { Log.e(VOICE_LOG_TAG, "voice.phase.send_failed phase=$phase", it) }
    }
    var mode by remember { mutableStateOf("routine") }
    var input by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var pendingActions by remember { mutableStateOf<List<PendingAction>>(emptyList()) }
    var calendarPermissionRequired by remember { mutableStateOf(false) }
    var lastCalendarPrompt by remember { mutableStateOf("") }
    var recording by remember { mutableStateOf(false) }
    var processingVoice by remember { mutableStateOf(false) }
    var speaking by remember { mutableStateOf(false) }
    var voiceError by remember { mutableStateOf<String?>(null) }
    var voicePressHeld by remember { mutableStateOf(false) }
    var pendingVoiceTranscript by remember { mutableStateOf<String?>(null) }
    var speakPendingResult by remember { mutableStateOf(false) }
    var activePlayer by remember { mutableStateOf<android.media.MediaPlayer?>(null) }
    var localFallbackVoice by remember { mutableStateOf<Voice?>(null) }
    var currentSpeechText by remember { mutableStateOf("") }
    var voiceRequestInProgress by remember { mutableStateOf(false) }
    var voiceProgressMessage by remember { mutableStateOf("") }
    var progressSpeechCompletion by remember { mutableStateOf<CompletableDeferred<Unit>?>(null) }
    val voiceStatusTurns = remember { mutableStateMapOf<String, Int>() }
    val recognizer = remember(context) { if (SpeechRecognizer.isRecognitionAvailable(context)) SpeechRecognizer.createSpeechRecognizer(context) else null }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    DisposableEffect(context) {
        lateinit var engine: TextToSpeech
        engine = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                engine.setAudioAttributes(android.media.AudioAttributes.Builder()
                    .setUsage(android.media.AudioAttributes.USAGE_ASSISTANT)
                    .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build())
                val locale = Locale.getDefault()
                engine.setLanguage(locale)
                val voices = engine.voices.orEmpty().filter { it.locale.language.equals(locale.language, ignoreCase = true) }
                // Prefer the exact locale, then the most natural modelling the engine installs, and keep a
                // fully-offline voice ready so a network voice that fails mid-reply can fall back instantly.
                val bestVoice = voices.maxWithOrNull(
                    compareByDescending<Voice> { it.locale == locale }
                        .thenByDescending { it.locale.country.equals(locale.country, ignoreCase = true) }
                        .thenByDescending { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                )
                localFallbackVoice = voices.filterNot { it.isNetworkConnectionRequired }.maxByOrNull { it.quality }
                if (bestVoice != null) engine.setVoice(bestVoice)
                // Slightly slower and warmer than the default, which reads as less robotic.
                engine.setSpeechRate(0.97f)
                engine.setPitch(1.03f)
            } else voiceError = "Text-to-speech is unavailable. Check your Android speech settings."
        }
        tts = engine
        engine.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) { Log.i(VOICE_LOG_TAG, "tts.playback_started utterance=${utteranceId.orEmpty()}"); speaking = true }
            override fun onDone(utteranceId: String?) {
                Log.i(VOICE_LOG_TAG, "tts.finished utterance=${utteranceId.orEmpty()}")
                speaking = false
                if (utteranceId == "lumi_progress") { progressSpeechCompletion?.complete(Unit); setVoicePhase("PROCESSING") }
                if (utteranceId == "lumi_response" || utteranceId == "lumi_response_fallback") {
                    setVoicePhase("IDLE")
                    context.startService(Intent(context, com.dayflow.LumiVoiceControlService::class.java).setAction(com.dayflow.LumiVoiceControlService.ACTION_RELEASE_WAKE))
                }
            }
            @Deprecated("Deprecated in Java") override fun onError(utteranceId: String?) {
                Log.e(VOICE_LOG_TAG, "tts.failed utterance=${utteranceId.orEmpty()}")
                speaking = false
                if (utteranceId == "lumi_progress") { progressSpeechCompletion?.complete(Unit); setVoicePhase("PROCESSING") }
                if (utteranceId == "lumi_response" && engine.voice?.isNetworkConnectionRequired == true) {
                    localFallbackVoice?.let { voice ->
                        engine.setVoice(voice)
                        engine.speak(currentSpeechText, TextToSpeech.QUEUE_FLUSH, null, "lumi_response_fallback")
                    }
                } else if (utteranceId == "lumi_response" || utteranceId == "lumi_response_fallback") {
                    setVoicePhase("IDLE")
                    context.startService(Intent(context, com.dayflow.LumiVoiceControlService::class.java).setAction(com.dayflow.LumiVoiceControlService.ACTION_RELEASE_WAKE))
                }
            }
        })
        onDispose { engine.stop(); engine.shutdown(); tts = null }
    }
    val messages by app.aiDao.observeMessages(mode).collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    fun releaseVoiceWake() {
        runCatching {
            context.startService(Intent(context, com.dayflow.LumiVoiceControlService::class.java)
                .setAction(com.dayflow.LumiVoiceControlService.ACTION_RELEASE_WAKE))
        }
    }
    fun stopSpeaking() {
        activePlayer?.let { player -> runCatching { player.stop() }; runCatching { player.release() } }
        activePlayer = null
        tts?.stop()
        speaking = false
    }
    /** Plays a generated voice file through the media audio stream and cleans it up afterwards. */
    fun playVoiceFile(file: java.io.File, tag: String): Boolean {
        activePlayer?.let { runCatching { it.stop() }; runCatching { it.release() } }
        activePlayer = null
        tts?.stop()
        val player = android.media.MediaPlayer()
        val started = runCatching {
            player.setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SPEECH)
                .build())
            player.setDataSource(file.absolutePath)
            player.setOnPreparedListener { prepared -> speaking = true; prepared.start() }
            player.setOnCompletionListener { done -> speaking = false; done.release(); activePlayer = null; file.delete(); setVoicePhase("IDLE"); releaseVoiceWake() }
            player.setOnErrorListener { failed, _, _ -> speaking = false; runCatching { failed.release() }; activePlayer = null; setVoicePhase("IDLE"); true }
            player.prepareAsync()
        }.isSuccess
        if (!started) { runCatching { player.release() }; return false }
        activePlayer = player
        Log.i(VOICE_LOG_TAG, "$tag.started")
        return true
    }

    /**
     * Speaks a reply with the voice the user chose, and never goes silent: a neural voice on the device
     * is tried first because it is the most natural and needs no server, then the gateway, then Android.
     */
    fun speakReply(text: String) {
        val spoken = AssistantResponseFormatter.forSpeech(text)
        if (spoken.isBlank()) { setVoicePhase("IDLE"); return }
        currentSpeechText = spoken
        setVoicePhase("RESPONDING")
        scope.launch {
            val chosen = app.preferences.state.value
            val config = app.aiConfiguration.read()
            val systemOnly = chosen.voiceEngine == com.dayflow.data.VoiceEngine.SYSTEM
            val neuralAllowed = !systemOnly && chosen.voiceEngine != com.dayflow.data.VoiceEngine.GATEWAY
            if (neuralAllowed && com.dayflow.data.NeuralVoice.isInstalled(context, chosen.neuralVoiceModel)) {
                val wav = withContext(Dispatchers.IO) {
                    runCatching {
                        com.dayflow.data.NeuralVoice.synthesize(
                            context, chosen.neuralVoiceModel, spoken, gender = chosen.agentGender
                        )
                    }.getOrNull()
                }
                if (wav != null && playVoiceFile(wav, "neural_voice")) return@launch
                Log.w(VOICE_LOG_TAG, "neural_voice.unavailable")
            }
            if (!systemOnly && config.ttsModel.isNotBlank()) {
                val audio = withContext(Dispatchers.IO) { runCatching { com.dayflow.data.CloudVoice.synthesize(config, spoken) }.getOrNull() }
                if (audio != null && audio.isNotEmpty()) {
                    val file = java.io.File(context.cacheDir, "lumi_reply.mp3")
                    if (runCatching { file.writeBytes(audio) }.isSuccess && playVoiceFile(file, "cloud_voice")) return@launch
                }
                Log.w(VOICE_LOG_TAG, "cloud_voice.unavailable falling back to Android speech")
            }
            val result = tts?.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "lumi_response")
            Log.i(VOICE_LOG_TAG, "tts.started utterance=lumi_response accepted=${result == TextToSpeech.SUCCESS}")
            if (result != TextToSpeech.SUCCESS) setVoicePhase("IDLE")
        }
    }
    suspend fun announceVoiceProgress(text: String) {
        val engine = tts ?: return
        val completion = CompletableDeferred<Unit>()
        progressSpeechCompletion = completion
        val started = engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "lumi_progress") == TextToSpeech.SUCCESS
        Log.i(VOICE_LOG_TAG, "tts.started utterance=lumi_progress accepted=$started")
        if (started) withTimeoutOrNull(2500) { completion.await() }
        if (progressSpeechCompletion === completion) progressSpeechCompletion = null
    }
    /**
     * A status line that matches the work the request actually triggers, rotated so the same sentence
     * does not repeat back to back.
     */
    fun chooseVoiceProgress(prompt: String, requestMode: String): String {
        val category = com.dayflow.data.LumiStatusPhrases.categoryFor(prompt, requestMode)
        val turn = voiceStatusTurns[category.name] ?: 0
        voiceStatusTurns[category.name] = turn + 1
        return com.dayflow.data.LumiStatusPhrases.pick(category, turn)
    }
    fun submit(text: String = input, fromVoice: Boolean = false, persistUser: Boolean = true) {
        val prompt = text.trim()
        if (prompt.isEmpty() || busy) return
        input = ""; error = null; busy = true; pendingActions = emptyList(); calendarPermissionRequired = false
        val requestMode = mode
        // Set for typed requests too: the chat window shows the same honest status the voice would say.
        voiceProgressMessage = chooseVoiceProgress(prompt, requestMode)
        if (fromVoice) {
            Log.i(VOICE_LOG_TAG, "audio.submitted transcriptLength=${prompt.length} mode=$requestMode")
            setVoicePhase("PROCESSING")
            voiceRequestInProgress = true
        }
        scope.launch {
            if (persistUser) app.aiDao.insertMessage(AiMessageEntity(mode = requestMode, role = "user", content = prompt))
            if (fromVoice) announceVoiceProgress(voiceProgressMessage)
            val answer = runCatching {
                withContext(Dispatchers.IO) {
                    val memoryService = LumiMemoryService(app.aiDao)
                    memoryService.learnFromMessage(prompt)
                    val archivedMessages = app.aiDao.recentMessages(requestMode, 500).asReversed()
                    val justSavedMessageId = if (persistUser) archivedMessages.lastOrNull { it.role == "user" && it.content == prompt }?.id else null
                    val recentHistory = archivedMessages.filterNot { it.id == justSavedMessageId }.takeLast(8)
                    val recalledHistory = memoryService.relevantConversationContext(requestMode, prompt)
                        .filterNot { it.id == justSavedMessageId }
                    val history = (recalledHistory + recentHistory).distinctBy { it.id }
                        .sortedBy { it.createdAtMillis }.takeLast(12).map { it.role to it.content }
                    val profile = app.aiDao.getProfile()
                    val memories = memoryService.relevant(prompt)
                    val personal = buildString {
                        if (!profile?.name.isNullOrBlank()) append("User name: ${profile?.name}. ")
                        if (!profile?.preferences.isNullOrBlank()) append("Preferences: ${profile?.preferences}. ")
                        if (!profile?.goals.isNullOrBlank()) append("Goals: ${profile?.goals}. ")
                        if (memories.isNotEmpty()) append("Saved user memories: ${memories.joinToString("; ") { it.content }}. ")
                    }
                    val routine = requestMode == "routine"
                    val identity = app.preferences.state.value
                    // The assistant answers as whoever the user named it, with the voice they chose.
                    var localContext = "You are ${identity.agentName}, the user's personal planning assistant." +
                        " You present with a ${identity.agentGender.label.lowercase()} speaking voice; refer to yourself as ${identity.agentName}." +
                        " Active mode: ${if (routine) "Routine" else "Assistant"}. " + personal + if (routine) routineContext(app, prompt) else ""
                    if (fromVoice) localContext += " This is a spoken interaction. Keep the answer to one brief, natural sentence when possible, at most two short sentences. Do not read source links aloud; the user can see them in chat."
                    val client = LumiAiClient(app.aiConfiguration)
                    val service = RoutineToolService(app.repository, app.aiDao)
                    val assistantService = AssistantToolService(context.applicationContext, app.aiConfiguration)
                    val tools = when (requestMode) { "routine" -> service.toolDefinitions(); else -> assistantService.toolDefinitions() }
                    LumiMemoryService(app.aiDao).toolDefinitions().let { memoryTools ->
                        for (index in 0 until memoryTools.length()) tools.put(memoryTools.getJSONObject(index))
                    }
                    tools.put(CurrentDateTimeTool.definition())
                    val followups = mutableListOf<JSONObject>()
                    val explicitSearch = !routine && Regex("\\b(search|browse|look up|find online|research|search about|search for)\\b", RegexOption.IGNORE_CASE).containsMatchIn(prompt) &&
                        !Regex("https?://\\S+", RegexOption.IGNORE_CASE).containsMatchIn(prompt)
                    var verifiedSearchResults: String? = null
                    if (explicitSearch) {
                        val searchCall = LumiFunctionCall("lumi_search", "search_web", JSONObject().put("query", prompt))
                        val searchResult = assistantService.execute(searchCall)
                        val resultText = when (searchResult) {
                            is AssistantActionResult.Complete -> searchResult.message
                            is AssistantActionResult.Failure -> return@withContext ChatOutcome.Answer("I couldn't search the web right now. ${searchResult.message}")
                            else -> return@withContext ChatOutcome.Answer("I couldn't search the web right now.")
                        }
                        if (resultText.contains("No current web facts were verified")) {
                            return@withContext ChatOutcome.Answer("I couldn't get usable web search results just now.")
                        }
                        verifiedSearchResults = resultText
                        localContext += " Web search results already retrieved for this request; answer using these sources and do not search again:\n$resultText"
                    }
                    // Multi-step agent loop. A model can return several tool calls at once, and a
                    // multi-part request needs several rounds. Every call must be executed and answered
                    // with a matching tool result, otherwise the next request is rejected and the plan stalls.
                    val maxRounds = 12
                    var response = client.completeTurn(prompt, history, localContext, tools)
                    var rounds = 0
                    val pending = mutableListOf<PendingAction>()
                    val failed = mutableListOf<String>()
                    while (response.calls.isNotEmpty() && rounds++ < maxRounds) {
                        followups += response.assistantMessage
                        for (call in response.calls) {
                            val action: Any = when {
                                call.name == CurrentDateTimeTool.NAME -> RoutineActionResult.Complete(CurrentDateTimeTool.read())
                                call.name == "save_memory" -> memoryService.execute(call)
                                routine -> service.execute(call)
                                else -> assistantService.execute(call)
                            }
                            val content: String = when (action) {
                                is RoutineActionResult.Complete -> action.message
                                is AssistantActionResult.Complete -> action.message
                                is RoutineActionResult.Failure -> { failed += action.message; "ERROR: ${action.message}" }
                                is AssistantActionResult.Failure -> { failed += action.message; "ERROR: ${action.message}" }
                                is RoutineActionResult.Confirm -> {
                                    pending += PendingAction(action.call, action.message, requestMode)
                                    "AWAITING_USER_CONFIRMATION: the app is asking the user to approve this now. Do not say it is done or cancelled. Continue with any remaining steps."
                                }
                                is AssistantActionResult.Confirm -> {
                                    pending += PendingAction(action.call, action.message, requestMode)
                                    "AWAITING_USER_CONFIRMATION: the app is asking the user to approve this now. Do not say it is done or cancelled. Continue with any remaining steps."
                                }
                                is AssistantActionResult.PermissionRequired -> "PERMISSION_REQUIRED: ${action.message} Tell the user to grant access, then stop."
                                else -> "No result."
                            }
                            followups += JSONObject().put("role", "tool").put("tool_call_id", call.id).put("content", content)
                        }
                        response = client.completeTurn(prompt, history, localContext, tools, followups)
                    }
                    if (pending.isNotEmpty()) return@withContext ChatOutcome.NeedsConfirmation(pending)
                    val answerText = response.text
                    val modelDeclinedSearch = explicitSearch && Regex("(?i)(no|don't|do not|cannot|can't)\\s+(have|access|use)?\\s*(access to )?(the )?(web )?(search|internet|browsing|tools?)\\b|search (tool|capability) (is )?(not|unavailable)")
                        .containsMatchIn(answerText)
                    if (modelDeclinedSearch && verifiedSearchResults != null) {
                        val resultSummary = verifiedSearchResults!!.substringAfter('\n', "").trim().split("\\n\\s*\\n".toRegex())
                            .take(2).joinToString(". ") { block -> block.lines().filter(String::isNotBlank).joinToString(". ") }
                        ChatOutcome.Answer("I found these web results: $resultSummary")
                    } else {
                        ChatOutcome.Answer(
                            answerText.ifBlank {
                                failed.firstOrNull() ?: "I could not complete that request. Please try again with the task and time details."
                            }
                        )
                    }
                }
            }
            if (answer.isSuccess) when (val outcome = answer.getOrThrow()) {
                is ChatOutcome.Answer -> {
                    val cleanText = AssistantResponseFormatter.forDisplay(outcome.text)
                    app.aiDao.insertMessage(AiMessageEntity(mode = requestMode, role = "assistant", content = cleanText))
                    if (fromVoice) speakReply(cleanText)
                }
                is ChatOutcome.NeedsConfirmation -> {
                    pendingActions = outcome.actions
                    speakPendingResult = fromVoice
                    val prompt = outcome.actions.first().message
                    app.aiDao.insertMessage(AiMessageEntity(mode = requestMode, role = "assistant", content = AssistantResponseFormatter.forDisplay(prompt)))
                    if (fromVoice) speakReply(prompt)
                }
                is ChatOutcome.NeedsPermission -> {
                    calendarPermissionRequired = true
                    lastCalendarPrompt = prompt
                    app.aiDao.insertMessage(AiMessageEntity(mode = requestMode, role = "assistant", content = AssistantResponseFormatter.forDisplay(outcome.message)))
                    if (fromVoice) speakReply(outcome.message)
                }
            } else {
                error = answer.exceptionOrNull()?.message ?: "Request failed"
                if (fromVoice) speakReply(error.orEmpty())
            }
            busy = false
            if (fromVoice) voiceRequestInProgress = false
        }
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.all { it }) submit(lastCalendarPrompt, persistUser = false)
        else error = "Calendar permission was denied. You can allow it in Android Settings."
    }
    val audioPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            runCatching { androidx.core.content.ContextCompat.startForegroundService(context, Intent(context, com.dayflow.LumiVoiceControlService::class.java)) }
                .onFailure { voiceError = "Couldn't enable background earbud controls: ${it.message.orEmpty()}" }
            setVoicePhase("RECORDING")
            startSpeech(recognizer, context) { recording = it; processingVoice = !it; if (!it) setVoicePhase("IDLE") }
        } else {
            Log.w(VOICE_LOG_TAG, "recording.permission_denied")
            setVoicePhase("IDLE")
            voiceError = "Microphone permission is required for push-to-talk."
        }
    }
    LaunchedEffect(Unit) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, com.dayflow.LumiVoiceControlService::class.java)
                .setAction(com.dayflow.LumiVoiceControlService.ACTION_VOICE_UI_ATTACHED)) }
                .onFailure { voiceError = "Couldn't enable background earbud controls: ${it.message.orEmpty()}" }
        }
    }
    DisposableEffect(recognizer) {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: android.os.Bundle?) { Log.i(VOICE_LOG_TAG, "recording.started source=recognizer"); recording = true; processingVoice = false; setVoicePhase("RECORDING") }
            override fun onBeginningOfSpeech() { recording = true }
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() { Log.i(VOICE_LOG_TAG, "recording.ended source=recognizer waitingForPress=$voicePressHeld"); recording = false; processingVoice = true }
            override fun onError(errorCode: Int) {
                Log.e(VOICE_LOG_TAG, "recognizer.failed code=$errorCode")
                recording = false; processingVoice = false
                voicePressHeld = false
                pendingVoiceTranscript = null
                setVoicePhase("IDLE")
                voiceError = when (errorCode) { SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "I didn't catch that. Hold the mic and try again."; else -> "Speech recognition failed. Check your network and microphone permission." }
            }
            override fun onResults(results: android.os.Bundle?) {
                recording = false; processingVoice = false
                val transcript = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty().trim()
                Log.i(VOICE_LOG_TAG, "recognizer.result length=${transcript.length} held=$voicePressHeld")
                if (transcript.isBlank()) voiceError = "No speech was recognized. Try again."
                else {
                    voiceError = null
                    if (voicePressHeld) { pendingVoiceTranscript = transcript; setVoicePhase("RECORDING") }
                    else { input = transcript; submit(transcript, fromVoice = true) }
                }
                if (transcript.isBlank()) setVoicePhase("IDLE")
            }
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
        onDispose {
            Log.i(VOICE_LOG_TAG, "recognizer.release recording=$recording processing=$processingVoice")
            recognizer?.cancel(); recognizer?.destroy()
            setVoicePhase("IDLE")
        }
    }
    fun requestOrStartVoice() {
        voiceError = null
        if (recognizer == null) { setVoicePhase("IDLE"); voiceError = "Speech recognition is not available on this device."; return }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, com.dayflow.LumiVoiceControlService::class.java)) }
                .onFailure { voiceError = "Couldn't enable background earbud controls: ${it.message.orEmpty()}" }
            setVoicePhase("RECORDING")
            startSpeech(recognizer, context) { recording = it; processingVoice = !it; if (!it) setVoicePhase("IDLE") }
        } else audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
    // Keep the pointer coroutine alive while Compose updates recording/busy state.
    // Keying pointerInput on those states cancels the press gesture before release,
    // which leaves SpeechRecognizer listening and makes push-to-talk unreliable.
    val latestMicPress = rememberUpdatedState<() -> Unit> {
        if (speaking) stopSpeaking()
        else if (!busy && !processingVoice) {
            voicePressHeld = true
            pendingVoiceTranscript = null
            requestOrStartVoice()
        }
    }
    val latestMicRelease = rememberUpdatedState<() -> Unit> {
        voicePressHeld = false
        if (recording) {
            recognizer?.stopListening()
            recording = false
            processingVoice = true
            setVoicePhase("PROCESSING")
        }
        pendingVoiceTranscript?.let { transcript ->
            pendingVoiceTranscript = null
            input = transcript
            submit(transcript, fromVoice = true)
        }
    }
    val latestEarbudToggle = rememberUpdatedState<() -> Unit> {
        voiceError = null
        Log.i(VOICE_LOG_TAG, "voice_toggle.state recording=$recording processing=$processingVoice pending=${pendingVoiceTranscript != null} speaking=$speaking busy=$busy")
        if (speaking) {
            stopSpeaking()
            setVoicePhase("IDLE")
        } else if (pendingVoiceTranscript != null && !busy) {
            val transcript = pendingVoiceTranscript.orEmpty()
            pendingVoiceTranscript = null
            voicePressHeld = false
            input = transcript
            submit(transcript, fromVoice = true)
        } else if (recording || processingVoice) {
            voicePressHeld = false
            if (recording) {
                recognizer?.stopListening()
                recording = false
                processingVoice = true
            }
        } else if (!busy) {
            voicePressHeld = true
            pendingVoiceTranscript = null
            requestOrStartVoice()
        }
    }
    DisposableEffect(context) {
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: android.content.Context?, intent: Intent?) {
                Log.i(VOICE_LOG_TAG, "voice_toggle.broadcast_received action=${intent?.action}")
                if (intent?.action == com.dayflow.LumiVoiceControlService.ACTION_TOGGLE_VOICE) latestEarbudToggle.value()
            }
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context, receiver,
            android.content.IntentFilter(com.dayflow.LumiVoiceControlService.ACTION_TOGGLE_VOICE),
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
        onDispose {
            context.unregisterReceiver(receiver)
            runCatching {
                context.startService(Intent(context, com.dayflow.LumiVoiceControlService::class.java)
                    .setAction(com.dayflow.LumiVoiceControlService.ACTION_VOICE_UI_DETACHED))
            }.onFailure { Log.w(VOICE_LOG_TAG, "voice_ui.detach_failed", it) }
        }
    }
    fun resolvePending(confirmed: Boolean) {
        val head = pendingActions.firstOrNull() ?: return
        val fromVoice = speakPendingResult
        pendingActions = pendingActions.drop(1)
        speakPendingResult = false
        busy = true
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                if (!confirmed) RoutineActionResult.Complete("Cancelled. No schedule change was made.")
                else if (head.mode == "routine") RoutineToolService(app.repository, app.aiDao).execute(head.call, confirmed = true)
                else AssistantToolService(context.applicationContext, app.aiConfiguration).execute(head.call, confirmed = true)
            }
            val message = when (result) {
                is RoutineActionResult.Complete -> result.message
                is RoutineActionResult.Failure -> result.message
                is RoutineActionResult.Confirm -> result.message
                is AssistantActionResult.Complete -> result.message
                is AssistantActionResult.Failure -> result.message
                is AssistantActionResult.Confirm -> result.message
                is AssistantActionResult.PermissionRequired -> result.message
                else -> "The operation did not return a result."
            }
            app.aiDao.insertMessage(AiMessageEntity(mode = head.mode, role = "assistant", content = AssistantResponseFormatter.forDisplay(message)))
            if (fromVoice) speakReply(message)
            if (result is AssistantActionResult.PermissionRequired) calendarPermissionRequired = true
            when (result) {
                is RoutineActionResult.Confirm -> { pendingActions = listOf(PendingAction(result.call, result.message, head.mode)) + pendingActions; speakPendingResult = fromVoice }
                is AssistantActionResult.Confirm -> { pendingActions = listOf(PendingAction(result.call, result.message, head.mode)) + pendingActions; speakPendingResult = fromVoice }
                else -> Unit
            }
            busy = false
        }
    }
    Column(Modifier.fillMaxSize().padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Default.ArrowBack, "Back", tint = Muted) }
            Column(Modifier.weight(1f)) { Text(LocalAgentName.current, color = Text, fontSize = 19.sp); Text("Your routine and personal assistant", color = Muted, fontSize = 11.sp) }
            TextButton(onClick = { nav.navigate("settings") }) { Text("Settings", color = Lavender) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            listOf("routine" to "Routine", "assistant" to "Assistant").forEachIndexed { index, (key, label) ->
                SegmentedButton(selected = mode == key, onClick = { if (pendingActions.isEmpty()) mode = key }, shape = SegmentedButtonDefaults.itemShape(index, 2)) { Text(label) }
            }
        }
        if (mode == "routine") Text("Ask about your saved schedule, progress, and gaps, or request a task change.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        else Text("Ask anything, search current information, or use your device calendar.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 10.dp)) {
            items(messages.sortedBy { it.createdAtMillis }, key = { it.id }) { message ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start) {
                    Card(colors = CardDefaults.cardColors(containerColor = if (message.role == "user") Raised else Surface), modifier = Modifier.fillMaxWidth(.9f)) {
                        Text(AssistantResponseFormatter.forDisplay(message.content), color = Text, modifier = Modifier.padding(12.dp), fontSize = 14.sp)
                    }
                }
            }
        }
        pendingActions.firstOrNull()?.let { action ->
            CardBox {
                Text(action.message, color = Text, fontSize = 13.sp)
                if (pendingActions.size > 1) Text("${pendingActions.size - 1} more change${if (pendingActions.size == 2) "" else "s"} waiting after this one", color = Muted, fontSize = 11.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { resolvePending(false) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Cancel") }
                    Button(onClick = { resolvePending(true) }, enabled = !busy, modifier = Modifier.weight(1f)) { Text("Confirm") }
                }
            }
        }
        if (calendarPermissionRequired) OutlinedButton(onClick = { calendarPermissionLauncher.launch(arrayOf(Manifest.permission.READ_CALENDAR, Manifest.permission.WRITE_CALENDAR)) }, modifier = Modifier.fillMaxWidth()) { Text("Grant calendar access") }
        if (voiceError != null) Text(voiceError!!, color = Danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp))
                Text(
                    when {
                        recording -> "Recording · release the mic or press your earbud to send"
                        processingVoice -> "Transcribing speech…"
                        busy && voiceProgressMessage.isNotBlank() -> voiceProgressMessage
                        speaking -> "${LocalAgentName.current} is speaking"
                        busy -> "${LocalAgentName.current} is thinking…"
                        else -> "Earbud: press to talk, press again to send · works while locked"
                    },
                    color = if (recording) Danger else Muted, fontSize = 11.sp
                )
        error?.let { Text(it, color = Danger, fontSize = 12.sp, modifier = Modifier.padding(vertical = 4.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(input, { input = it }, modifier = Modifier.weight(1f), placeholder = { Text("Ask Lumi…") }, maxLines = 4, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { submit() }))
            IconButton(onClick = { submit() }, enabled = !busy && input.isNotBlank()) { Icon(Icons.Default.Send, "Send", tint = Lavender) }
            Box(
                Modifier.size(52.dp).clip(CircleShape).background(if (recording) Danger else Raised)
                    .semantics { contentDescription = "Press and hold to talk to Lumi" }
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            latestMicPress.value()
                            tryAwaitRelease()
                            latestMicRelease.value()
                        })
                    },
                contentAlignment = Alignment.Center
            ) { Icon(if (speaking) Icons.Default.VolumeOff else if (recording) Icons.Default.Stop else Icons.Default.Mic, "Push to talk", tint = if (recording) Color.White else Lavender) }
        }
    }
    LaunchedEffect(startVoice) { if (startVoice) requestOrStartVoice() }
}

/** A write the AI proposed that is waiting for the user to approve it. */
private data class PendingAction(val call: LumiFunctionCall, val message: String, val mode: String)

private sealed interface ChatOutcome {
    data class Answer(val text: String) : ChatOutcome
    data class NeedsConfirmation(val actions: List<PendingAction>) : ChatOutcome
    data class NeedsPermission(val message: String) : ChatOutcome
}

private fun startSpeech(recognizer: SpeechRecognizer?, context: android.content.Context, state: (Boolean) -> Unit) {
    if (recognizer == null) { state(false); return }
    val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        // Generous silence windows so a natural pause does not end the request mid-sentence.
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
        putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
        putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false)
    }
    runCatching { recognizer.startListening(intent); state(true) }
        .onFailure { Log.e(VOICE_LOG_TAG, "recording.start_failed", it); state(false) }
}

private suspend fun routineContext(app: DayflowApplication, prompt: String): String {
    val today = LocalDate.now()
    val allTasks = app.repository.observeTasks().first()
    val tasks = allTasks.filter { RecurrenceEngine.occursOn(it, today) }.sortedBy { it.startTimeMinutes }
    val completions = app.repository.observeCompletionsBetween(today.minusDays(7).toEpochDay(), today.toEpochDay()).first().associateBy { it.taskId to it.dateEpochDay }
    val lines = tasks.map { task ->
        val state = completions[task.id to today.toEpochDay()]?.status ?: "PLANNED"
        "${WeekUtils.timeLabel(task.startTimeMinutes, true)}–${WeekUtils.timeLabel((task.startTimeMinutes + task.durationMinutes).coerceAtMost(1439), true)} ${task.title} (${task.durationMinutes} min, $state)"
    }
    val yesterday = today.minusDays(1)
    val yesterdayTasks = allTasks.filter { RecurrenceEngine.occursOn(it, yesterday) }
    val missed = yesterdayTasks.filter { task ->
        val completion = completions[task.id to yesterday.toEpochDay()]
        completion?.status != "COMPLETED" && completion?.status != "SKIPPED"
    }
    val missedLines = missed.joinToString("\n") { "${it.title} (${it.durationMinutes} min)" }.ifBlank { "None recorded as missed (there is no explicit missed status; only scheduled and incomplete records)." }
    val ordered = tasks.sortedBy { it.startTimeMinutes }
    var occupiedUntil = ordered.firstOrNull()?.let { it.startTimeMinutes + it.durationMinutes } ?: 0
    val gaps = buildList {
        ordered.drop(1).forEach { task ->
            if (task.startTimeMinutes > occupiedUntil) add("${WeekUtils.timeLabel(occupiedUntil, true)}–${WeekUtils.timeLabel(task.startTimeMinutes, true)} (${task.startTimeMinutes - occupiedUntil} min)")
            occupiedUntil = maxOf(occupiedUntil, task.startTimeMinutes + task.durationMinutes)
        }
    }
    val weekly = (0..6).map { today.minusDays(it.toLong()) }.reversed().joinToString("\n") { day ->
        val occurring = allTasks.filter { RecurrenceEngine.occursOn(it, day) }
        val done = occurring.count { completions[it.id to day.toEpochDay()]?.status == "COMPLETED" }
        "${day.dayOfWeek}: $done/${occurring.size} completed"
    }
    val monthStart = today.minusDays(29)
    val monthOccurrences = app.repository.observeCompletionsBetween(monthStart.toEpochDay(), today.toEpochDay()).first()
        .associateBy { it.taskId to it.dateEpochDay }
    val monthDays = (0..29).map { monthStart.plusDays(it.toLong()) }
    val performance = allTasks.mapNotNull { task ->
        val scheduled = monthDays.filter { RecurrenceEngine.occursOn(task, it) }
        if (scheduled.size < 3) return@mapNotNull null
        val completed = scheduled.count { day -> monthOccurrences[task.id to day.toEpochDay()]?.status == "COMPLETED" }
        "${task.title}: completed $completed of ${scheduled.size} scheduled occurrences"
    }.take(5)
    return "Today's saved tasks:\n${lines.joinToString("\n").ifBlank { "No tasks scheduled." }}\nGaps between today's scheduled tasks:\n${gaps.joinToString("\n").ifBlank { "No gaps between tasks." }}\nYesterday's incomplete scheduled tasks (potentially missed, not automatically classified):\n$missedLines\nSeven-day recorded progress (completed/occurring):\n$weekly\n30-day completion pattern (scheduled dates and recorded completions, not a judgment):\n${performance.ifEmpty { listOf("Not enough repeated scheduled dates to infer a pattern.") }.joinToString("\n")}\nCurrent request: $prompt"
}
