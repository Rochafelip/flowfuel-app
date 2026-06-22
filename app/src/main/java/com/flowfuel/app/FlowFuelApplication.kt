package com.flowfuel.app

import android.app.Application
import android.content.Context
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.WorkManager
import coil.Coil
import coil.ImageLoader
import com.flowfuel.app.core.observability.SentryTree
import dagger.hilt.android.HiltAndroidApp
import io.sentry.android.core.SentryAndroid
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class FlowFuelApplication : Application() {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var imageLoader: ImageLoader

    override fun onCreate() {
        super.onCreate()

        // Initialize WorkManager with HiltWorkerFactory
        WorkManager.initialize(
            this,
            Configuration.Builder()
                .setWorkerFactory(workerFactory)
                .build(),
        )

        // Initialize Coil with the authenticated OkHttp client
        Coil.setImageLoader(imageLoader)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        } else if (BuildConfig.SENTRY_DSN.isNotBlank()) {
            SentryAndroid.init(this) { options ->
                options.dsn = BuildConfig.SENTRY_DSN
            }
            Timber.plant(SentryTree())
        }
    }
}
