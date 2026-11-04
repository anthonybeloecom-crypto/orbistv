package app.orbistv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbistv.Graph
import app.orbistv.data.ALL
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
import app.orbistv.ui.rememberResumeTick
import app.orbistv.ui.theme.OrbisColors

/** Grille d'affiches pour les films ou les séries. */
@Composable
fun VodScreen(kind: Kind, onOpen: (Stream) -> Unit) {
    val repo = Graph.repo
    val load = produceLoad(kind) { repo.catalog(kind) }
    val label = if (kind == Kind.MOVIE) "films" else "séries"

    when (val s = load.value) {
        Load.Loading -> LoadingBox("Chargement des $label…")
        is Load.Error -> ErrorBox(s.message, load.retry)
        is Load.Ok -> {
            val catalog = s.value
            var selected by rememberSaveable { mutableStateOf(ALL) }
            val visible = remember(catalog, selected) { catalog.streamsIn(selected) }
            val gridState = rememberLazyGridState()
            LaunchedEffect(selected) { gridState.scrollToItem(0) }

            CatalogLayout(catalog.categories, selected, { selected = it }) { mod ->
                Column(mod) {
                    ScreenTitle(catalog.categoryName(selected), "${visible.size} $label")
                    if (selected == ALL && visible.isNotEmpty()) {
                        VodHome(catalog, onOpen)
                    } else if (visible.isEmpty()) {
                        EmptyBox(
                            if (kind == Kind.SERIES && catalog.streams.isEmpty()) "Aucune série (les playlists M3U ne contiennent pas de fiches séries)"
                            else "Rien dans cette catégorie",
                        )
                    } else {
                        LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(150.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalArrangement = Arrangement.spacedBy(18.dp),
                        ) {
                            items(visible, key = { it.key }) { item -> PosterCard(item) { onOpen(item) } }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PosterCard(stream: Stream, onClick: () -> Unit) {
    val tick = rememberResumeTick()
    val progress = remember(tick, stream.key) { Graph.prefs.progress(stream.key) }
    Focusable(onClick = onClick, focusScale = 1.07f, modifier = Modifier.fillMaxWidth()) { focused ->
        Column {
            Box {
                Thumb(stream.logo, stream.name, Modifier.fillMaxWidth().aspectRatio(2f / 3f), ContentScale.Crop)
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = OrbisColors.Accent,
                        trackColor = OrbisColors.Surface2,
                        modifier = Modifier.fillMaxWidth().height(4.dp).align(Alignment.BottomCenter),
                    )
                }
            }
            Text(
                stream.name,
                fontSize = 14.sp,
                fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            )
        }
    }
}


// ---------------------------------------------------------------- Accueil « en grand »

@Composable
private fun VodHome(catalog: app.orbistv.data.Catalog, onOpen: (Stream) -> Unit) {
    val recent = remember(catalog) { catalog.recent(20) }
    val rows = remember(catalog) {
        catalog.categories.filter { it.id != ALL }.take(16)
            .map { it to catalog.streamsIn(it.id).take(20) }
            .filter { it.second.isNotEmpty() }
    }
    androidx.compose.foundation.lazy.LazyColumn(
        verticalArrangement = Arrangement.spacedBy(26.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        recent.firstOrNull()?.let { featured ->
            item(key = "hero") { VodHero(featured) { onOpen(featured) } }
        }
        item(key = "recent") { PosterRow("Ajoutés récemment", recent, onOpen) }
        rows.forEach { (cat, list) ->
            item(key = "cat:" + cat.id) { PosterRow(cat.name, list, onOpen) }
        }
    }
}

@Composable
private fun VodHero(stream: Stream, onOpen: () -> Unit) {
    var backdrop by remember(stream.key) { mutableStateOf<String?>(null) }
    var plot by remember(stream.key) { mutableStateOf(stream.plot) }
    LaunchedEffect(stream.key) {
        runCatching {
            if (stream.kind == Kind.MOVIE && stream.url == null) {
                val info = Graph.repo.vodInfo(stream.id)
                backdrop = info.backdrop
                plot = info.plot ?: plot
            } else if (stream.kind == Kind.SERIES) {
                val info = Graph.repo.seriesInfo(stream.id)
                backdrop = info.backdrop
                plot = info.plot ?: plot
            }
        }
    }
    Box(Modifier.fillMaxWidth().height(320.dp).androidxClip()) {
        coil.compose.AsyncImage(
            model = backdrop ?: stream.logo,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().then(if (backdrop == null) Modifier.blur(24.dp) else Modifier),
        )
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.horizontalGradient(
                    listOf(OrbisColors.Bg, OrbisColors.Bg.copy(alpha = 0.7f), androidx.compose.ui.graphics.Color.Transparent),
                ),
            ),
        )
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxSize().padding(28.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Thumb(stream.logo, stream.name, Modifier.height(240.dp).aspectRatio(2f / 3f).androidxClip(), ContentScale.Crop)
            androidx.compose.foundation.layout.Spacer(Modifier.padding(start = 28.dp))
            Column(Modifier.weight(1f)) {
                Text("À LA UNE", fontSize = 12.sp, letterSpacing = 2.sp, color = OrbisColors.Muted, fontWeight = FontWeight.SemiBold)
                Text(stream.name, fontSize = 34.sp, fontWeight = FontWeight.Medium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                plot?.let { Text(it, fontSize = 14.sp, color = OrbisColors.Text.copy(alpha = 0.8f), maxLines = 3, overflow = TextOverflow.Ellipsis) }
                androidx.compose.foundation.layout.Spacer(Modifier.height(14.dp))
                app.orbistv.ui.ActionButton(
                    if (stream.kind == Kind.SERIES) "Voir les épisodes" else "Voir le film",
                    androidx.compose.material.icons.Icons.Filled.PlayArrow,
                    onOpen,
                    primary = true,
                )
            }
        }
    }
}

@Composable
private fun PosterRow(title: String, list: List<Stream>, onOpen: (Stream) -> Unit) {
    Column {
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp, bottom = 10.dp))
        androidx.compose.foundation.lazy.LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
        ) {
            items(list.size, key = { list[it].key }) { i ->
                Box(Modifier.width(150.dp)) { PosterCard(list[i]) { onOpen(list[i]) } }
            }
        }
    }
}

private fun Modifier.androidxClip(): Modifier = this.clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
