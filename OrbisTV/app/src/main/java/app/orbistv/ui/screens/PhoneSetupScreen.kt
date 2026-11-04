package app.orbistv.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbistv.Graph
import app.orbistv.data.SetupServer
import app.orbistv.ui.Wordmark
import app.orbistv.ui.theme.OrbisColors
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.runBlocking

/**
 * « Configure ta TV avec ton téléphone » : QR code vers un formulaire servi par la TV elle-même.
 * Quand le téléphone envoie le compte, la TV se connecte et [onConnected] est appelé.
 */
@Composable
fun PhoneSetupScreen(onConnected: () -> Unit) {
    var status by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(false) }

    val server = remember {
        SetupServer { account ->
            status = "Connexion en cours…"
            val error = runCatching { runBlocking { Graph.repo.login(account) } }.exceptionOrNull()?.message
                ?.let { it.ifBlank { "Connexion impossible" } }
            if (error == null) {
                status = "Connecté"
                done = true
            } else {
                status = error
            }
            error
        }.also { it.start() }
    }
    DisposableEffect(Unit) {
        onDispose { server.stop() }
    }
    if (done) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onConnected() }
    }

    val url = remember { server.url() }
    val qr = remember(url) { url?.let { qrBitmap(it, 600) } }

    Box(Modifier.fillMaxSize().background(OrbisColors.Bg).padding(56.dp), contentAlignment = Alignment.Center) {
        Row(horizontalArrangement = Arrangement.spacedBy(64.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.widthIn(max = 520.dp)) {
                Wordmark()
                Spacer(Modifier.height(28.dp))
                Text("Configure ta TV\navec ton téléphone", fontSize = 40.sp, fontWeight = FontWeight.Medium, lineHeight = 46.sp)
                Spacer(Modifier.height(18.dp))
                Step("1", "Connecte ton téléphone au même Wi-Fi que la TV.")
                Step("2", "Scanne le QR code avec l'appareil photo.")
                Step("3", "Saisis ton abonnement sur le téléphone, puis Envoyer.")
                Spacer(Modifier.height(18.dp))
                if (url != null) {
                    Text("Ou ouvre : ${url.substringBefore("?")}", color = OrbisColors.Muted, fontSize = 14.sp)
                }
                status?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(it, color = if (done) Color(0xFF34C759) else OrbisColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    Modifier.size(300.dp).clip(RoundedCornerShape(20.dp)).background(Color.White).padding(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (qr != null) {
                        Image(qr.asImageBitmap(), contentDescription = "QR code de configuration", modifier = Modifier.fillMaxSize())
                    } else {
                        Text("Pas de Wi-Fi détecté", color = Color.Black)
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(server.token.chunked(3).joinToString(" "), fontSize = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 4.sp)
                Text("Code de la TV", color = OrbisColors.Muted, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun Step(n: String, text: String) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(30.dp).clip(RoundedCornerShape(15.dp)).background(OrbisColors.Surface2), contentAlignment = Alignment.Center) {
            Text(n, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.width(14.dp))
        Text(text, fontSize = 17.sp, color = OrbisColors.Text.copy(alpha = 0.9f))
    }
}

private fun qrBitmap(text: String, size: Int): Bitmap {
    val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
    val pixels = IntArray(size * size)
    for (y in 0 until size) for (x in 0 until size) {
        pixels[y * size + x] = if (matrix[x, y]) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
    }
    return Bitmap.createBitmap(pixels, size, size, Bitmap.Config.ARGB_8888)
}
