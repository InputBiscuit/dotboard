package io.github.inputbiscuit.dotboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.inputbiscuit.dotboard.BuildConfig
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.render.LedTheme
import io.github.inputbiscuit.dotboard.core.render.SpareSpace
import io.github.inputbiscuit.dotboard.data.Settings
import io.github.inputbiscuit.dotboard.location.LocationFeatures
import io.github.inputbiscuit.dotboard.ui.components.DotboardScaffold
import io.github.inputbiscuit.dotboard.ui.components.RadioGroup
import io.github.inputbiscuit.dotboard.ui.components.SectionTitle
import io.github.inputbiscuit.dotboard.ui.components.StationField
import io.github.inputbiscuit.dotboard.ui.components.SwitchRow
import io.github.inputbiscuit.dotboard.ui.components.spareSpaceLabel

@Composable
fun SettingsScreen(onBack: () -> Unit, onKeyGuide: () -> Unit, onAbout: () -> Unit, vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hasKey by vm.hasApiKey.collectAsStateWithLifecycle()
    val stations by vm.stations.collectAsStateWithLifecycle()

    DotboardScaffold("Settings", onBack) {
        SectionTitle("Data")
        SwitchRow(
            title = "Use sample data",
            subtitle = if (settings.useSampleData) "Built-in sample boards. No API calls." else "Live departures from the Rail Data Marketplace",
            checked = settings.useSampleData,
            onChange = { on -> vm.update { it.copy(useSampleData = on) } },
        )
        if (settings.useSampleData) {
            RadioGroup(
                options = FakeScenario.entries,
                selected = settings.sampleScenario,
                label = { it.label },
                onSelect = { s -> vm.update { it.copy(sampleScenario = s) } },
            )
        }
        ApiKeyField(hasKey, onSave = vm::saveApiKey)
        TextButton(onClick = onKeyGuide) { Text("Why and how to use your own key") }

        SectionTitle("Default station")
        Text(
            "Used by the preview, and by boards added from the app until you set them up (long-press a board).",
            style = MaterialTheme.typography.bodySmall,
        )
        if (BuildConfig.LOCATION) {
            LocationFeatures.DefaultStationPicker(stations, onPick = { crs -> vm.update { it.copy(defaultCrs = crs) } })
        }
        StationField("Station", settings.defaultCrs, { crs -> vm.update { it.copy(defaultCrs = crs) } }, stations)
        StationField(
            "Calling at",
            settings.defaultFilterCrs,
            { crs -> vm.update { it.copy(defaultFilterCrs = crs) } },
            stations,
            optional = true,
        )

        SectionTitle("Board")
        RadioGroup(
            options = LedTheme.entries,
            selected = settings.theme,
            label = { it.label },
            onSelect = { t -> vm.update { it.copy(theme = t) } },
            leading = { Box(Modifier.size(16.dp).background(Color(it.palette.lit), CircleShape)) },
        )
        SwitchRow(
            title = "24-hour clock",
            subtitle = if (settings.use24h) "Times like 22:09" else "Times like 10:09 (no am/pm, to fit the board)",
            checked = settings.use24h,
            onChange = { on -> vm.update { it.copy(use24h = on) } },
        )
        SwitchRow(
            title = "Times at calling points",
            subtitle = "\"Calling at: Slough (10:42), Reading (11:02)\". Each board can change this in its own setup.",
            checked = settings.showCallingPointTimes,
            onChange = { on -> vm.update { it.copy(showCallingPointTimes = on) } },
        )
        SwitchRow(
            title = "Station name",
            subtitle = "\"London Paddington Station\" across the top, when the board has room. Each board can change this.",
            checked = settings.showStationName,
            onChange = { on -> vm.update { it.copy(showStationName = on) } },
        )
        Text("Use spare space for", style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = SpareSpace.entries,
            selected = settings.spareSpace,
            label = ::spareSpaceLabel,
            onSelect = { spare -> vm.update { it.copy(spareSpace = spare) } },
        )
        Text(
            "On a bigger board, list more trains, or show the 2nd, 3rd and later trains' calling points first. " +
                "Each board can change this.",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Widget")
        Text("Refresh departures", style = MaterialTheme.typography.bodyLarge)
        RadioGroup(
            options = Settings.REFRESH_CHOICES,
            selected = settings.refreshMinutes,
            label = { if (it == 60) "Every hour" else "Every $it minutes" },
            onSelect = { m -> vm.update { it.copy(refreshMinutes = m) } },
        )
        Text(
            "The scrolling text and clock run on your home screen without waking Dotboard. Departed trains drop " +
                "off when they leave, and you can tap a board to refresh it at any time.",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("About")
        TextButton(onClick = onAbout) { Text("About Dotboard, licences and credits") }
    }
}

@Composable
private fun ApiKeyField(hasKey: Boolean, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    var show by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Your own API key (optional)") },
            placeholder = { Text(if (hasKey) "Saved (enter a new one to replace it)" else "Consumer key") },
            supportingText = {
                Text(
                    if (hasKey) {
                        "Using your key, stored encrypted on this device. Requests go straight to the Rail Data Marketplace."
                    } else {
                        "Not needed. Without one, live data comes through Dotboard's proxy."
                    },
                )
            },
            singleLine = true,
            visualTransformation = if (show) VisualTransformation.None else PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
            modifier = Modifier.fillMaxWidth(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { onSave(text); text = "" }, enabled = text.isNotBlank()) { Text("Save key") }
            TextButton(onClick = { show = !show }) { Text(if (show) "Hide" else "Show") }
            if (hasKey) TextButton(onClick = { onSave("") }) { Text("Remove") }
        }
    }
}
