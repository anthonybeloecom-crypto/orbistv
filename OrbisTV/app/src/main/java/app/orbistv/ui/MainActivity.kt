package app.orbistv.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import app.orbistv.Graph
import app.orbistv.data.Kind
import app.orbistv.data.Stream
import app.orbistv.ui.screens.HomeScreen
import app.orbistv.ui.screens.DiagnosticScreen
import app.orbistv.ui.screens.PhoneSetupScreen
import app.orbistv.ui.screens.ProfilePickerScreen
import app.orbistv.ui.screens.LoginScreen
import app.orbistv.ui.screens.MovieDetailScreen
import app.orbistv.ui.screens.Section
import app.orbistv.ui.screens.SeriesDetailScreen
import app.orbistv.ui.screens.SplashScreen
import app.orbistv.ui.theme.OrbisColors
import app.orbistv.ui.theme.OrbisTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrbisTheme { OrbisRoot() }
        }
    }
}

private sealed interface Route {
    data object Login : Route
    data object Home : Route
    data class Movie(val stream: Stream) : Route
    data class Series(val stream: Stream) : Route
    data object Diagnostic : Route
    data object PhoneSetup : Route
}

private fun Route.saveKey(): String = when (this) {
    Route.Login -> "login"
    Route.Home -> "home"
    is Route.Movie -> "movie:${stream.key}"
    is Route.Series -> "series:${stream.key}"
    Route.Diagnostic -> "diagnostic"
    Route.PhoneSetup -> "phone"
}

@Composable
private fun OrbisRoot() {
    var splash by rememberSaveable { mutableStateOf(true) }
    Crossfade(targetState = splash, animationSpec = tween(450), label = "splash") { showSplash ->
        if (showSplash) {
            SplashScreen(playSound = Graph.prefs.introSound) { splash = false }
        } else {
            OrbisContent()
        }
    }
}

@Composable
private fun OrbisContent() {
    val holder = rememberSaveableStateHolder()
    var stack by remember {
        mutableStateOf<List<Route>>(listOf(if (Graph.prefs.account() != null) Route.Home else Route.Login))
    }
    var section by rememberSaveable { mutableStateOf(Section.LIVE) }
    val top = stack.last()

    fun push(r: Route) { stack = stack + r }
    fun open(s: Stream) {
        when (s.kind) {
            Kind.MOVIE -> push(Route.Movie(s))
            Kind.SERIES -> push(Route.Series(s))
            Kind.LIVE -> Unit
        }
    }

    BackHandler(enabled = stack.size > 1) { stack = stack.dropLast(1) }
    BackHandler(enabled = stack.size == 1 && top == Route.Home && section != Section.LIVE) { section = Section.LIVE }

    Box(Modifier.fillMaxSize().background(OrbisColors.Bg)) {
        holder.SaveableStateProvider(top.saveKey()) {
            when (top) {
                Route.Login -> LoginScreen(
                    onDone = { stack = listOf(Route.Home) },
                    onPhoneSetup = { push(Route.PhoneSetup) },
                )
                Route.Diagnostic -> DiagnosticScreen()
                Route.PhoneSetup -> PhoneSetupScreen(onConnected = {
                    section = Section.LIVE
                    stack = listOf(Route.Home)
                })
                Route.Home -> if (!Graph.prefs.profileChosen) {
                    ProfilePickerScreen(onChosen = {})
                } else HomeScreen(
                    section = section,
                    onSection = { section = it },
                    onOpen = ::open,
                    onLogout = {
                        Graph.prefs.clearAccount()
                        Graph.repo.clearCache()
                        section = Section.LIVE
                        stack = listOf(Route.Login)
                    },
                    onDiagnostic = { push(Route.Diagnostic) },
                    onPhoneSetup = { push(Route.PhoneSetup) },
                    onProfiles = { Graph.prefs.profileChosen = false },
                )
                is Route.Movie -> MovieDetailScreen(top.stream)
                is Route.Series -> SeriesDetailScreen(top.stream)
            }
        }
    }
}
