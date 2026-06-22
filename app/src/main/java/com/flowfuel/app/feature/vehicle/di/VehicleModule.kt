package com.flowfuel.app.feature.vehicle.di

import com.flowfuel.app.feature.vehicle.data.VehicleRepositoryImpl
import com.flowfuel.app.feature.vehicle.data.remote.VehicleApi
import com.flowfuel.app.feature.vehicle.domain.VehicleRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleBindModule {
    @Binds @Singleton
    abstract fun bindVehicleRepository(impl: VehicleRepositoryImpl): VehicleRepository
}

@Module
@InstallIn(SingletonComponent::class)
object VehicleApiModule {
    @Provides @Singleton
    fun provideVehicleApi(retrofit: Retrofit): VehicleApi = retrofit.create(VehicleApi::class.java)
}