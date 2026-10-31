package com.stash.data.lyrics.di

import javax.inject.Qualifier

/** Shared qualifier; the Android Hilt module and Desktop transport use the same contract. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class LrclibBaseUrl
