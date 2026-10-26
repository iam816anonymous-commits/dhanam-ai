package com.example.notifications

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationReminderManagerTest {

    @Test
    fun testStableNotificationIdCollisionAvoidance() {
        val id1 = NotificationReminderManager.getStableNotificationId(1L, 10000)
        val id2 = NotificationReminderManager.getStableNotificationId(2L, 10000)
        val idLarge = NotificationReminderManager.getStableNotificationId(Int.MAX_VALUE.toLong() + 500000L, 10000)

        assertNotEquals(id1, id2)
        assertEquals(id1, NotificationReminderManager.getStableNotificationId(1L, 10000))
        assertNotEquals(id1, NotificationReminderManager.getStableNotificationId(1L, 30000))
        assertTrue(idLarge > 0)
    }
}
