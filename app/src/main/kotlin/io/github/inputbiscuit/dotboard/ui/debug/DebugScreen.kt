package io.github.inputbiscuit.dotboard.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import io.github.inputbiscuit.dotboard.R
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.model.CoachClass
import io.github.inputbiscuit.dotboard.core.model.Departure
import io.github.inputbiscuit.dotboard.core.model.DepartureStatus
import io.github.inputbiscuit.dotboard.ui.theme.LedAmber

/** Debug screen: API key + CRS (+ optional filter) → list of live departures. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugScreen(onBack: () -> Unit, vm: DebugViewModel = viewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Departures list") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(painterResource(R.drawable.ic_arrow_back), "Back") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Use sample data", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (state.useSampleData) "Built-in sample boards, no API calls" else "Live data (through Dotboard's proxy unless you add your own key)",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Switch(checked = state.useSampleData, onCheckedChange = vm::onUseSampleData)
                }
            }
            if (state.useSampleData) {
                item {
                    Column(Modifier.selectableGroup()) {
                        FakeScenario.entries.forEach { scenario ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .selectable(
                                        selected = scenario == state.scenario,
                                        onClick = { vm.onScenario(scenario) },
                                        role = Role.RadioButton,
                                    ),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = scenario == state.scenario, onClick = null)
                                Text(scenario.label, Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                }
            } else {
                item {
                    OutlinedTextField(
                        value = state.apiKey,
                        onValueChange = vm::onApiKey,
                        label = { Text("Your own API key (optional)") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(autoCorrectEnabled = false, imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = state.crs,
                        onValueChange = vm::onCrs,
                        label = { Text("Station") },
                        supportingText = { Text(state.stationName ?: "CRS or name") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Next,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = state.filterCrs,
                        onValueChange = vm::onFilterCrs,
                        label = { Text("Calling at") },
                        supportingText = { Text(state.filterName ?: "Optional CRS") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            autoCorrectEnabled = false,
                            imeAction = ImeAction.Go,
                        ),
                        keyboardActions = KeyboardActions(onGo = { vm.fetch() }),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
            if (state.suggestions.isNotEmpty()) {
                item {
                    Column {
                        state.suggestions.forEach { s ->
                            SuggestionChip(onClick = { vm.pickStation(s) }, label = { Text("${s.name} (${s.crs})") })
                        }
                    }
                }
            }
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = vm::fetch, enabled = !state.loading) { Text("Get departures") }
                    if (state.loading) {
                        Spacer(Modifier.width(16.dp))
                        CircularProgressIndicator(Modifier.size(24.dp))
                    }
                }
            }
            state.error?.let { error ->
                item { Text(error, color = MaterialTheme.colorScheme.error) }
            }
            state.board?.let { board ->
                item {
                    Text(
                        buildString {
                            append(board.stationName)
                            board.filterName?.let { append(" → $it") }
                        },
                        style = MaterialTheme.typography.titleLarge,
                    )
                    board.generatedAt?.let { Text("Generated $it", style = MaterialTheme.typography.bodySmall) }
                    if (state.stale) {
                        Text("Showing the last board fetched", style = MaterialTheme.typography.bodySmall)
                    }
                }
                items(board.messages) { msg ->
                    Text(msg, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
                }
                if (board.departures.isEmpty()) {
                    item { Text("No services") }
                }
                items(board.departures) { DepartureCard(it) }
            }
        }
    }
}

@Composable
private fun DepartureCard(d: Departure) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.Black)) {
        Column(Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LedText(d.scheduled, Modifier.width(56.dp))
                LedText(d.destination, Modifier.weight(1f), bold = true)
                d.platform?.let { LedText("Plat $it", Modifier.padding(start = 8.dp)) }
            }
            Row {
                LedText(d.via.orEmpty(), Modifier.weight(1f))
                LedText(
                    d.status.boardText,
                    bold = d.status == DepartureStatus.Cancelled || d.status == DepartureStatus.Delayed,
                )
            }
            if (d.callingPoints.isNotEmpty()) {
                LedText(
                    "Calling at: " + d.callingPoints.joinToString { it.name },
                    maxLines = 3,
                    small = true,
                )
            }
            (d.cancelReason ?: d.delayReason)?.let { LedText(it, maxLines = 2, small = true) }
            // Debug only: whether the API sent a coach formation.
            LedText(
                d.formation?.let { f ->
                    "Coaches: " + f.coaches.joinToString(" ") { c ->
                        c.number + (if (c.coachClass == CoachClass.FIRST) "F" else "") + (c.loading?.let { "($it%)" } ?: "")
                    }
                } ?: "Coaches: no formation data",
                maxLines = 2,
                small = true,
            )
            d.operator?.let { LedText(it, small = true) }
        }
    }
}

@Composable
private fun LedText(
    text: String,
    modifier: Modifier = Modifier,
    bold: Boolean = false,
    small: Boolean = false,
    maxLines: Int = 1,
) {
    Text(
        text = text,
        modifier = modifier,
        color = LedAmber,
        fontFamily = FontFamily.Monospace,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        style = if (small) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}
