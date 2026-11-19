package io.github.inputbiscuit.dotboard.widget

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import io.github.inputbiscuit.dotboard.core.stations.NearbyStation
import io.github.inputbiscuit.dotboard.core.stations.StationList
import io.github.inputbiscuit.dotboard.location.ApproximateLocation
import io.github.inputbiscuit.dotboard.ui.components.formatMiles
import kotlinx.coroutines.launch

/**
 * Board setup's "Use my location": turns on following, so the board shows whichever station is nearest,
 * re-checked on each background refresh. Needs approximate location, plus "Allow all the time" for the
 * background checks. Without that the board keeps the station found here.
 */
@Composable
fun FollowLocationSection(
    stations: StationList?,
    following: Boolean,
    refreshMinutes: Int,
    onFollow: (crs: String) -> Unit,
    onStopFollowing: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var nearest by remember { mutableStateOf<NearbyStation?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var background by remember { mutableStateOf(ApproximateLocation.hasBackgroundPermission(context)) }
    LifecycleResumeEffect(Unit) {
        background = ApproximateLocation.hasBackgroundPermission(context)
        onPauseOrDispose { }
    }

    fun locate() {
        val list = stations ?: return
        loading = true
        message = null
        scope.launch {
            when (val result = ApproximateLocation.current(context)) {
                is ApproximateLocation.Result.Found -> {
                    nearest = list.nearest(result.location.latitude, result.location.longitude, limit = 1).firstOrNull()
                    nearest?.let { onFollow(it.station.crs) } ?: run { message = "No stations found nearby." }
                }
                ApproximateLocation.Result.NoPermission -> message = "Location isn't allowed. You can still type a station."
                ApproximateLocation.Result.LocationOff -> message = "Location is turned off. Turn it on, or type a station."
                ApproximateLocation.Result.Unavailable -> message = "Couldn't get your location. Try again, or type a station."
            }
            loading = false
        }
    }

    val foreground = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else message = "Location isn't allowed. You can still type a station."
    }
    // On Android 11+ this opens the app's location permission page, where "Allow all the time" is chosen.
    val backgroundRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        background = granted
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        if (!following) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = {
                        if (ApproximateLocation.hasPermission(context)) locate()
                        else foreground.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                    },
                    enabled = stations != null && !loading,
                ) { Text("Use my location") }
                if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            }
            Text(
                "The board will follow you, showing the nearest station. It checks every $refreshMinutes minutes.",
                style = MaterialTheme.typography.bodySmall,
            )
        } else {
            Text(
                buildString {
                    append("Following your location")
                    nearest?.let { append(": nearest is ${it.station.name} (${formatMiles(it.distanceMetres)})") }
                    append(". Checked every $refreshMinutes minutes, and when you tap the board.")
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (!background) {
                Text(
                    "To check your location in the background, Dotboard needs location set to \"Allow all the " +
                        "time\" (approximate is enough). Without it, the board stays on this station.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                Button(onClick = { backgroundRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION) }) {
                    Text("Allow all the time")
                }
            }
            TextButton(onClick = {
                nearest = null
                onStopFollowing()
            }) { Text("Stop following (keep this station)") }
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
    }
}
