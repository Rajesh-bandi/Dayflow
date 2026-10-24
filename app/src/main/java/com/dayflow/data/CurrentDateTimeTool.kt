package com.dayflow.data

import org.json.JSONObject
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

/** Provides the model with the device's live local clock only when the model requests it. */
object CurrentDateTimeTool {
    const val NAME = "get_current_datetime"

    fun definition(): JSONObject = JSONObject()
        .put("type", "function")
        .put("function", JSONObject()
            .put("name", NAME)
            .put("description", "Get the current date, time, day of week, and timezone from this device. Use for questions about today's date or the current time.")
            .put("parameters", JSONObject()
                .put("type", "object")
                .put("properties", JSONObject())
                .put("required", org.json.JSONArray())
                .put("additionalProperties", false)))

    fun read(): String {
        val now = ZonedDateTime.now(ZoneId.systemDefault())
        return JSONObject()
            .put("local_date", now.format(DateTimeFormatter.ISO_LOCAL_DATE))
            .put("local_time", now.format(DateTimeFormatter.ofPattern("HH:mm:ss")))
            .put("day_of_week", now.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() })
            .put("timezone", now.zone.id)
            .put("utc_offset", now.offset.id)
            .put("iso_datetime", now.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
            .toString()
    }
}
