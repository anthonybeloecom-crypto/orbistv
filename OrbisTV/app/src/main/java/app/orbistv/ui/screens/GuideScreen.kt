package app.orbistv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbistv.Graph
import app.orbistv.Playback
import app.orbistv.data.ALL
import app.orbistv.data.EpgEntry
import app.orbistv.data.Kind
import app.orbistv.data.Stream
import app.orbistv.ui.CatalogLayout
import app.orbistv.ui.EmptyBox
import app.orbistv.ui.ErrorBox
import app.orbistv.ui.Focusable
import app.orbistv.ui.Load
import app.orbistv.ui.LoadingBox
import app.orbistv.ui.ScreenTitle
import app.orbistv.ui.Thumb
import app.orbistv.ui.produceLoad
import app.orbistv.ui.theme.OrbisColors
import app.orbistv.ui.tileTone

/** Guide des programmes : chaque chaîne avec ce qui passe maintenant et ensuite. */
@Composable
fun GuideScreen() {
    val repo = Graph.repo
    if (!repo.isXtream) {
        EmptyBox("Le guide TV est disponible avec un compte Xtream Codes.\nAvec un lien get.php, reconnecte-toi : l'app bascule toute seule en Xtream.")
        return
    }
    val ctx = LocalContext.current
    val load = produceLoad(Kind.LIVE) { repo.catalog(Kind.LIVE) }
    when (val s = load.value) {
        Load.Loading -> LoadingBox("Chargement du guide…")
        is Load.Error -> ErrorBox(s.message, load.retry)
        is Load.Ok -> {
            val catalog = s.value
            var selected by rememberSaveable { mutableStateOf(ALL) }
            val channels = remember(catalog, selected) { catalog.streamsIn(selected) }
            CatalogLayout(catalog.categories, selected, { selected = it }) { mod ->
                Column(mod) {
                    ScreenTitle("Guide TV", catalog.categoryName(selected))
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(4.dp)) {
                        itemsIndexed(channels, key = { _, c -> c.key }) { i, c ->
                            GuideRow(c) { Playback.start(ctx, repo.liveQueue(channels), i) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GuideRow(stream: Stream, onPlay: () -> Unit) {
    var entries by remember(stream.key) { mutableStateOf<List<EpgEntry>>(emptyList()) }
    var loaded by remember(stream.key) { mutableStateOf(false) }
    LaunchedEffect(stream.key) {
        entries = Graph.repo.epg(stream, limit = 6)
        loaded = true
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Focusable(onClick = onPlay, modifier = Modifier.width(150.dp).height(76.dp), focusScale = 1.04f) {
            Column(Modifier.fillMaxHeight().padding(8.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
                Thumb(stream.logo, stream.name, Modifier.size(width = 64.dp, height = 36.dp).clip(RoundedCornerShape(6.dp)), background = tileTone(stream.name), initialsSize = 12.sp)
                Spacer(Modifier.height(4.dp))
                Text(stream.name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Spacer(Modifier.width(10.dp))
        if (entries.isEmpty()) {
            Text(if (loaded) "Pas de programme disponible" else "…", color = OrbisColors.Muted, fontSize = 14.sp)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 16.dp)) {
                items(entries, key = { it.start }) { e ->
                    val now = e.isNow
                    Focusable(
                        onClick = { if (now) onPlay() },
                        modifier = Modifier.width(blockWidth(e)).height(76.dp),
                        selected = now,
                        focusScale = 1.04f,
                    ) { focused ->
                        Column(
                            Modifier.fillMaxHeight().background(if (now && !focused) Color.White else Color.Transparent).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            val fg = if (now && !focused) Color.Black else OrbisColors.Text
                            Text(e.timeRange, fontSize = 11.sp, color = fg.copy(alpha = 0.7f))
                            Text(e.title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = fg, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (now) {
                                Box(Modifier.fillMaxWidth().height(3.dp).background(fg.copy(alpha = 0.2f))) {
                                    Box(Modifier.fillMaxHeight().fillMaxWidth(e.progress()).background(fg))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Largeur proportionnelle à la durée restante (3 dp par minute, bornée). */
private fun blockWidth(e: EpgEntry) = run {
    val from = maxOf(e.start, System.currentTimeMillis())
    val minutes = (e.end - from) / 60_000f
    (minutes * 3f).coerceIn(150f, 380f).dp
}
