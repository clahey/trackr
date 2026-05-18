package com.trackr.app.di

import com.trackr.app.data.ImageStore
import com.trackr.app.data.TrackrRepository
import com.trackr.app.data.local.LocalImageStore
import com.trackr.app.data.local.LocalTrackrRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// @spec APP-DI-004
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindTrackrRepository(impl: LocalTrackrRepository): TrackrRepository

    @Binds
    @Singleton
    abstract fun bindImageStore(impl: LocalImageStore): ImageStore
}
