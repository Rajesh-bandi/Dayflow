package com.dayflow

import com.dayflow.data.AgentGender
import com.dayflow.data.DEFAULT_AGENT_NAME
import com.dayflow.data.NeuralVoice
import com.dayflow.data.sanitisedAgentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A name the user types ends up in headings, spoken sentences and the model's instructions, so it has
 * to survive whitespace and abuse. Gender has to move the voice, or choosing "masculine" would leave a
 * feminine voice speaking.
 */
class AgentIdentityTest {

    @Test
    fun keepsANormalName() {
        assertEquals("Aria", sanitisedAgentName("Aria"))
        assertEquals("Max Power", sanitisedAgentName("  Max   Power  "))
    }

    @Test
    fun fallsBackToTheDefaultWhenNothingUsefulIsTyped() {
        assertEquals(DEFAULT_AGENT_NAME, sanitisedAgentName(""))
        assertEquals(DEFAULT_AGENT_NAME, sanitisedAgentName("    "))
        assertEquals(DEFAULT_AGENT_NAME, sanitisedAgentName("\n\t "))
    }

    @Test
    fun boundsTheNameSoItCannotBreakALayoutOrASentence() {
        val long = sanitisedAgentName("A".repeat(200))
        assertEquals(24, long.length)
    }

    @Test
    fun genderSelectsADistinctVoiceOnAMultiSpeakerModel() {
        val feminine = NeuralVoice.speakerId("kokoro", AgentGender.FEMININE)
        val masculine = NeuralVoice.speakerId("kokoro", AgentGender.MASCULINE)
        assertNotEquals("both genders must not speak with one voice", feminine, masculine)
        // Documented kokoro-en-v0_19 speakers: 2 is af_nicole, 6 is am_michael.
        assertEquals(2, feminine)
        assertEquals(6, masculine)
        assertTrue("speaker ids must exist in the model", feminine in 0..10 && masculine in 0..10)
    }

    @Test
    fun singleSpeakerModelsKeepTheirOwnSpeaker() {
        NeuralVoice.MODELS.filterNot { it.kokoro }.forEach { model ->
            assertEquals("${model.id} must not change speaker", model.speakerId, NeuralVoice.speakerId(model.id, AgentGender.FEMININE))
            assertEquals("${model.id} must not change speaker", model.speakerId, NeuralVoice.speakerId(model.id, AgentGender.MASCULINE))
        }
    }

    @Test
    fun eachGenderHasAVoiceThatCanActuallySpeakIt() {
        AgentGender.entries.forEach { gender ->
            val voices = NeuralVoice.modelsFor(gender)
            assertTrue("$gender has no voice to offer", voices.isNotEmpty())
            voices.forEach { voice ->
                assertTrue("${voice.id} cannot speak as $gender", gender in voice.genders)
                assertTrue("${voice.id} must be downloadable", voice.archiveMegabytes > 0)
            }
        }
        assertTrue("a masculine voice must be offered", NeuralVoice.modelsFor(AgentGender.MASCULINE).any { it.id == "piper-lessac" })
        assertTrue("a feminine voice must be offered", NeuralVoice.modelsFor(AgentGender.FEMININE).any { it.id == "piper-amy" })
    }

    @Test
    fun anInstalledVoiceWinsWhenTheGenderChanges() {
        assertEquals("piper-lessac", NeuralVoice.recommendedModel(AgentGender.MASCULINE) { it == "piper-lessac" })
        assertEquals("kokoro", NeuralVoice.recommendedModel(AgentGender.MASCULINE) { it == "kokoro" })
        assertEquals("piper-amy", NeuralVoice.recommendedModel(AgentGender.FEMININE) { false })
        // A voice that cannot speak the chosen gender must never be suggested when a better one is present.
        assertEquals("kokoro", NeuralVoice.recommendedModel(AgentGender.MASCULINE) { it == "piper-amy" || it == "kokoro" })
    }
}
