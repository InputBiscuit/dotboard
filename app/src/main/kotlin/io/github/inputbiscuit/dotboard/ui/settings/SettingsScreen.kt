package io.github.inputbiscuit.dotboard.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.inputbiscuit.dotboard.core.data.FakeScenario
import io.github.inputbiscuit.dotboard.core.render.LedTheme
import io.github.inputbiscuit.dotboard.data.Settings
import io.github.inputbiscuit.dotboard.ui.components.DotboardScaffold
import io.github.inputbiscuit.dotboard.ui.components.NearestStationPicker
import io.github.inputbiscuit.dotboard.ui.components.SectionTitle
import io.github.inputbiscuit.dotboard.ui.components.StationField

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
        TextButton(onClick = onKeyGuide) { Text("How to get a free API key") }

        SectionTitle("Default station")
        Text(
            "Used by the preview, and by boards added from the app until you set them up (long-press a board).",
            style = MaterialTheme.typography.bodySmall,
        )
        NearestStationPicker(stations, onPick = { crs -> vm.update { it.copy(defaultCrs = crs) } })
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
            label = { Text("Rail Data Marketplace API key") },
            placeholder = { Text(if (hasKey) "Saved (enter a new one to replace it)" else "Consumer key") },
            supportingText = { Text(if (hasKey) "A key is saved, encrypted on this device" else "No key saved") },
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

@Composable
private fun SwitchRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun <T> RadioGroup(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    leading: (@Composable (T) -> Unit)? = null,
) {
    Column(Modifier.selectableGroup()) {
        options.forEach { option ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .selectable(selected = option == selected, onClick = { onSelect(option) }, role = Role.RadioButton)
                    .padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadioButton(selected = option == selected, onClick = null)
                leading?.invoke(option)
                Text(label(option))
            }
        }
    }
}
