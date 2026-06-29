package com.flowfuel.app.feature.export.di

import com.flowfuel.app.feature.export.data.ExportRepositoryImpl
import com.flowfuel.app.feature.export.data.remote.ExportApi
import com.flowfuel.app.feature.export.domain.ExportRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportBindModule {
    @Binds @Singleton
    abstract fun bindExportRepository(impl: ExportRepositoryImpl): ExportRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ExportApiModule {
    @Provides @Singleton
    fun provideExportApi(retrofit: Retrofit): ExportApi =
        retrofit.create(ExportApi::class.java)
}
