package io.github.inputbiscuit.dotboard.ui.onboarding

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.inputbiscuit.dotboard.app
import io.github.inputbiscuit.dotboard.core.data.BoardQuery
import io.github.inputbiscuit.dotboard.core.render.BoardContent
import io.github.inputbiscuit.dotboard.ui.components.DotboardScaffold
import io.github.inputbiscuit.dotboard.ui.home.BoardPreview
import io.github.inputbiscuit.dotboard.data.Settings
import io.github.inputbiscuit.dotboard.widget.WidgetUpdater
import kotlinx.coroutines.launch

/** Step indices, so Settings can open the API key guide directly. */
object OnboardingStep {
    const val WELCOME = 0
    const val API_KEY = 1
    const val WIDGET = 2
}

/**
 * First-run guide: what Dotboard is, how to get a free Rail Data Marketplace key, and how to add a board.
 * [onFinish] is called when done or skipped (and when going back from the first step).
 */
@Composable
fun OnboardingScreen(
    startStep: Int,
    onFinish: () -> Unit,
    onAddWidget: () -> Unit,
    standaloneGuide: Boolean = false,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var step by rememberSaveable { mutableIntStateOf(startStep) }
    var key by rememberSaveable { mutableStateOf("") }

    fun finish() {
        scope.launch {
            context.app.settings.update { it.copy(onboardingDone = true) }
            onFinish()
        }
    }

    DotboardScaffold(
        title = when (step) {
            OnboardingStep.WELCOME -> "Welcome to Dotboard"
            OnboardingStep.API_KEY -> "Get a free API key"
            else -> "Add a board"
        },
        onBack = if (standaloneGuide) onFinish else null,
    ) {
        when (step) {
            OnboardingStep.WELCOME -> {
                val sample by produceState<BoardContent>(BoardContent.Message("Dotboard")) {
                    value = runCatching {
                        BoardContent.Departures(context.app.departures().board(BoardQuery("PAD")))
                    }.getOrDefault(BoardContent.Message("Dotboard"))
                }
                BoardPreview(sample, Settings().theme, use24h = true)
                Text(
                    "Dotboard puts a UK station departure board on your home screen, drawn dot by dot like the " +
                        "real thing.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                Text(
                    "Live times come from the Rail Data Marketplace's free Live Departure Board feed. You'll need your " +
                        "own key, which takes a few minutes to request. Until then Dotboard shows sample boards.",
                )
                Button(onClick = { step = OnboardingStep.API_KEY }, Modifier.fillMaxWidth()) { Text("Next") }
                TextButton(onClick = ::finish) { Text("Skip and use sample data") }
            }

            OnboardingStep.API_KEY -> {
                GuideStep(1, "Go to raildata.org.uk and register for a free account.") {
                    OutlinedButton(onClick = {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://raildata.org.uk/")))
                    }) { Text("Open raildata.org.uk") }
                }
                GuideStep(2, "Confirm your email address, then sign in.")
                GuideStep(3, "Search the data catalogue for \"Live Departure Board\" and open it. It's free.")
                GuideStep(
                    4,
                    "Subscribe to it and accept the terms. Approval can take up to a few working days; " +
                        "you can use sample data meanwhile.",
                )
                GuideStep(5, "Once approved, open your subscription and copy its Consumer key from the details page.")
                GuideStep(
                    6,
                    "Paste it here. It's stored encrypted on this phone and only ever sent to api1.raildata.org.uk.",
                )
                OutlinedTextField(
                    value = key,
                    onValueChange = { key = it },
                    label = { Text("Consumer key") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(autoCorrectEnabled = false),
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = {
                        scope.launch {
                            context.app.apiKeys.set(key)
                            context.app.settings.update { it.copy(useSampleData = false) }
                            WidgetUpdater.updateAll(context, WidgetUpdater.Mode.FORCE)
                            if (standaloneGuide) onFinish() else step = OnboardingStep.WIDGET
                        }
                    },
                    enabled = key.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Save key and use live data") }
                TextButton(onClick = { if (standaloneGuide) onFinish() else step = OnboardingStep.WIDGET }) {
                    Text(if (standaloneGuide) "Close" else "I'll do this later")
                }
            }

            else -> {
                Text("Add a board to your home screen:", style = MaterialTheme.typography.bodyLarge)
                GuideStep(1, "Long-press an empty part of your home screen and choose Widgets.")
                GuideStep(2, "Find Dotboard and drag \"Departure board\" onto the home screen.")
                GuideStep(3, "Pick the station (and optionally a station it should call at), then tap Add board.")
                Text(
                    "Resize it to show more rows. Tap it to refresh; long-press it to change the station.",
                    style = MaterialTheme.typography.bodySmall,
                )
                OutlinedButton(onClick = onAddWidget, Modifier.fillMaxWidth()) { Text("Add one now") }
                Button(onClick = ::finish, Modifier.fillMaxWidth()) { Text("Done") }
            }
        }
    }
}

@Composable
private fun GuideStep(number: Int, text: String, extra: (@Composable () -> Unit)? = null) {
    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("$number.", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp))
        Column(Modifier.weight(1f).padding(bottom = 4.dp)) {
            Text(text)
            extra?.invoke()
        }
    }
}
