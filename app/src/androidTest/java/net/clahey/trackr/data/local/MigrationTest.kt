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
    // already fails the test if the resulting schema doesn't match 5.json, so this test's job is
    // just making sure the migration runs at all for a device starting fresh at version 3.
    @Test fun migrate3To5Directly_runs() {
        helper.createDatabase(TEST_DB, 3).apply { close() }
        helper.runMigrationsAndValidate(TEST_DB, 5, true, MIGRATION_3_5)
    }

    @Test fun migrateAllTheWayFrom2To5_succeeds() {
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
            TEST_DB, 5, true,
            MIGRATION_2_3, MIGRATION_3_5,
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
