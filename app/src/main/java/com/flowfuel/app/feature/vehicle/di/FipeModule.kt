package com.flowfuel.app.feature.vehicle.di

import com.flowfuel.app.feature.vehicle.data.FipeRepositoryImpl
import com.flowfuel.app.feature.vehicle.data.remote.fipe.FipeApi
import com.flowfuel.app.feature.vehicle.domain.FipeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class FipeBindModule {
    @Binds @Singleton
    abstract fun bindFipeRepository(impl: FipeRepositoryImpl): FipeRepository
}

@Module
@InstallIn(SingletonComponent::class)
object FipeApiModule {
    @Provides @Singleton
    fun provideFipeApi(@Named("fipe") retrofit: Retrofit): FipeApi = retrofit.create(FipeApi::class.java)
}
