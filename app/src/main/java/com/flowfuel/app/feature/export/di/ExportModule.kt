package com.flowfuel.app.feature.export.di

import com.flowfuel.app.feature.export.data.ExportRepositoryImpl
import com.flowfuel.app.feature.export.domain.ExportRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    @Binds @Singleton
    abstract fun bindExportRepository(impl: ExportRepositoryImpl): ExportRepository
}
