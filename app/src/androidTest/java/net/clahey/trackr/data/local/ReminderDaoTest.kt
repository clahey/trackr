package net.clahey.trackr.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

// @spec REM-DATA-001, REM-DATA-006, REM-DATA-007, REM-DATA-008
@RunWith(AndroidJUnit4::class)
class ReminderDaoTest {
    // TODO: run on a device/emulator (Phase 6 already covers this behavior via
    // FakeTrackrRepositoryTest for the logic layer; this exercises the real Room/SQLite side).
    // Tests to cover:
    //   REM-DATA-001 — CASCADE DELETE: deleting a category removes its reminder row
    //   REM-DATA-006 — getByCategoryId/upsert round-trip
    //   REM-DATA-007 — getAllEnabledOnce returns only enabled=true rows
    //   REM-DATA-008 — a saveCategoryWithReminder-style transaction preserves the DB's current
    //     nextFireAt regardless of what's passed in (exercised at LocalTrackrRepository level)
}
