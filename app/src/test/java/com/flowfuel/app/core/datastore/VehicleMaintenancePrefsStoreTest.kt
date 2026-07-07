package com.flowfuel.app.core.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.vehicleevent.domain.model.EventCategory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
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
        // VehicleMaintenancePrefsStore.kt backs itself with `Context.vehicleMaintenanceDataStore`,
        // a private top-level property. Robolectric reuses the same sandbox/classloader for every
        // test method in this class (same @Config), so that property's underlying DataStore
        // instance -- and its in-memory Preferences -- survive across test methods even though
        // each test gets a fresh Application/Context. Deleting the backing file on disk does not
        // fix this: the already-created DataStore instance keeps serving its cached in-memory
        // value and never re-reads the file.
        //
        // To reset it without adding a test-only public method to the production class, reach
        // the *same* live DataStore instance through Kotlin's public synthetic bridge accessor
        // (generated because `edit`/`map` are inline functions called on this property from
        // within VehicleMaintenancePrefsStore) and clear it through its own public `edit` API.
        clearVehicleMaintenanceDataStore(context)
        store = VehicleMaintenancePrefsStore(context)
    }

    private fun clearVehicleMaintenanceDataStore(context: Context) {
        val accessor = Class
            .forName("com.flowfuel.app.core.datastore.VehicleMaintenancePrefsStoreKt")
            .getMethod("access\$getVehicleMaintenanceDataStore", Context::class.java)
        @Suppress("UNCHECKED_CAST")
        val dataStore = accessor.invoke(null, context) as DataStore<Preferences>
        runBlocking { dataStore.edit { it.clear() } }
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
