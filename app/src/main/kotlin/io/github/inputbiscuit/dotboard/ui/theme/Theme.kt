package io.github.inputbiscuit.dotboard.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** Amber used by the LED board; also the accent for the app's dark scheme. */
val LedAmber = Color(0xFFFFB300)

@Composable
fun DotboardTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    // Material You dynamic colour on Android 12+; Android 11 gets fixed schemes, amber-accented when dark.
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme(primary = LedAmber, onPrimary = Color.Black)
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
