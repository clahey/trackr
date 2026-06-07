package net.clahey.trackr.di

import net.clahey.trackr.data.ImageStore
import net.clahey.trackr.data.TrackrRepository
import net.clahey.trackr.data.local.LocalImageStore
import net.clahey.trackr.data.local.LocalTrackrRepository
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
