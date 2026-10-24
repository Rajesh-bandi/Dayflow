package com.dayflow.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.DayOfWeek

enum class VoiceEngine { AUTOMATIC, NEURAL_ON_DEVICE, GATEWAY, SYSTEM }

/** Which voice the assistant presents with. It picks the matching neural voice. */
enum class AgentGender(val label: String) { FEMININE("Feminine"), MASCULINE("Masculine") }

const val DEFAULT_AGENT_NAME = "Lumi"

/** One-line name, trimmed and length-limited so it cannot break the layout or a sentence. */
internal fun sanitisedAgentName(raw: String): String =
    raw.trim().replace(Regex("\\s+"), " ").take(24).ifBlank { DEFAULT_AGENT_NAME }

data class DayflowPreferences(
    val firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY,
    val remindersEnabled: Boolean = false,
    val use24HourTime: Boolean = true,
    val lightTheme: Boolean = false,
    val onboardingComplete: Boolean = false,
    val voiceEngine: VoiceEngine = VoiceEngine.AUTOMATIC,
    val neuralVoiceModel: String = NeuralVoice.DEFAULT_MODEL_ID,
    val agentName: String = DEFAULT_AGENT_NAME,
    val agentGender: AgentGender = AgentGender.FEMININE
)

/** Small, synchronous local preference store; values are available immediately at app launch. */
class AppPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("dayflow_preferences", Context.MODE_PRIVATE)
    private val mutable = MutableStateFlow(read())
    val state: StateFlow<DayflowPreferences> = mutable

    private fun read() = DayflowPreferences(
        firstDayOfWeek = if (prefs.getString("first_day", "MONDAY") == "SUNDAY") DayOfWeek.SUNDAY else DayOfWeek.MONDAY,
        remindersEnabled = prefs.getBoolean("reminders", false),
        use24HourTime = prefs.getBoolean("24h", true),
        lightTheme = prefs.getBoolean("light_theme", false),
        onboardingComplete = prefs.getBoolean("onboarding_complete", false),
        voiceEngine = runCatching { VoiceEngine.valueOf(prefs.getString("voice_engine", null) ?: VoiceEngine.AUTOMATIC.name) }
            .getOrDefault(VoiceEngine.AUTOMATIC),
        neuralVoiceModel = prefs.getString("neural_voice_model", null) ?: NeuralVoice.DEFAULT_MODEL_ID,
        agentName = sanitisedAgentName(prefs.getString("agent_name", null) ?: DEFAULT_AGENT_NAME),
        agentGender = runCatching { AgentGender.valueOf(prefs.getString("agent_gender", null) ?: AgentGender.FEMININE.name) }
            .getOrDefault(AgentGender.FEMININE)
    )
    fun setFirstDay(day: DayOfWeek) { prefs.edit().putString("first_day", day.name).apply(); mutable.value = read() }
    fun setReminders(enabled: Boolean) { prefs.edit().putBoolean("reminders", enabled).apply(); mutable.value = read() }
    fun set24Hour(enabled: Boolean) { prefs.edit().putBoolean("24h", enabled).apply(); mutable.value = read() }
    fun setLightTheme(enabled: Boolean) { prefs.edit().putBoolean("light_theme", enabled).apply(); mutable.value = read() }
    fun completeOnboarding() { prefs.edit().putBoolean("onboarding_complete", true).apply(); mutable.value = read() }
    fun setVoiceEngine(engine: VoiceEngine) { prefs.edit().putString("voice_engine", engine.name).apply(); mutable.value = read() }
    fun setNeuralVoiceModel(modelId: String) { prefs.edit().putString("neural_voice_model", modelId).apply(); mutable.value = read() }
    fun setAgentName(raw: String) { prefs.edit().putString("agent_name", sanitisedAgentName(raw)).apply(); mutable.value = read() }
    fun setAgentGender(gender: AgentGender) { prefs.edit().putString("agent_gender", gender.name).apply(); mutable.value = read() }
}
