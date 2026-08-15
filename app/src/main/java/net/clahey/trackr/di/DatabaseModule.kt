package net.clahey.trackr.di

import android.content.Context
import androidx.room.Room
import net.clahey.trackr.data.local.CategoryDao
import net.clahey.trackr.data.local.EventDao
import net.clahey.trackr.data.local.MIGRATION_1_2
import net.clahey.trackr.data.local.MIGRATION_2_3
import net.clahey.trackr.data.local.MIGRATION_3_5
import net.clahey.trackr.data.local.ReminderDao
import net.clahey.trackr.data.local.TrackrDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// @spec APP-DI-002
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // No fallbackToDestructiveMigration: every schema change must arrive with an explicit
    // migration, or the app crashes on open rather than silently dropping the user's data.
    // @spec LS-BE-070
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrackrDatabase =
        Room.databaseBuilder(context, TrackrDatabase::class.java, "trackr.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_5)
            .build()

    @Provides
    fun provideCategoryDao(db: TrackrDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideEventDao(db: TrackrDatabase): EventDao = db.eventDao()

    @Provides
    fun provideReminderDao(db: TrackrDatabase): ReminderDao = db.reminderDao()
}
