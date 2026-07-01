package com.flowfuel.app.feature.station.data

import android.Manifest
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.flowfuel.app.feature.station.domain.model.LocationResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

// Robolectric grants every manifest-declared permission by default, so the
// "not granted" path must be forced explicitly via denyPermissions().
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class FusedLocationProviderTest {

    @Test
    fun `returns PermissionDenied when location permission not granted`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(context).denyPermissions(Manifest.permission.ACCESS_FINE_LOCATION)
        val provider = FusedLocationProvider(context)

        val result = provider.getCurrentLocation()

        assertEquals(LocationResult.PermissionDenied, result)
    }
}
