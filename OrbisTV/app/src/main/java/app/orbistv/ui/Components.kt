package app.orbistv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.orbistv.data.Category
import app.orbistv.ui.theme.OrbisColors
import coil.compose.AsyncImage
import kotlinx.coroutines.CancellationException
import android.view.KeyEvent as AndroidKeyEvent

// ---------------------------------------------------------------- Élément focusable (télécommande + tactile)

/**
 * Surface cliquable pensée pour la télécommande : grossit et s'entoure d'un liseré quand elle a le focus.
 * Appui long sur OK (ou touche Menu) = [onLongClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Focusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    selected: Boolean = false,
    shape: Shape = RoundedCornerShape(8.dp),
    focusScale: Float = 1.04f,
    onFocused: (() -> Unit)? = null,
    content: @Composable BoxScope.(focused: Boolean) -> Unit,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    if (onFocused != null) {
        LaunchedEffect(focused) { if (focused) onFocused() }
    }
    val scale by animateFloatAsState(if (focused) focusScale else 1f, label = "focusScale")
    var longFired by remember { mutableStateOf(false) }
    val bg = when {
        focused -> OrbisColors.Surface2
        selected -> OrbisColors.Selected
        else -> OrbisColors.Surface
    }
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(2.dp, if (focused) OrbisColors.Accent else Color.Transparent, shape)
            .onPreviewKeyEvent { ev ->
                val long = onLongClick ?: return@onPreviewKeyEvent false
                val k = ev.nativeKeyEvent
                val isOk = k.keyCode == AndroidKeyEvent.KEYCODE_DPAD_CENTER || k.keyCode == AndroidKeyEvent.KEYCODE_ENTER
                when {
                    k.keyCode == AndroidKeyEvent.KEYCODE_MENU && ev.type == KeyEventType.KeyUp -> { long(); true }
                    isOk && ev.type == KeyEventType.KeyDown && k.repeatCount > 0 -> {
                        if (!longFired) { longFired = true; long() }
                        true
                    }
                    isOk && ev.type == KeyEventType.KeyUp && longFired -> { longFired = false; true }
                    else -> false
                }
            }
            .combinedClickable(
                interactionSource = src,
                indication = null,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
    ) {
        content(focused)
    }
}

@Composable
fun ActionButton(
    text: String,
    icon: ImageVector?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
) {
    val src = remember { MutableInteractionSource() }
    val focused by src.collectIsFocusedAsState()
    val scale by animateFloatAsState(if (focused) 1.06f else 1f, label = "btnScale")
    val bg = when {
        focused -> Color.White
        primary -> OrbisColors.Light
        else -> OrbisColors.Surface2
    }
    val fg = if (focused || primary) OrbisColors.Ink else OrbisColors.Text
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .clickable(interactionSource = src, indication = null, onClick = onClick),
    ) {
        Row(Modifier.padding(horizontal = 28.dp, vertical = 13.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, color = fg, fontWeight = FontWeight.Medium, fontSize = 15.sp, letterSpacing = 0.2.sp)
        }
    }
}

@Composable
fun Chip(text: String, selected: Boolean, onClick: () -> Unit) {
    Focusable(onClick = onClick, selected = selected, shape = RoundedCornerShape(50), focusScale = 1.06f) { f ->
        Text(
            text,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            color = if (f || selected) OrbisColors.Text else OrbisColors.Muted,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
        )
    }
}

// ---------------------------------------------------------------- Images

@Composable
fun Thumb(
    url: String?,
    name: String,
    modifier: Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    background: Color = Color(0xFF1B1E29),
    initialsSize: TextUnit = 16.sp,
) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier.background(background), contentAlignment = Alignment.Center) {
        if (!loaded) {
            Text(
                initials(name),
                color = if (background == Color.Transparent) OrbisColors.Text.copy(alpha = 0.85f) else OrbisColors.Muted,
                fontWeight = FontWeight.SemiBold,
                fontSize = initialsSize,
                letterSpacing = 1.sp,
            )
        }
        if (!url.isNullOrBlank()) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = contentScale,
                onSuccess = { loaded = true },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private val tileTones = listOf(
    Color(0xFF14263A), Color(0xFF1D3B2A), Color(0xFF3A1D28), Color(0xFF2E3A1A),
    Color(0xFF3A311A), Color(0xFF2A1D3A), Color(0xFF1A2E3A), Color(0xFF3A241A),
)

/** Teinte sombre stable par chaîne (même nom = même couleur) pour le fond des tuiles. */
fun tileTone(name: String): Color = tileTones[(name.hashCode() and 0x7fffffff) % tileTones.size]

private fun initials(name: String): String =
    name.split(' ', '-', '|', ':').filter { it.isNotBlank() }.take(2)
        .joinToString("") { it.first().uppercase() }.ifEmpty { "?" }

// ---------------------------------------------------------------- Marque

@Composable
fun OrbisMark(size: Dp) {
    Canvas(Modifier.size(size)) {
        val stroke = this.size.minDimension * 0.11f
        val r = this.size.minDimension / 2f - stroke * 1.6f
        drawCircle(
            color = Color.White,
            radius = r,
            style = Stroke(width = stroke),
        )
        drawCircle(
            color = OrbisColors.Brand,
            radius = stroke * 0.75f,
            center = center.copy(x = center.x + r * 0.72f, y = center.y - r * 0.72f),
        )
    }
}

@Composable
fun Wordmark(fontSize: TextUnit = 18.sp, markSize: Dp = 24.dp) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        OrbisMark(markSize)
        Spacer(Modifier.width(10.dp))
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.SemiBold, color = OrbisColors.Text)) { append("ORBIS") }
            },
            fontSize = fontSize,
            letterSpacing = (fontSize.value * 0.32f).sp,
        )
    }
}

// ---------------------------------------------------------------- États

@Composable
fun ScreenTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(bottom = 14.dp)) {
        Text(title, fontSize = 28.sp, fontWeight = FontWeight.Medium, color = OrbisColors.Text, letterSpacing = (-0.3).sp)
        if (subtitle != null) Text(subtitle, fontSize = 14.sp, color = OrbisColors.Muted)
    }
}

@Composable
fun LoadingBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = OrbisColors.Accent)
            Spacer(Modifier.height(16.dp))
            Text(text, color = OrbisColors.Muted)
        }
    }
}

@Composable
fun ErrorBox(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Oups", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text(message, color = OrbisColors.Muted)
            Spacer(Modifier.height(20.dp))
            ActionButton("Réessayer", Icons.Filled.Refresh, onRetry, primary = true)
        }
    }
}

@Composable
fun EmptyBox(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = OrbisColors.Muted, fontSize = 16.sp)
    }
}

// ---------------------------------------------------------------- Chargement asynchrone

sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Error(val message: String) : Load<Nothing>
    data class Ok<T>(val value: T) : Load<T>
}

class LoadState<T>(val value: Load<T>, val retry: () -> Unit)

@Composable
fun <T> produceLoad(key: Any?, block: suspend () -> T): LoadState<T> {
    var attempt by remember { mutableIntStateOf(0) }
    var state by remember(key) { mutableStateOf<Load<T>>(Load.Loading) }
    LaunchedEffect(key, attempt) {
        state = Load.Loading
        state = try {
            Load.Ok(block())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Error(e.message ?: e.javaClass.simpleName)
        }
    }
    return LoadState(state) { attempt++ }
}

/** Incrémente à chaque retour au premier plan (ex. après le lecteur) pour rafraîchir la reprise. */
@Composable
fun rememberResumeTick(): Int {
    var tick by remember { mutableIntStateOf(0) }
    val owner = LocalLifecycleOwner.current
    DisposableEffect(owner) {
        val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) tick++ }
        owner.lifecycle.addObserver(obs)
        onDispose { owner.lifecycle.removeObserver(obs) }
    }
    return tick
}

// ---------------------------------------------------------------- Mise en page catalogue

/** Catégories à gauche sur TV / tablette, en bandeau horizontal sur téléphone. */
@Composable
fun CatalogLayout(
    categories: List<Category>,
    selected: String,
    onSelect: (String) -> Unit,
    content: @Composable (Modifier) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth < 700.dp) {
            Column(Modifier.fillMaxSize()) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                ) {
                    items(categories, key = { it.id }) { c -> Chip(c.name, c.id == selected) { onSelect(c.id) } }
                }
                Spacer(Modifier.height(8.dp))
                content(Modifier.weight(1f).fillMaxWidth())
            }
        } else {
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    modifier = Modifier.width(250.dp).fillMaxHeight(),
                    contentPadding = PaddingValues(vertical = 6.dp, horizontal = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(categories, key = { it.id }) { c ->
                        val sel = c.id == selected
                        Focusable(
                            onClick = { onSelect(c.id) },
                            selected = sel,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            focusScale = 1.02f,
                        ) { f ->
                            Text(
                                c.name,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp),
                                color = if (f || sel) OrbisColors.Text else OrbisColors.Muted,
                                fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                Spacer(Modifier.width(20.dp))
                content(Modifier.weight(1f).fillMaxHeight())
            }
        }
    }
}

fun formatTime(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

// ---------------------------------------------------------------- Profils

val profileColors = listOf(
    Color(0xFFED5C33), Color(0xFF4578F5), Color(0xFF26AD6E), Color(0xFFF2A821),
    Color(0xFF8B6CFF), Color(0xFFE64578), Color(0xFF21ADBF),
)

@Composable
fun Avatar(profile: app.orbistv.data.Profile, size: Dp = 64.dp) {
    Box(
        Modifier.size(size).clip(androidx.compose.foundation.shape.CircleShape)
            .background(profileColors[profile.color % profileColors.size]),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            profile.name.take(1).uppercase(),
            color = Color.White,
            fontWeight = FontWeight.SemiBold,
            fontSize = (size.value * 0.42f).sp,
        )
    }
}

// ---------------------------------------------------------------- Guide : « en ce moment »

/** Titre du programme en cours et sa progression, chargés à l'affichage de la chaîne. */
@Composable
fun EpgNowLine(stream: app.orbistv.data.Stream, modifier: Modifier = Modifier) {
    var now by remember(stream.key) { mutableStateOf<app.orbistv.data.EpgEntry?>(null) }
    LaunchedEffect(stream.key) {
        val list = app.orbistv.Graph.repo.epg(stream, limit = 2)
        now = list.firstOrNull { it.isNow } ?: list.firstOrNull()
    }
    val entry = now ?: return
    Column(modifier) {
        Text(entry.title, fontSize = 12.sp, color = OrbisColors.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Box(Modifier.width(90.dp).height(3.dp).clip(RoundedCornerShape(2.dp)).background(Color.White.copy(alpha = 0.2f))) {
            Box(Modifier.fillMaxHeight().width((90 * entry.progress()).dp).background(Color.White))
        }
    }
}
