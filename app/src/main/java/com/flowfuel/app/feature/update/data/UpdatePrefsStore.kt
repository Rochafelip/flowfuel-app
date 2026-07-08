package com.flowfuel.app.feature.update.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.firstOrNull
import javax.inject.Inject
import javax.inject.Singleton

private val Context.updateDataStore by preferencesDataStore(name = "flowfuel_update")

@Singleton
class UpdatePrefsStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val DISMISSED_VERSION = stringPreferencesKey("dismissed_version")
    }

    suspend fun dismissedVersion(): String? =
        context.updateDataStore.data.firstOrNull()?.get(Keys.DISMISSED_VERSION)

    suspend fun dismissVersion(tag: String) {
        context.updateDataStore.edit { it[Keys.DISMISSED_VERSION] = tag }
    }
}
