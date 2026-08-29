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
        assertEquals(noon.minusSeconds(20 * 60), result.single().postedAt)
    }

    // ---- Reads taken while the app's own change is still in flight (REM-NOTIF-014) ----

    // @spec REM-NOTIF-014
    @Test fun `a cancelled reminder is out even while the read still shows it`() {
        val result = outstandingRemindersAfterCancel(
            listOf(showing("cat1", 10), showing("cat2", 5)),
            categoryId = "cat1",
        )
        assertEquals(listOf("cat2"), result.map { it.categoryId })
    }

    // What the summary's fate turns on: the last reminder cancelled has to leave nothing behind.
    // @spec REM-NOTIF-004, REM-NOTIF-014
    @Test fun `cancelling the only reminder leaves nothing outstanding while the read lags`() {
        val result = outstandingRemindersAfterCancel(
            listOf(showing("cat1", 10), showing(null, 10, isGroupSummary = true)),
            categoryId = "cat1",
        )
        assertTrue(result.isEmpty())
    }

    // @spec REM-NOTIF-014
    @Test fun `a cancel the read has already applied is not undone`() {
        val result = outstandingRemindersAfterCancel(listOf(showing("cat2", 5)), categoryId = "cat1")
        assertEquals(listOf("cat2"), result.map { it.categoryId })
    }

    // @spec REM-NOTIF-014
    @Test fun `a posted reminder is outstanding even while the read still omits it`() {
        val result = outstandingRemindersAfterPost(
            listOf(showing("cat1", 10)),
            categoryId = "cat2",
            postedAt = noon,
        )
        assertEquals(listOf("cat2", "cat1"), result.map { it.categoryId })
    }

    // Re-posting for a category already showing replaces that notification (REM-NOTIF-007), so it
    // must not produce a second row for it.
    // @spec REM-NOTIF-007, REM-NOTIF-014
    @Test fun `a post the read has already applied is not duplicated`() {
        val result = outstandingRemindersAfterPost(
            listOf(showing("cat1", 10)),
            categoryId = "cat1",
            postedAt = noon,
        )
        assertEquals(listOf("cat1"), result.map { it.categoryId })
        assertEquals(noon.minusSeconds(10 * 60), result.single().postedAt)
    }
}
