package com.dayflow.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import android.text.Html
import androidx.core.content.ContextCompat
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.net.URLDecoder
import java.net.URLEncoder
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import java.io.ByteArrayOutputStream
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.Locale

private const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36"

sealed interface AssistantActionResult {
    data class Complete(val message: String) : AssistantActionResult
    data class Confirm(val message: String, val call: LumiFunctionCall) : AssistantActionResult
    data class PermissionRequired(val message: String) : AssistantActionResult
    data class Failure(val message: String) : AssistantActionResult
}

/** Assistant tools use ordinary Android providers and a credential-free web search endpoint. */
class AssistantToolService(private val context: Context, private val aiConfiguration: AiConfiguration? = null) {
    private data class CalendarEvent(val id: Long, val title: String, val startMillis: Long, val endMillis: Long)
    fun toolDefinitions() = JSONArray().apply {
        put(tool("search_web", "Search the public web for current information. Use when the user asks to search or asks about current or changing information. Return source URLs.", props("query" to str("Search query")), listOf("query")))
        put(tool("fetch_webpage", "Open and read a user-provided public HTTPS webpage. Use when the user gives a URL and asks you to inspect it or get information from it.", props("url" to str("Public HTTPS webpage URL")), listOf("url")))
        put(tool("get_calendar_events", "Read events from calendars configured on this Android device for a local date.", props("date" to str("Date YYYY-MM-DD; defaults to today."))))
        put(tool("create_calendar_event", "Create an event in the first writable visible device calendar. The app will ask before saving.", props("title" to str("Event title"), "date" to str("Date YYYY-MM-DD"), "start_time" to str("Start time HH:mm"), "end_time" to str("End time HH:mm"), "description" to str("Optional details")), listOf("title", "date", "start_time", "end_time")))
        put(tool("update_calendar_event", "Edit an event using its event id from calendar results. The app will ask before saving.", props("event_id" to num("Calendar event id"), "title" to str("Optional new title"), "date" to str("Optional new date YYYY-MM-DD"), "start_time" to str("Optional start HH:mm"), "end_time" to str("Optional end HH:mm")), listOf("event_id")))
        put(tool("delete_calendar_event", "Delete a calendar event using its event id from calendar results. The app will ask first.", props("event_id" to num("Calendar event id")), listOf("event_id")))
    }

    suspend fun execute(call: LumiFunctionCall, confirmed: Boolean = false): AssistantActionResult {
        return try {
            when (call.name) {
                "search_web" -> AssistantActionResult.Complete(search(call.arguments.optString("query")))
                "fetch_webpage" -> AssistantActionResult.Complete(fetchWebpage(call.arguments.optString("url")))
                "get_calendar_events" -> events(parseDate(call.arguments.optString("date"), LocalDate.now()) ?: return AssistantActionResult.Failure("Date must use YYYY-MM-DD."))
                "create_calendar_event" -> create(call, confirmed)
                "update_calendar_event" -> update(call, confirmed)
                "delete_calendar_event" -> delete(call, confirmed)
                else -> AssistantActionResult.Failure("Unsupported assistant action '${call.name}'.")
            }
        } catch (e: SecurityException) {
            AssistantActionResult.PermissionRequired("Allow Dayflow calendar access to read or change device calendar events.")
        } catch (e: Exception) {
            AssistantActionResult.Failure(e.message ?: "The assistant action failed.")
        }
    }

    /**
     * Layered search. A user-supplied search key is used first because it is the only route that
     * returns clean structured results; the remaining sources need no account at all and each one
     * covers a different failure mode (markup change, bot challenge, or narrow result set).
     */
    private fun search(query: String): String {
        require(query.isNotBlank()) { "Enter a search query." }
        val trimmed = query.trim()
        val searchKey = aiConfiguration?.read()?.searchApiKey.orEmpty()
        if (searchKey.isNotBlank()) {
            runCatching { exaSearch(trimmed, searchKey) }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
                return "Search results for: $trimmed (source: Exa)\n$it"
            }
        }
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val endpoints = listOf("https://html.duckduckgo.com/html/?q=$encoded", "https://lite.duckduckgo.com/lite/?q=$encoded")
        val failures = mutableListOf<String>()
        for (endpoint in endpoints) {
            val html = try { httpGet(endpoint, accept = "text/html,application/xhtml+xml", language = Locale.getDefault().toLanguageTag()) }
            catch (e: Exception) { failures += (e.message ?: "Network error").take(120); continue }
            val results = parseSearchResults(html)
            if (results.isNotEmpty()) return "Search results for: $trimmed (source: DuckDuckGo)\n${results.joinToString("\n\n")}"
            if (Regex("(?i)captcha|anomaly|bot challenge").containsMatchIn(html)) failures += "The search provider blocked this request."
        }
        val bing = runCatching { httpGet("https://www.bing.com/search?q=$encoded", accept = "text/html,application/xhtml+xml") }
            .getOrElse { failures += (it.message ?: "Network error").take(120); "" }
        val bingResults = parseBingResults(bing)
        if (bingResults.isNotEmpty()) return "Search results for: $trimmed (source: Bing)\n${bingResults.joinToString("\n\n")}"
        // Keyless sources that do not depend on scraping a results page, so a markup change upstream
        // cannot take search down completely.
        runCatching { wikipediaSearch(trimmed) }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
            return "Search results for: $trimmed (source: Wikipedia)\n$it"
        }
        runCatching { instantAnswer(trimmed) }.getOrNull()?.takeIf { it.isNotBlank() }?.let {
            return "Search results for: $trimmed (source: DuckDuckGo instant answers)\n$it"
        }
        return if (failures.isEmpty()) "No current web facts were verified for that search."
        else "Web search could not be completed: ${failures.distinct().joinToString("; ")}. No current web facts were verified."
    }

    private fun httpGet(url: String, accept: String = "*/*", language: String? = null): String {
        val connection = (URL(url).openConnection() as java.net.HttpURLConnection).apply {
            connectTimeout = 10000; readTimeout = 12000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", accept)
            if (language != null) setRequestProperty("Accept-Language", language)
        }
        return try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Search provider returned HTTP $code.")
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally { connection.disconnect() }
    }

    /** Structured results from Exa, used only when the user has pasted a search key in Settings. */
    private fun exaSearch(query: String, apiKey: String): String {
        val connection = (URL("https://api.exa.ai/search").openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "POST"; connectTimeout = 10000; readTimeout = 15000; doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("x-api-key", apiKey)
        }
        return try {
            val body = JSONObject().put("query", query).put("numResults", 5)
                .put("contents", JSONObject().put("text", JSONObject().put("maxCharacters", 500))).toString()
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val response = (if (code in 200..299) connection.inputStream else connection.errorStream)
                ?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) throw IllegalStateException("Search provider returned HTTP $code.")
            val results = JSONObject(response).optJSONArray("results") ?: return ""
            (0 until results.length()).mapNotNull { index ->
                val item = results.optJSONObject(index) ?: return@mapNotNull null
                val title = item.optString("title").trim()
                val url = item.optString("url").trim()
                val text = item.optString("text").replace(Regex("\\s+"), " ").trim().take(400)
                if (title.isBlank() || !url.startsWith("http")) null else listOf(title, text, url).filter(String::isNotBlank).joinToString("\n")
            }.take(5).joinToString("\n\n")
        } finally { connection.disconnect() }
    }

    /** Wikipedia's JSON API is free, needs no key and never changes shape under us. */
    private fun wikipediaSearch(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = httpGet("https://en.wikipedia.org/w/api.php?action=query&list=search&srsearch=$encoded&srlimit=4&format=json&utf8=1", accept = "application/json")
        val hits = JSONObject(json).optJSONObject("query")?.optJSONArray("search") ?: return ""
        return (0 until hits.length()).mapNotNull { index ->
            val hit = hits.optJSONObject(index) ?: return@mapNotNull null
            val title = hit.optString("title").trim()
            if (title.isBlank()) return@mapNotNull null
            val snippet = hit.optString("snippet").replace(Regex("<[^>]+>"), "").replace("&quot;", "\"").trim()
            val pageId = hit.optLong("pageid", 0L)
            val url = if (pageId > 0) "https://en.wikipedia.org/?curid=$pageId"
            else "https://en.wikipedia.org/wiki/${URLEncoder.encode(title.replace(' ', '_'), "UTF-8")}"
            listOf(title, snippet, url).filter(String::isNotBlank).joinToString("\n")
        }.joinToString("\n\n")
    }

    /** Last-resort keyless source for direct factual questions. */
    private fun instantAnswer(query: String): String {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val json = httpGet("https://api.duckduckgo.com/?q=$encoded&format=json&no_html=1&no_redirect=1&skip_disambig=1", accept = "application/json")
        val root = JSONObject(json)
        val blocks = mutableListOf<String>()
        root.optString("AbstractText").trim().takeIf { it.isNotBlank() }?.let { abstract ->
            blocks += listOf(root.optString("Heading").ifBlank { query }, abstract, root.optString("AbstractURL"))
                .filter(String::isNotBlank).joinToString("\n")
        }
        val topics = root.optJSONArray("RelatedTopics") ?: JSONArray()
        for (index in 0 until minOf(topics.length(), 3)) {
            val topic = topics.optJSONObject(index) ?: continue
            val text = topic.optString("Text").trim()
            val url = topic.optString("FirstURL").trim()
            if (text.isNotBlank()) blocks += listOf(text, url).filter(String::isNotBlank).joinToString("\n")
        }
        return blocks.joinToString("\n\n")
    }

    private fun parseSearchResults(html: String): List<String> {
        val anchors = Regex("(?is)<a\\b([^>]*class=['\"][^'\"]*(?:result__a|result-link)[^'\"]*['\"][^>]*)>(.*?)</a>")
            .findAll(html).toList()
        return anchors.mapIndexedNotNull { index, link ->
            val attributes = link.groupValues[1]
            val rawUrl = Regex("(?is)href=['\"]([^'\"]+)['\"]").find(attributes)?.groupValues?.get(1) ?: return@mapIndexedNotNull null
            val url = unwrapSearchUrl(cleanHtml(rawUrl))
            val title = cleanHtml(link.groupValues[2])
            val tailEnd = anchors.getOrNull(index + 1)?.range?.first ?: (link.range.last + 1 + 2500).coerceAtMost(html.length)
            val tail = html.substring((link.range.last + 1).coerceAtMost(html.length), tailEnd)
            val snippet = Regex("(?is)(?:result__snippet|result-snippet)[^>]*>(.*?)</(?:a|div|td)").find(tail)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty()
            if (title.isBlank() || !url.startsWith("http")) null else listOf(title, snippet, url).filter(String::isNotBlank).joinToString("\n")
        }.distinct().take(5)
    }

    private fun parseBingResults(html: String): List<String> {
        if (html.isBlank()) return emptyList()
        val entries = Regex("(?is)<li\\b[^>]*class=['\"][^'\"]*b_algo[^'\"]*['\"][^>]*>(.*?)(?=<li\\b[^>]*class=['\"][^'\"]*b_algo|</ol>)")
            .findAll(html).mapNotNull { match ->
                val entry = match.groupValues[1]
                val link = Regex("(?is)<h2[^>]*>\\s*<a\\b[^>]*href=['\"]([^'\"]+)['\"][^>]*>(.*?)</a>")
                    .find(entry) ?: return@mapNotNull null
                val url = cleanHtml(link.groupValues[1])
                val title = cleanHtml(link.groupValues[2])
                val snippet = Regex("(?is)<p[^>]*>(.*?)</p>").find(entry)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty()
                if (title.isBlank() || !url.startsWith("http")) null
                else listOf(title, snippet, url).filter(String::isNotBlank).joinToString("\n")
            }.distinct().take(5).toList()
        return entries
    }

    private fun fetchWebpage(rawUrl: String): String {
        var target = validatePublicHttpsUrl(rawUrl)
        for (redirect in 0..4) {
            val connection = (target.openConnection() as javax.net.ssl.HttpsURLConnection).apply {
                connectTimeout = 10000
                readTimeout = 15000
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/124 Mobile Safari/537.36")
                setRequestProperty("Accept", "text/html,application/xhtml+xml,application/json,text/plain;q=0.9,*/*;q=0.5")
            }
            try {
                val code = connection.responseCode
                if (code in 300..399) {
                    val location = connection.getHeaderField("Location") ?: throw IllegalStateException("The page redirected without a destination.")
                    if (redirect == 4) throw IllegalStateException("The page redirected too many times.")
                    target = validatePublicHttpsUrl(target.toURI().resolve(location).toString())
                    continue
                }
                if (code !in 200..299) throw IllegalStateException("The website returned HTTP $code.")
                val contentType = connection.contentType.orEmpty().lowercase(Locale.ROOT)
                if (contentType.isNotBlank() && !listOf("text/html", "application/xhtml+xml", "text/plain", "application/json").any(contentType::startsWith)) {
                    throw IllegalStateException("That link is not a readable webpage or text document.")
                }
                val body = connection.inputStream.use(::readWebBody)
                val finalUrl = connection.url.toString()
                val (title, content) = if (contentType.contains("html")) extractPageText(body) else "" to body.trim()
                if (content.isBlank()) throw IllegalStateException("The webpage did not contain readable text.")
                return buildString {
                    append("Page title: ").append(title.ifBlank { target.host }).append('\n')
                    append("Source URL: ").append(finalUrl).append("\n\n")
                    append(content.take(14000))
                }
            } finally { connection.disconnect() }
        }
        throw IllegalStateException("Could not open that webpage.")
    }

    private fun validatePublicHttpsUrl(value: String): URL {
        val cleaned = value.trim().trimEnd('.', ',', ';', ')', ']', '}')
        val uri = runCatching { java.net.URI(cleaned) }.getOrElse { throw IllegalArgumentException("Enter a valid HTTPS webpage URL.") }
        require(uri.scheme.equals("https", ignoreCase = true) && uri.userInfo == null) { "Only public HTTPS webpage links can be opened." }
        val host = uri.host?.trimEnd('.')?.lowercase(Locale.ROOT) ?: throw IllegalArgumentException("That webpage URL has no valid host.")
        require(host != "localhost" && !host.endsWith(".localhost") && !host.endsWith(".local") && !host.endsWith(".internal")) {
            "Only public internet webpages can be opened."
        }
        val addresses = try { InetAddress.getAllByName(host) } catch (e: Exception) { throw IllegalStateException("Could not resolve the website host.", e) }
        require(addresses.isNotEmpty() && addresses.all(::isPublicInternetAddress)) { "That URL does not resolve to a public internet address." }
        return uri.toURL()
    }

    private fun isPublicInternetAddress(address: InetAddress): Boolean {
        if (address.isAnyLocalAddress || address.isLoopbackAddress || address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress) return false
        val bytes = address.address.map { it.toInt() and 0xff }
        if (address is Inet4Address) {
            val first = bytes[0]; val second = bytes[1]
            if (first == 0 || first >= 224 || (first == 100 && second in 64..127) || (first == 198 && second in 18..19)) return false
        }
        if (address is Inet6Address && (bytes[0] and 0xfe) == 0xfc) return false
        return true
    }

    private fun readWebBody(input: java.io.InputStream): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > 1_000_000) throw IllegalStateException("That webpage is too large to read safely.")
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun extractPageText(html: String): Pair<String, String> {
        val title = Regex("(?is)<title[^>]*>(.*?)</title>").find(html)?.groupValues?.get(1)?.let(::cleanHtml).orEmpty()
        val readable = html
            .replace(Regex("(?is)<!--.*?-->|<(script|style|noscript|svg|iframe|nav|footer|header|form)\\b[^>]*>.*?</\\1>"), " ")
            .replace(Regex("(?i)<(br|/p|/div|/li|/h[1-6]|/article|/section)\\b[^>]*>"), "\n")
            .replace(Regex("(?is)<[^>]+>"), " ")
        val text = cleanHtml(readable).replace(Regex("[ \\t]+"), " ").replace(Regex(" *\\n *"), "\n").trim()
        return title to text
    }

    private fun cleanHtml(value: String) = Html.fromHtml(value, Html.FROM_HTML_MODE_LEGACY).toString().replace(Regex("\\s+"), " ").trim()
    private fun unwrapSearchUrl(value: String): String = runCatching {
        val uri = java.net.URI(value)
        if (uri.host?.contains("duckduckgo.com") == true) {
            val target = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("uddg=") }?.substringAfter('=')
            target?.let { URLDecoder.decode(it, "UTF-8") } ?: value
        } else value
    }.getOrDefault(value)

    private fun events(date: LocalDate): AssistantActionResult {
        requirePermission(Manifest.permission.READ_CALENDAR)
        val zone = ZoneId.systemDefault()
        val from = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val until = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = CalendarContract.Instances.CONTENT_URI.buildUpon().appendPath(from.toString()).appendPath(until.toString()).build()
        val projection = arrayOf(CalendarContract.Instances.EVENT_ID, CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN, CalendarContract.Instances.END, CalendarContract.Instances.EVENT_LOCATION, CalendarContract.Instances.CALENDAR_ID)
        val result = context.contentResolver.query(uri, projection, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { cursor ->
            buildList {
                val idIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                val titleIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                val beginIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                val endIndex = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                val locationIndex = cursor.getColumnIndex(CalendarContract.Instances.EVENT_LOCATION)
                while (cursor.moveToNext()) {
                    val start = java.time.Instant.ofEpochMilli(cursor.getLong(beginIndex)).atZone(zone)
                    val end = java.time.Instant.ofEpochMilli(cursor.getLong(endIndex)).atZone(zone)
                    add("id=${cursor.getLong(idIndex)} · ${start.toLocalTime()}–${end.toLocalTime()} · ${cursor.getString(titleIndex)}" + (if (locationIndex >= 0) cursor.getString(locationIndex)?.let { " · $it" }.orEmpty() else ""))
                }
            }
        }.orEmpty()
        return AssistantActionResult.Complete(if (result.isEmpty()) "No calendar events found on $date." else "Device calendar events on $date:\n${result.joinToString("\n")}")
    }

    private fun create(call: LumiFunctionCall, confirmed: Boolean): AssistantActionResult {
        requirePermission(Manifest.permission.WRITE_CALENDAR)
        val args = call.arguments
        val title = args.optString("title").trim(); require(title.isNotBlank()) { "Event title is required." }
        val date = parseDate(args.optString("date"), null) ?: return AssistantActionResult.Failure("Event date must be YYYY-MM-DD.")
        val start = parseTime(args.optString("start_time")) ?: return AssistantActionResult.Failure("Start time must be HH:mm.")
        val end = parseTime(args.optString("end_time")) ?: return AssistantActionResult.Failure("End time must be HH:mm.")
        require(end.isAfter(start)) { "Event end time must be after its start time." }
        if (!confirmed) return AssistantActionResult.Confirm("Create '$title' on $date from $start to $end in your device calendar?", call)
        val calendarId = writableCalendarId() ?: return AssistantActionResult.Failure("No writable calendar is available on this device. Add or enable a calendar account first.")
        val zone = ZoneId.systemDefault()
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId); put(CalendarContract.Events.TITLE, title)
            put(CalendarContract.Events.DTSTART, date.atTime(start).atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.DTEND, date.atTime(end).atZone(zone).toInstant().toEpochMilli())
            put(CalendarContract.Events.EVENT_TIMEZONE, zone.id); put(CalendarContract.Events.DESCRIPTION, args.optString("description"))
            put(CalendarContract.Events.HAS_ALARM, 0)
        }
        val uri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return AssistantActionResult.Failure("Calendar provider did not create the event.")
        val eventId = ContentUris.parseId(uri)
        val verified = queryEvent(eventId)
        return if (verified != null) AssistantActionResult.Complete("Created calendar event '${verified.title}' on $date from $start to $end (event id $eventId).") else AssistantActionResult.Failure("The event insert could not be verified.")
    }

    private fun update(call: LumiFunctionCall, confirmed: Boolean): AssistantActionResult {
        requirePermission(Manifest.permission.WRITE_CALENDAR)
        val id = call.arguments.optLong("event_id", -1L)
        val existing = queryEvent(id) ?: return AssistantActionResult.Failure("That calendar event id is no longer available.")
        if (!confirmed) return AssistantActionResult.Confirm("Update calendar event '${existing.title}'?", call)
        val values = ContentValues()
        if (call.arguments.has("title")) values.put(CalendarContract.Events.TITLE, call.arguments.optString("title"))
        val date = if (call.arguments.has("date")) parseDate(call.arguments.optString("date"), null) ?: return AssistantActionResult.Failure("Date must be YYYY-MM-DD.") else null
        val start = if (call.arguments.has("start_time")) parseTime(call.arguments.optString("start_time")) ?: return AssistantActionResult.Failure("Start time must be HH:mm.") else null
        val end = if (call.arguments.has("end_time")) parseTime(call.arguments.optString("end_time")) ?: return AssistantActionResult.Failure("End time must be HH:mm.") else null
        if (date != null || start != null || end != null) {
            val zone = ZoneId.systemDefault()
            val chosenDate = date ?: java.time.Instant.ofEpochMilli(existing.startMillis).atZone(zone).toLocalDate()
            val chosenStart = start ?: java.time.Instant.ofEpochMilli(existing.startMillis).atZone(zone).toLocalTime()
            val chosenEnd = end ?: java.time.Instant.ofEpochMilli(existing.endMillis).atZone(zone).toLocalTime()
            require(chosenEnd.isAfter(chosenStart)) { "Event end time must be after its start time." }
            values.put(CalendarContract.Events.DTSTART, chosenDate.atTime(chosenStart).atZone(zone).toInstant().toEpochMilli())
            values.put(CalendarContract.Events.DTEND, chosenDate.atTime(chosenEnd).atZone(zone).toInstant().toEpochMilli())
            values.put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        if (values.size() == 0) return AssistantActionResult.Failure("Specify a title, date, or time change.")
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        if (context.contentResolver.update(uri, values, null, null) != 1) return AssistantActionResult.Failure("Calendar provider did not update the event.")
        val updated = queryEvent(id)
        return if (updated != null) AssistantActionResult.Complete("Updated calendar event '${updated.title}'.") else AssistantActionResult.Failure("Calendar update could not be verified.")
    }

    private fun delete(call: LumiFunctionCall, confirmed: Boolean): AssistantActionResult {
        requirePermission(Manifest.permission.WRITE_CALENDAR)
        val id = call.arguments.optLong("event_id", -1L)
        val event = queryEvent(id) ?: return AssistantActionResult.Failure("That calendar event id is no longer available.")
        if (!confirmed) return AssistantActionResult.Confirm("Delete calendar event '${event.title}'? This cannot be undone.", call)
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        context.contentResolver.delete(uri, null, null)
        return if (queryEvent(id) == null) AssistantActionResult.Complete("Deleted calendar event '${event.title}'.") else AssistantActionResult.Failure("Calendar provider could not verify deletion.")
    }

    private fun queryEvent(id: Long): CalendarEvent? {
        if (id < 0) return null
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        return context.contentResolver.query(uri, arrayOf(CalendarContract.Events._ID, CalendarContract.Events.TITLE, CalendarContract.Events.DTSTART, CalendarContract.Events.DTEND), null, null, null)?.use {
            if (it.moveToFirst()) CalendarEvent(it.getLong(0), it.getString(1).orEmpty(), it.getLong(2), it.getLong(3)) else null
        }
    }

    private fun writableCalendarId(): Long? {
        val uri = CalendarContract.Calendars.CONTENT_URI
        return context.contentResolver.query(uri, arrayOf(CalendarContract.Calendars._ID), "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=?", arrayOf(CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR.toString()), "${CalendarContract.Calendars.IS_PRIMARY} DESC")?.use { if (it.moveToFirst()) it.getLong(0) else null }
    }

    private fun requirePermission(permission: String) {
        if (ContextCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) throw SecurityException("Calendar permission required.")
    }
    private fun parseDate(value: String, fallback: LocalDate?): LocalDate? = if (value.isBlank()) fallback else runCatching { LocalDate.parse(value) }.getOrNull()
    private fun parseTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
    private fun tool(name: String, desc: String, props: JSONObject, required: List<String> = emptyList()) = JSONObject().put("type", "function").put("function", JSONObject().put("name", name).put("description", desc).put("parameters", JSONObject().put("type", "object").put("properties", props).put("required", JSONArray(required)).put("additionalProperties", false)))
    private fun props(vararg values: Pair<String, JSONObject>) = JSONObject().apply { values.forEach { put(it.first, it.second) } }
    private fun str(desc: String) = JSONObject().put("type", "string").put("description", desc)
    private fun num(desc: String) = JSONObject().put("type", "integer").put("description", desc)
}
