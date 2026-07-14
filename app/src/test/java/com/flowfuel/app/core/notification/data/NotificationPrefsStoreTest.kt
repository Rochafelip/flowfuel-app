package com.flowfuel.app.core.notification.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class NotificationPrefsStoreTest {

    // Um único teste cobre o round-trip completo (não dois testes separados):
    // o DataStore por trás deste store é um singleton de nível de classloader
    // no Robolectric (mesma instância viva durante toda a execução da classe,
    // ver comentário em VehicleMaintenancePrefsStoreTest), e como só existe uma
    // chave booleana aqui (sem parâmetro tipo vehicleId para isolar por teste),
    // dois métodos de teste se contaminariam dependendo da ordem de execução.
    @Test
    fun `hasShownRationale defaults to false and becomes true after markRationaleShown`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = NotificationPrefsStore(context)

        assertFalse(store.hasShownRationale())

        store.markRationaleShown()

        assertTrue(store.hasShownRationale())
    }
}
