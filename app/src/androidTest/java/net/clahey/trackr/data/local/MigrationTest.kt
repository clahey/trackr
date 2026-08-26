package net.clahey.trackr.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TEST_DB = "migration-test"

// @spec LS-BE-070
class MigrationTest {

    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        TrackrDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    // MIGRATION_1_2 itself is untestable via MigrationTestHelper: no 1.json schema was ever
    // exported (exportSchema wasn't enabled yet at that point in the project's history, and it
    // isn't reconstructable after the fact), so there's no "before" schema to build a v1 database
    // from. It's the one migration in this file this doesn't cover — see docs/arrows/local-storage.md.

    @Test fun migrate2To3_convertsNumberUnitToDefaultValueJson() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO categories (id, name, emoji, color, valueType, unit, allowEmptyText, sortOrder, parentId)
                VALUES ('c1', 'Water', '💧', 100, 'number', 'oz', 1, 0, NULL)
                """.trimIndent(),
            )
            // A non-number category's unit (if ever set) must not leak into defaultValue.
            execSQL(
                """
                INSERT INTO categories (id, name, emoji, color, valueType, unit, allowEmptyText, sortOrder, parentId)
                VALUES ('c2', 'Mood', '🙂', 200, 'scale', NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        db.query("SELECT defaultValue FROM categories WHERE id = 'c1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                """{"type":"NumberValue","value":0.0,"unit":"oz"}""",
                cursor.getString(cursor.getColumnIndexOrThrow("defaultValue")),
            )
        }
        db.query("SELECT defaultValue FROM categories WHERE id = 'c2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("defaultValue")))
        }
    }

    // Fresh table creation (no pre-existing rows to preserve or lose) — runMigrationsAndValidate
    // already fails the test if the resulting schema doesn't match the target version's schema, so
    // these two only have to prove the migration runs at all for a device starting fresh at
    // version 3. MIGRATION_3_5 is the interim path, retired once every device has reached 6.
    @Test fun migrate3To5Directly_runs() {
        helper.createDatabase(TEST_DB, 3).apply { close() }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_3_5)
    }

    @Test fun migrate3To6Directly_runs() {
        helper.createDatabase(TEST_DB, 3).apply { close() }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_3_6)
    }

    // The only migration that has to preserve reminder rows: `times` becomes NOT NULL, so a stored
    // null has to arrive as an empty list.
    // @spec LS-BE-073
    @Test fun migrate5To6_backfillsNullTimesToAnEmptyList() {
        helper.createDatabase(TEST_DB, 5).apply {
            execSQL(
                """
                INSERT INTO categories (id, name, emoji, color, valueType, defaultValue, allowEmptyText, sortOrder, parentId)
                VALUES ('c1', 'Water', '💧', 100, 'number', NULL, 1, 0, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO categories (id, name, emoji, color, valueType, defaultValue, allowEmptyText, sortOrder, parentId)
                VALUES ('c2', 'Mood', '🙂', 200, 'scale', NULL, 1, 1, NULL)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO reminders (categoryId, enabled, mode, times, windowStart, windowEnd,
                    occurrencesPerDay, daysActive, showCategoryInNotification, nextFireAt)
                VALUES ('c1', 1, 'RANDOM', NULL, '09:00', '21:00', 4, '["MONDAY"]', 0, 1700000000000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO reminders (categoryId, enabled, mode, times, windowStart, windowEnd,
                    occurrencesPerDay, daysActive, showCategoryInNotification, nextFireAt)
                VALUES ('c2', 1, 'FIXED', '["08:00","20:00"]', '00:00', '00:00', 1, '["TUESDAY"]', 1, NULL)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_5_6)
        db.query("SELECT times FROM reminders WHERE categoryId = 'c1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("[]", cursor.getString(cursor.getColumnIndexOrThrow("times")))
        }
        // Every other column rides along untouched.
        db.query("SELECT * FROM reminders WHERE categoryId = 'c2'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("""["08:00","20:00"]""", cursor.getString(cursor.getColumnIndexOrThrow("times")))
            assertEquals("FIXED", cursor.getString(cursor.getColumnIndexOrThrow("mode")))
            assertEquals(1, cursor.getInt(cursor.getColumnIndexOrThrow("showCategoryInNotification")))
            assertNull(cursor.getString(cursor.getColumnIndexOrThrow("nextFireAt")))
        }
        // The rebuilt table keeps the CASCADE that LS-BE-072 requires.
        db.execSQL("PRAGMA foreign_keys = ON")
        db.execSQL("DELETE FROM categories WHERE id = 'c1'")
        db.query("SELECT COUNT(*) FROM reminders WHERE categoryId = 'c1'").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(0, cursor.getInt(0))
        }
    }

    @Test fun migrateAllTheWayFrom2To6_succeeds() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL(
                """
                INSERT INTO categories (id, name, emoji, color, valueType, unit, allowEmptyText, sortOrder, parentId)
                VALUES ('c1', 'Water', '💧', 100, 'number', 'oz', 1, 0, NULL)
                """.trimIndent(),
            )
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 6, true,
            MIGRATION_2_3, MIGRATION_3_6,
        )
        db.query("""SELECT defaultValue FROM categories WHERE id = 'c1'""").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(
                """{"type":"NumberValue","value":0.0,"unit":"oz"}""",
                cursor.getString(cursor.getColumnIndexOrThrow("defaultValue")),
            )
        }
    }
}
