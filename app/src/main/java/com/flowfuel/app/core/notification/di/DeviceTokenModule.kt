package com.flowfuel.app.core.notification.di

import com.flowfuel.app.core.notification.data.DeviceTokenRepositoryImpl
import com.flowfuel.app.core.notification.data.remote.DeviceTokenApi
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class DeviceTokenBindModule {
    @Binds @Singleton
    abstract fun bindDeviceTokenRepository(impl: DeviceTokenRepositoryImpl): DeviceTokenRepository
}

@Module
@InstallIn(SingletonComponent::class)
object DeviceTokenApiModule {
    @Provides @Singleton
    fun provideDeviceTokenApi(retrofit: Retrofit): DeviceTokenApi = retrofit.create(DeviceTokenApi::class.java)
}
