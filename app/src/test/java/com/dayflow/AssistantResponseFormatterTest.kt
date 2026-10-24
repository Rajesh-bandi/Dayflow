package com.dayflow

import com.dayflow.data.AssistantResponseFormatter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssistantResponseFormatterTest {

    @Test
    fun speechReadsTheWholeReplyInsteadOfAFragment() {
        val reply = "You missed two tasks yesterday. DSA practice was 45 minutes. Reading was 30 minutes. " +
            "You have 75 minutes of unfinished work. Would you like me to fit these into today?"
        val spoken = AssistantResponseFormatter.forSpeech(reply)

        // A previous build truncated spoken output to two sentences, so later details were never heard.
        assertTrue("expected the first detail", spoken.contains("DSA practice"))
        assertTrue("expected a mid-reply detail", spoken.contains("75 minutes"))
        assertTrue("expected the closing question", spoken.contains("Would you like me to fit these into today"))
        assertTrue("expected the full reply, got ${spoken.length} chars", spoken.length > 150)
    }

    @Test
    fun speechRewritesShorthandIntoSpokenWords() {
        assertEquals(
            "DSA practice from 4:00 to 5:00 PM for 45 minutes.",
            AssistantResponseFormatter.forSpeech("DSA practice from 4:00–5:00 PM for 45 min")
        )
        assertEquals(
            "You completed 80 percent of today's plan.",
            AssistantResponseFormatter.forSpeech("You completed 80% of today's plan")
        )
    }

    @Test
    fun speechDropsLinksAndDanglingLabels() {
        val spoken = AssistantResponseFormatter.forSpeech("Source: https://example.com/some/page")
        assertFalse(spoken.contains("example.com"))
        assertFalse(spoken.contains(":"))
    }

    @Test
    fun displayOutputHasNoMarkdownOrSymbols() {
        val display = AssistantResponseFormatter.forDisplay("**Done** - Added ✅ *DSA practice* at 4:00 PM ✨")
        assertFalse("markdown survived: $display", display.contains("*"))
        assertFalse("emoji survived: $display", display.contains("✅"))
        assertFalse("emoji survived: $display", display.contains("✨"))
        assertTrue(display.contains("Done"))
        assertTrue(display.contains("DSA practice"))
    }

    @Test
    fun taskIdsAreSpokenWithoutSymbols() {
        val spoken = AssistantResponseFormatter.forSpeech("Saved task 'Reading' (id=42)")
        assertFalse(spoken.contains("="))
        assertTrue(spoken.contains("id 42"))
    }
}
