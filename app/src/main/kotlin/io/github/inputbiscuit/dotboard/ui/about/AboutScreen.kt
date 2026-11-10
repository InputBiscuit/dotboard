package io.github.inputbiscuit.dotboard.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.inputbiscuit.dotboard.core.render.BitmapFont
import io.github.inputbiscuit.dotboard.ui.components.DotboardScaffold
import io.github.inputbiscuit.dotboard.ui.components.SectionTitle

private const val REPO_URL = "https://github.com/InputBiscuit/dotboard"

private enum class Licence(val title: String) { GPL("GNU GPL v3"), OFL("SIL Open Font License 1.1"), ODBL("ODbL 1.0") }

@Composable
fun AboutScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull() ?: "?"
    }
    var showing by remember { mutableStateOf<Licence?>(null) }

    DotboardScaffold("About", onBack) {
        Text("Dotboard $version", style = MaterialTheme.typography.headlineSmall)
        Text("A dot-matrix departure board widget for UK stations.")

        SectionTitle("Data")
        Text("Powered by National Rail Enquiries", style = MaterialTheme.typography.titleMedium)
        Text(
            "Live departures come from the Live Departure Board feed on the Rail Data Marketplace, " +
                "using your own API key.",
        )
        Text(
            "Dotboard is an independent project. It isn't affiliated with or endorsed by National Rail Enquiries, " +
                "Rail Delivery Group or any train operator.",
            style = MaterialTheme.typography.bodySmall,
        )

        SectionTitle("Licence")
        Text("Dotboard is free software, released under the GNU General Public License version 3.")
        TextButton(onClick = { showing = Licence.GPL }) { Text("Read the GPL v3") }
        TextButton(onClick = { context.open(REPO_URL) }) { Text("Source code") }

        SectionTitle("Credits")
        Text("Dot Matrix typeface by Daniel Hart, used under the SIL Open Font License 1.1.")
        TextButton(onClick = { showing = Licence.OFL }) { Text("Read the font licence") }
        Text(
            "Station names and codes from uk-railway-stations by David Wheatley, derived from Trainline EU's " +
                "stations dataset and its sources. Licensed under the Open Database License 1.0.",
        )
        TextButton(onClick = { showing = Licence.ODBL }) { Text("Read the ODbL") }
    }

    showing?.let { licence ->
        AlertDialog(
            onDismissRequest = { showing = null },
            confirmButton = { TextButton(onClick = { showing = null }) { Text("Close") } },
            title = { Text(licence.title) },
            text = {
                Text(
                    remember(licence) { context.licenceText(licence) },
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()),
                )
            },
        )
    }
}

private fun Context.licenceText(licence: Licence): String = runCatching {
    when (licence) {
        Licence.GPL -> assets.open("licenses/GPL-3.0.txt").bufferedReader().use { it.readText() }
        Licence.ODBL -> assets.open("licenses/ODbL-1.0.txt").bufferedReader().use { it.readText() }
        // The font licence ships next to the generated fonts in :core.
        Licence.OFL -> BitmapFont::class.java.getResourceAsStream("/io/github/inputbiscuit/dotboard/core/fonts/OFL.txt")!!
            .bufferedReader().use { it.readText() }
    }
}.getOrDefault("Licence text unavailable.")

private fun Context.open(url: String) = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
