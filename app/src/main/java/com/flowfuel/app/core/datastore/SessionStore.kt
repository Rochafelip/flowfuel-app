package com.flowfuel.app.core.datastore

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore by preferencesDataStore(name = "flowfuel_session")

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val ACCESS           = stringPreferencesKey("access_token")
        val REFRESH          = stringPreferencesKey("refresh_token")
        val USER_ID          = stringPreferencesKey("user_id")
        val USER_NAME        = stringPreferencesKey("user_name")
        val USER_EMAIL       = stringPreferencesKey("user_email")
        val ONBOARDED        = booleanPreferencesKey("onboarded")
        val ACTIVE_VEHICLE   = intPreferencesKey("active_vehicle_id")
        val ACTIVE_VEHICLE_IS_GUEST = booleanPreferencesKey("active_vehicle_is_guest")
    }

    // ─── Sessão ───────────────────────────────────────────────────────────────

    val sessionFlow: Flow<Session> = context.dataStore.data.map { prefs ->
        Session(
            accessToken  = prefs[Keys.ACCESS],
            refreshToken = prefs[Keys.REFRESH],
            userId       = prefs[Keys.USER_ID],
            userName     = prefs[Keys.USER_NAME],
            userEmail    = prefs[Keys.USER_EMAIL],
        )
    }

    val onboardedFlow: Flow<Boolean> = context.dataStore.data.map { it[Keys.ONBOARDED] ?: false }

    suspend fun accessToken(): String? = context.dataStore.data.firstOrNull()?.get(Keys.ACCESS)

    suspend fun refreshToken(): String? = context.dataStore.data.firstOrNull()?.get(Keys.REFRESH)

    suspend fun save(
        accessToken: String,
        refreshToken: String,
        userId: String,
        userName: String? = null,
        userEmail: String? = null,
    ) {
        context.dataStore.edit {
            it[Keys.ACCESS]   = accessToken
            it[Keys.REFRESH]  = refreshToken
            it[Keys.USER_ID]  = userId
            if (userName != null) it[Keys.USER_NAME] = userName
            if (userEmail != null) it[Keys.USER_EMAIL] = userEmail
        }
    }

    /** Limpa tokens, dados do usuário e veículo ativo (usado no logout). */
    suspend fun clear() {
        context.dataStore.edit {
            it.remove(Keys.ACCESS)
            it.remove(Keys.REFRESH)
            it.remove(Keys.USER_ID)
            it.remove(Keys.USER_NAME)
            it.remove(Keys.USER_EMAIL)
            it.remove(Keys.ACTIVE_VEHICLE)
            it.remove(Keys.ACTIVE_VEHICLE_IS_GUEST)
        }
    }

    suspend fun markOnboarded() {
        context.dataStore.edit { it[Keys.ONBOARDED] = true }
    }

    // ─── Motivo do logout forçado ────────────────────────────────────────────
    // Sinal efêmero (em memória, não persistido) lido uma única vez pela tela
    // de login para explicar por que a sessão foi encerrada (ex: refresh token
    // revogado/expirado). Setado pelo TokenRefreshAuthenticator antes de clear().

    private val _forcedLogoutReason = MutableStateFlow<String?>(null)

    fun setForcedLogoutReason(code: String?) {
        _forcedLogoutReason.value = code
    }

    /** Lê e limpa o motivo do logout forçado (consumo único). */
    fun consumeForcedLogoutReason(): String? =
        _forcedLogoutReason.value.also { _forcedLogoutReason.value = null }

    // ─── Veículo ativo ────────────────────────────────────────────────────────

    /** ID do último veículo selecionado pelo usuário, ou null se ainda não houve seleção. */
    val activeVehicleIdFlow: Flow<Int?> = context.dataStore.data.map { it[Keys.ACTIVE_VEHICLE] }

    /** True quando o veículo ativo é emprestado (o usuário é convidado, não dono). */
    val activeVehicleIsGuestFlow: Flow<Boolean> =
        context.dataStore.data.map { it[Keys.ACTIVE_VEHICLE_IS_GUEST] ?: false }

    suspend fun saveActiveVehicleId(id: Int, isGuest: Boolean = false) {
        context.dataStore.edit {
            it[Keys.ACTIVE_VEHICLE] = id
            it[Keys.ACTIVE_VEHICLE_IS_GUEST] = isGuest
        }
    }

    suspend fun clearActiveVehicleId() {
        context.dataStore.edit {
            it.remove(Keys.ACTIVE_VEHICLE)
            it.remove(Keys.ACTIVE_VEHICLE_IS_GUEST)
        }
    }
}

data class Session(
    val accessToken: String?,
    val refreshToken: String?,
    val userId: String?,
    val userName: String? = null,
    val userEmail: String? = null,
) {
    val isLoggedIn: Boolean get() = !accessToken.isNullOrBlank()
}