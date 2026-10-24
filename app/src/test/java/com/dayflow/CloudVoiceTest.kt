package com.dayflow

import com.dayflow.data.CloudVoice
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudVoiceTest {

    @Test
    fun buildsTheSpeechRouteForAGatewayRoot() {
        assertEquals("http://192.168.1.20:8080/v1/audio/speech", CloudVoice.speechEndpoint("http://192.168.1.20:8080"))
    }

    @Test
    fun buildsTheSpeechRouteForABaseUrlThatAlreadyEndsInV1() {
        assertEquals("https://gateway.example.com/v1/audio/speech", CloudVoice.speechEndpoint("https://gateway.example.com/v1"))
    }

    @Test
    fun toleratesTrailingSlashesAndWhitespace() {
        assertEquals("https://gateway.example.com/v1/audio/speech", CloudVoice.speechEndpoint("  https://gateway.example.com/v1/  "))
    }

    @Test
    fun buildsTheModelsRouteTheSameWay() {
        assertEquals("https://gateway.example.com/v1/models", CloudVoice.modelsEndpoint("https://gateway.example.com/v1"))
        assertEquals("http://10.0.0.4:20128/v1/models", CloudVoice.modelsEndpoint("http://10.0.0.4:20128"))
    }

    @Test
    fun recognisesTheSpeechModelsAGatewayCanServe() {
        listOf(
            "tts-1",
            "tts-1-hd",
            "gpt-4o-mini-tts",
            "kokoro",
            "kokoro-82m",
            "speech-2.5-hd",
            "piper-en-gb",
            "eleven_multilingual_v2",
            "sonic-2"
        ).forEach { model -> assertTrue("$model should count as speech", CloudVoice.isSpeechModel(model)) }
    }

    @Test
    fun ignoresModelsThatConsumeAudioOrAreNotVoices() {
        listOf(
            "whisper-1",
            "gpt-4o-transcribe",
            "whisper-large-v3-turbo",
            "text-embedding-3-small",
            "gpt-4o",
            "voiceAgent",
            "o3-mini"
        ).forEach { model -> assertFalse("$model should not count as speech", CloudVoice.isSpeechModel(model)) }
    }
}
