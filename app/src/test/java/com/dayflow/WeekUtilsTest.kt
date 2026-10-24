package com.dayflow

import com.dayflow.domain.WeekUtils
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class WeekUtilsTest {

    private val wednesday = LocalDate.of(2026, 10, 7)

    @Test
    fun weekStartIsMonday() {
        assertEquals(LocalDate.of(2026, 10, 5), WeekUtils.weekStart(wednesday))
        assertEquals(LocalDate.of(2026, 10, 5), WeekUtils.weekStart(LocalDate.of(2026, 10, 5)))
    }

    @Test
    fun sundayBelongsToItsOwnWeekNotTheNext() {
        val sunday = LocalDate.of(2026, 10, 11)
        assertEquals(LocalDate.of(2026, 10, 5), WeekUtils.weekStart(sunday))
        assertEquals(LocalDate.of(2026, 10, 12), WeekUtils.weekStart(sunday.plusDays(1)))
    }

    @Test
    fun weekSpansExactlySevenDays() {
        val start = WeekUtils.weekStart(wednesday)
        val dates = WeekUtils.weekDates(start)
        assertEquals(7, dates.size)
        assertEquals(start, dates.first())
        assertEquals(start.plusDays(6), dates.last())
        assertEquals(LocalDate.of(2026, 10, 11), WeekUtils.weekEnd(start))
    }

    @Test
    fun weekNavigationCrossesMonthBoundary() {
        val nextWeek = WeekUtils.weekStart(wednesday).plusWeeks(1)
        assertEquals(LocalDate.of(2026, 10, 12), nextWeek)
        val previousWeek = WeekUtils.weekStart(LocalDate.of(2026, 11, 2)).minusWeeks(1)
        assertEquals(LocalDate.of(2026, 10, 26), previousWeek)
    }

    @Test
    fun durationLabelFormatsHoursAndMinutes() {
        assertEquals("45m", WeekUtils.durationLabel(45))
        assertEquals("3h", WeekUtils.durationLabel(180))
        assertEquals("3h 30m", WeekUtils.durationLabel(210))
    }

    @Test
    fun timeLabelIsZeroPadded24h() {
        assertEquals("06:05", WeekUtils.timeLabel(6 * 60 + 5))
        assertEquals("19:00", WeekUtils.timeLabel(19 * 60))
    }
}
