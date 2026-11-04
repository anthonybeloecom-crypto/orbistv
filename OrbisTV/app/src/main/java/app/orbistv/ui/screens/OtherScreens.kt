package app.orbistv.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbistv.Graph
import app.orbistv.Playback
import app.orbistv.data.Account
import app.orbistv.data.Kind
import app.orbistv.data.Stream
import app.orbistv.ui.EmptyBox
import app.orbistv.ui.Focusable
import app.orbistv.ui.ScreenTitle
import app.orbistv.ui.Thumb
import app.orbistv.ui.theme.OrbisColors
import kotlinx.coroutines.delay

private fun kindLabel(k: Kind) = when (k) {
    Kind.LIVE -> "Chaîne"
    Kind.MOVIE -> "Film"
    Kind.SERIES -> "Série"
}

// ---------------------------------------------------------------- Favoris

@Composable
fun FavoritesScreen(onOpen: (Stream) -> Unit) {
    val prefs = Graph.prefs
    val repo = Graph.repo
    val ctx = LocalContext.current
    val favorites = prefs.favorites.toList()
    val live = favorites.filter { it.kind == Kind.LIVE }
    val vod = favorites.filter { it.kind != Kind.LIVE }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Favoris", "Appui long sur OK sur une chaîne pour la retirer")
        if (favorites.isEmpty()) {
            EmptyBox("Pas encore de favoris.\nAppui long sur OK sur une chaîne, ou bouton « Ajouter aux favoris » sur une fiche.")
            return@Column
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            contentPadding = PaddingValues(8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (live.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Chaînes") }
                items(live, key = { it.key }) { s ->
                    Focusable(
                        onClick = { Playback.start(ctx, repo.liveQueue(live), live.indexOf(s)) },
                        onLongClick = { prefs.toggleFavorite(s) },
                        focusScale = 1.07f,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column {
                            Thumb(s.logo, s.name, Modifier.fillMaxWidth().aspectRatio(16f / 10f).padding(10.dp))
                            Text(s.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                        }
                    }
                }
            }
            if (vod.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) { SectionLabel("Films et séries") }
                items(vod, key = { it.key }) { s -> PosterCard(s) { onOpen(s) } }
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = OrbisColors.Muted, modifier = Modifier.padding(top = 8.dp))
}

// ---------------------------------------------------------------- Recherche

@Composable
fun SearchScreen(onOpen: (Stream) -> Unit) {
    val repo = Graph.repo
    val ctx = LocalContext.current
    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Stream>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }

    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); return@LaunchedEffect }
        delay(350)
        searching = true
        results = runCatching { repo.search(query) }.getOrDefault(emptyList())
        searching = false
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTitle("Recherche", "Chaînes, films et séries")
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Tape au moins 2 lettres…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrbisColors.Accent,
                    unfocusedBorderColor = OrbisColors.Surface2,
                    cursorColor = OrbisColors.Accent,
                ),
                modifier = Modifier.widthIn(max = 640.dp).fillMaxWidth(),
            )
            if (searching) {
                Spacer(Modifier.width(16.dp))
                CircularProgressIndicator(Modifier.size(24.dp), color = OrbisColors.Accent, strokeWidth = 3.dp)
            }
        }
        Spacer(Modifier.height(16.dp))
        if (query.trim().length >= 2 && !searching && results.isEmpty()) {
            EmptyBox("Aucun résultat pour « $query »")
            return@Column
        }
        val liveResults = remember(results) { results.filter { it.kind == Kind.LIVE } }
        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp), contentPadding = PaddingValues(4.dp)) {
            items(results, key = { it.key }) { s ->
                Focusable(
                    onClick = {
                        if (s.kind == Kind.LIVE) Playback.start(ctx, repo.liveQueue(liveResults), liveResults.indexOf(s))
                        else onOpen(s)
                    },
                    focusScale = 1.02f,
                    modifier = Modifier.fillMaxWidth(),
                ) { focused ->
                    Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        val thumbMod = if (s.kind == Kind.LIVE) Modifier.size(width = 72.dp, height = 44.dp) else Modifier.size(width = 44.dp, height = 66.dp)
                        Thumb(
                            s.logo,
                            s.name,
                            thumbMod.clip(RoundedCornerShape(8.dp)),
                            if (s.kind == Kind.LIVE) ContentScale.Fit else ContentScale.Crop,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.name, fontSize = 16.sp, fontWeight = if (focused) FontWeight.SemiBold else FontWeight.Normal, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(kindLabel(s.kind), fontSize = 13.sp, color = OrbisColors.Accent)
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- Réglages

@Composable
fun SettingsScreen(
    onLogout: () -> Unit,
    onDiagnostic: () -> Unit = {},
    onPhoneSetup: () -> Unit = {},
    onProfiles: () -> Unit = {},
) {
    val prefs = Graph.prefs
    val repo = Graph.repo
    val account = remember { prefs.account() }
    var hideAdult by remember { mutableStateOf(prefs.hideAdult) }
    var format by remember { mutableStateOf(prefs.liveFormat) }
    var introSound by remember { mutableStateOf(prefs.introSound) }
    var refreshed by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).widthIn(max = 760.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ScreenTitle("Réglages")

        SettingRow(
            title = "Compte",
            subtitle = when (account) {
                is Account.Xtream -> "Xtream Codes · ${account.user} @ ${account.base}"
                is Account.M3u -> "Playlist M3U · ${account.url.take(60)}${if (account.url.length > 60) "…" else ""}"
                null -> "Non connecté"
            },
            icon = { Icon(Icons.Filled.Person, null, tint = OrbisColors.Accent) },
            onClick = {},
        )

        SettingRow(
            title = "Masquer les catégories adultes",
            subtitle = "Filtre les catégories dont le nom contient « adult », « xxx », « 18+ »…",
            trailing = {
                Switch(
                    checked = hideAdult,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color.Black, uncheckedTrackColor = OrbisColors.Surface2),
                )
            },
            onClick = {
                hideAdult = !hideAdult
                prefs.hideAdult = hideAdult
                repo.clearCache()
            },
        )

        SettingRow(
            title = "Profils",
            subtitle = "${prefs.profiles.size} profil(s) · actif : ${prefs.activeProfile.name}. Chacun a ses favoris et sa reprise.",
            icon = { app.orbistv.ui.Avatar(prefs.activeProfile, 26.dp) },
            onClick = onProfiles,
        )

        SettingRow(
            title = "Wi-Fi ou fournisseur ?",
            subtitle = "Teste ta connexion, Internet, le serveur et le débit d'une chaîne",
            icon = { Icon(Icons.Filled.Info, null, tint = OrbisColors.Accent) },
            onClick = onDiagnostic,
        )

        SettingRow(
            title = "Changer d'abonnement depuis le téléphone",
            subtitle = "Affiche un QR code : tu saisis ton serveur sur ton téléphone",
            icon = { Icon(Icons.Filled.Person, null, tint = OrbisColors.Accent) },
            onClick = onPhoneSetup,
        )

        SettingRow(
            title = "Son d'ouverture",
            subtitle = "Joue le son Orbis pendant l'animation au lancement de l'app",
            trailing = {
                Switch(
                    checked = introSound,
                    onCheckedChange = null,
                    colors = SwitchDefaults.colors(checkedTrackColor = Color.White, checkedThumbColor = Color.Black, uncheckedTrackColor = OrbisColors.Surface2),
                )
            },
            onClick = {
                introSound = !introSound
                prefs.introSound = introSound
            },
        )

        if (account is Account.Xtream) {
            SettingRow(
                title = "Format des chaînes en direct",
                subtitle = if (format == "ts") "MPEG-TS (recommandé, le plus compatible)" else "HLS (.m3u8) : à essayer si une chaîne ne démarre pas",
                trailing = { Text(if (format == "ts") "TS" else "HLS", fontWeight = FontWeight.Bold, color = OrbisColors.Accent) },
                onClick = {
                    format = if (format == "ts") "m3u8" else "ts"
                    prefs.liveFormat = format
                },
            )
        }

        SettingRow(
            title = "Recharger les listes",
            subtitle = if (refreshed) "Listes vidées : elles seront rechargées à la prochaine ouverture" else "Récupère les dernières chaînes, films et séries du serveur",
            icon = { Icon(Icons.Filled.Refresh, null, tint = OrbisColors.Accent) },
            onClick = { repo.clearCache(); refreshed = true },
        )

        SettingRow(
            title = "Se déconnecter",
            subtitle = "Supprime le compte, les favoris et l'historique de cet appareil (les profils restent)",
            icon = { Icon(Icons.Filled.PowerSettingsNew, null, tint = OrbisColors.Error) },
            onClick = onLogout,
        )

        Spacer(Modifier.height(10.dp))
        SettingRow(
            title = "Orbis TV 1.1",
            subtitle = "Lecteur uniquement : Orbis TV ne fournit, n'héberge et ne vend aucune chaîne ni aucun contenu. " +
                "Utilise-le avec un abonnement dont tu as légalement le droit.",
            icon = { Icon(Icons.Filled.Info, null, tint = OrbisColors.Muted) },
            onClick = {},
        )
    }
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Focusable(onClick = onClick, focusScale = 1.015f, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                icon()
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                Text(subtitle, fontSize = 13.sp, color = OrbisColors.Muted)
            }
            if (trailing != null) {
                Spacer(Modifier.width(16.dp))
                trailing()
            }
        }
    }
}
