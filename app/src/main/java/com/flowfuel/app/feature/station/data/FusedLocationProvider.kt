package com.flowfuel.app.feature.station.data

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.flowfuel.app.feature.station.domain.LocationProvider
import com.flowfuel.app.feature.station.domain.model.GeoLocation
import com.flowfuel.app.feature.station.domain.model.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FusedLocationProvider @Inject constructor(
    @ApplicationContext private val context: Context,
) : LocationProvider {

    private val client by lazy { LocationServices.getFusedLocationProviderClient(context) }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(): LocationResult {
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasPermission) return LocationResult.PermissionDenied

        val cancellationSource = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token)
                .addOnSuccessListener { location ->
                    val result = location?.let { GeoLocation(it.latitude, it.longitude) }
                        ?.let { LocationResult.Available(it) }
                        ?: LocationResult.Unavailable
                    continuation.resume(result)
                }
                .addOnFailureListener {
                    continuation.resume(LocationResult.Unavailable)
                }
            continuation.invokeOnCancellation { cancellationSource.cancel() }
        }
    }
}
