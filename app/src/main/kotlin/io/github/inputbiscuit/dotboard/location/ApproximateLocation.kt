package io.github.inputbiscuit.dotboard.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * One-off approximate location from Android's own LocationManager (no Google Play Services, so it stays
 * F-Droid friendly). Only used when the user taps "Use my location"; never stored or sent anywhere.
 */
object ApproximateLocation {

    sealed interface Result {
        data class Found(val location: Location) : Result
        data object NoPermission : Result
        data object LocationOff : Result
        data object Unavailable : Result
    }

    private const val TIMEOUT_MILLIS = 15_000L

    /** Last known fixes older than this are ignored in favour of a fresh one. */
    private const val MAX_AGE_MILLIS = 10 * 60_000L

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** "Allow all the time": needed for the background refresh to read the location of a following board. */
    fun hasBackgroundPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission") // checked by hasPermission
    suspend fun current(context: Context): Result {
        if (!hasPermission(context)) return Result.NoPermission
        val manager = context.getSystemService(LocationManager::class.java)
        if (!manager.isLocationEnabled) return Result.LocationOff

        // The platform fused provider (not Play Services) where available, else the network provider.
        val providers = listOf(LocationManager.FUSED_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { it in manager.allProviders && manager.isProviderEnabled(it) }

        val recent = providers.mapNotNull { manager.getLastKnownLocation(it) }
            .filter { System.currentTimeMillis() - it.time < MAX_AGE_MILLIS }
            .minByOrNull { it.accuracy }
        if (recent != null) return Result.Found(recent)

        for (provider in providers) {
            val fresh = withTimeoutOrNull(TIMEOUT_MILLIS) { currentFrom(manager, provider, context) }
            if (fresh != null) return Result.Found(fresh)
        }
        // Better an older fix than nothing for "which station is nearest".
        return providers.mapNotNull { manager.getLastKnownLocation(it) }.maxByOrNull { it.time }
            ?.let { Result.Found(it) } ?: Result.Unavailable
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentFrom(manager: LocationManager, provider: String, context: Context): Location? =
        suspendCancellableCoroutine { cont ->
            val cancel = CancellationSignal()
            cont.invokeOnCancellation { cancel.cancel() }
            manager.getCurrentLocation(provider, cancel, context.mainExecutor) { location -> cont.resume(location) }
        }
}
