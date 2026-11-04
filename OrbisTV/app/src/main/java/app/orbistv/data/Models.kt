package app.orbistv.data

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

const val ALL = "__all__"

/**
 * Identifiant envoyé aux serveurs. Certains fournisseurs refusent les lecteurs qu'ils ne connaissent pas
 * (codes 403, 884…) : l'app essaie alors un identifiant de lecteur standard et retient celui qui marche.
 */
object Net {
    val candidates = listOf(
        "OrbisTV/1.0 (Linux; Android)",
        "VLC/3.0.20 LibVLC/3.0.20",
        "Mozilla/5.0 (Linux; Android 12; TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0 Safari/537.36",
    )

    @Volatile
    var userAgent: String = candidates[0]
}

enum class Kind { LIVE, MOVIE, SERIES }

data class Category(val id: String, val name: String)

data class Stream(
    val id: String,
    val name: String,
    val logo: String?,
    val categoryId: String,
    val kind: Kind,
    /** URL directe (playlists M3U). Null pour Xtream : construite au moment de la lecture. */
    val url: String? = null,
    val ext: String? = null,
    val rating: String? = null,
    val plot: String? = null,
    /** Date d'ajout chez le fournisseur (timestamp), pour « Ajoutés récemment ». */
    val added: Long = 0,
) {
    val key: String get() = "${kind.name}:$id"

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("logo", logo ?: "")
        put("cat", categoryId)
        put("kind", kind.name)
        put("url", url ?: "")
        put("ext", ext ?: "")
        put("rating", rating ?: "")
        put("plot", plot ?: "")
        put("added", added)
    }

    companion object {
        fun fromJson(o: JSONObject): Stream = Stream(
            id = o.getString("id"),
            name = o.optString("name"),
            logo = o.optString("logo").ifBlank { null },
            categoryId = o.optString("cat"),
            kind = Kind.valueOf(o.optString("kind", "LIVE")),
            url = o.optString("url").ifBlank { null },
            ext = o.optString("ext").ifBlank { null },
            rating = o.optString("rating").ifBlank { null },
            plot = o.optString("plot").ifBlank { null },
            added = o.optLong("added", 0),
        )
    }
}

data class Catalog(val categories: List<Category>, val streams: List<Stream>) {
    private val byCategory: Map<String, List<Stream>> by lazy { streams.groupBy { it.categoryId } }

    fun streamsIn(categoryId: String): List<Stream> =
        if (categoryId == ALL) streams else byCategory[categoryId].orEmpty()

    fun categoryName(categoryId: String): String =
        categories.firstOrNull { it.id == categoryId }?.name ?: "Toutes"

    /** Les plus récents d'abord (si le fournisseur donne la date d'ajout). */
    fun recent(limit: Int): List<Stream> {
        val dated = streams.filter { it.added > 0 }
        return if (dated.isEmpty()) streams.take(limit) else dated.sortedByDescending { it.added }.take(limit)
    }
}

data class Episode(
    val id: String,
    val title: String,
    val season: Int,
    val number: Int,
    val ext: String,
    val image: String?,
    val plot: String?,
) {
    val key: String get() = "EP:$id"
}

data class SeriesInfo(
    val plot: String?,
    val cover: String?,
    val genre: String?,
    val year: String?,
    val backdrop: String?,
    val seasons: Map<Int, List<Episode>>,
) {
    val allEpisodes: List<Episode> get() = seasons.values.flatten()
}

data class VodInfo(
    val plot: String? = null,
    val genre: String? = null,
    val duration: String? = null,
    val year: String? = null,
    val cast: String? = null,
    val backdrop: String? = null,
)

/** Un programme du guide TV (heures en millisecondes). */
data class EpgEntry(val title: String, val desc: String?, val start: Long, val end: Long) {
    val isNow: Boolean get() = System.currentTimeMillis() in start until end

    fun progress(now: Long = System.currentTimeMillis()): Float =
        if (end > start) ((now - start).toFloat() / (end - start)).coerceIn(0f, 1f) else 0f

    val timeRange: String
        get() {
            val f = SimpleDateFormat("HH:mm", Locale.FRANCE)
            return "${f.format(Date(start))} – ${f.format(Date(end))}"
        }
}

data class Profile(val id: String, val name: String, val color: Int)

sealed class Account {
    data class Xtream(val server: String, val user: String, val pass: String) : Account() {
        val base: String get() = normalizeServer(server)
    }

    data class M3u(val url: String) : Account()
}

fun normalizeServer(raw: String): String {
    var s = raw.trim().trimEnd('/')
    if (!s.startsWith("http://", ignoreCase = true) && !s.startsWith("https://", ignoreCase = true)) {
        s = "http://$s"
    }
    return s.removeSuffix("/player_api.php").removeSuffix("/get.php").trimEnd('/')
}

/**
 * Un lien M3U du type http://serveur/get.php?username=…&password=… cache un compte Xtream Codes :
 * on le convertit pour passer par l'API Xtream (plus fiable, et avec les séries et le guide).
 */
fun xtreamFromM3u(link: String): Account.Xtream? {
    val url = link.trim().toHttpUrlOrNull() ?: return null
    if (!url.encodedPath.endsWith("get.php", ignoreCase = true)) return null
    val user = url.queryParameter("username")?.takeIf { it.isNotBlank() } ?: return null
    val pass = url.queryParameter("password")?.takeIf { it.isNotBlank() } ?: return null
    val defaultPort = if (url.scheme == "https") 443 else 80
    val server = buildString {
        append(url.scheme).append("://").append(url.host)
        if (url.port != defaultPort) append(":").append(url.port)
    }
    return Account.Xtream(server, user, pass)
}

/** Un élément de la file de lecture transmise au lecteur. */
data class PlayItem(
    val key: String,
    val title: String,
    val url: String,
    val isLive: Boolean,
    val subtitle: String? = null,
    /** null = reprise automatique, 0 = depuis le début, >0 = position en ms. */
    val startAt: Long? = null,
)
