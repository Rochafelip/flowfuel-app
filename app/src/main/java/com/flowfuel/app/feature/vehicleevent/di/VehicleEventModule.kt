package com.flowfuel.app.feature.vehicleevent.di

import com.flowfuel.app.feature.vehicleevent.data.VehicleEventRepositoryImpl
import com.flowfuel.app.feature.vehicleevent.data.remote.VehicleEventApi
import com.flowfuel.app.feature.vehicleevent.domain.VehicleEventRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class VehicleEventBindModule {
    @Binds @Singleton
    abstract fun bindVehicleEventRepository(impl: VehicleEventRepositoryImpl): VehicleEventRepository
}

@Module
@InstallIn(SingletonComponent::class)
object VehicleEventApiModule {
    @Provides @Singleton
    fun provideVehicleEventApi(retrofit: Retrofit): VehicleEventApi =
        retrofit.create(VehicleEventApi::class.java)
}
