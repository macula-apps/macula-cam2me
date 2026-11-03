package io.macula.cam2me.reachability

import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * A single best-effort location fix for nearest-station discovery.
 * Coarse precision only -- see AndroidManifest's own note on why FINE
 * isn't requested; plain [LocationManager] rather than Play Services'
 * FusedLocationProvider, since a one-off fix doesn't need it and this
 * project's stack otherwise stays clear of Big Tech SDKs in the data
 * path. Both entry points return null whenever a fix genuinely isn't
 * available (permission not granted, no provider enabled, or nothing
 * cached yet) -- the caller's job is to fall back to the static station
 * list, not to surface this as an app error.
 */
object LocationFix {
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED

    /** The most recent cached fix from any enabled provider. Deliberately
     * NOT requesting a fresh fix (`requestLocationUpdates`, which can
     * take real time to resolve) -- this runs once at launch, and a fix
     * that's a few minutes stale is still exactly precise enough to
     * decide "which country/city is nearest". */
    fun lastKnown(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return manager.allProviders
            .mapNotNull { provider ->
                try {
                    manager.getLastKnownLocation(provider)
                } catch (e: SecurityException) {
                    null
                }
            }
            .maxByOrNull { it.time }
    }

    /** One coarse fix, asked for now rather than read from the cache.
     *
     * For the phone that has nothing cached -- a new device, location
     * just switched on, a reboot nothing has asked for a fix since. It
     * asks a single time and returns as soon as a provider answers; the
     * caller owns the time limit (`fixForDiscovery`) and no second
     * request is ever made, so a phone whose providers cannot answer
     * keeps the fallback station instead of polling. Cancelling the
     * calling coroutine removes the listener, which is what keeps the
     * request bounded when the caller's limit expires. */
    @Suppress("DEPRECATION") // requestSingleUpdate: one fix, and no battery-held listener after it
    suspend fun currentOnce(context: Context): Location? {
        if (!hasPermission(context)) return null
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .firstOrNull { enabled(manager, it) } ?: return null
        return suspendCancellableCoroutine { continuation ->
            val listener = singleUpdateListener { fix ->
                if (continuation.isActive) continuation.resume(fix)
            }
            continuation.invokeOnCancellation { manager.removeUpdates(listener) }
            try {
                manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            } catch (e: SecurityException) {
                if (continuation.isActive) continuation.resume(null)
            }
        }
    }

    private fun enabled(manager: LocationManager, provider: String): Boolean =
        try {
            manager.isProviderEnabled(provider)
        } catch (e: IllegalArgumentException) {
            false // the device has no such provider at all
        }
}

/** The listener a single requested fix needs, kept out of [LocationFix]
 * so a JVM test can check its shape without a device. */
internal fun singleUpdateListener(onFix: (Location?) -> Unit): LocationListener =
    object : LocationListener {
        override fun onLocationChanged(location: Location) {
            onFix(location)
        }
    }

/** The position in the shape discovery and its tests use. */
internal fun Location.toFix(): Fix = Fix(latitude, longitude)
