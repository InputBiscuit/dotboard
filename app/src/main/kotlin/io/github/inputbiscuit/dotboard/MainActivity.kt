package io.github.inputbiscuit.dotboard

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import io.github.inputbiscuit.dotboard.ui.about.AboutScreen
import io.github.inputbiscuit.dotboard.ui.debug.DebugScreen
import io.github.inputbiscuit.dotboard.ui.home.HomeAction
import io.github.inputbiscuit.dotboard.ui.home.HomeScreen
import io.github.inputbiscuit.dotboard.ui.onboarding.OnboardingScreen
import io.github.inputbiscuit.dotboard.ui.onboarding.OnboardingStep
import io.github.inputbiscuit.dotboard.ui.settings.SettingsScreen
import io.github.inputbiscuit.dotboard.ui.theme.DotboardTheme
import io.github.inputbiscuit.dotboard.widget.BoardWidgetProvider

private enum class Screen { LOADING, ONBOARDING, HOME, SETTINGS, KEY_GUIDE, ABOUT, DEBUG }

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            DotboardTheme {
                var screen by rememberSaveable { mutableStateOf(Screen.LOADING) }
                LaunchedEffect(Unit) {
                    if (screen == Screen.LOADING) {
                        screen = if (app.settings.current().onboardingDone) Screen.HOME else Screen.ONBOARDING
                    }
                }
                val home = { screen = Screen.HOME }
                BackHandler(enabled = screen in setOf(Screen.SETTINGS, Screen.ABOUT, Screen.DEBUG)) { home() }
                BackHandler(enabled = screen == Screen.KEY_GUIDE) { screen = Screen.SETTINGS }

                when (screen) {
                    Screen.LOADING -> Unit
                    Screen.ONBOARDING -> OnboardingScreen(OnboardingStep.WELCOME, onFinish = home, onAddWidget = ::requestPinWidget)
                    Screen.HOME -> HomeScreen(
                        actions = listOf(
                            HomeAction("Add a board to your home screen", "Or long-press your home screen → Widgets → Dotboard") {
                                requestPinWidget()
                            },
                            HomeAction("Settings", "API key, default station, colour, refresh") { screen = Screen.SETTINGS },
                            HomeAction("About", "Version, licences and credits") { screen = Screen.ABOUT },
                            HomeAction("Departures list", "Debug view of the raw board data") { screen = Screen.DEBUG },
                        ),
                    )
                    Screen.SETTINGS -> SettingsScreen(
                        onBack = home,
                        onKeyGuide = { screen = Screen.KEY_GUIDE },
                        onAbout = { screen = Screen.ABOUT },
                    )
                    Screen.KEY_GUIDE -> OnboardingScreen(
                        OnboardingStep.API_KEY,
                        onFinish = { screen = Screen.SETTINGS },
                        onAddWidget = ::requestPinWidget,
                        standaloneGuide = true,
                    )
                    Screen.ABOUT -> AboutScreen(onBack = home)
                    Screen.DEBUG -> DebugScreen(onBack = home)
                }
            }
        }
    }

    /** Asks the launcher to pin a board; it shows the default station until reconfigured (long-press it). */
    private fun requestPinWidget() {
        val manager = getSystemService(AppWidgetManager::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(this, BoardWidgetProvider::class.java), null, null)
        } else {
            Toast.makeText(this, "Long-press your home screen, then Widgets → Dotboard", Toast.LENGTH_LONG).show()
        }
    }
}
