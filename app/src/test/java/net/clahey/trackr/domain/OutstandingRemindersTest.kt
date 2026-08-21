package net.clahey.trackr.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant

class OutstandingRemindersTest {

    private val noon: Instant = Instant.parse("2024-01-15T12:00:00Z")

    private fun showing(tag: String?, minutesAgo: Long, isGroupSummary: Boolean = false) =
        ShowingNotification(
            tag = tag,
            isGroupSummary = isGroupSummary,
            postedAt = noon.minusSeconds(minutesAgo * 60),
        )

    // @spec REM-NOTIF-008
    @Test fun `nothing showing means nothing outstanding`() {
        assertTrue(outstandingReminders(emptyList()).isEmpty())
    }

    // @spec REM-NOTIF-008
    @Test fun `each showing notification contributes its tag as a category`() {
        val result = outstandingReminders(listOf(showing("cat1", 10), showing("cat2", 5)))
        assertEquals(listOf("cat2", "cat1"), result.map { it.categoryId })
    }

    // The summary is one of the app's own notifications, so it comes back from the same read.
    // @spec REM-NOTIF-004, REM-NOTIF-008
    @Test fun `the group summary is not itself an outstanding reminder`() {
        val result = outstandingReminders(
            listOf(showing("cat1", 10), showing(null, 10, isGroupSummary = true)),
        )
        assertEquals(listOf("cat1"), result.map { it.categoryId })
    }

    // @spec REM-NOTIF-004, REM-NOTIF-008
    @Test fun `a summary showing alone leaves nothing outstanding`() {
        assertTrue(outstandingReminders(listOf(showing(null, 1, isGroupSummary = true))).isEmpty())
    }

    // Nothing the app posts is untagged (REM-NOTIF-007), but an untagged entry cannot be
    // attributed to a category, so it is dropped rather than rendered as a blank row.
    // @spec REM-NOTIF-007, REM-NOTIF-008
    @Test fun `an untagged notification is not an outstanding reminder`() {
        assertTrue(outstandingReminders(listOf(showing(null, 1))).isEmpty())
    }

    // @spec EL-UI-096
    @Test fun `outstanding reminders are ordered most recently fired first`() {
        val result = outstandingReminders(
            listOf(showing("oldest", 30), showing("newest", 1), showing("middle", 15)),
        )
        assertEquals(listOf("newest", "middle", "oldest"), result.map { it.categoryId })
    }

    // @spec EL-UI-096
    @Test fun `each outstanding reminder carries the time its notification was posted`() {
        val result = outstandingReminders(listOf(showing("cat1", 20)))
        assertEquals(noon.minusSeconds(20 * 60), result.single().firedAt)
    }
}
