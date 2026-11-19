package io.github.inputbiscuit.dotboard.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.inputbiscuit.dotboard.app
import io.github.inputbiscuit.dotboard.core.data.DepartureException
import kotlinx.coroutines.delay

private sealed interface Platforms {
    data object Loading : Platforms
    data class Found(val list: List<String>) : Platforms
    data object Failed : Platforms
}

/**
 * Board setup's optional platform filter: "All platforms" (the default) or one or more of the station's
 * platforms. Hidden for stations that show no platform numbers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PlatformPicker(
    crs: String,
    selected: Set<String>,
    keepNoPlatform: Boolean,
    onSelect: (Set<String>) -> Unit,
    onKeepNoPlatform: (Boolean) -> Unit,
) {
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    val platforms by produceState<Platforms>(Platforms.Loading, crs, attempt) {
        value = Platforms.Loading
        if (crs.length != 3) return@produceState
        delay(300) // let typing settle
        value = try {
            Platforms.Found(context.app.platforms.platforms(crs, context.app.departures()))
        } catch (e: DepartureException) {
            Platforms.Failed
        }
    }

    when (val p = platforms) {
        Platforms.Loading -> if (crs.length == 3) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                Text("Finding platforms…", style = MaterialTheme.typography.bodySmall)
            }
        }
        Platforms.Failed -> Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Couldn't load this station's platforms.", style = MaterialTheme.typography.bodySmall)
            TextButton(onClick = { attempt++ }) { Text("Try again") }
        }
        is Platforms.Found -> if (p.list.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Platforms", style = MaterialTheme.typography.bodyLarge)
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selected.isEmpty(),
                        onClick = { onSelect(emptySet()) },
                        label = { Text("All platforms") },
                    )
                    // Platforms chosen earlier that haven't been seen lately stay, so they can be turned off.
                    (p.list + selected.filterNot { it in p.list }).forEach { platform ->
                        FilterChip(
                            selected = platform in selected,
                            onClick = { onSelect(if (platform in selected) selected - platform else selected + platform) },
                            label = { Text(platform) },
                        )
                    }
                }
                if (selected.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = keepNoPlatform, onCheckedChange = onKeepNoPlatform)
                        Text("Also show trains with no platform yet", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}
