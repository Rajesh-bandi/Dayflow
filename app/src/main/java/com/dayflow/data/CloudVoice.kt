package com.dayflow.data

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/**
 * Natural speech through the gateway the user already configured.
 *
 * This reuses the existing OmniRoute base URL and key and calls the OpenAI-compatible
 * /v1/audio/speech route, so a natural voice needs no extra account or subscription. Every failure
 * throws so the caller can fall back to the on-device voice instead of going silent.
 */
object CloudVoice {

    /** Accepts a gateway base URL with or without the trailing /v1 segment. */
    internal fun speechEndpoint(baseUrl: String): String = endpoint(baseUrl, "/audio/speech")

    /** Model catalogue of the same gateway, used to discover which speech models it can serve. */
    internal fun modelsEndpoint(baseUrl: String): String = endpoint(baseUrl, "/models")

    private fun endpoint(baseUrl: String, path: String): String {
        val base = baseUrl.trim().trimEnd('/')
        return if (base.endsWith("/v1")) "$base$path" else "$base/v1$path"
    }

    /**
     * Model ids a gateway can actually speak. Whisper-style transcription models are excluded:
     * they appear under the same route family but consume audio instead of producing it.
     */
    internal fun isSpeechModel(modelId: String): Boolean {
        val id = modelId.lowercase(Locale.ROOT)
        if ("whisper" in id || "transcrib" in id || "embed" in id || "moderation" in id) return false
        return SPEECH_MARKERS.any(id::contains)
    }

    // Markers stay specific on purpose: chat models are often named after voices too
    // (the configured model here is literally "voiceAgent"), and a wrong pick is audible nonsense.
    private val SPEECH_MARKERS = listOf("tts", "speech", "kokoro", "piper", "sonic", "eleven")

    /** Speech-capable model names advertised by the gateway, for the voice picker. */
    fun speechModels(config: AiConfig): List<String> {
        require(config.baseUrl.isNotBlank()) { "No gateway configured." }
        val connection = openGet(modelsEndpoint(config.baseUrl), config)
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(160)
                throw IllegalStateException("Gateway returned HTTP $code. $detail")
            }
            val payload = connection.inputStream.bufferedReader().use { reader -> reader.readText() }
            val data = JSONObject(payload).optJSONArray("data") ?: return emptyList()
            val names = mutableListOf<String>()
            for (index in 0 until data.length()) {
                val entry = data.optJSONObject(index) ?: continue
                val id = entry.optString("id")
                if (id.isNotBlank()) names.add(id)
            }
            return names.filter(::isSpeechModel).distinct().sorted().take(30)
        } finally {
            connection.disconnect()
        }
    }

    private fun openGet(url: String, config: AiConfig): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 20_000
            setRequestProperty("Accept", "application/json")
            if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }

    fun synthesize(config: AiConfig, text: String): ByteArray {
        require(config.baseUrl.isNotBlank()) { "No gateway configured." }
        require(config.ttsModel.isNotBlank()) { "No voice model configured." }
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "Nothing to say." }

        val endpoint = speechEndpoint(config.baseUrl)
        val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "audio/mpeg, audio/*")
            if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        try {
            // The endpoint caps input at 4096 characters.
            val body = JSONObject()
                .put("model", config.ttsModel.trim())
                .put("input", trimmed.take(4000))
                .put("voice", config.ttsVoice.trim().ifBlank { "alloy" })
                .put("response_format", "mp3")
                .put("speed", 1.0)
                .toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

            val code = connection.responseCode
            if (code !in 200..299) {
                val detail = connection.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty().take(160)
                throw IllegalStateException("Voice service returned HTTP $code. $detail")
            }
            val bytes = connection.inputStream.use { it.readBytes() }
            val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
            if (bytes.isEmpty()) throw IllegalStateException("Voice service returned no audio.")
            // Some gateways answer 200 with a JSON error body; that is not audio.
            if (contentType.contains("json") || bytes[0] == '{'.code.toByte()) {
                throw IllegalStateException("Voice service returned an error instead of audio.")
            }
            return bytes
        } finally {
            connection.disconnect()
        }
    }
}
