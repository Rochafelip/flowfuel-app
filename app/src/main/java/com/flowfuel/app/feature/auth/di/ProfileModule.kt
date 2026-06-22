package com.flowfuel.app.feature.auth.di

import com.flowfuel.app.feature.auth.data.ProfileRepositoryImpl
import com.flowfuel.app.feature.auth.data.remote.ProfileApi
import com.flowfuel.app.feature.auth.domain.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import retrofit2.Retrofit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ProfileBindModule {
    @Binds @Singleton
    abstract fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository
}

@Module
@InstallIn(SingletonComponent::class)
object ProfileApiModule {
    @Provides @Singleton
    fun provideProfileApi(retrofit: Retrofit): ProfileApi =
        retrofit.create(ProfileApi::class.java)
}
