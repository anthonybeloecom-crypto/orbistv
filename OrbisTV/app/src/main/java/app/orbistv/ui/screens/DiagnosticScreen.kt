package app.orbistv.ui.screens

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.orbistv.Graph
import app.orbistv.data.Kind
import app.orbistv.ui.ActionButton
import app.orbistv.ui.ScreenTitle
import app.orbistv.ui.theme.OrbisColors
import kotlinx.coroutines.launch

private enum class DiagStatus { WAITING, RUNNING, OK, WARN, FAIL }

private data class DiagStep(val title: String, val status: DiagStatus = DiagStatus.WAITING, val detail: String = "")

/** « Wi-Fi ou fournisseur ? » : teste la connexion, Internet, le serveur et le débit d'un flux. */
@Composable
fun DiagnosticScreen() {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val steps = remember {
        mutableStateListOf(
            DiagStep("Connexion de l'appareil"),
            DiagStep("Internet"),
            DiagStep("Serveur du fournisseur"),
            DiagStep("Débit du flux vidéo"),
        )
    }
    var verdict by remember { mutableStateOf<Pair<String, String>?>(null) }
    var running by remember { mutableStateOf(false) }
    var runs by remember { mutableIntStateOf(0) }

    LaunchedEffect(runs) {
        running = true
        verdict = null
        for (i in steps.indices) steps[i] = steps[i].copy(status = DiagStatus.WAITING, detail = "")
        fun set(i: Int, s: DiagStatus, d: String) { steps[i] = steps[i].copy(status = s, detail = d) }
        val repo = Graph.repo

        run {
            set(0, DiagStatus.RUNNING, "")
            val link = linkType(ctx)
            if (link == null) {
                set(0, DiagStatus.FAIL, "Aucune connexion réseau")
                verdict = "Pas de réseau" to "L'appareil n'est connecté à rien. Vérifie le Wi-Fi ou le câble de la box."
                return@run
            }
            set(0, DiagStatus.OK, link)

            set(1, DiagStatus.RUNNING, "")
            val (netMs, _) = repo.ping("https://connectivitycheck.gstatic.com/generate_204")
            if (netMs == null) {
                set(1, DiagStatus.FAIL, "Injoignable")
                verdict = "Problème de Wi-Fi ou de box" to "L'appareil est connecté au réseau mais Internet ne répond pas. Redémarre ta box ou rapproche la TV du Wi-Fi."
                return@run
            }
            set(1, if (netMs > 400) DiagStatus.WARN else DiagStatus.OK, "Réponse en $netMs ms")

            set(2, DiagStatus.RUNNING, "")
            val server = repo.serverTestUrl
            if (server == null) {
                set(2, DiagStatus.FAIL, "Aucun abonnement connecté")
                return@run
            }
            val (srvMs, code) = repo.ping(server)
            if (srvMs == null || code !in 200..299) {
                set(2, DiagStatus.FAIL, if (srvMs == null) "Injoignable" else "Refus du serveur (code $code)")
                verdict = "Problème côté fournisseur" to "Internet fonctionne, mais le serveur de ton fournisseur ne répond pas correctement. Ce n'est pas ton Wi-Fi : contacte ton fournisseur ou réessaie plus tard."
                return@run
            }
            set(2, if (srvMs > 1500) DiagStatus.WARN else DiagStatus.OK, "Réponse en $srvMs ms")

            set(3, DiagStatus.RUNNING, "Mesure sur une chaîne…")
            val first = runCatching { repo.catalog(Kind.LIVE).streams.firstOrNull() }.getOrNull()
            val mbps = first?.let { s -> runCatching { repo.playUrl(s, format = "ts") }.getOrNull()?.let { repo.throughput(it) } }
            if (mbps == null) {
                set(3, DiagStatus.FAIL, "Le flux ne démarre pas")
                verdict = "Flux refusé par le fournisseur" to "Le serveur répond mais n'envoie pas de vidéo. L'abonnement est peut-être déjà utilisé sur un autre appareil, ou la chaîne testée est hors service."
            } else {
                val txt = "%.1f Mbit/s".format(mbps)
                if (mbps < 3) {
                    set(3, DiagStatus.WARN, txt)
                    verdict = "Débit trop faible" to "Le flux arrive à $txt. Il faut environ 5 Mbit/s pour la HD. Si ton Internet est rapide, le fournisseur est saturé ; sinon branche la TV en câble ou rapproche-la du Wi-Fi."
                } else {
                    set(3, DiagStatus.OK, txt)
                    verdict = "Tout est bon" to "Ta connexion et le serveur de ton fournisseur fonctionnent. Si une chaîne coupe, c'est cette chaîne en particulier qui pose problème chez le fournisseur."
                }
            }
        }
        running = false
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(40.dp).widthIn(max = 760.dp)) {
        ScreenTitle("Wi-Fi ou fournisseur ?", "Le test télécharge quelques secondes d'une chaîne pour mesurer le débit réel.")
        steps.forEach { step ->
            Row(
                Modifier.fillMaxWidth().padding(vertical = 6.dp).clip(RoundedCornerShape(12.dp)).background(OrbisColors.Surface).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                when (step.status) {
                    DiagStatus.RUNNING -> CircularProgressIndicator(Modifier.size(22.dp), color = Color.White, strokeWidth = 2.dp)
                    DiagStatus.WAITING -> Icon(Icons.Filled.RadioButtonUnchecked, null, tint = OrbisColors.Muted, modifier = Modifier.size(22.dp))
                    DiagStatus.OK -> Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF34C759), modifier = Modifier.size(22.dp))
                    DiagStatus.WARN -> Icon(Icons.Filled.Warning, null, tint = Color(0xFFFF9F0A), modifier = Modifier.size(22.dp))
                    DiagStatus.FAIL -> Icon(Icons.Filled.Error, null, tint = OrbisColors.Error, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(step.title, fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    if (step.detail.isNotEmpty()) Text(step.detail, fontSize = 13.sp, color = OrbisColors.Muted)
                }
            }
        }
        verdict?.let { (title, text) ->
            Spacer(Modifier.height(14.dp))
            Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(OrbisColors.Surface2).padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(title, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
                Text(text, fontSize = 15.sp, color = OrbisColors.Text.copy(alpha = 0.85f))
            }
        }
        Spacer(Modifier.height(20.dp))
        ActionButton(if (running) "Test en cours…" else "Relancer le test", Icons.Filled.Refresh, {
            if (!running) scope.launch { runs++ }
        })
    }
}

private fun linkType(ctx: Context): String? {
    val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return null
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return null
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Câble Ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Données mobiles"
        else -> "Connecté"
    }
}
