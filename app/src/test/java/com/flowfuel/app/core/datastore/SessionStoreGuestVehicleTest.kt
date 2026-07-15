package com.flowfuel.app.core.datastore

import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SessionStoreGuestVehicleTest {

    private val sessionStore = SessionStore(ApplicationProvider.getApplicationContext())

    @Test
    fun saveActiveVehicleId_semIsGuest_flagFicaFalse() = runTest {
        sessionStore.saveActiveVehicleId(10)

        assertEquals(10, sessionStore.activeVehicleIdFlow.first())
        assertFalse(sessionStore.activeVehicleIsGuestFlow.first())
    }

    @Test
    fun saveActiveVehicleId_comIsGuestTrue_flagFicaTrue() = runTest {
        sessionStore.saveActiveVehicleId(20, isGuest = true)

        assertEquals(20, sessionStore.activeVehicleIdFlow.first())
        assertTrue(sessionStore.activeVehicleIsGuestFlow.first())
    }

    @Test
    fun saveActiveVehicleId_trocaDeGuestParaProprio_zeraFlag() = runTest {
        sessionStore.saveActiveVehicleId(20, isGuest = true)
        sessionStore.saveActiveVehicleId(30, isGuest = false)

        assertFalse(sessionStore.activeVehicleIsGuestFlow.first())
    }

    @Test
    fun clearActiveVehicleId_limpaIdEFlag() = runTest {
        sessionStore.saveActiveVehicleId(20, isGuest = true)

        sessionStore.clearActiveVehicleId()

        assertEquals(null, sessionStore.activeVehicleIdFlow.first())
        assertFalse(sessionStore.activeVehicleIsGuestFlow.first())
    }

    @Test
    fun consumeGuestAccessEndedMessage_semMensagemSetada_retornaNull() {
        assertEquals(null, sessionStore.consumeGuestAccessEndedMessage())
    }

    @Test
    fun setGuestAccessEndedMessage_consumeGuestAccessEndedMessage_retornaUmaVezSoDepoisLimpaNull() {
        sessionStore.setGuestAccessEndedMessage("Esse veículo não está mais compartilhado com você")

        val first = sessionStore.consumeGuestAccessEndedMessage()
        val second = sessionStore.consumeGuestAccessEndedMessage()

        assertEquals("Esse veículo não está mais compartilhado com você", first)
        assertEquals(null, second)
    }
}
