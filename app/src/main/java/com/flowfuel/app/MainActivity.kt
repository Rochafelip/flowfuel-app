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

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        deepLinkUri = intent.data
        var keepSplash = true
        splash.setKeepOnScreenCondition { keepSplash }
        setContent {
            FlowFuelTheme {
                FlowFuelNavHost(
                    onSplashReady = { keepSplash = false },
                    deepLinkUri = deepLinkUri,
                    onDeepLinkConsumed = { deepLinkUri = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        deepLinkUri = intent.data
    }
}
