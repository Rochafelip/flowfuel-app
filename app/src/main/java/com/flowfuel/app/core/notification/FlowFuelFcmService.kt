package com.flowfuel.app.core.notification

import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.flowfuel.app.MainActivity
import com.flowfuel.app.R
import com.flowfuel.app.core.notification.domain.DeviceTokenRepository
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class FlowFuelFcmService : FirebaseMessagingService() {

    @Inject lateinit var deviceTokenRepository: DeviceTokenRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        scope.launch { deviceTokenRepository.registerToken(token) }
    }

    override fun onMessageReceived(message: RemoteMessage) {
        super.onMessageReceived(message)
        val payload = PushPayload.fromData(message.data) ?: return
        showNotification(payload)
    }

    private fun showNotification(payload: PushPayload) {
        val intent = Intent(this, MainActivity::class.java).apply {
            if (!payload.deepLink.isNullOrBlank()) {
                action = Intent.ACTION_VIEW
                data = Uri.parse(payload.deepLink)
            }
            putExtra(MainActivity.EXTRA_NOTIFICATION_TITLE, payload.title)
            putExtra(MainActivity.EXTRA_NOTIFICATION_BODY, payload.body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, NotificationChannelSetup.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(payload.title)
            .setContentText(payload.body)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(this).notify(System.currentTimeMillis().toInt(), notification)
    }
}
