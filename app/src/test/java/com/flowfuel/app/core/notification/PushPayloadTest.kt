package com.flowfuel.app.core.notification

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PushPayloadTest {

    @Test
    fun `fromData parses all fields`() {
        val data = mapOf(
            "title" to "Convite recebido",
            "body" to "Maria compartilhou um veículo com você",
            "deepLink" to "flowfuel://vehicle/details/1",
            "type" to "vehicle_share_invite",
        )

        val payload = PushPayload.fromData(data)

        assertEquals(
            PushPayload(
                title = "Convite recebido",
                body = "Maria compartilhou um veículo com você",
                deepLink = "flowfuel://vehicle/details/1",
                type = "vehicle_share_invite",
            ),
            payload,
        )
    }

    @Test
    fun `fromData defaults type to generic when absent`() {
        val data = mapOf("title" to "Título", "body" to "Corpo")

        val payload = PushPayload.fromData(data)

        assertEquals("generic", payload?.type)
    }

    @Test
    fun `fromData allows missing deepLink`() {
        val data = mapOf("title" to "Título", "body" to "Corpo")

        val payload = PushPayload.fromData(data)

        assertNull(payload?.deepLink)
    }

    @Test
    fun `fromData returns null when title is missing`() {
        val data = mapOf("body" to "Corpo")

        assertNull(PushPayload.fromData(data))
    }

    @Test
    fun `fromData returns null when body is missing`() {
        val data = mapOf("title" to "Título")

        assertNull(PushPayload.fromData(data))
    }
}
