package com.flowfuel.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.flowfuel.app.core.designsystem.theme.FlowFuelTheme
import com.flowfuel.app.navigation.FlowFuelNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private var deepLinkUri by mutableStateOf<Uri?>(null)
    private var notificationTitle by mutableStateOf<String?>(null)
    private var notificationBody by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        applyIntentExtras(intent)
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        setContent {
            FlowFuelTheme {
                FlowFuelNavHost(
                    onSplashReady = { keepSplash = false },
                    deepLinkUri = deepLinkUri,
                    notificationTitle = notificationTitle,
                    notificationBody = notificationBody,
                    onDeepLinkConsumed = {
                        deepLinkUri = null
                        notificationTitle = null
                        notificationBody = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        applyIntentExtras(intent)
    }

    private fun applyIntentExtras(intent: Intent) {
        deepLinkUri = intent.data
        notificationTitle = intent.getStringExtra(EXTRA_NOTIFICATION_TITLE)
        notificationBody = intent.getStringExtra(EXTRA_NOTIFICATION_BODY)
    }

    companion object {
        const val EXTRA_NOTIFICATION_TITLE = "notification_title"
        const val EXTRA_NOTIFICATION_BODY = "notification_body"
    }
}
