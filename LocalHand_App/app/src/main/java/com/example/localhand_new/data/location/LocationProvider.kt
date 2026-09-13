package com.example.localhand_new.data.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A point the user has chosen or the device has reported. */
data class LatLng(val latitude: Double, val longitude: Double)

/**
 * Reads the device's location using the platform [LocationManager].
 *
 * Deliberately avoids Play Services: it needs no extra dependency or API key,
 * and neighbourhood-scale distances don't need fused-provider accuracy.
 */
class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Best-effort current position. Returns the last known fix immediately when
     * one exists, otherwise waits briefly for a fresh one. Null if permission is
     * missing, location is off, or nothing arrives in time.
     */
    suspend fun current(timeoutMs: Long = 8_000): LatLng? {
        if (!hasPermission()) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        lastKnown(manager)?.let { return it }

        return withTimeoutOrNull(timeoutMs) { requestSingleUpdate(manager) }
    }

    @Suppress("MissingPermission") // guarded by hasPermission() above
    private fun lastKnown(manager: LocationManager): LatLng? {
        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        )
        return providers
            .mapNotNull { provider ->
                runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
            }
            // Prefer the most recent fix across providers.
            .maxByOrNull { it.time }
            ?.let { LatLng(it.latitude, it.longitude) }
    }

    @Suppress("MissingPermission")
    private suspend fun requestSingleUpdate(manager: LocationManager): LatLng? =
        suspendCancellableCoroutine { cont ->
            val provider = when {
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                else -> null
            }
            if (provider == null) {
                cont.resume(null)
                return@suspendCancellableCoroutine
            }

            val listener = object : android.location.LocationListener {
                override fun onLocationChanged(location: Location) {
                    if (cont.isActive) cont.resume(LatLng(location.latitude, location.longitude))
                }

                // Required on older API levels; no-ops here.
                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(p: String?, s: Int, e: android.os.Bundle?) = Unit
                override fun onProviderDisabled(p: String) = Unit
                override fun onProviderEnabled(p: String) = Unit
            }

            runCatching {
                manager.requestLocationUpdates(provider, 0L, 0f, listener)
            }.onFailure {
                if (cont.isActive) cont.resume(null)
            }

            cont.invokeOnCancellation {
                runCatching { manager.removeUpdates(listener) }
            }
        }

    /**
     * Resolves a point to a human-readable area name, so the pin the user drops
     * fills in their locality without them typing it.
     */
    suspend fun areaName(point: LatLng): String? = withContext(Dispatchers.IO) {
        if (!Geocoder.isPresent()) return@withContext null
        runCatching {
            @Suppress("DEPRECATION") // the async overload is API 33+ only
            Geocoder(context)
                .getFromLocation(point.latitude, point.longitude, 1)
                ?.firstOrNull()
                ?.let { address ->
                    // Prefer the most local name available.
                    address.subLocality
                        ?: address.locality
                        ?: address.subAdminArea
                        ?: address.adminArea
                }
        }.getOrNull()
    }
}

/** Metres between two points, via the haversine formula. */
fun distanceMetres(a: LatLng, b: LatLng): Double {
    val earthRadius = 6_371_000.0
    val dLat = Math.toRadians(b.latitude - a.latitude)
    val dLng = Math.toRadians(b.longitude - a.longitude)
    val lat1 = Math.toRadians(a.latitude)
    val lat2 = Math.toRadians(b.latitude)

    val h = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLng / 2).pow(2)
    return 2 * earthRadius * atan2(sqrt(h), sqrt(1 - h))
}

/**
 * Distance phrased the way a neighbour would: metres up close, then kilometres,
 * because "minutes away" only reads as local at small numbers.
 */
fun formatDistance(metres: Double): String = when {
    metres < 100 -> "just around the corner"
    metres < 1_000 -> "${(metres / 50).roundToInt() * 50} m away"
    metres < 10_000 -> String.format("%.1f km away", metres / 1000.0)
    else -> "${(metres / 1000).roundToInt()} km away"
}
