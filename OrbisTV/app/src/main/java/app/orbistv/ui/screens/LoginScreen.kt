package app.orbistv.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbistv.Graph
import app.orbistv.data.Account
import app.orbistv.ui.ActionButton
import app.orbistv.ui.Chip
import app.orbistv.ui.Wordmark
import app.orbistv.ui.theme.OrbisColors
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(onDone: () -> Unit, onPhoneSetup: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    var mode by rememberSaveable { mutableStateOf("xtream") }
    var server by rememberSaveable { mutableStateOf("") }
    var user by rememberSaveable { mutableStateOf("") }
    var pass by rememberSaveable { mutableStateOf("") }
    var m3u by rememberSaveable { mutableStateOf("") }
    var loading by rememberSaveable { mutableStateOf(false) }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    fun submit() {
        if (loading) return
        val account = if (mode == "xtream") Account.Xtream(server, user, pass) else Account.M3u(m3u)
        if (mode == "xtream" && server.isBlank()) { error = "Indique l'adresse du serveur"; return }
        if (mode == "m3u" && m3u.isBlank()) { error = "Colle le lien de ta playlist M3U"; return }
        loading = true
        error = null
        scope.launch {
            try {
                Graph.repo.login(account)
                onDone()
            } catch (e: Exception) {
                error = e.message ?: "Connexion impossible"
            } finally {
                loading = false
            }
        }
    }

    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(OrbisColors.Bg),
    ) {
        val wide = maxWidth >= 800.dp
        val hero: @Composable () -> Unit = {
            Column {
                Wordmark(fontSize = 22.sp, markSize = 30.dp)
                Spacer(Modifier.height(20.dp))
                Text("Orbis TV", fontSize = 64.sp, fontWeight = FontWeight.Medium, letterSpacing = (-1).sp)
                Text("Le lecteur IPTV pour ta TV.", fontSize = 22.sp, fontWeight = FontWeight.Light, color = OrbisColors.Accent2)
                Spacer(Modifier.height(12.dp))
                Text(
                    "Connecte ton abonnement Xtream Codes ou ta playlist M3U.\nOrbis TV ne fournit aucune chaîne ni aucun contenu.",
                    color = OrbisColors.Muted,
                    fontSize = 15.sp,
                )
            }
        }
        val form: @Composable () -> Unit = {
            Column(
                Modifier
                    .widthIn(max = 520.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(OrbisColors.Surface)
                    .padding(28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Chip("Xtream Codes", mode == "xtream") { mode = "xtream"; error = null }
                    Chip("Lien M3U", mode == "m3u") { mode = "m3u"; error = null }
                }
                if (mode == "xtream") {
                    Field("Serveur (ex. http://exemple.com:8080)", server, { server = it }, KeyboardType.Uri)
                    Field("Identifiant", user, { user = it })
                    Field("Mot de passe", pass, { pass = it }, password = true)
                } else {
                    Field("Lien de la playlist M3U", m3u, { m3u = it }, KeyboardType.Uri)
                }
                error?.let { Text(it, color = OrbisColors.Error, fontSize = 14.sp) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ActionButton(if (loading) "Connexion…" else "Se connecter", Icons.Filled.Login, { submit() }, primary = true)
                    if (loading) {
                        Spacer(Modifier.width(16.dp))
                        CircularProgressIndicator(Modifier.size(24.dp), color = OrbisColors.Accent, strokeWidth = 3.dp)
                    }
                }
                ActionButton("Configurer depuis mon téléphone", Icons.Filled.PhoneAndroid, onPhoneSetup)
                Text(
                    "Tes identifiants restent sur cet appareil.",
                    color = OrbisColors.Muted,
                    fontSize = 12.sp,
                )
            }
        }

        if (wide) {
            Row(
                Modifier.fillMaxSize().padding(64.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(64.dp),
            ) {
                Box(Modifier.weight(1f)) { hero() }
                Box(Modifier.weight(1f)) { form() }
            }
        } else {
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                hero()
                form()
            }
        }
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    password: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        visualTransformation = if (password) PasswordVisualTransformation() else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (password) KeyboardType.Password else keyboardType,
            imeAction = ImeAction.Next,
            autoCorrectEnabled = false,
        ),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = OrbisColors.Accent,
            unfocusedBorderColor = Color(0xFF2A2C33),
            focusedLabelColor = OrbisColors.Accent,
            cursorColor = OrbisColors.Accent,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
