package com.dayflow.data

import java.util.Locale

/**
 * What Lumi says while it is working.
 *
 * Two things matter here. The line has to be *true* — it describes the step actually being taken, so
 * "checking your routine" is only used when the routine is really being read. And it has to vary:
 * the same sentence on every request reads as a machine, while a small rotation per intent reads as
 * someone working. Lines stay short because they are spoken before the answer, not after it.
 */
enum class LumiStatusCategory {
    WEB_PAGE, CALENDAR, WEATHER, NEWS, CLOCK, TIMER, ALARM, WEB_SEARCH, TRAVEL, PRICE, TRANSLATION, MATH,
    FOCUS, MEMORY_SAVE, MEMORY_RECALL, PROFILE, MESSAGING, APP_OPEN, HEALTH, MEDIA,
    ROUTINE_REVIEW, ROUTINE_ADD, ROUTINE_MOVE, ROUTINE_DELETE, ROUTINE_COMPLETE, RITUAL,
    PROGRESS, MISSED, FREE_TIME, MULTI_STEP, CONTINUATION, CONFIRMATION, GREETING, GENERAL
}

object LumiStatusPhrases {

    /**
     * Reads the request and decides which work is about to happen. The order matters: a request that
     * mentions a link is a page read even if it also mentions a calendar.
     */
    fun categoryFor(prompt: String, mode: String): LumiStatusCategory {
        val q = prompt.lowercase(Locale.ROOT)
        val has = { words: List<String> -> words.any(q::contains) }
        // The model returns the whole turn, so the request text decides, not the screen it came from.

        return when {
            Regex("https?://\\S+").containsMatchIn(prompt) -> LumiStatusCategory.WEB_PAGE

            has(listOf("translate", "in spanish", "in french", "in german", "in hindi", "in japanese")) -> LumiStatusCategory.TRANSLATION

            // A bare yes/no is an answer to the confirmation Lumi just asked for.
            q.trim().length <= 24 && Regex("^(yes|yeah|yep|ok|okay|sure|go ahead|do it|confirm|please do|no|nope|not now|cancel that|stop)\\b").containsMatchIn(q.trim()) ->
                LumiStatusCategory.CONFIRMATION

            has(listOf("and also", "also add", "also move", "one more", "as well", "after that", "then add", "then move", "second thing")) ->
                LumiStatusCategory.CONTINUATION

            // Several asks in one turn get one honest line instead of a specific one that would be half wrong.
            PromptShape.isMultiPart(prompt) -> LumiStatusCategory.MULTI_STEP

            has(listOf("weather", "forecast", "will it rain", "raining", "temperature outside", "humidity", "sunset", "sunrise")) -> LumiStatusCategory.WEATHER
            has(listOf("news", "headline", "what's happening in", "whats happening in")) -> LumiStatusCategory.NEWS
            has(listOf("price", "cost of", "how much is", "cheaper", "deal", "discount", "buy", "order", "shopping")) -> LumiStatusCategory.PRICE
            has(listOf("directions", "route", "drive to", "how do i get to", "traffic", "travel time")) -> LumiStatusCategory.TRAVEL
            has(listOf("calculate", "how many", "percentage", "percent of", "divided by", "times table", "sum of", "average of")) -> LumiStatusCategory.MATH
            has(listOf("alarm", "wake me", "remind me at", "alert me at")) -> LumiStatusCategory.ALARM
            has(listOf("timer", "countdown", "time me", "stopwatch")) -> LumiStatusCategory.TIMER
            has(listOf("calendar", "event", "meeting", "appointment", "availability", "invite")) -> LumiStatusCategory.CALENDAR
            has(listOf("what time", "what's the time", "what day is", "what's the date", "today's date", "current time", "current date", "date and time")) -> LumiStatusCategory.CLOCK
            has(listOf("focus session", "deep work", "pomodoro", "start focus", "focus block", "distraction")) -> LumiStatusCategory.FOCUS
            has(listOf("play ", "song", "music", "playlist", "podcast")) -> LumiStatusCategory.MEDIA
            has(listOf("sleep", "workout", "steps", "water", "meditation", "calories", "meditate")) -> LumiStatusCategory.HEALTH
            has(listOf("remember that", "save this", "note that", "keep in mind", "don't forget", "dont forget")) -> LumiStatusCategory.MEMORY_SAVE
            has(listOf("what do you remember", "what do you know about me", "my memories", "remember about me")) -> LumiStatusCategory.MEMORY_RECALL
            has(listOf("my preferences", "my goals", "my name", "about me", "my profile")) -> LumiStatusCategory.PROFILE
            // Kept narrow on purpose: "add a task to call the dentist" is a task, not a phone call.
            has(listOf("send a message", "send an email", "send a text", "email ", "text him", "text her", "text them", "message my", "message him", "message her", "message them", "whatsapp", "sms ", "call my", "call him", "call her", "phone my")) -> LumiStatusCategory.MESSAGING
            has(listOf("open ", "launch ", "switch to ")) -> LumiStatusCategory.APP_OPEN

            has(listOf("move ", "reschedule", "postpone", "push back", "shift ", "bring forward", "change the time")) -> LumiStatusCategory.ROUTINE_MOVE
            has(listOf("delete", "remove ", "cancel the", "drop the", "clear the", "get rid of")) -> LumiStatusCategory.ROUTINE_DELETE
            has(listOf("complete", "mark done", "mark that done", "tick off", "finished ", "done with")) -> LumiStatusCategory.ROUTINE_COMPLETE
            has(listOf("add a task", "add task", "create a task", "schedule a", "add to my", "plan a", "new task", "add a routine")) -> LumiStatusCategory.ROUTINE_ADD

            has(listOf("how am i doing", "progress", "productivity", "streak", "summary of my week", "review my")) -> LumiStatusCategory.PROGRESS
            has(listOf("missed", "did i miss", "did i skip", "miss anything", "skipped", "overdue", "didn't finish", "didnt finish", "wasn't finished", "wasnt finished", "incomplete yesterday", "fell behind")) -> LumiStatusCategory.MISSED
            has(listOf("free time", "free slot", "gap", "when am i free", "open slot", "spare time")) -> LumiStatusCategory.FREE_TIME
            has(listOf("ritual", "habit", "recurring", "every day", "routine template")) -> LumiStatusCategory.RITUAL

            has(listOf("search", "look up", "google", "find online", "research", "latest on", "current", "who won", "news about")) -> LumiStatusCategory.WEB_SEARCH

            mode == "routine" || has(listOf("task", "routine", "schedule", "today", "tomorrow", "this week", "plan", "list my", "what's on")) ->
                LumiStatusCategory.ROUTINE_REVIEW

            PromptShape.isGreeting(q) -> LumiStatusCategory.GREETING

            else -> LumiStatusCategory.GENERAL
        }
    }

    /** Short factual shapes that steer the wording rather than the work. */
    private object PromptShape {
        fun isGreeting(q: String): Boolean =
            q.trim().length <= 30 && Regex("^(hi|hey|hello|yo|good morning|good evening|good afternoon|thanks|thank you|how are you|good night)\\b").containsMatchIn(q.trim())

        /** Two or more distinct asks in one request need a longer, more honest status line. */
        fun isMultiPart(prompt: String): Boolean {
            if (prompt.length > 220) return true
            val asks = listOf(" and ", " plus ", ",").count { prompt.lowercase(Locale.ROOT).contains(it) }
            val verbs = listOf("add", "move", "delete", "check", "show", "tell", "summar", "find", "search", "list", "start")
                .count { prompt.lowercase(Locale.ROOT).contains(it) }
            return asks >= 2 && verbs >= 2
        }
    }

    private val library: Map<LumiStatusCategory, List<String>> = mapOf(
        LumiStatusCategory.WEB_PAGE to listOf(
            "Opening that page now.",
            "Reading through that site.",
            "Fetching the page you linked.",
            "Grabbing that article.",
            "Pulling up that link."
        ),
        LumiStatusCategory.CALENDAR to listOf(
            "Checking your calendar now.",
            "Looking over your events.",
            "Seeing what's booked.",
            "Checking those times.",
            "Looking at your appointments."
        ),
        LumiStatusCategory.WEATHER to listOf(
            "Checking the forecast.",
            "Looking at the weather.",
            "Seeing what the sky's doing.",
            "Checking today's conditions."
        ),
        LumiStatusCategory.NEWS to listOf(
            "Pulling the latest headlines.",
            "Checking the news for you.",
            "Seeing what's recent.",
            "Getting the current stories."
        ),
        LumiStatusCategory.CLOCK to listOf(
            "Checking the date and time.",
            "Looking at your clock.",
            "Reading the time now.",
            "Checking today's date."
        ),
        LumiStatusCategory.TIMER to listOf(
            "Setting that timer.",
            "Starting the countdown.",
            "Setting it up now."
        ),
        LumiStatusCategory.ALARM to listOf(
            "Setting that alarm.",
            "Scheduling that alert.",
            "Note to your device clock."
        ),
        LumiStatusCategory.WEB_SEARCH to listOf(
            "Searching the web for that.",
            "Looking that up online.",
            "Checking current sources.",
            "Finding the latest on that.",
            "Running a quick search."
        ),
        LumiStatusCategory.TRAVEL to listOf(
            "Checking the best route.",
            "Looking at travel time now.",
            "Seeing how the roads are.",
            "Working out the journey."
        ),
        LumiStatusCategory.PRICE to listOf(
            "Checking prices now.",
            "Comparing a few options.",
            "Looking at what it costs.",
            "Checking current prices."
        ),
        LumiStatusCategory.TRANSLATION to listOf(
            "Translating that now.",
            "Working on the wording.",
            "Putting that into words."
        ),
        LumiStatusCategory.MATH to listOf(
            "Working that out.",
            "Running the numbers.",
            "Doing the arithmetic.",
            "Calculating that now."
        ),
        LumiStatusCategory.FOCUS to listOf(
            "Looking at your focus history.",
            "Checking your focus sessions.",
            "Seeing how your focus has gone."
        ),
        LumiStatusCategory.MEMORY_SAVE to listOf(
            "Saving that for later.",
            "Noting that down.",
            "Keeping that in mind.",
            "Writing that to memory."
        ),
        LumiStatusCategory.MEMORY_RECALL to listOf(
            "Checking what I remember.",
            "Looking through my notes.",
            "Recalling your saved details."
        ),
        LumiStatusCategory.PROFILE to listOf(
            "Looking at your preferences.",
            "Checking what you've told me.",
            "Reviewing your saved profile."
        ),
        LumiStatusCategory.MESSAGING to listOf(
            "Opening that for you.",
            "Getting that ready.",
            "Pulling that app up."
        ),
        LumiStatusCategory.APP_OPEN to listOf(
            "Opening that now.",
            "Bringing that up.",
            "Launching it for you."
        ),
        LumiStatusCategory.HEALTH to listOf(
            "Checking your activity.",
            "Looking at those numbers.",
            "Seeing where you stand."
        ),
        LumiStatusCategory.MEDIA to listOf(
            "Getting your music ready.",
            "Opening your player.",
            "Queuing that up."
        ),
        LumiStatusCategory.ROUTINE_REVIEW to listOf(
            "Checking your routine now.",
            "Looking through today's plan.",
            "Pulling up your tasks.",
            "Seeing what's scheduled.",
            "Reviewing your schedule.",
            "Looking at what's left today."
        ),
        LumiStatusCategory.ROUTINE_ADD to listOf(
            "Finding a good time for that.",
            "Checking your schedule before I add it.",
            "Looking for a gap to fit that in.",
            "Seeing where that best fits."
        ),
        LumiStatusCategory.ROUTINE_MOVE to listOf(
            "Looking for a better time.",
            "Checking what's free around then.",
            "Reviewing that part of your plan.",
            "Finding a new slot."
        ),
        LumiStatusCategory.ROUTINE_DELETE to listOf(
            "Finding that task first.",
            "Locating that entry.",
            "Pulling it up before I change it."
        ),
        LumiStatusCategory.ROUTINE_COMPLETE to listOf(
            "Marking that off now.",
            "Updating your progress.",
            "Recording that as done.",
            "Ticking that one off."
        ),
        LumiStatusCategory.RITUAL to listOf(
            "Checking your rituals.",
            "Looking at your repeating tasks.",
            "Reviewing your routine pattern."
        ),
        LumiStatusCategory.PROGRESS to listOf(
            "Adding up your week.",
            "Looking back over your completions.",
            "Crunching your progress.",
            "Reviewing how you've been doing.",
            "Totalling that up."
        ),
        LumiStatusCategory.MISSED to listOf(
            "Checking what slipped yesterday.",
            "Looking for what wasn't finished.",
            "Reviewing anything unfinished."
        ),
        LumiStatusCategory.FREE_TIME to listOf(
            "Working out when you're free.",
            "Finding the gaps in your day.",
            "Checking your open slots."
        ),
        LumiStatusCategory.MULTI_STEP to listOf(
            "This has a few parts, one moment.",
            "Working through your request step by step.",
            "Give me a moment, there's a lot here.",
            "Chewing on that one."
        ),
        LumiStatusCategory.CONTINUATION to listOf(
            "Got it, continuing.",
            "On it, one more step.",
            "Sure, adding that in.",
            "Right, working on the next part."
        ),
        LumiStatusCategory.CONFIRMATION to listOf(
            "Understood, doing that now.",
            "Right, actioning that.",
            "Okay, making the change.",
            "Noted, taking care of it."
        ),
        LumiStatusCategory.GREETING to listOf(
            "Just a moment.",
            "Hello — one second.",
            "Hi, give me a moment."
        ),
        LumiStatusCategory.GENERAL to listOf(
            "Let me check that for you.",
            "Working through that now.",
            "Looking into it.",
            "One moment, sorting that out.",
            "Checking the details now.",
            "On it.",
            "Give me a second here."
        )
    )

    fun variants(category: LumiStatusCategory): List<String> = library.getValue(category)

    /** Picks the next wording for this intent, walking the list so repeats are far apart. */
    fun pick(category: LumiStatusCategory, turn: Int): String {
        val choices = variants(category)
        val index = ((turn % choices.size) + choices.size) % choices.size
        return choices[index]
    }

    fun forRequest(prompt: String, mode: String, turn: Int = 0): String =
        pick(categoryFor(prompt, mode), turn)
}
