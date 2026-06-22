package com.flowfuel.app.feature.history.di

import com.flowfuel.app.feature.history.data.HistoryRepositoryImpl
import com.flowfuel.app.feature.history.data.remote.HistoryApi
import com.flowfuel.app.feature.history.domain.HistoryRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HistoryBindModule {
    @Binds @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository
}

@Module
@InstallIn(SingletonComponent::class)
object HistoryApiModule {
    @Provides @Singleton
    fun provideHistoryApi(retrofit: Retrofit): HistoryApi =
        retrofit.create(HistoryApi::class.java)
}
