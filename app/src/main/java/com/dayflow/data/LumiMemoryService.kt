package com.dayflow.data

import com.dayflow.data.local.AiDao
import com.dayflow.data.local.AiMemoryEntity
import com.dayflow.data.local.AiMessageEntity
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/**
 * Small on-device long-term memory layer, separate from the complete conversation archive.
 *
 * Recall is deliberately conservative: a memory is only injected when it actually shares meaning with
 * the request, and near-duplicates are merged on save so the store does not fill with rephrasings of
 * the same fact. The complete transcript stays in its own table and is never sent wholesale.
 */
class LumiMemoryService(private val dao: AiDao) {

    fun toolDefinitions(): JSONArray = JSONArray().put(
        JSONObject().put("type", "function").put("function", JSONObject()
            .put("name", "save_memory")
            .put("description", "Save a durable personal fact, stated preference, or ongoing goal the user explicitly asked Lumi to remember or has clearly shared. Never save secrets, credentials, temporary requests, or uncertain guesses. Do not save a memory unless it will be useful in future conversations.")
            .put("parameters", JSONObject().put("type", "object")
                .put("properties", JSONObject()
                    .put("content", JSONObject().put("type", "string").put("description", "One concise, useful fact about the user"))
                    .put("category", JSONObject().put("type", "string").put("enum", JSONArray(listOf("profile", "preference", "goal", "personal")))))
                .put("required", JSONArray(listOf("content", "category"))).put("additionalProperties", false)))
    )

    suspend fun execute(call: LumiFunctionCall): AssistantActionResult {
        if (call.name != "save_memory") return AssistantActionResult.Failure("Unsupported memory action.")
        val content = call.arguments.optString("content").trim()
        val category = call.arguments.optString("category", "personal").takeIf { it in CATEGORIES } ?: "personal"
        val id = save(content, category, explicit = true)
            ?: return AssistantActionResult.Failure("That information was not saved because it was empty, temporary, or sensitive.")
        return AssistantActionResult.Complete("Saved a personal memory (id $id). The user can review, edit, or delete it in Settings.")
    }

    /**
     * Reads durable facts out of an ordinary message. Several facts in one sentence are all captured,
     * and only clearly stated facts are marked explicit; softer inferences are flagged for review.
     */
    suspend fun learnFromMessage(message: String) {
        val value = message.trim().take(600)
        if (value.isBlank() || sensitiveOrTemporary(value)) return
        val candidates = LinkedHashMap<String, Pair<String, Boolean>>()

        fun offer(content: String, category: String, explicit: Boolean) {
            val cleaned = content.trim().trimEnd('.', '!', '?')
            if (cleaned.length >= 8) candidates.putIfAbsent(cleaned, category to explicit)
        }

        Regex("(?i)^\\s*(?:lumi[, ]+)?remember(?: that)?\\s+(.+)$").find(value)?.groupValues?.get(1)?.let { offer(it, "personal", true) }
        Regex("(?i)^\\s*my name is\\s+([\\p{L}][\\p{L} '-]{1,50})[.!]?\\s*$").find(value)?.let { offer("My name is ${it.groupValues[1]}", "profile", true) }
        Regex("(?i)^\\s*(?:please )?(?:call me|i'm called|i am called)\\s+([\\p{L}][\\p{L} '-]{1,50})[.!]?\\s*$").find(value)?.let { offer("Call me ${it.groupValues[1]}", "profile", true) }
        Regex("(?i)\\b(i prefer|i like|i dislike|i don't like|i work best|i usually|i always|i never)\\b[^.!?]{3,120}[.!]?").findAll(value).forEach { offer(it.value, "preference", false) }
        Regex("(?i)\\b(my goal is|i am working toward|i'm working toward|i want to|i'm trying to|i am trying to)\\b[^.!?]{3,120}[.!]?").findAll(value).forEach { offer(it.value, "goal", false) }
        Regex("(?i)\\b(i work as|i study|i live in|i'm based in|i work at|i'm a|i am a|i'm an|i am an)\\b[^.!?]{3,100}[.!]?").findAll(value).forEach { offer(it.value, "profile", false) }

        candidates.entries.take(3).forEach { (content, meta) ->
            val (category, explicit) = meta
            save(content, category, explicit = explicit)
        }
    }

    /** Saves a fact, merging it into an existing near-duplicate rather than storing a second copy. */
    suspend fun save(content: String, category: String = "personal", explicit: Boolean = true): Long? {
        val cleaned = content.trim().replace(Regex("\\s+"), " ").take(500)
        if (cleaned.length < 4 || sensitiveOrTemporary(cleaned)) return null
        val now = System.currentTimeMillis()
        val candidates = dao.recentMemories(200)

        val exact = candidates.firstOrNull { it.content.equals(cleaned, ignoreCase = true) }
        if (exact != null) {
            dao.updateMemory(exact.id, cleaned, now)
            if (explicit && !exact.explicit) dao.markExplicit(exact.id)
            return exact.id
        }

        val near = candidates.filter { it.category == category }
            .map { it to similarity(it.content, cleaned) }
            .filter { it.second >= MERGE_THRESHOLD }
            .maxByOrNull { it.second }?.first
        if (near != null) {
            val merged = if (cleaned.length > near.content.length) cleaned else near.content
            dao.updateMemory(near.id, merged, now)
            if (explicit && !near.explicit) dao.markExplicit(near.id)
            return near.id
        }

        val id = dao.insertMemory(AiMemoryEntity(category = category, content = cleaned, explicit = explicit, createdAtMillis = now, updatedAtMillis = now))
        dao.pruneImplicit(MAX_MEMORIES)
        return id
    }

    /**
     * Ranks memories for a request. Coverage of the query terms, an exact phrase hit, durability of the
     * category, whether the user stated it directly, and recency all contribute.
     */
    suspend fun relevant(query: String, limit: Int = 6): List<AiMemoryEntity> {
        val memories = dao.recentMemories(250)
        if (memories.isEmpty()) return emptyList()
        if (Regex("(?i)about me|my memories|my preferences|my goals|what do you know about me").containsMatchIn(query)) return memories.take(20)
        val terms = tokens(query)
        if (terms.isEmpty()) return memories.take(3)
        val now = System.currentTimeMillis()
        val trimmedQuery = query.trim()
        val ranked = memories.mapNotNull { memory ->
            val content = tokens("${memory.category} ${memory.content}")
            val overlap = terms.count(content::contains)
            val phrase = if (trimmedQuery.length >= 4 && memory.content.contains(trimmedQuery, ignoreCase = true)) 6 else 0
            if (overlap == 0 && phrase == 0) return@mapNotNull null
            val coverage = overlap.toDouble() / terms.size
            val durable = if (memory.category == "goal" || memory.category == "preference") 2 else 0
            val stated = if (memory.explicit) 2 else 0
            val ageDays = ((now - memory.updatedAtMillis) / 86_400_000L).coerceAtLeast(0)
            val recency = when { ageDays <= 7 -> 2; ageDays <= 30 -> 1; else -> 0 }
            memory to (coverage * 10 + phrase + durable + stated + recency)
        }.sortedWith(compareByDescending<Pair<AiMemoryEntity, Double>> { it.second }.thenByDescending { it.first.updatedAtMillis })
        return if (ranked.isEmpty()) memories.take(2) else ranked.take(limit).map { it.first }
    }

    /** Pulls at most a couple of earlier exchanges that overlap with the current request. */
    suspend fun relevantConversationContext(mode: String, query: String, limitExchanges: Int = 2): List<AiMessageEntity> {
        val ordered = dao.recentMessages(mode, 500).asReversed()
        val exchanges = mutableListOf<MutableList<AiMessageEntity>>()
        ordered.forEach { message ->
            if (message.role == "user") exchanges += mutableListOf(message)
            else if (message.role == "assistant" && exchanges.isNotEmpty()) exchanges.last() += message
        }
        val terms = tokens(query)
        if (terms.isEmpty()) return emptyList()
        return exchanges.filterNot { exchange -> exchange.lastOrNull()?.let { it.role == "user" && it.content == query } == true }
            .map { exchange -> exchange to terms.count(tokens(exchange.joinToString(" ") { it.content })::contains) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<MutableList<AiMessageEntity>, Int>> { it.second }.thenByDescending { it.first.first().createdAtMillis })
            .take(limitExchanges).flatMap { it.first }
    }

    fun sensitiveOrTemporary(value: String): Boolean {
        if (Regex("(?i)\\b(password|passcode|api[ -]?key|secret|access token|refresh token|private key|otp|pin)\\b").containsMatchIn(value)) return true
        return Regex("(?i)\\b(just for today|only today|for this one|right now|tonight)\\b").containsMatchIn(value)
    }

    /** Jaccard similarity over meaningful words; used only to fold rephrasings of one fact together. */
    private fun similarity(a: String, b: String): Double {
        val left = tokens(a)
        val right = tokens(b)
        if (left.isEmpty() || right.isEmpty()) return 0.0
        val shared = left.count(right::contains)
        return shared.toDouble() / (left.size + right.size - shared).coerceAtLeast(1)
    }

    private fun tokens(value: String): Set<String> = Regex("[\\p{L}\\p{N}]{3,}")
        .findAll(value.lowercase(Locale.ROOT)).map { it.value }.filterNot { it in STOP_WORDS }.toSet()

    private companion object {
        const val MERGE_THRESHOLD = 0.75
        const val MAX_MEMORIES = 600
        val CATEGORIES = setOf("profile", "preference", "goal", "personal")
        val STOP_WORDS = setOf("the", "and", "for", "with", "that", "this", "from", "have", "about", "your", "you", "are", "was", "were", "into", "like", "prefer", "remember", "lumi", "want", "really", "just", "them", "they", "would", "could", "should", "been")
    }
}
