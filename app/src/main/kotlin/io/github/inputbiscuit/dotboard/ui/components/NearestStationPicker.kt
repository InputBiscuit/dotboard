package io.github.inputbiscuit.dotboard.ui.components

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
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
import io.github.inputbiscuit.dotboard.core.stations.NearbyStation
import io.github.inputbiscuit.dotboard.core.stations.StationList
import io.github.inputbiscuit.dotboard.location.ApproximateLocation
import kotlinx.coroutines.launch

/**
 * "Use my location": asks for approximate location (only when tapped), then offers the nearest stations from
 * the bundled list. No API call; the location isn't stored. Typing a station still works if it's declined.
 */
@Composable
fun NearestStationPicker(stations: StationList?, onPick: (String) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(false) }
    var nearby by remember { mutableStateOf<List<NearbyStation>>(emptyList()) }
    var message by remember { mutableStateOf<String?>(null) }

    fun locate() {
        val list = stations ?: return
        loading = true
        message = null
        scope.launch {
            message = when (val result = ApproximateLocation.current(context)) {
                is ApproximateLocation.Result.Found -> {
                    nearby = list.nearest(result.location.latitude, result.location.longitude, limit = 5)
                    if (nearby.isEmpty()) "No stations found nearby." else null
                }
                ApproximateLocation.Result.NoPermission -> "Location isn't allowed. You can still type a station."
                ApproximateLocation.Result.LocationOff -> "Location is turned off. Turn it on, or type a station."
                ApproximateLocation.Result.Unavailable -> "Couldn't get your location. Try again, or type a station."
            }
            loading = false
        }
    }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) locate() else message = "Location isn't allowed. You can still type a station."
    }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(
                onClick = {
                    if (ApproximateLocation.hasPermission(context)) locate()
                    else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
                },
                enabled = stations != null && !loading,
            ) { Text("Use my location") }
            if (loading) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
        }
        message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
        nearby.forEach { n ->
            SuggestionChip(
                onClick = {
                    onPick(n.station.crs)
                    nearby = emptyList()
                },
                label = { Text("${n.station.name} (${n.station.crs}) · ${formatMiles(n.distanceMetres)}") },
            )
        }
    }
}

/** UK-style distance: miles to one decimal place under 10 miles. */
internal fun formatMiles(metres: Double): String {
    val miles = metres / 1609.344
    return if (miles < 10) "%.1f mi".format(miles) else "%.0f mi".format(miles)
}
