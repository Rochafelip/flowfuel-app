package com.flowfuel.app.core.notification

data class PushPayload(
    val title: String,
    val body: String,
    val deepLink: String?,
    val type: String,
) {
    companion object {
        fun fromData(data: Map<String, String>): PushPayload? {
            val title = data["title"] ?: return null
            val body = data["body"] ?: return null
            return PushPayload(
                title = title,
                body = body,
                deepLink = data["deepLink"],
                type = data["type"] ?: "generic",
            )
        }
    }
}
