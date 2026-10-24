package com.dayflow.data

/** Removes model formatting so replies stay readable in chat and natural when spoken aloud. */
object AssistantResponseFormatter {

    /** Chat text: plain, no Markdown, links preserved as readable text. */
    fun forDisplay(text: String): String = normalize(removeUnaskedClosing(text), keepLinks = true)

    /**
     * Spoken text. The whole reply is read aloud; it is never cut down to a fragment.
     * Numbers, units and time ranges are rewritten into the words a person would actually say.
     */
    fun forSpeech(text: String): String {
        val flattened = normalize(removeUnaskedClosing(text), keepLinks = false)
            .replace(Regex("https?://\\S+"), "")
            .replace(Regex("\\s*\\n\\s*"), ". ")
            .replace(Regex("\\s+"), " ")
            .replace(Regex("[.]{2,}"), ".")
            .trim()
        return naturalizeForSpeech(flattened)
    }

    /** Rewrites terse, symbol-heavy text into a natural spoken sentence. */
    private fun naturalizeForSpeech(text: String): String {
        var spoken = text
            // Time and number ranges: "4:00–5:00 PM" or "4:00 - 5:00" read as "4:00 to 5:00".
            .replace(Regex("""(\d{1,2}:\d{2})\s*(?:[\u2013\u2014-]|to)\s*(\d{1,2}:\d{2})"""), "$1 to $2")
            .replace(Regex("""(\d{1,2}\s?(?:AM|PM|am|pm))\s*[\u2013\u2014-]\s*(\d{1,2}\s?(?:AM|PM|am|pm))"""), "$1 to $2")
            // Units written in shorthand.
            .replace(Regex("(?<=\\d)\\s?min\\b", RegexOption.IGNORE_CASE), " minutes")
            .replace(Regex("(?<=\\d)\\s?mins\\b", RegexOption.IGNORE_CASE), " minutes")
            .replace(Regex("\\b1 minutes\\b"), "1 minute")
            .replace(Regex("(?<=\\d)\\s?hrs?\\b", RegexOption.IGNORE_CASE), " hours")
            .replace(Regex("\\b1 hours\\b"), "1 hour")
            .replace(Regex("(?<=\\d)\\s?m\\b"), " minutes")
            .replace(Regex("(?<=\\d)\\s?h\\b"), " hours")
            // Identifiers and symbols.
            .replace(Regex("\\bid\\s*=\\s*(\\d+)", RegexOption.IGNORE_CASE), "id $1")
            .replace(Regex("(?<=\\d)%"), " percent")
            .replace("·", ",")
            .replace(Regex("\\s*&\\s*"), " and ")
            // Shorthand that reads as noise aloud. Slashes only expand between words, so dates and
            // fractions like 10/15 are left for the voice to read as a date.
            .replace(Regex("\\b(?:e\\.g\\.|eg)[,;]"), "for example,")
            .replace(Regex("\\b(?:i\\.e\\.|ie)[,;]"), "that is,")
            .replace(Regex("\\betc\\.?(?=\\s|$)"), "et cetera")
            .replace(Regex("(?<=[A-Za-z])/(?=[A-Za-z])"), " or ")
            .replace(Regex("\\s\\+\\s"), " plus ")
            .replace(Regex("\\s=\\s"), " equals ")
            .replace(Regex("\\s*[|<>_]\\s*"), " ")
            .replace(Regex("""\s*[\u2022\u2714\u2713\u2728\u2192]+\s*"""), " ")
            // A dash used as a clause break becomes a comma pause instead of being skipped.
            .replace(Regex("""\s+[\u2013\u2014]\s+"""), ", ")
            .replace(Regex("\\s+,\\s+"), ", ")
            // Symbols and emoji carry no sound at all.
            .replace(SYMBOL_RANGE, "")
            .replace(Regex("\\s{2,}"), " ")
            .replace(Regex("\\s+([,.!?])"), "$1")
            .replace(Regex("(,\\s*){2,}"), ", ")
            // A label left behind after its value was removed should not be read as "Source colon".
            .replace(Regex("[\\s:;,]+$"), "")
            .trim()
        if (spoken.isEmpty()) return ""
        if (spoken.last() !in ".!?") spoken += "."
        return spoken
    }

    private fun removeUnaskedClosing(text: String): String {
        val genericClosing = Regex(
            "(?is)\\s+(?:is there anything else\\b[^.!?]*[.!?]?|what else can I help you with\\??|do you have any other questions\\??|let me know if you need anything else[^.!?]*[.!?]?|feel free to ask[^.!?]*[.!?]?|I hope this helps\\.?)[\\s]*$"
        )
        return text.trim().replace(genericClosing, "").trim()
    }

    private fun normalize(text: String, keepLinks: Boolean): String {
        val withoutCodeFences = text.replace(Regex("```[\\w-]*\\s*"), "").replace("```", "")
        val lines = withoutCodeFences.lines().mapNotNull { raw ->
            var line = raw.trim()
            if (line.isEmpty() || line.matches(Regex("^[|\\s:+-]+$"))) return@mapNotNull null
            line = line.replace(Regex("^#{1,6}\\s*"), "")
            line = line.replace(Regex("^[-*+]\\s+"), "").replace(Regex("^\\d+[.)]\\s+"), "")
            line = line.replace(Regex("!?\\[([^]]+)]\\((https?://[^)]+)\\)")) { match ->
                if (keepLinks) "${match.groupValues[1]} (${match.groupValues[2]})" else match.groupValues[1]
            }
            if (line.startsWith("|") || line.endsWith("|")) {
                line = line.trim('|').split('|').map(String::trim).filter(String::isNotEmpty).joinToString(", ")
            }
            line = line.replace(Regex("\\*\\*(.*?)\\*\\*|__(.*?)__")) { it.groupValues.drop(1).firstOrNull(String::isNotEmpty).orEmpty() }
            line = line.replace(Regex("(?<!\\*)\\*([^*]+)\\*(?!\\*)|(?<!_)_([^_]+)_(?!_)")) { it.groupValues.drop(1).firstOrNull(String::isNotEmpty).orEmpty() }
            line = line.replace(Regex("`([^`]*)`"), "$1")
                .replace("&", " and ")
                .replace(SYMBOL_RANGE, "")
                .replace(Regex("\\s+"), " ")
                .trim()
            line.takeIf(String::isNotEmpty)
        }
        return lines.joinToString("\n")
    }

    /**
     * Emoji, dingbats, arrows, zero-width marks and decorative characters that must never appear in
     * plain-text output. Written as a raw string so the escapes reach the regex engine intact: a
     * normal Kotlin string turned `\u1F000` into `\u1F00` plus a literal `0`, and the following dash
     * then formed the range `0-\u1FAF`, which silently deleted ASCII letters and digits.
     */
    private val SYMBOL_RANGE = Regex("""[\u200B-\u200F\u2028-\u202F\u2190-\u21FF\u2300-\u23FF\u25A0-\u27BF\u2B00-\u2BFF\uFE0F\u2700-\u27BF\uD83C-\uDBFF\uDC00-\uDFFF]""")
}
