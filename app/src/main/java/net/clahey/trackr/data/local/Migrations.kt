package net.clahey.trackr.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Direct 3->5 path for any device that goes straight from before the reminders table existed to
// the current schema, skipping the transient nullable-columns shape MIGRATION_3_4 originally
// created; coexists with MIGRATION_3_4 + MIGRATION_4_5, which any device that already upgraded
// through that intermediate shape will use instead (Room picks by matching the device's current
// stored version to a migration's start version).
// @spec REM-DATA-001, REM-DATA-002
val MIGRATION_3_5 = object : Migration(3, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE reminders (
                categoryId TEXT NOT NULL PRIMARY KEY,
                enabled INTEGER NOT NULL,
                mode TEXT NOT NULL,
                times TEXT,
                windowStart TEXT NOT NULL,
                windowEnd TEXT NOT NULL,
                occurrencesPerDay INTEGER NOT NULL,
                daysActive TEXT NOT NULL,
                showCategoryInNotification INTEGER NOT NULL,
                nextFireAt INTEGER,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
            )
        """)
    }
}

// @spec REM-DATA-002
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // windowStart/windowEnd/occurrencesPerDay are always populated by the app (REM-DATA-002 —
        // preserved across mode switches, never cleared), so SQLite's inability to ALTER COLUMN
        // nullability means rebuilding the table to make that a real NOT NULL guarantee.
        // COALESCE handles any pre-existing null row with the same defaults ReminderUIState uses.
        db.execSQL("""
            CREATE TABLE reminders_new (
                categoryId TEXT NOT NULL PRIMARY KEY,
                enabled INTEGER NOT NULL,
                mode TEXT NOT NULL,
                times TEXT,
                windowStart TEXT NOT NULL,
                windowEnd TEXT NOT NULL,
                occurrencesPerDay INTEGER NOT NULL,
                daysActive TEXT NOT NULL,
                showCategoryInNotification INTEGER NOT NULL,
                nextFireAt INTEGER,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
            )
        """)
        db.execSQL("""
            INSERT INTO reminders_new
            SELECT categoryId, enabled, mode, times,
                COALESCE(windowStart, '00:00'), COALESCE(windowEnd, '00:00'), COALESCE(occurrencesPerDay, 1),
                daysActive, showCategoryInNotification, nextFireAt
            FROM reminders
        """)
        db.execSQL("DROP TABLE reminders")
        db.execSQL("ALTER TABLE reminders_new RENAME TO reminders")
    }
}

// @spec REM-DATA-001
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE reminders (
                categoryId TEXT NOT NULL PRIMARY KEY,
                enabled INTEGER NOT NULL,
                mode TEXT NOT NULL,
                times TEXT,
                windowStart TEXT,
                windowEnd TEXT,
                occurrencesPerDay INTEGER,
                daysActive TEXT NOT NULL,
                showCategoryInNotification INTEGER NOT NULL,
                nextFireAt INTEGER,
                FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE CASCADE
            )
        """)
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("""
            CREATE TABLE categories_new (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                emoji TEXT,
                color INTEGER,
                valueType TEXT,
                defaultValue TEXT,
                allowEmptyText INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                parentId TEXT
            )
        """)
        db.execSQL("""
            INSERT INTO categories_new
            SELECT id, name, emoji, color, valueType,
                CASE
                    WHEN valueType = 'number' AND unit IS NOT NULL
                    THEN '{"type":"NumberValue","value":0.0,"unit":"' || unit || '"}'
                    ELSE NULL
                END,
                allowEmptyText, sortOrder, parentId
            FROM categories
        """)
        db.execSQL("DROP TABLE categories")
        db.execSQL("ALTER TABLE categories_new RENAME TO categories")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories(parentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_sortOrder ON categories(sortOrder)")
    }
}

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // SQLite cannot ALTER COLUMN nullability, so rebuild the table to make
        // emoji, color, and valueType nullable (for SubCategory inheritance) and add parentId.
        db.execSQL("""
            CREATE TABLE categories_new (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                emoji TEXT,
                color INTEGER,
                valueType TEXT,
                unit TEXT,
                allowEmptyText INTEGER NOT NULL,
                sortOrder INTEGER NOT NULL,
                parentId TEXT
            )
        """)
        db.execSQL("INSERT INTO categories_new SELECT id, name, emoji, color, valueType, unit, allowEmptyText, sortOrder, NULL FROM categories")
        db.execSQL("DROP TABLE categories")
        db.execSQL("ALTER TABLE categories_new RENAME TO categories")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories(parentId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_sortOrder ON categories(sortOrder)")
    }
}
