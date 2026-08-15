package com.flowfuel.app.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.core.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class ThemePrefsStoreTest {

    // Único teste cobre o round-trip completo — mesmo motivo do
    // NotificationPrefsStoreTest: só existe uma chave aqui, sem parâmetro
    // pra isolar por teste, e o DataStore é um singleton de nível de
    // classloader no Robolectric (mesma instância viva durante toda a
    // execução da classe).
    @Test
    fun `themeModeFlow defaults to SYSTEM and reflects setThemeMode`() = runTest {
        val context: Context = ApplicationProvider.getApplicationContext()
        val store = ThemePrefsStore(context)

        assertEquals(ThemeMode.SYSTEM, store.themeModeFlow().first())

        store.setThemeMode(ThemeMode.DARK)
        assertEquals(ThemeMode.DARK, store.themeModeFlow().first())

        store.setThemeMode(ThemeMode.LIGHT)
        assertEquals(ThemeMode.LIGHT, store.themeModeFlow().first())
    }
}
