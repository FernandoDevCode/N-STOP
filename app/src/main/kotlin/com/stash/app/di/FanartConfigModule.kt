package com.stash.app.di

import com.stash.app.BuildConfig
import com.stash.core.data.artistphotos.FanartConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
object FanartConfigModule {
    @Provides
    fun provideFanartConfig(): FanartConfig = FanartConfig(BuildConfig.FANART_API_KEY)
}
