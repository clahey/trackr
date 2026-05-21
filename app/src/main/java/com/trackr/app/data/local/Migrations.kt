package com.trackr.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE categories ADD COLUMN parentId TEXT DEFAULT NULL")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_categories_parentId ON categories(parentId)")
    }
}
