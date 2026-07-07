package com.flowfuel.app.core.datastore

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VehicleMaintenancePrefsStoreTest {

    private lateinit var store: VehicleMaintenancePrefsStore

    @Before
    fun setUp() {
        val context: Context = ApplicationProvider.getApplicationContext()
        store = VehicleMaintenancePrefsStore(context)
    }

    // Cada teste usa vehicleIds que nenhum outro teste desta classe grava, para não depender
    // da ordem de execução: o DataStore por trás de VehicleMaintenancePrefsStore é um singleton
    // de nível de classloader (a mesma instância viva por toda a execução da classe no
    // Robolectric), então dois testes que gravassem no mesmo vehicleId poderiam se contaminar
    // dependendo da ordem em que o JUnit os executa.

    @Test
    fun `licensingDueDateFlow is null before anything is saved`() = runTest {
        assertNull(store.licensingDueDateFlow(101).first())
    }

    @Test
    fun `saveLicensingDueDate persists and round-trips per vehicle`() = runTest {
        store.saveLicensingDueDate(102, "2026-08-15")
        store.saveLicensingDueDate(103, "2027-01-01")

        assertEquals("2026-08-15", store.licensingDueDateFlow(102).first())
        assertEquals("2027-01-01", store.licensingDueDateFlow(103).first())
    }

    @Test
    fun `anchorKmFlow is null before anything is saved`() = runTest {
        assertNull(store.anchorKmFlow(104, EventCategory.OIL_CHANGE).first())
    }

    @Test
    fun `saveAnchorKm persists per vehicle and category independently`() = runTest {
        store.saveAnchorKm(105, EventCategory.OIL_CHANGE, 50_000)
        store.saveAnchorKm(105, EventCategory.TIRES, 48_000)
        store.saveAnchorKm(106, EventCategory.OIL_CHANGE, 12_000)

        assertEquals(50_000, store.anchorKmFlow(105, EventCategory.OIL_CHANGE).first())
        assertEquals(48_000, store.anchorKmFlow(105, EventCategory.TIRES).first())
        assertEquals(12_000, store.anchorKmFlow(106, EventCategory.OIL_CHANGE).first())
    }
}
