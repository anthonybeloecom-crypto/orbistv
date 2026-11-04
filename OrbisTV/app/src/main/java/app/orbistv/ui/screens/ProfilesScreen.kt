package app.orbistv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.orbistv.Graph
import app.orbistv.data.Profile
import app.orbistv.ui.ActionButton
import app.orbistv.ui.Avatar
import app.orbistv.ui.Focusable
import app.orbistv.ui.profileColors
import app.orbistv.ui.theme.OrbisColors
import kotlinx.coroutines.delay

/** Écran « Qui regarde ? » : chaque profil a ses favoris et sa reprise de lecture. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProfilePickerScreen(onChosen: () -> Unit) {
    val prefs = Graph.prefs
    var editing by remember { mutableStateOf(false) }
    var editor by remember { mutableStateOf<Profile?>(null) }
    var creating by remember { mutableStateOf(false) }
    val first = remember { FocusRequester() }
    LaunchedEffect(Unit) { delay(150); runCatching { first.requestFocus() } }

    Column(
        Modifier.fillMaxSize().background(OrbisColors.Bg).padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("Qui regarde ?", fontSize = 34.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(40.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(28.dp),
            modifier = Modifier.widthIn(max = 900.dp),
        ) {
            prefs.profiles.forEachIndexed { i, p ->
                ProfileBubble(
                    profile = p,
                    editing = editing,
                    modifier = if (i == 0) Modifier.focusRequester(first) else Modifier,
                ) {
                    if (editing) editor = p else { prefs.selectProfile(p.id); onChosen() }
                }
            }
            Focusable(onClick = { creating = true }, shape = RoundedCornerShape(20.dp), focusScale = 1.08f) {
                Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier.size(96.dp).clip(CircleShape).border(2.dp, OrbisColors.Surface2, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, tint = OrbisColors.Muted, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("Ajouter", color = OrbisColors.Muted)
                }
            }
        }
        Spacer(Modifier.height(40.dp))
        ActionButton(if (editing) "Terminé" else "Modifier les profils", Icons.Filled.Edit, { editing = !editing })
    }

    editor?.let { p -> ProfileEditorDialog(existing = p, onDismiss = { editor = null }) }
    if (creating) {
        ProfileEditorDialog(existing = null, onDismiss = { creating = false }) { created ->
            prefs.selectProfile(created.id)
            onChosen()
        }
    }
}

@Composable
private fun ProfileBubble(profile: Profile, editing: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Focusable(onClick = onClick, modifier = modifier, shape = RoundedCornerShape(20.dp), focusScale = 1.08f) {
        Column(Modifier.padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box {
                Avatar(profile, 96.dp)
                if (editing) {
                    Box(
                        Modifier.align(Alignment.TopEnd).size(28.dp).clip(CircleShape).background(Color.White),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.Edit, contentDescription = "Modifier", tint = Color.Black, modifier = Modifier.size(16.dp))
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            Text(profile.name, fontSize = 16.sp, maxLines = 1)
        }
    }
}

/** Création ou modification d'un profil (prénom + couleur). */
@Composable
fun ProfileEditorDialog(existing: Profile?, onDismiss: () -> Unit, onCreated: (Profile) -> Unit = {}) {
    val prefs = Graph.prefs
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var color by remember { mutableIntStateOf(existing?.color ?: (prefs.profiles.size % profileColors.size)) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier.clip(RoundedCornerShape(20.dp)).background(OrbisColors.Surface).padding(28.dp).widthIn(min = 320.dp, max = 460.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(if (existing == null) "Nouveau profil" else "Modifier le profil", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Avatar(Profile("preview", name.ifBlank { "?" }, color), 84.dp)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Prénom") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color(0xFF2A2C33),
                    focusedLabelColor = Color.White,
                    cursorColor = Color.White,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                profileColors.forEachIndexed { i, c ->
                    Focusable(onClick = { color = i }, shape = CircleShape, focusScale = 1.15f) {
                        Box(
                            Modifier.size(34.dp).clip(CircleShape).background(c)
                                .border(3.dp, if (color == i) Color.White else Color.Transparent, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (color == i) Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(if (existing == null) "Créer" else "Enregistrer", Icons.Filled.Check, {
                    if (existing == null) {
                        val created = prefs.addProfile(name, color)
                        onDismiss()
                        onCreated(created)
                    } else {
                        prefs.updateProfile(existing.id, name, color)
                        onDismiss()
                    }
                }, primary = true)
                if (existing != null && prefs.profiles.size > 1) {
                    ActionButton("Supprimer", Icons.Filled.Delete, {
                        prefs.deleteProfile(existing.id)
                        onDismiss()
                    })
                }
            }
            Spacer(Modifier.width(1.dp))
        }
    }
}
