package com.flowfuel.app.core.vehicleshare.di

import com.flowfuel.app.core.vehicleshare.data.VehicleShareRepositoryImpl
import com.flowfuel.app.core.vehicleshare.data.remote.VehicleShareApi
import com.flowfuel.app.core.vehicleshare.domain.VehicleShareRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleShareBindModule {
    @Binds @Singleton
    abstract fun bindVehicleShareRepository(impl: VehicleShareRepositoryImpl): VehicleShareRepository
}

@Module
@InstallIn(SingletonComponent::class)
object VehicleShareApiModule {
    @Provides @Singleton
    fun provideVehicleShareApi(retrofit: Retrofit): VehicleShareApi = retrofit.create(VehicleShareApi::class.java)
}
