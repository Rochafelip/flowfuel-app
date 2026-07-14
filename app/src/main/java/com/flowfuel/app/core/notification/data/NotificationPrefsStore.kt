package com.flowfuel.app.core.notification.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.notificationDataStore by preferencesDataStore(name = "flowfuel_notification")

@Singleton
class NotificationPrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val RATIONALE_SHOWN = booleanPreferencesKey("rationale_shown")
    }

    suspend fun hasShownRationale(): Boolean =
        context.notificationDataStore.data.firstOrNull()?.get(Keys.RATIONALE_SHOWN) ?: false

    suspend fun markRationaleShown() {
        context.notificationDataStore.edit { it[Keys.RATIONALE_SHOWN] = true }
    }
}
