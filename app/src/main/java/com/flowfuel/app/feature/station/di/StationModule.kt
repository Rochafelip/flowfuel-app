package com.flowfuel.app.feature.station.di

import com.flowfuel.app.feature.station.data.FusedLocationProvider
import com.flowfuel.app.feature.station.data.StationRepositoryImpl
import com.flowfuel.app.feature.station.data.remote.StationApi
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.StationRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class StationBindModule {
    @Binds @Singleton
    abstract fun bindLocationProvider(impl: FusedLocationProvider): LocationProvider

    @Binds @Singleton
    abstract fun bindStationRepository(impl: StationRepositoryImpl): StationRepository
}

@Module
@InstallIn(SingletonComponent::class)
object StationApiModule {
    @Provides @Singleton
    fun provideStationApi(retrofit: Retrofit): StationApi =
        retrofit.create(StationApi::class.java)
}
