package io.github.inputbiscuit.dotboard.ui.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

/** A row on the home screen that opens another screen or does something. */
data class HomeAction(val title: String, val subtitle: String, val onClick: () -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(actions: List<HomeAction>, vm: HomeViewModel = viewModel()) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val content by vm.content.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Dotboard") }) }) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            BoardPreview(
                content = content,
                theme = settings.theme,
                use24h = settings.use24h,
                modifier = Modifier.padding(horizontal = 16.dp).clickable(onClick = vm::refresh),
            )
            Text(
                buildString {
                    append(settings.defaultCrs)
                    if (settings.defaultFilterCrs.isNotEmpty()) append(" → ${settings.defaultFilterCrs}")
                    append(if (settings.useSampleData) " · sample data" else " · live")
                    append(" · tap the board to refresh")
                },
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            actions.forEach { action ->
                ListItem(
                    headlineContent = { Text(action.title) },
                    supportingContent = { Text(action.subtitle) },
                    modifier = Modifier.clickable(onClick = action.onClick),
                )
            }
        }
    }
}
