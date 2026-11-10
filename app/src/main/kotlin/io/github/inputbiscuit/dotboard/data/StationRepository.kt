package io.github.inputbiscuit.dotboard.data

import android.content.Context
import io.github.inputbiscuit.dotboard.core.stations.StationList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Loads the bundled `assets/stations.csv` once and keeps it in memory. */
class StationRepository(private val context: Context) {

    private val mutex = Mutex()
    private var cached: StationList? = null

    suspend fun stations(): StationList = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) {
            context.assets.open("stations.csv").bufferedReader().use { StationList.parse(it.readText()) }
        }.also { cached = it }
    }

    suspend fun name(crs: String): String? = stations()[crs]?.name
}
