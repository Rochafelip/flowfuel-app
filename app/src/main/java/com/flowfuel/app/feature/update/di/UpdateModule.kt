package com.flowfuel.app.feature.update.di

import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.flowfuel.app.BuildConfig
import com.flowfuel.app.feature.update.data.UpdateRepositoryImpl
import com.flowfuel.app.feature.update.data.remote.GithubReleasesApi
import com.flowfuel.app.feature.update.domain.UpdateRepository
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindModule {
    @Binds @Singleton
    abstract fun bindUpdateRepository(impl: UpdateRepositoryImpl): UpdateRepository
}

@Module
@InstallIn(SingletonComponent::class)
object UpdateNetworkModule {

    @Provides @Singleton @Named("isDebugBuild")
    fun provideIsDebugBuild(): Boolean = BuildConfig.DEBUG

    /** Cliente HTTP dedicado à API pública do GitHub — sem AuthInterceptor/TokenRefreshAuthenticator, mesmo molde do cliente @Named("refresh") em NetworkModule. */
    @Provides @Singleton @Named("github")
    fun provideGithubOkHttp(
        logging: HttpLoggingInterceptor,
        chucker: ChuckerInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(chucker)
        .addInterceptor(logging)
        .build()

    @Provides @Singleton @Named("github")
    fun provideGithubRetrofit(
        @Named("github") client: OkHttpClient,
        json: Json,
    ): Retrofit = Retrofit.Builder()
        .baseUrl("https://api.github.com/")
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    @Provides @Singleton
    fun provideGithubReleasesApi(@Named("github") retrofit: Retrofit): GithubReleasesApi =
        retrofit.create(GithubReleasesApi::class.java)
}
