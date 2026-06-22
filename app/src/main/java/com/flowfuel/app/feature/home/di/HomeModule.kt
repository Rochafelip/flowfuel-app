package com.flowfuel.app.feature.home.di

import com.flowfuel.app.feature.home.data.HomeRepositoryImpl
import com.flowfuel.app.feature.home.data.remote.DashboardApi
import com.flowfuel.app.feature.home.data.remote.RefuelApi
import com.flowfuel.app.feature.home.domain.HomeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class HomeBindModule {
    @Binds @Singleton
    abstract fun bindHomeRepository(impl: HomeRepositoryImpl): HomeRepository
}

@Module
@InstallIn(SingletonComponent::class)
object HomeApiModule {
    @Provides @Singleton
    fun provideDashboardApi(retrofit: Retrofit): DashboardApi =
        retrofit.create(DashboardApi::class.java)

    @Provides @Singleton
    fun provideRefuelApi(retrofit: Retrofit): RefuelApi =
        retrofit.create(RefuelApi::class.java)
}