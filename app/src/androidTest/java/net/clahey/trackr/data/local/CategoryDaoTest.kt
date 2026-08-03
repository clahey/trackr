package net.clahey.trackr.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith

// @spec LS-BE-010, LS-BE-011, LS-BE-012, LS-BE-013
@RunWith(AndroidJUnit4::class)
class CategoryDaoTest {
    // TODO: implement when TrackrDatabase and CategoryDao are wired up in Phase 6
    // Tests to cover:
    //   LS-BE-010 — getAll() ordered by sortOrder ASC
    //   LS-BE-011 — new category sortOrder = currentMin - 1
    //   LS-BE-012 — reorderCategories reassigns sequential sortOrder values
    //   LS-BE-013 — getEventCountForCategory returns live Flow<Int>
    //   LS-BE-071 — CASCADE DELETE removes child events when category deleted
}
