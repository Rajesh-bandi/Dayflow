package com.dayflow

import com.dayflow.data.LumiStatusCategory
import com.dayflow.data.LumiStatusPhrases
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The status line has to describe the work really being done, and it has to vary. */
class LumiStatusPhrasesTest {

    @Test
    fun recognisesTheWorkBehindCommonRequests() {
        val cases = listOf(
            "read https://example.com/article and summarise it" to LumiStatusCategory.WEB_PAGE,
            "what's on my calendar tomorrow" to LumiStatusCategory.CALENDAR,
            "will it rain this evening" to LumiStatusCategory.WEATHER,
            "what's the latest news about the election" to LumiStatusCategory.NEWS,
            "what time is it right now" to LumiStatusCategory.CLOCK,
            "set a timer for 20 minutes" to LumiStatusCategory.TIMER,
            "wake me at 6" to LumiStatusCategory.ALARM,
            "search for the best running shoes" to LumiStatusCategory.WEB_SEARCH,
            "how do i get to the airport" to LumiStatusCategory.TRAVEL,
            "how much is a new laptop" to LumiStatusCategory.PRICE,
            "translate this into french" to LumiStatusCategory.TRANSLATION,
            "calculate 18 percent of 2400" to LumiStatusCategory.MATH,
            "start a focus session for 45 minutes" to LumiStatusCategory.FOCUS,
            "remember that i prefer mornings" to LumiStatusCategory.MEMORY_SAVE,
            "what do you remember about me" to LumiStatusCategory.MEMORY_RECALL,
            "show my preferences" to LumiStatusCategory.PROFILE,
            "message my sister" to LumiStatusCategory.MESSAGING,
            "open spotify" to LumiStatusCategory.APP_OPEN,
            "how did my workout go" to LumiStatusCategory.HEALTH,
            "play some music" to LumiStatusCategory.MEDIA,
            "add a task to call the dentist" to LumiStatusCategory.ROUTINE_ADD,
            "move my 6pm task to 8pm" to LumiStatusCategory.ROUTINE_MOVE,
            "delete the standup task" to LumiStatusCategory.ROUTINE_DELETE,
            "mark the gym task complete" to LumiStatusCategory.ROUTINE_COMPLETE,
            "how am i doing this week" to LumiStatusCategory.PROGRESS,
            "what did i miss yesterday" to LumiStatusCategory.MISSED,
            "when am i free tomorrow" to LumiStatusCategory.FREE_TIME,
            "show my daily ritual" to LumiStatusCategory.RITUAL,
            "yes go ahead" to LumiStatusCategory.CONFIRMATION,
            "hi lumi" to LumiStatusCategory.GREETING,
            "what's on my plate today" to LumiStatusCategory.ROUTINE_REVIEW
        )
        cases.forEach { (prompt, expected) ->
            assertEquals("wrong intent for: $prompt", expected, LumiStatusPhrases.categoryFor(prompt, "assistant"))
        }
    }

    @Test
    fun routineModeFallsBackToTheScheduleItWillRead() {
        assertEquals(LumiStatusCategory.ROUTINE_REVIEW, LumiStatusPhrases.categoryFor("what's on my plate today", "routine"))
    }

    @Test
    fun multiPartRequestsAreRecognisedAsMultiStep() {
        val request = "add a dentist appointment on friday, move my standup to 10, and show me what's left today"
        assertEquals(LumiStatusCategory.MULTI_STEP, LumiStatusPhrases.categoryFor(request, "routine"))
    }

    @Test
    fun everyIntentHasRealVariety() {
        LumiStatusCategory.entries.forEach { category ->
            val variants = LumiStatusPhrases.variants(category)
            assertTrue("$category has too few lines", variants.size >= 3)
            assertEquals("$category repeats a line", variants.size, variants.toSet().size)
            variants.forEach { line ->
                assertTrue("$category line is not a sentence: $line", line.last() == '.')
                assertTrue("$category line is too long to speak first: $line", line.length <= 60)
                assertTrue("$category line starts lowercase: $line", line.first().isUpperCase())
            }
        }
    }

    @Test
    fun rotationWalksTheWholeListBeforeRepeating() {
        val category = LumiStatusCategory.ROUTINE_REVIEW
        val count = LumiStatusPhrases.variants(category).size
        val seen = (0 until count).map { LumiStatusPhrases.pick(category, it) }
        assertEquals("rotation skipped a line", count, seen.toSet().size)
        assertEquals("rotation lost its place after a full cycle", seen.first(), LumiStatusPhrases.pick(category, count))
        assertEquals("rotation lost its place on a negative turn", seen.last(), LumiStatusPhrases.pick(category, -1))
    }

    @Test
    fun consecutiveTurnsNeverSpeakTheSameLine() {
        val category = LumiStatusCategory.ROUTINE_REVIEW
        val first = LumiStatusPhrases.pick(category, 0)
        val second = LumiStatusPhrases.pick(category, 1)
        assertNotEquals(first, second)
    }

    @Test
    fun theStatusLineIsBuiltFromTheRequestsOwnIntent() {
        assertEquals(
            LumiStatusPhrases.pick(LumiStatusCategory.WEATHER, 0),
            LumiStatusPhrases.forRequest("will it rain this evening", "assistant", 0)
        )
    }
}
