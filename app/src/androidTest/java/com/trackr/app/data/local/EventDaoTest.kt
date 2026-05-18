package com.trackr.app.data.local

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.runner.RunWith

// @spec LS-BE-020, LS-BE-021, LS-BE-030, LS-BE-031, LS-BE-032, LS-BE-040
@RunWith(AndroidJUnit4::class)
class EventDaoTest {
    // TODO: implement when TrackrDatabase and EventDao are wired up in Phase 6
    // Tests to cover:
    //   LS-BE-020 — getAll ordered timestamp DESC, createdAt DESC, id ASC
    //   LS-BE-021 — getByCategory same ordering
    //   LS-BE-030 — deleteEvent: DB row deleted before image files
    //   LS-BE-031 — deleteCategory: cascade collects paths, deletes DB row, then files
    //   LS-BE-032 — saveEvent: read old paths → upsert → delete removed files
    //   LS-BE-040 — onStartup scans image dir and deletes unreferenced files
}
