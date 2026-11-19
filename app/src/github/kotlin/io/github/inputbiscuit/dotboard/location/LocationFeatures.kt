package io.github.inputbiscuit.dotboard.location

import android.content.Context
import androidx.compose.runtime.Composable
import io.github.inputbiscuit.dotboard.core.stations.StationList
import io.github.inputbiscuit.dotboard.ui.components.NearestStationPicker
import io.github.inputbiscuit.dotboard.widget.FollowLocationSection

/**
 * The GitHub version's location features: "Use my location" and location-following boards. The Play version has
 * its own copy of this object that does nothing, so it contains no location code at all.
 */
object LocationFeatures {

    /** Settings → Default station: suggests the nearest stations. */
    @Composable
    fun DefaultStationPicker(stations: StationList?, onPick: (String) -> Unit) = NearestStationPicker(stations, onPick)

    /** Board setup: makes the board follow the user's location. */
    @Composable
    fun FollowSection(
        stations: StationList?,
        following: Boolean,
        refreshMinutes: Int,
        onFollow: (crs: String) -> Unit,
        onStopFollowing: () -> Unit,
    ) = FollowLocationSection(stations, following, refreshMinutes, onFollow, onStopFollowing)

    /** The user's approximate (latitude, longitude), or null without permission, with location off or no fix. */
    suspend fun approximate(context: Context): Pair<Double, Double>? =
        (ApproximateLocation.current(context) as? ApproximateLocation.Result.Found)?.location?.let { it.latitude to it.longitude }
}
