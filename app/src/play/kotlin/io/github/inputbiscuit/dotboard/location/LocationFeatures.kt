package io.github.inputbiscuit.dotboard.location

import android.content.Context
import androidx.compose.runtime.Composable
import io.github.inputbiscuit.dotboard.core.stations.StationList

/** The Play version has no location features: this stands in for the GitHub version's, and does nothing. */
object LocationFeatures {

    @Composable
    @Suppress("UNUSED_PARAMETER")
    fun DefaultStationPicker(stations: StationList?, onPick: (String) -> Unit) = Unit

    @Composable
    @Suppress("UNUSED_PARAMETER")
    fun FollowSection(
        stations: StationList?,
        following: Boolean,
        refreshMinutes: Int,
        onFollow: (crs: String) -> Unit,
        onStopFollowing: () -> Unit,
    ) = Unit

    @Suppress("UNUSED_PARAMETER", "RedundantSuspendModifier")
    suspend fun approximate(context: Context): Pair<Double, Double>? = null
}
