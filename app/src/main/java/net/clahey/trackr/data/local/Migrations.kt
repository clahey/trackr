package net.clahey.trackr.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// Creates the reminders table (see docs/llds/local-storage.md § Migration Strategy) directly in
// its current NOT NULL shape — the sole path from before the reminders table existed to today's
// schema.
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
