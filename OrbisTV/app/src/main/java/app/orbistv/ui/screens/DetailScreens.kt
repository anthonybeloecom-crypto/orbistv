package app.orbistv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbistv.Graph
import app.orbistv.Playback
import app.orbistv.data.Episode
import app.orbistv.data.PlayItem
import app.orbistv.data.Stream
import app.orbistv.data.VodInfo
import app.orbistv.ui.ActionButton
import app.orbistv.ui.Chip
import app.orbistv.ui.EmptyBox
import app.orbistv.ui.ErrorBox
import app.orbistv.ui.Focusable
import app.orbistv.ui.Load
import app.orbistv.ui.LoadingBox
import app.orbistv.ui.Thumb
import app.orbistv.ui.formatTime
import app.orbistv.ui.produceLoad
import app.orbistv.ui.rememberResumeTick
import app.orbistv.ui.theme.OrbisColors
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// ---------------------------------------------------------------- Film

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MovieDetailScreen(stream: Stream) {
    val repo = Graph.repo
    val prefs = Graph.prefs
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val load = produceLoad(stream.id) { if (stream.url == null) repo.vodInfo(stream.id) else VodInfo() }
    val vi = (load.value as? Load.Ok<VodInfo>)?.value
    val resume = remember(tick) { prefs.resume(stream.key) }
    val progress = remember(tick) { prefs.progress(stream.key) }
    val favorite = prefs.isFavorite(stream)
    val playFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(120); runCatching { playFocus.requestFocus() } }

    fun play(start: Long) {
        Playback.start(ctx, listOf(PlayItem(stream.key, stream.name, repo.playUrl(stream), isLive = false, startAt = start)), 0)
    }

    Backdrop(vi?.backdrop ?: stream.logo) {
        AdaptiveDetail(
            poster = { mod -> Thumb(stream.logo, stream.name, mod, ContentScale.Crop) },
        ) {
            Text(stream.name, fontSize = 44.sp, fontWeight = FontWeight.Medium, lineHeight = 50.sp, letterSpacing = (-0.5).sp)
            val meta = listOfNotNull(vi?.year, vi?.genre, vi?.duration, stream.rating?.let { "★ $it" })
            if (meta.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(meta.joinToString("   ·   "), color = OrbisColors.Muted, fontSize = 15.sp)
            }
            Spacer(Modifier.height(16.dp))
            Text(
                vi?.plot ?: stream.plot ?: if (load.value is Load.Loading) "…" else "",
                color = OrbisColors.Text.copy(alpha = 0.85f),
                fontSize = 16.sp,
                lineHeight = 23.sp,
                maxLines = 6,
                overflow = TextOverflow.Ellipsis,
            )
            if (progress > 0f) {
                Spacer(Modifier.height(18.dp))
                LinearProgressIndicator(
                    progress = { progress },
                    color = OrbisColors.Accent,
                    trackColor = OrbisColors.Surface2,
                    modifier = Modifier.width(320.dp).height(4.dp),
                )
            }
            Spacer(Modifier.height(24.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (resume > 0) {
                    ActionButton("Reprendre · ${formatTime(resume)}", Icons.Filled.PlayArrow, { play(resume) }, Modifier.focusRequester(playFocus), primary = true)
                    ActionButton("Depuis le début", Icons.Filled.Replay, { play(0) })
                } else {
                    ActionButton("Lecture", Icons.Filled.PlayArrow, { play(0) }, Modifier.focusRequester(playFocus), primary = true)
                }
                ActionButton(
                    if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
                    if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    { prefs.toggleFavorite(stream) },
                )
            }
            vi?.cast?.let {
                Spacer(Modifier.height(20.dp))
                Text("Avec : $it", color = OrbisColors.Muted, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

// ---------------------------------------------------------------- Série

@Composable
fun SeriesDetailScreen(stream: Stream) {
    val repo = Graph.repo
    val prefs = Graph.prefs
    val ctx = LocalContext.current
    val tick = rememberResumeTick()
    val load = produceLoad(stream.id) { repo.seriesInfo(stream.id) }
    val favorite = prefs.isFavorite(stream)

    Backdrop(stream.logo) {
        when (val s = load.value) {
            Load.Loading -> LoadingBox("Chargement des épisodes…")
            is Load.Error -> ErrorBox(s.message, load.retry)
            is Load.Ok -> {
                val info = s.value
                val seasons = info.seasons.keys.toList()
                var season by rememberSaveable { mutableIntStateOf(seasons.firstOrNull() ?: 1) }
                val episodes = info.seasons[season].orEmpty()
                val all = remember(info) { info.allEpisodes }
                val firstEp = remember { FocusRequester() }
                LaunchedEffect(Unit) { delay(150); runCatching { firstEp.requestFocus() } }

                fun play(ep: Episode) {
                    val queue = all.map {
                        PlayItem(
                            key = it.key,
                            title = "${stream.name} · S${it.season}E${it.number} · ${it.title}",
                            url = repo.episodeUrl(it),
                            isLive = false,
                        )
                    }
                    Playback.start(ctx, queue, all.indexOf(ep))
                }

                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= 800.dp
                    val header: @Composable () -> Unit = {
                        Column {
                            Text(stream.name, fontSize = if (wide) 32.sp else 24.sp, fontWeight = FontWeight.Bold)
                            val meta = listOfNotNull(info.year, info.genre, stream.rating?.let { "★ $it" })
                            if (meta.isNotEmpty()) Text(meta.joinToString("   ·   "), color = OrbisColors.Muted, fontSize = 14.sp)
                            Spacer(Modifier.height(12.dp))
                            Text(
                                info.plot ?: stream.plot ?: "",
                                color = OrbisColors.Text.copy(alpha = 0.85f),
                                fontSize = 15.sp,
                                lineHeight = 21.sp,
                                maxLines = if (wide) 8 else 3,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.height(16.dp))
                            ActionButton(
                                if (favorite) "Retirer des favoris" else "Ajouter aux favoris",
                                if (favorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                                { prefs.toggleFavorite(stream) },
                            )
                        }
                    }
                    val list: @Composable (Modifier) -> Unit = { mod ->
                        Column(mod) {
                            if (seasons.size > 1) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                                    items(seasons) { n -> Chip("Saison $n", n == season) { season = n } }
                                }
                                Spacer(Modifier.height(10.dp))
                            }
                            if (episodes.isEmpty()) {
                                EmptyBox("Aucun épisode disponible")
                            } else {
                                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(4.dp)) {
                                    items(episodes, key = { it.key }) { ep ->
                                        EpisodeRow(
                                            ep = ep,
                                            fallbackImage = stream.logo,
                                            progress = remember(tick, ep.key) { prefs.progress(ep.key) },
                                            onClick = { play(ep) },
                                            modifier = if (ep == episodes.first()) Modifier.focusRequester(firstEp) else Modifier,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (wide) {
                        Row(Modifier.fillMaxSize().padding(40.dp)) {
                            Column(Modifier.width(380.dp).fillMaxHeight().verticalScroll(rememberScrollState())) {
                                Thumb(stream.logo, stream.name, Modifier.size(width = 200.dp, height = 300.dp).clip(RoundedCornerShape(16.dp)), ContentScale.Crop)
                                Spacer(Modifier.height(20.dp))
                                header()
                            }
                            Spacer(Modifier.width(36.dp))
                            list(Modifier.weight(1f).fillMaxHeight())
                        }
                    } else {
                        Column(Modifier.fillMaxSize().padding(16.dp)) {
                            header()
                            Spacer(Modifier.height(16.dp))
                            list(Modifier.weight(1f).fillMaxWidth())
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(ep: Episode, fallbackImage: String?, progress: Float, onClick: () -> Unit, modifier: Modifier) {
    Focusable(onClick = onClick, focusScale = 1.02f, modifier = modifier.fillMaxWidth()) { focused ->
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                Thumb(ep.image ?: fallbackImage, ep.title, Modifier.size(width = 144.dp, height = 81.dp).clip(RoundedCornerShape(8.dp)), ContentScale.Crop)
                if (progress > 0f) {
                    LinearProgressIndicator(
                        progress = { progress },
                        color = OrbisColors.Accent,
                        trackColor = Color.Black.copy(alpha = 0.5f),
                        modifier = Modifier.size(width = 144.dp, height = 3.dp).align(Alignment.BottomCenter),
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text("Épisode ${ep.number}", color = OrbisColors.Accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    ep.title,
                    fontSize = 16.sp,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                ep.plot?.let {
                    Text(it, color = OrbisColors.Muted, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Mise en page commune

@Composable
private fun Backdrop(image: String?, content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize()) {
        if (!image.isNullOrBlank()) {
            AsyncImage(
                model = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.22f),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(listOf(OrbisColors.Bg, OrbisColors.Bg.copy(alpha = 0.75f), Color.Transparent)),
            ),
        )
        content()
    }
}

@Composable
private fun AdaptiveDetail(
    poster: @Composable (Modifier) -> Unit,
    body: @Composable () -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 800.dp) {
            Row(Modifier.fillMaxSize().padding(56.dp), verticalAlignment = Alignment.CenterVertically) {
                poster(Modifier.size(width = 240.dp, height = 360.dp).clip(RoundedCornerShape(16.dp)))
                Spacer(Modifier.width(44.dp))
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) { body() }
            }
        } else {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                poster(Modifier.size(width = 160.dp, height = 240.dp).clip(RoundedCornerShape(14.dp)))
                Spacer(Modifier.height(20.dp))
                body()
            }
        }
    }
}
