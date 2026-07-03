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
import kotlinx.coroutines.withTimeoutOrNull
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

        // getCurrentLocation() sem timeout explícito pode ficar esperando um fix de GPS
        // indefinidamente (sinal fraco/indoor) — daí o app "travar" na tela de Postos até
        // finalmente cair em erro. Limita a espera por um fix fresco e cai para a última
        // localização conhecida (quase instantânea) em vez de travar.
        val fresh = withTimeoutOrNull(FRESH_FIX_TIMEOUT_MILLIS) { requestFreshLocation() }
        val geoLocation = fresh ?: lastKnownLocation()
        return geoLocation?.let { LocationResult.Available(it) } ?: LocationResult.Unavailable
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestFreshLocation(): GeoLocation? {
        val cancellationSource = CancellationTokenSource()
        return suspendCancellableCoroutine { continuation ->
            client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cancellationSource.token)
                .addOnSuccessListener { location ->
                    continuation.resume(location?.let { GeoLocation(it.latitude, it.longitude) })
                }
                .addOnFailureListener {
                    continuation.resume(null)
                }
            continuation.invokeOnCancellation { cancellationSource.cancel() }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun lastKnownLocation(): GeoLocation? = suspendCancellableCoroutine { continuation ->
        client.lastLocation
            .addOnSuccessListener { location ->
                continuation.resume(location?.let { GeoLocation(it.latitude, it.longitude) })
            }
            .addOnFailureListener { continuation.resume(null) }
    }

    private companion object {
        const val FRESH_FIX_TIMEOUT_MILLIS = 10_000L
    }
}
