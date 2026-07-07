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
        // Clear datastore before each test for isolation
        kotlinx.coroutines.runBlocking {
            store.clear()
        }
    }

    @Test
    fun `licensingDueDateFlow is null before anything is saved`() = runTest {
        assertNull(store.licensingDueDateFlow(1).first())
    }

    @Test
    fun `saveLicensingDueDate persists and round-trips per vehicle`() = runTest {
        store.saveLicensingDueDate(1, "2026-08-15")
        store.saveLicensingDueDate(2, "2027-01-01")

        assertEquals("2026-08-15", store.licensingDueDateFlow(1).first())
        assertEquals("2027-01-01", store.licensingDueDateFlow(2).first())
    }

    @Test
    fun `anchorKmFlow is null before anything is saved`() = runTest {
        assertNull(store.anchorKmFlow(1, EventCategory.OIL_CHANGE).first())
    }

    @Test
    fun `saveAnchorKm persists per vehicle and category independently`() = runTest {
        store.saveAnchorKm(1, EventCategory.OIL_CHANGE, 50_000)
        store.saveAnchorKm(1, EventCategory.TIRES, 48_000)
        store.saveAnchorKm(2, EventCategory.OIL_CHANGE, 12_000)

        assertEquals(50_000, store.anchorKmFlow(1, EventCategory.OIL_CHANGE).first())
        assertEquals(48_000, store.anchorKmFlow(1, EventCategory.TIRES).first())
        assertEquals(12_000, store.anchorKmFlow(2, EventCategory.OIL_CHANGE).first())
    }
}
