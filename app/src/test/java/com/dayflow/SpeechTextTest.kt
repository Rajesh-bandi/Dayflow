package com.dayflow

import com.dayflow.data.AssistantResponseFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Text handed to a voice engine has to be things a person would actually say: no Markdown, no
 * shorthand symbols, and no leftover labels. This is what stops a good voice reading as robotic.
 */
class SpeechTextTest {

    @Test
    fun removesMarkdownBeforeSpeaking() {
        val spoken = AssistantResponseFormatter.forSpeech("**Bold** heading\n- first item\n- second item")
        assertFalse("markdown survived: $spoken", spoken.contains("*"))
        assertFalse("a bullet survived: $spoken", spoken.contains("-"))
        assertTrue(spoken.endsWith("."))
    }

    @Test
    fun expandsShorthandThatReadsAsNoise() {
        assertTrue(AssistantResponseFormatter.forSpeech("Take it, e.g., after lunch").contains("for example"))
        assertTrue(AssistantResponseFormatter.forSpeech("Sleep, i.e., eight hours").contains("that is"))
        assertTrue(AssistantResponseFormatter.forSpeech("Bring a pen, etc.").contains("et cetera"))
        assertTrue(AssistantResponseFormatter.forSpeech("Tell him/her tonight").contains("him or her"))
        assertTrue(AssistantResponseFormatter.forSpeech("2 + 1 tasks left").contains("plus"))
        assertTrue(AssistantResponseFormatter.forSpeech("Total = 4 tasks").contains("equals"))
    }

    @Test
    fun leavesDatesAloneForTheVoiceToRead() {
        assertTrue(AssistantResponseFormatter.forSpeech("The meeting is on 10/15 at 9:30").contains("10/15"))
    }

    @Test
    fun turnsRangesIntoSpokenWords() {
        assertTrue(AssistantResponseFormatter.forSpeech("Focus 4:00–5:00 PM").contains("4:00 to 5:00"))
        assertTrue(AssistantResponseFormatter.forSpeech("Rest 25 min").contains("25 minutes"))
    }

    @Test
    fun dropsOtherPeopleSClosingSmallTalk() {
        val spoken = AssistantResponseFormatter.forSpeech("Your next block starts at 4. Is there anything else I can help with?")
        assertFalse("unasked closing survived: $spoken", spoken.contains("anything else"))
    }
}
