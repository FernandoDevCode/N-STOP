package com.stash.feature.search.engine

import com.stash.data.ytmusic.YouTubeWebSearchClient
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton
import okhttp3.OkHttpClient

@Module
@InstallIn(SingletonComponent::class)
object LiveSearchModule {
    @Provides
    @Singleton
    fun provideYouTubeWebSearchClient(client: OkHttpClient): YouTubeWebSearchClient =
        YouTubeWebSearchClient(client)
}
