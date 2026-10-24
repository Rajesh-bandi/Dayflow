package com.dayflow.data

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class LumiFunctionCall(val id: String, val name: String, val arguments: JSONObject)
data class LumiCompletion(val text: String, val calls: List<LumiFunctionCall>, val assistantMessage: JSONObject)

class LumiAiClient(private val configuration: AiConfiguration) {
    fun testConnection(): String = complete("Reply with the single word Connected.", emptyList())

    fun complete(userText: String, history: List<Pair<String, String>>, localContext: String = ""): String {
        return completeTurn(userText, history, localContext).text
    }

    fun completeTurn(
        userText: String,
        history: List<Pair<String, String>>,
        localContext: String = "",
        toolSpecs: JSONArray = JSONArray(),
        followupMessages: List<JSONObject> = emptyList()
    ): LumiCompletion {
        val config = configuration.read()
        require(config.baseUrl.isNotBlank() && config.model.isNotBlank()) { "Configure the OmniRoute URL and model in Settings first." }
        val routinePolicy = if (toolSpecs.length() > 0) " In Routine mode, use the provided functions for schedule facts and every task change. Never claim a write succeeded until the tool returns success. Ask for missing required details instead of guessing." else ""
        val hasToolResult = followupMessages.any { it.optString("role") == "tool" }
        val requiredWebTool = if (hasToolResult) null else requiredWebTool(userText, localContext, toolSpecs)
        val messages = JSONArray().put(JSONObject().put("role", "system").put("content", "You are Lumi, a warm, concise personal assistant inside a routine planner. Answer only what the user asked, then stop. A single request often contains several instructions: carry out every one of them in this turn, calling tools one after another as needed, and only reply once they are all handled. Never stop after the first action, and never ask the user to repeat an instruction you were already given. If a step fails, report that step and continue with the others. Do not add suggestions, doubts, follow-up questions, invitations, offers, unrelated facts, or a generic closing. Ask a question only when essential information is missing for the requested action, or when the app requires confirmation. For a simple factual question, reply in one short, natural sentence. Speak in a relaxed conversational voice, using contractions where natural and avoiding formal or scripted phrasing. For questions about the current date, day, or time, call get_current_datetime and answer from its result; never guess or say you cannot access the clock when the tool is available. In Assistant mode, if the user provides a URL and asks to read, inspect, summarize, or get data from it, you MUST call fetch_webpage first. Never claim you cannot browse or open a website; these tools provide that ability. For web searches or questions needing current information, use web search results already supplied in the context when present; otherwise call search_web, base the answer on returned results, and include relevant source URLs. If a search tool fails or returns no usable data, say you could not verify it online instead of guessing. Save a memory only for a durable personal fact, preference, or ongoing goal the user explicitly asks you to remember or clearly states; do not save temporary requests or uncertain inferences. Never save credentials or sensitive secrets. The complete conversation archive is stored separately, so do not turn ordinary chat into a memory. After a memory tool confirms a save, give a brief acknowledgment and do not save the same fact again. Use plain text only: no Markdown, tables, code fences, emoji, bullet symbols, asterisks, headings, or decorative characters. Keep it short and friendly: usually one to three short sentences, and never more sentences than the answer needs. When you list several changed items, use one short line per item with no leading symbol. Keep wording easy to understand when read aloud. Be honest about actions: this app has not changed tasks unless it explicitly confirms a saved change. Do not invent personal data. Local schedule data, if included, is factual context.$routinePolicy $localContext"))
        history.takeLast(12).forEach { (role, content) -> messages.put(JSONObject().put("role", role).put("content", content)) }
        messages.put(JSONObject().put("role", "user").put("content", userText))
        followupMessages.forEach(messages::put)
        val endpoint = config.baseUrl.trimEnd('/').let { if (it.endsWith("/v1")) "$it/chat/completions" else "$it/v1/chat/completions" }
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 15000; readTimeout = 45000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            if (config.apiKey.isNotBlank()) setRequestProperty("Authorization", "Bearer ${config.apiKey}")
        }
        try {
            val request = JSONObject().put("model", config.model).put("messages", messages).put("stream", false)
            if (toolSpecs.length() > 0) {
                request.put("tools", toolSpecs)
                val toolChoice = requiredWebTool?.let { name ->
                    JSONObject().put("type", "function").put("function", JSONObject().put("name", name))
                } ?: "auto"
                request.put("tool_choice", toolChoice).put("parallel_tool_calls", false)
            }
            val body = request.toString()
            conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val response = (if (code in 200..299) conn.inputStream else conn.errorStream)?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException(when (code) { 401, 403 -> "OmniRoute rejected the API key (HTTP $code)."; 429 -> "OmniRoute rate limit reached. Try again shortly."; else -> "OmniRoute request failed (HTTP $code): ${response.take(250)}" })
            val assistant = JSONObject(response).getJSONArray("choices").getJSONObject(0).getJSONObject("message")
            val calls = buildList {
                val rawCalls = assistant.optJSONArray("tool_calls") ?: return@buildList
                for (index in 0 until rawCalls.length()) {
                    val call = rawCalls.optJSONObject(index) ?: continue
                    val fn = call.optJSONObject("function") ?: continue
                    val args = runCatching { JSONObject(fn.optString("arguments", "{}")) }.getOrDefault(JSONObject())
                    add(LumiFunctionCall(call.optString("id"), fn.optString("name"), args))
                }
            }
            val result = assistant.optString("content").trim()
            if (result.isBlank() && calls.isEmpty()) throw IllegalStateException("OmniRoute returned an empty response.")
            return LumiCompletion(result, calls, assistant)
        } catch (e: java.net.SocketTimeoutException) { throw IllegalStateException("OmniRoute timed out. Check the server URL and try again.", e) }
        catch (e: java.net.UnknownHostException) { throw IllegalStateException("Could not reach OmniRoute. Check the server URL and network.", e) }
        catch (e: org.json.JSONException) { throw IllegalStateException("OmniRoute returned an invalid response.", e) }
        finally { conn.disconnect() }
    }

    private fun requiredWebTool(userText: String, localContext: String, toolSpecs: JSONArray): String? {
        if (!localContext.contains("Active mode: Assistant", ignoreCase = true)) return null
        if (localContext.contains("Web search results already retrieved", ignoreCase = true)) return null
        val hasUrl = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE).containsMatchIn(userText)
        val requestedName = when {
            hasUrl -> "fetch_webpage"
            Regex("\\b(search|browse|look up|find online|research|what(?:'s| is) the latest|current news)\\b", RegexOption.IGNORE_CASE).containsMatchIn(userText) -> "search_web"
            else -> return null
        }
        val availableNames = (0 until toolSpecs.length()).mapNotNull { index ->
            toolSpecs.optJSONObject(index)?.optJSONObject("function")?.optString("name")
        }
        return requestedName.takeIf(availableNames::contains)
    }
}
