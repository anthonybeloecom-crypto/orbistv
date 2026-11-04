package app.orbistv.data

import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.SecureRandom
import kotlin.concurrent.thread

/**
 * Mini-serveur web local pour configurer la TV depuis un téléphone sur le même Wi-Fi.
 * La TV affiche un QR code vers http://IP:port/?k=CODE ; le téléphone ouvre un formulaire,
 * et les identifiants arrivent directement sur la TV (ils ne passent par aucun serveur externe).
 */
class SetupServer(
    /** Appelé sur un thread réseau avec le compte saisi ; renvoie null si OK, sinon le message d'erreur. */
    private val onAccount: (Account) -> String?,
) {
    val token: String = buildString {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
        val rnd = SecureRandom()
        repeat(6) { append(chars[rnd.nextInt(chars.length)]) }
    }

    private var server: ServerSocket? = null
    @Volatile private var running = false

    val port: Int get() = server?.localPort ?: 0

    fun start() {
        val s = runCatching { ServerSocket(8765) }.getOrElse { ServerSocket(0) }
        server = s
        running = true
        thread(name = "orbis-setup", isDaemon = true) {
            while (running) {
                val client = runCatching { s.accept() }.getOrNull() ?: break
                thread(isDaemon = true) { runCatching { handle(client) }; runCatching { client.close() } }
            }
        }
    }

    fun stop() {
        running = false
        runCatching { server?.close() }
    }

    fun url(): String? = localIp()?.let { "http://$it:$port/?k=$token" }

    private fun handle(socket: Socket) {
        socket.soTimeout = 15_000
        val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.UTF_8))
        val requestLine = reader.readLine() ?: return
        val parts = requestLine.split(" ")
        if (parts.size < 2) return
        val method = parts[0]
        val path = parts[1]
        var contentLength = 0
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
            if (line.startsWith("Content-Length:", ignoreCase = true)) {
                contentLength = line.substringAfter(':').trim().toIntOrNull() ?: 0
            }
        }
        val query = parseForm(path.substringAfter('?', ""))

        if (method == "POST") {
            val body = CharArray(contentLength.coerceAtMost(16_384))
            var read = 0
            while (read < body.size) {
                val n = reader.read(body, read, body.size - read)
                if (n < 0) break
                read += n
            }
            val form = parseForm(String(body, 0, read))
            if (form["k"] != token) {
                respond(socket, page("Code expiré", "<p>Ce lien n'est plus valable. Scanne à nouveau le QR code affiché sur la TV.</p>"))
                return
            }
            val account = when (form["mode"]) {
                "m3u" -> form["url"]?.takeIf { it.isNotBlank() }?.let { Account.M3u(it.trim()) }
                else -> {
                    val srv = form["server"].orEmpty().trim()
                    if (srv.isBlank()) null else Account.Xtream(srv, form["user"].orEmpty().trim(), form["pass"].orEmpty())
                }
            }
            if (account == null) {
                respond(socket, page("Il manque une info", "<p>Remplis le serveur, l'identifiant et le mot de passe, ou un lien M3U.</p><a class=\"btn\" href=\"/?k=$token\">Revenir</a>"))
                return
            }
            val error = onAccount(account)
            if (error == null) {
                respond(socket, page("C'est prêt", "<p>Ta TV est connectée. Tu peux reposer ton téléphone.</p>"))
            } else {
                respond(socket, page("Connexion refusée", "<p>${escape(error)}</p><a class=\"btn\" href=\"/?k=$token\">Réessayer</a>"))
            }
            return
        }

        if (query["k"] != token) {
            respond(socket, page("Orbis TV", "<p>Scanne le QR code affiché sur ta TV pour ouvrir ce formulaire.</p>"))
            return
        }
        respond(socket, page("Configurer ta TV", form()))
    }

    private fun form(): String = """
        <p>Les informations sont envoyées directement à ta TV sur ton Wi-Fi.</p>
        <form method="post">
          <input type="hidden" name="k" value="$token">
          <div class="tabs">
            <label><input type="radio" name="mode" value="xtream" checked onchange="sw()"> Xtream Codes</label>
            <label><input type="radio" name="mode" value="m3u" onchange="sw()"> Lien M3U</label>
          </div>
          <div id="x">
            <label>Serveur<input name="server" placeholder="http://exemple.com:8080" autocapitalize="off" autocorrect="off" inputmode="url"></label>
            <label>Identifiant<input name="user" autocapitalize="off" autocorrect="off"></label>
            <label>Mot de passe<input name="pass" type="password"></label>
          </div>
          <div id="m" style="display:none">
            <label>Lien de la playlist<input name="url" placeholder="http://…/get.php?username=…" autocapitalize="off" autocorrect="off" inputmode="url"></label>
          </div>
          <button class="btn" type="submit">Envoyer à la TV</button>
        </form>
        <script>function sw(){var m=document.querySelector('input[name=mode]:checked').value;document.getElementById('x').style.display=m=='m3u'?'none':'block';document.getElementById('m').style.display=m=='m3u'?'block':'none';}</script>
    """.trimIndent()

    private fun page(title: String, content: String): String = """
        <!doctype html><html lang="fr"><head><meta charset="utf-8">
        <meta name="viewport" content="width=device-width,initial-scale=1">
        <title>Orbis TV</title>
        <style>
          body{margin:0;background:#000;color:#fff;font-family:-apple-system,system-ui,sans-serif;padding:28px 22px}
          .mark{display:flex;align-items:center;gap:10px;letter-spacing:.32em;font-weight:600;margin-bottom:28px}
          .ring{width:18px;height:18px;border:3px solid #fff;border-radius:50%;position:relative}
          .ring:after{content:"";position:absolute;width:7px;height:7px;background:#8B6CFF;border-radius:50%;top:-5px;right:-5px}
          h1{font-weight:500;font-size:30px;margin:0 0 10px}
          p{color:#b9bcc4;line-height:1.5}
          label{display:block;font-size:13px;color:#9a9da6;margin:14px 0 6px}
          input:not([type=radio]){display:block;width:100%;box-sizing:border-box;margin-top:6px;padding:14px;border-radius:8px;border:1px solid #2a2c33;background:#111216;color:#fff;font-size:16px}
          .tabs{display:flex;gap:16px;margin-top:18px}.tabs label{display:flex;align-items:center;gap:6px;color:#fff;font-size:15px;margin:0}
          .btn{display:block;width:100%;margin-top:24px;padding:15px;border:0;border-radius:8px;background:#f4f4f4;color:#171a20;font-size:16px;font-weight:600;text-align:center;text-decoration:none}
        </style></head><body>
        <div class="mark"><span class="ring"></span>ORBIS</div>
        <h1>${escape(title)}</h1>
        $content
        </body></html>
    """.trimIndent()

    private fun respond(socket: Socket, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val out = socket.getOutputStream()
        out.write(
            ("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=utf-8\r\nContent-Length: ${bytes.size}\r\n" +
                "Cache-Control: no-store\r\nConnection: close\r\n\r\n").toByteArray(Charsets.US_ASCII),
        )
        out.write(bytes)
        out.flush()
    }

    private fun parseForm(s: String): Map<String, String> =
        s.split('&').filter { it.contains('=') }.associate {
            val k = it.substringBefore('=')
            val v = it.substringAfter('=')
            URLDecoder.decode(k, "UTF-8") to URLDecoder.decode(v, "UTF-8")
        }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    companion object {
        fun localIp(): String? = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .firstOrNull { it.isSiteLocalAddress }
                ?.hostAddress
        }.getOrNull()
    }
}
