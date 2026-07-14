package com.flowfuel.app.core.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.content.getSystemService

object NotificationChannelSetup {
    const val CHANNEL_ID = "general"

    fun create(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Geral",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        context.getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }
}
