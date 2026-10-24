package com.dayflow.domain

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Week/day boundary math and display formatting. Pure JVM, unit tested. */
object WeekUtils {

    /** First day (Monday, ISO) of the week containing [date]. */
    fun weekStart(date: LocalDate, firstDay: java.time.DayOfWeek = java.time.DayOfWeek.MONDAY): LocalDate =
        date.minusDays(((date.dayOfWeek.value - firstDay.value + 7) % 7).toLong())

    fun weekDates(start: LocalDate): List<LocalDate> = (0L until 7L).map { start.plusDays(it) }

    fun weekEnd(start: LocalDate): LocalDate = start.plusDays(6L)

    fun weekRangeLabel(start: LocalDate, locale: Locale = Locale.getDefault()): String {
        val dayMonth = DateTimeFormatter.ofPattern("MMM d", locale)
        return "${start.format(dayMonth)} – ${weekEnd(start).format(dayMonth)}"
    }

    fun monthYearLabel(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))

    fun dayTitle(date: LocalDate, locale: Locale = Locale.getDefault()): String =
        date.format(DateTimeFormatter.ofPattern("EEEE, MMM d", locale))

    fun timeLabel(minutes: Int, twentyFourHour: Boolean = true): String {
        val hour = minutes / 60
        val minute = minutes % 60
        return if (twentyFourHour) "%02d:%02d".format(hour, minute)
        else "%d:%02d %s".format(if (hour % 12 == 0) 12 else hour % 12, minute, if (hour < 12) "AM" else "PM")
    }

    fun durationLabel(minutes: Int): String = when {
        minutes < 60 -> "${minutes}m"
        minutes % 60 == 0 -> "${minutes / 60}h"
        else -> "${minutes / 60}h ${minutes % 60}m"
    }

    fun isToday(date: LocalDate, today: LocalDate = LocalDate.now()): Boolean = date == today
}
