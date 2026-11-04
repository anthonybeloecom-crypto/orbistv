package app.orbistv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbistv.data.Kind
import app.orbistv.data.Stream
import app.orbistv.ui.Focusable
import app.orbistv.ui.OrbisMark
import app.orbistv.ui.Wordmark
import app.orbistv.ui.theme.OrbisColors
import kotlinx.coroutines.delay

enum class Section(val label: String, val icon: ImageVector) {
    LIVE("TV en direct", Icons.Filled.LiveTv),
    GUIDE("Guide TV", Icons.Filled.ViewList),
    MOVIES("Films", Icons.Filled.Movie),
    SERIES("Séries", Icons.Filled.Tv),
    FAVORITES("Favoris", Icons.Filled.Star),
    SEARCH("Recherche", Icons.Filled.Search),
    SETTINGS("Réglages", Icons.Filled.Settings),
}

@Composable
fun HomeScreen(
    section: Section,
    onSection: (Section) -> Unit,
    onOpen: (Stream) -> Unit,
    onLogout: () -> Unit,
    onDiagnostic: () -> Unit = {},
    onPhoneSetup: () -> Unit = {},
    onProfiles: () -> Unit = {},
) {
    val holder = rememberSaveableStateHolder()
    val firstFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        delay(150)
        runCatching { firstFocus.requestFocus() }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 700.dp
        Row(Modifier.fillMaxSize()) {
            // ---------- Barre de navigation
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(if (compact) 72.dp else 220.dp)
                    .padding(vertical = 24.dp, horizontal = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Box(Modifier.padding(start = 8.dp, bottom = 18.dp)) {
                    if (compact) OrbisMark(30.dp) else Wordmark()
                }
                Section.entries.forEachIndexed { i, s ->
                    val sel = s == section
                    Focusable(
                        onClick = { onSection(s) },
                        selected = sel,
                        shape = RoundedCornerShape(12.dp),
                        focusScale = 1.03f,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                            .then(if (i == 0) Modifier.focusRequester(firstFocus) else Modifier),
                    ) { f ->
                        Row(
                            Modifier.fillMaxSize().padding(horizontal = 14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                s.icon,
                                contentDescription = s.label,
                                tint = if (sel || f) OrbisColors.Accent else OrbisColors.Muted,
                                modifier = Modifier.size(22.dp),
                            )
                            if (!compact) {
                                Spacer(Modifier.width(14.dp))
                                Text(
                                    s.label,
                                    color = if (sel || f) OrbisColors.Text else OrbisColors.Muted,
                                    fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                    fontSize = 16.sp,
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                val profile = app.orbistv.Graph.prefs.activeProfile
                Focusable(
                    onClick = onProfiles,
                    shape = RoundedCornerShape(12.dp),
                    focusScale = 1.03f,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                ) {
                    Row(Modifier.fillMaxSize().padding(horizontal = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        app.orbistv.ui.Avatar(profile, 34.dp)
                        if (!compact) {
                            Spacer(Modifier.width(12.dp))
                            Text(profile.name, fontSize = 15.sp, maxLines = 1)
                        }
                    }
                }
            }

            // ---------- Contenu
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(top = 24.dp, end = if (compact) 12.dp else 32.dp, bottom = 12.dp),
            ) {
                holder.SaveableStateProvider(section.name) {
                    when (section) {
                        Section.LIVE -> LiveScreen()
                        Section.GUIDE -> GuideScreen()
                        Section.MOVIES -> VodScreen(Kind.MOVIE, onOpen)
                        Section.SERIES -> VodScreen(Kind.SERIES, onOpen)
                        Section.FAVORITES -> FavoritesScreen(onOpen)
                        Section.SEARCH -> SearchScreen(onOpen)
                        Section.SETTINGS -> SettingsScreen(onLogout, onDiagnostic, onPhoneSetup, onProfiles)
                    }
                }
            }
        }
    }
}
