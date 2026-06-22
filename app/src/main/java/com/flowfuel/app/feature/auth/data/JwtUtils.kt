package com.flowfuel.app.feature.auth.data

import android.util.Base64
import org.json.JSONObject
import timber.log.Timber

/**
 * Extrai o `userId` do payload do JWT quando a API não o retorna no body.
 * O payload é a segunda parte do token (separada por '.'), codificada em Base64-URL.
 */
internal fun userIdFromJwt(token: String): String {
    return try {
        val payload = token.split(".").getOrNull(1) ?: return ""
        val json = String(Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING))
        JSONObject(json).opt("userId")?.toString() ?: ""
    } catch (e: Exception) {
        Timber.w(e, "Não foi possível extrair userId do JWT")
        ""
    }
}