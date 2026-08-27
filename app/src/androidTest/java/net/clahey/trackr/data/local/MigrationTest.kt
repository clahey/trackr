package net.clahey.trackr.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

private const val TEST_DB = "migration-test"
private const val WATER_DEFAULT_VALUE = """{"type":"NumberValue","value":0.0,"unit":"oz"}"""

private fun SupportSQLiteDatabase.defaultValueOf(id: String): String? =
    query("SELECT defaultValue FROM categories WHERE id = ?", arrayOf(id)).use { cursor ->
        assertTrue(cursor.moveToFirst())
        cursor.getString(cursor.getColumnIndexOrThrow("defaultValue"))
    }

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
    // from. It is the one migration this file does not cover — see docs/arrows/local-storage.md.

    @Test fun migrate2To3_convertsNumberUnitToDefaultValueJson() {
        helper.createDatabase(TEST_DB, 2).apply {
            // c2 is not a number type, so its unit must not be converted.
            execSQL("""
                INSERT INTO categories (id, name, emoji, color, valueType, unit, allowEmptyText, sortOrder, parentId)
                VALUES ('c1', 'Water', '💧', 100, 'number', 'oz', 1, 0, NULL),
                       ('c2', 'Mood', '🙂', 200, 'scale', 'stars', 1, 1, NULL)
            """)
            close()
        }

        val db = helper.runMigrationsAndValidate(TEST_DB, 3, true, MIGRATION_2_3)
        assertEquals(WATER_DEFAULT_VALUE, db.defaultValueOf("c1"))
        assertNull(db.defaultValueOf("c2"))
    }

    // There are no rows to preserve, and runMigrationsAndValidate fails the test when the result
    // does not match 6.json — the call is the whole assertion.
    // @spec LS-BE-073
    @Test fun migrate3To6_runs() {
        helper.createDatabase(TEST_DB, 3).apply { close() }
        helper.runMigrationsAndValidate(TEST_DB, 6, true, MIGRATION_3_6)
    }

    @Test fun migrate2To6_preservesConvertedDefaultValue() {
        helper.createDatabase(TEST_DB, 2).apply {
            execSQL("""
                INSERT INTO categories (id, name, emoji, color, valueType, unit, allowEmptyText, sortOrder, parentId)
                VALUES ('c1', 'Water', '💧', 100, 'number', 'oz', 1, 0, NULL)
            """)
            close()
        }

        val db = helper.runMigrationsAndValidate(
            TEST_DB, 6, true,
            MIGRATION_2_3, MIGRATION_3_6,
        )
        assertEquals(WATER_DEFAULT_VALUE, db.defaultValueOf("c1"))
    }
}
