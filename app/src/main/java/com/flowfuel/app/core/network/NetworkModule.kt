package com.flowfuel.app.core.network

import android.content.Context
import coil.ImageLoader
import com.chuckerteam.chucker.api.ChuckerCollector
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.chuckerteam.chucker.api.RetentionManager
import com.flowfuel.app.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides @Singleton
    fun provideLogging(): HttpLoggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    @Provides @Singleton
    fun provideChucker(@ApplicationContext context: Context): ChuckerInterceptor =
        ChuckerInterceptor.Builder(context)
            .collector(
                ChuckerCollector(
                    context = context,
                    showNotification = true,
                    retentionPeriod = RetentionManager.Period.ONE_HOUR,
                )
            )
            .maxContentLength(250_000L)
            .alwaysReadResponseBody(true)
            .build()

    /**
     * Cliente HTTP dedicado ao refresh de token.
     * Não possui [AuthInterceptor] nem [TokenRefreshAuthenticator] para
     * evitar dependência circular e loop infinito de autenticação.
     */
    @Provides @Singleton @Named("refresh")
    fun provideRefreshOkHttp(
        logging: HttpLoggingInterceptor,
        chucker: ChuckerInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(chucker)
        .addInterceptor(logging)
        .build()

    /** Retrofit exclusivo para o endpoint de refresh (sem autenticação). */
    @Provides @Singleton @Named("refresh")
    fun provideRefreshRetrofit(
        @Named("refresh") client: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /** Cliente HTTP principal com autenticação e refresh automático. */
    @Provides @Singleton
    fun provideOkHttp(
        authInterceptor: AuthInterceptor,
        tokenRefreshAuthenticator: TokenRefreshAuthenticator,
        logging: HttpLoggingInterceptor,
        chucker: ChuckerInterceptor,
    ): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .addInterceptor(chucker)
        .addInterceptor(authInterceptor)
        .addInterceptor(logging)
        .authenticator(tokenRefreshAuthenticator)
        .build()

    @Provides @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    /** Coil ImageLoader using the authenticated OkHttpClient for private image URLs. */
    @Provides @Singleton
    fun provideImageLoader(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
    ): ImageLoader = ImageLoader.Builder(context)
        .okHttpClient(okHttpClient)
        .crossfade(true)
        .build()
}
