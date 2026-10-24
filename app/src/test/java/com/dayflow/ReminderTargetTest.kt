package com.dayflow

import com.dayflow.notifications.ReminderTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A reminder for an action task opens its link straight from the notification. That only applies to
 * secure links: an http page still opens the app first, where the task's own confirmation step can ask.
 */
class ReminderTargetTest {

    @Test
    fun aSecureActionLinkIsOpenedDirectly() {
        assertEquals("https://builder.aws.com/start", ReminderTarget.actionUrl("https://builder.aws.com/start"))
        assertEquals("HTTPS is case-insensitive", "HTTPS://example.com/a", ReminderTarget.actionUrl("  HTTPS://example.com/a  "))
    }

    @Test
    fun anInsecureLinkFallsBackToOpeningTheApp() {
        assertNull(ReminderTarget.actionUrl("http://example.com/action"))
    }

    @Test
    fun onlyRealLinksAreUsed() {
        assertNull(ReminderTarget.actionUrl(null))
        assertNull(ReminderTarget.actionUrl(""))
        assertNull(ReminderTarget.actionUrl("   "))
        assertNull("a bare scheme is not a link", ReminderTarget.actionUrl("https://"))
        assertNull(ReminderTarget.actionUrl("javascript:alert(1)"))
        assertNull(ReminderTarget.actionUrl("not a link"))
    }

    @Test
    fun reminderIdsReplaceTheSameAlarmRatherThanAddingOne() {
        val first = ReminderTarget.reminderId(12L, 20_451L)
        assertEquals("the same task and day must keep one alarm", first, ReminderTarget.reminderId(12L, 20_451L))
        assertNotEquals("a different day is a different reminder", first, ReminderTarget.reminderId(12L, 20_452L))
        assertNotEquals("a different task is a different reminder", first, ReminderTarget.reminderId(13L, 20_451L))
        assertTrue("notification ids must stay valid", first > 0)
    }
}
