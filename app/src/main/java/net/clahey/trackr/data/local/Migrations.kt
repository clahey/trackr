package net.clahey.trackr.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
