package app.orbistv.ui.screens

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.orbistv.Graph
import app.orbistv.Playback
import app.orbistv.data.ALL
import app.orbistv.data.EpgEntry
import app.orbistv.data.Kind
import app.orbistv.data.Net
import app.orbistv.data.Stream
import app.orbistv.ui.CatalogLayout
import app.orbistv.ui.Chip
import app.orbistv.ui.EmptyBox
import app.orbistv.ui.EpgNowLine
import app.orbistv.ui.ErrorBox
import app.orbistv.ui.Focusable
import app.orbistv.ui.Load
import app.orbistv.ui.LoadingBox
import app.orbistv.ui.ScreenTitle
import app.orbistv.ui.Thumb
import app.orbistv.ui.produceLoad
import app.orbistv.ui.theme.OrbisColors
import app.orbistv.ui.tileTone
import kotlinx.coroutines.delay

@Composable
fun LiveScreen() {
    val repo = Graph.repo
    val prefs = Graph.prefs
    val ctx = LocalContext.current
    val load = produceLoad(Kind.LIVE) { repo.catalog(Kind.LIVE) }

    when (val s = load.value) {
        Load.Loading -> LoadingBox("Chargement des chaînes…")
        is Load.Error -> ErrorBox(s.message, load.retry)
        is Load.Ok -> {
            val catalog = s.value
            var selected by rememberSaveable { mutableStateOf(ALL) }
            var grid by rememberSaveable { mutableStateOf(prefs.liveGrid) }
            val channels = remember(catalog, selected) { catalog.streamsIn(selected) }
            val listState = rememberLazyListState()
            val gridState = rememberLazyGridState()
            LaunchedEffect(selected) {
                listState.scrollToItem(0)
                gridState.scrollToItem(0)
            }

            // Aperçu de la chaîne sélectionnée (mode liste, grand écran)
            val preview = remember { PreviewPlayer(ctx) }
            var focusedChannel by remember { mutableStateOf<Stream?>(null) }
            DisposableEffect(Unit) { onDispose { preview.release() } }
            val owner = LocalLifecycleOwner.current
            DisposableEffect(owner) {
                val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_PAUSE) preview.stop() }
                owner.lifecycle.addObserver(obs)
                onDispose { owner.lifecycle.removeObserver(obs) }
            }
            LaunchedEffect(focusedChannel, grid) {
                val c = focusedChannel ?: return@LaunchedEffect
                if (grid) { preview.stop(); return@LaunchedEffect }
                delay(700)
                runCatching { preview.play(repo.playUrl(c), c.key) }
            }

            fun play(i: Int) {
                preview.stop()
                Playback.start(ctx, repo.liveQueue(channels), i)
            }

            CatalogLayout(catalog.categories, selected, { selected = it }) { mod ->
                Column(mod) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(Modifier.weight(1f)) {
                            ScreenTitle(
                                catalog.categoryName(selected),
                                "${channels.size} chaînes  ·  appui long sur OK pour ajouter aux favoris",
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Chip("Mosaïque", grid) { grid = true; prefs.liveGrid = true }
                            Chip("Liste", !grid) { grid = false; prefs.liveGrid = false }
                        }
                    }
                    when {
                        channels.isEmpty() -> EmptyBox("Aucune chaîne dans cette catégorie")
                        grid -> LazyVerticalGrid(
                            state = gridState,
                            columns = GridCells.Adaptive(168.dp),
                            contentPadding = PaddingValues(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            itemsIndexed(channels, key = { _, c -> c.key }) { i, c ->
                                ChannelTile(
                                    number = i + 1,
                                    channel = c,
                                    favorite = prefs.isFavorite(c),
                                    onClick = { play(i) },
                                    onLongClick = { prefs.toggleFavorite(c) },
                                )
                            }
                        }
                        else -> BoxWithConstraints(Modifier.fillMaxSize()) {
                            val wide = maxWidth >= 860.dp
                            Row(Modifier.fillMaxSize()) {
                                LazyColumn(
                                    state = listState,
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    contentPadding = PaddingValues(4.dp),
                                    modifier = Modifier.weight(if (wide) 0.56f else 1f).fillMaxHeight(),
                                ) {
                                    itemsIndexed(channels, key = { _, c -> c.key }) { i, c ->
                                        ChannelRow(
                                            number = i + 1,
                                            channel = c,
                                            favorite = prefs.isFavorite(c),
                                            onClick = { play(i) },
                                            onLongClick = { prefs.toggleFavorite(c) },
                                            onFocused = { focusedChannel = c },
                                        )
                                    }
                                }
                                if (wide) {
                                    Spacer(Modifier.width(20.dp))
                                    PreviewPanel(preview, focusedChannel ?: channels.firstOrNull(), Modifier.weight(0.44f))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Lecteur discret pour l'aperçu des chaînes (un seul flux à la fois, arrêté avant la lecture plein écran). */
@OptIn(UnstableApi::class)
class PreviewPlayer(context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(context).setDataSourceFactory(
                DefaultHttpDataSource.Factory().setUserAgent(Net.userAgent).setAllowCrossProtocolRedirects(true),
            ),
        )
        .build()
        .apply { volume = 0.7f; playWhenReady = true }

    private var currentKey: String? = null

    fun play(url: String, key: String) {
        if (key == currentKey) return
        currentKey = key
        val item = MediaItem.Builder().setUri(url).apply {
            if (url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()
        player.setMediaItem(item)
        player.prepare()
    }

    fun stop() {
        currentKey = null
        player.stop()
    }

    fun release() = player.release()
}

@OptIn(UnstableApi::class)
@Composable
private fun PreviewPanel(preview: PreviewPlayer, channel: Stream?, modifier: Modifier) {
    var entries by remember(channel?.key) { mutableStateOf<List<EpgEntry>>(emptyList()) }
    LaunchedEffect(channel?.key) {
        entries = channel?.let { Graph.repo.epg(it, limit = 3) } ?: emptyList()
    }
    Column(modifier.fillMaxHeight().padding(top = 4.dp)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(Color.Black),
        ) {
            AndroidView(
                factory = { c ->
                    PlayerView(c).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        player = preview.player
                    }
                },
                update = { it.player = preview.player },
                modifier = Modifier.fillMaxSize(),
            )
            if (channel != null) {
                Text(
                    channel.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.TopStart).padding(10.dp)
                        .clip(RoundedCornerShape(6.dp)).background(Color(0xAA000000)).padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        if (channel != null) {
            Text(channel.name, fontSize = 24.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(10.dp))
            if (entries.isEmpty()) {
                Text("Programme indisponible", color = OrbisColors.Muted, fontSize = 14.sp)
            }
            entries.forEachIndexed { i, e ->
                Column(Modifier.padding(vertical = 6.dp)) {
                    Text(
                        (if (e.isNow) "EN CE MOMENT  ·  " else if (i == 1) "ENSUITE  ·  " else "") + e.timeRange,
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        color = OrbisColors.Muted,
                    )
                    Text(e.title, fontSize = 17.sp, fontWeight = if (e.isNow) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (e.isNow) {
                        Spacer(Modifier.height(6.dp))
                        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(e.progress()).background(Color.White))
                        }
                        e.desc?.let {
                            Spacer(Modifier.height(8.dp))
                            Text(it, fontSize = 13.sp, color = OrbisColors.Muted, maxLines = 3, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

/** Tuile de chaîne : logo en grand sur une teinte propre à la chaîne. */
@Composable
fun ChannelTile(
    number: Int,
    channel: Stream,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val tone = tileTone(channel.name)
    Focusable(
        onClick = onClick,
        onLongClick = onLongClick,
        focusScale = 1.07f,
        modifier = Modifier.fillMaxWidth(),
    ) { focused ->
        Column {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(Brush.radialGradient(listOf(tone.lighter(), tone), radius = 420f)),
                contentAlignment = Alignment.Center,
            ) {
                Thumb(
                    channel.logo,
                    channel.name,
                    Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 16.dp),
                    ContentScale.Fit,
                    background = Color.Transparent,
                    initialsSize = 26.sp,
                )
                Text(
                    "$number",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
                if (favorite) {
                    Icon(
                        Icons.Filled.Star,
                        contentDescription = "Favori",
                        tint = OrbisColors.Gold,
                        modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(14.dp),
                    )
                }
            }
            Column(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                Text(
                    channel.name,
                    fontSize = 14.sp,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                EpgNowLine(channel, Modifier.padding(top = 4.dp))
            }
        }
    }
}

private fun Color.lighter(): Color =
    Color(red = (red + 0.10f).coerceAtMost(1f), green = (green + 0.10f).coerceAtMost(1f), blue = (blue + 0.12f).coerceAtMost(1f))

@Composable
fun ChannelRow(
    number: Int,
    channel: Stream,
    favorite: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onFocused: (() -> Unit)? = null,
) {
    Focusable(
        onClick = onClick,
        onLongClick = onLongClick,
        focusScale = 1.02f,
        onFocused = onFocused,
        modifier = Modifier.fillMaxWidth().height(72.dp),
    ) { focused ->
        Row(
            Modifier.fillMaxSize().padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$number",
                color = if (focused) OrbisColors.Accent else OrbisColors.Muted,
                fontSize = 14.sp,
                modifier = Modifier.width(48.dp),
            )
            Thumb(
                channel.logo,
                channel.name,
                Modifier.size(width = 72.dp, height = 44.dp).clip(RoundedCornerShape(8.dp)),
                background = tileTone(channel.name),
                initialsSize = 13.sp,
            )
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    channel.name,
                    fontSize = 17.sp,
                    fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                EpgNowLine(channel, Modifier.padding(top = 3.dp))
            }
            if (favorite) {
                Icon(Icons.Filled.Star, contentDescription = "Favori", tint = OrbisColors.Gold, modifier = Modifier.size(20.dp))
            }
        }
    }
}
