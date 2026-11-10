package io.github.inputbiscuit.dotboard.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Amber used by the LED board; also the accent for the app's dark scheme. */
val LedAmber = Color(0xFFFFB300)

@Composable
fun DotboardTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    // minSdk 31, so Material You dynamic colour is always available.
    val scheme = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = scheme, content = content)
}
