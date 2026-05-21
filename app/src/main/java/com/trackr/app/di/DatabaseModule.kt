package com.trackr.app.di

import android.content.Context
import androidx.room.Room
import com.trackr.app.data.local.CategoryDao
import com.trackr.app.data.local.EventDao
import com.trackr.app.data.local.MIGRATION_1_2
import com.trackr.app.data.local.TrackrDatabase
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

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): TrackrDatabase =
        Room.databaseBuilder(context, TrackrDatabase::class.java, "trackr.db")
            .addMigrations(MIGRATION_1_2)
            .build()

    @Provides
    fun provideCategoryDao(db: TrackrDatabase): CategoryDao = db.categoryDao()

    @Provides
    fun provideEventDao(db: TrackrDatabase): EventDao = db.eventDao()
}
