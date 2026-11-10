package io.github.inputbiscuit.dotboard.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import io.github.inputbiscuit.dotboard.core.stations.StationList

/**
 * Pick a station by CRS code or by typing part of its name. [crs] is the chosen code ("" for none);
 * [onCrsChange] receives a 3-letter code once one is chosen, or "" when cleared.
 */
@Composable
fun StationField(
    label: String,
    crs: String,
    onCrsChange: (String) -> Unit,
    stations: StationList?,
    modifier: Modifier = Modifier,
    optional: Boolean = false,
) {
    var text by remember { mutableStateOf(crs) }
    LaunchedEffect(crs) { if (!text.equals(crs, ignoreCase = true) && crs.isNotEmpty()) text = crs }

    val exact = text.trim().takeIf { it.length == 3 }?.let { stations?.get(it) }
    val suggestions = if (exact == null && text.trim().length >= 2) stations?.search(text, limit = 5).orEmpty() else emptyList()

    Column(modifier) {
        OutlinedTextField(
            value = text,
            onValueChange = { value ->
                text = value
                val match = value.trim().takeIf { it.length == 3 }?.let { stations?.get(it) }
                when {
                    match != null -> onCrsChange(match.crs)
                    value.isBlank() -> onCrsChange("")
                }
            },
            label = { Text(label) },
            supportingText = {
                Text(
                    when {
                        exact != null -> exact.name
                        text.isBlank() && optional -> "Optional: CRS code or name"
                        else -> "CRS code (e.g. EAR) or station name"
                    },
                )
            },
            isError = text.isNotBlank() && exact == null && suggestions.isEmpty(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words,
                autoCorrectEnabled = false,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        suggestions.forEach { s ->
            SuggestionChip(
                onClick = {
                    text = s.crs
                    onCrsChange(s.crs)
                },
                label = { Text("${s.name} (${s.crs})") },
            )
        }
    }
}
