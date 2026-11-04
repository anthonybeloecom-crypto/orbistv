package app.orbistv.data

import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.text.Normalizer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * Accès aux sources de l'utilisateur : API Xtream Codes ou playlist M3U.
 * Orbis TV ne fournit aucun contenu : il lit uniquement l'abonnement saisi par l'utilisateur.
 */
class Repository(private val prefs: Prefs) {

    val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(90, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    private val mutex = Mutex()
    private val cache = HashMap<Kind, Catalog>()
    private val seriesCache = HashMap<String, SeriesInfo>()
    private val epgCache = ConcurrentHashMap<String, Pair<Long, List<EpgEntry>>>()
    private var m3uEntries: List<M3uParser.Entry>? = null
    private var searchIndex: List<Pair<String, Stream>>? = null

    val isXtream: Boolean get() = prefs.account() is Account.Xtream

    fun clearCache() {
        cache.clear()
        seriesCache.clear()
        epgCache.clear()
        m3uEntries = null
        searchIndex = null
    }

    // ---------------------------------------------------------------- Connexion

    suspend fun login(account: Account) {
        clearCache()
        when (account) {
            is Account.Xtream -> {
                if (account.server.isBlank()) throw IOException("Indique l'adresse du serveur")
                if (account.user.isBlank() || account.pass.isBlank()) throw IOException("Identifiant et mot de passe requis")
                val body = get(api(account))
                val json = try { JSONObject(body) } catch (e: JSONException) {
                    throw IOException("Ce serveur ne répond pas comme un serveur Xtream Codes")
                }
                val ui = json.optJSONObject("user_info") ?: throw IOException("Réponse inattendue du serveur")
                if (ui.optInt("auth", 0) != 1) throw IOException("Identifiants refusés par le serveur")
                val status = ui.optString("status")
                if (status.isNotBlank() && !status.equals("Active", ignoreCase = true)) {
                    throw IOException("Abonnement non actif ($status)")
                }
                prefs.saveAccount(account)
            }
            is Account.M3u -> {
                val link = account.url.trim()
                if (link.isBlank()) throw IOException("Colle le lien de ta playlist M3U")
                // Lien get.php avec identifiants : on passe par l'API Xtream, le M3U reste en secours.
                var xtreamError: Exception? = null
                xtreamFromM3u(link)?.let { x ->
                    try {
                        login(x)
                        return
                    } catch (e: Exception) {
                        xtreamError = e
                        clearCache()
                    }
                }
                val body = try { get(link) } catch (e: Exception) { throw xtreamError ?: e }
                val entries = withContext(Dispatchers.Default) { M3uParser.parse(body) }
                if (entries.isEmpty()) throw IOException("Aucune entrée trouvée dans cette playlist")
                m3uEntries = entries
                prefs.saveAccount(Account.M3u(link))
            }
        }
    }

    // ---------------------------------------------------------------- Catalogues

    suspend fun catalog(kind: Kind): Catalog {
        mutex.withLock {
            cache[kind]?.let { return it }
            val account = prefs.account() ?: throw IOException("Non connecté")
            val raw = when (account) {
                is Account.Xtream -> loadXtream(account, kind)
                is Account.M3u -> loadM3u(account, kind)
            }
            val result = withContext(Dispatchers.Default) { finalize(raw) }
            cache[kind] = result
            return result
        }
    }

    private val adultRegex = Regex("""(adult|xxx|porn|\+18|18\+|for adults)""", RegexOption.IGNORE_CASE)

    private fun finalize(c: Catalog): Catalog {
        var cats = c.categories.distinctBy { it.id }
        var streams = c.streams.distinctBy { it.key }
        if (prefs.hideAdult) {
            val adultIds = cats.filter { adultRegex.containsMatchIn(it.name) }.map { it.id }.toSet()
            cats = cats.filter { it.id !in adultIds }
            streams = streams.filter { it.categoryId !in adultIds }
        }
        return Catalog(listOf(Category(ALL, "Toutes")) + cats, streams)
    }

    private suspend fun loadXtream(a: Account.Xtream, kind: Kind): Catalog {
        val (catAction, streamAction) = when (kind) {
            Kind.LIVE -> "get_live_categories" to "get_live_streams"
            Kind.MOVIE -> "get_vod_categories" to "get_vod_streams"
            Kind.SERIES -> "get_series_categories" to "get_series"
        }
        val catBody = get(api(a, "action" to catAction))
        val streamBody = get(api(a, "action" to streamAction))

        return withContext(Dispatchers.Default) {
            val cats = ArrayList<Category>()
            parseArray(catBody).forEachObject { o ->
                val id = o.str("category_id") ?: return@forEachObject
                cats += Category(id, o.str("category_name") ?: "Sans nom")
            }
            val streams = ArrayList<Stream>()
            parseArray(streamBody).forEachObject { o ->
                val s = when (kind) {
                    Kind.LIVE -> Stream(
                        id = o.str("stream_id") ?: return@forEachObject,
                        name = o.str("name") ?: "Sans nom",
                        logo = o.str("stream_icon"),
                        categoryId = o.str("category_id").orEmpty(),
                        kind = Kind.LIVE,
                    )
                    Kind.MOVIE -> Stream(
                        id = o.str("stream_id") ?: return@forEachObject,
                        name = o.str("name") ?: "Sans nom",
                        logo = o.str("stream_icon"),
                        categoryId = o.str("category_id").orEmpty(),
                        kind = Kind.MOVIE,
                        ext = o.str("container_extension"),
                        rating = o.str("rating")?.takeIf { it != "0" },
                        added = o.str("added")?.toLongOrNull() ?: 0,
                    )
                    Kind.SERIES -> Stream(
                        id = o.str("series_id") ?: return@forEachObject,
                        name = o.str("name") ?: "Sans nom",
                        logo = o.str("cover"),
                        categoryId = o.str("category_id").orEmpty(),
                        kind = Kind.SERIES,
                        rating = o.str("rating")?.takeIf { it != "0" },
                        plot = o.str("plot"),
                        added = o.str("last_modified")?.toLongOrNull() ?: 0,
                    )
                }
                streams += s
            }
            Catalog(cats, streams)
        }
    }

    private suspend fun loadM3u(a: Account.M3u, kind: Kind): Catalog {
        val entries = m3uEntries ?: withContext(Dispatchers.Default) { M3uParser.parse(get(a.url)) }
            .also { m3uEntries = it }
        if (kind == Kind.SERIES) return Catalog(emptyList(), emptyList())

        return withContext(Dispatchers.Default) {
            val wanted = entries.filter { e ->
                val isVod = e.url.contains("/movie/") || e.url.contains("/series/")
                if (kind == Kind.MOVIE) isVod else !isVod
            }
            val cats = wanted.map { it.group }.distinct().map { Category(it, it) }
            val streams = wanted.map { e ->
                Stream(
                    id = e.url.hashCode().toUInt().toString(16) + e.url.length.toString(36),
                    name = e.name,
                    logo = e.logo,
                    categoryId = e.group,
                    kind = kind,
                    url = e.url,
                )
            }
            Catalog(cats, streams)
        }
    }

    // ---------------------------------------------------------------- Détails

    suspend fun vodInfo(id: String): VodInfo {
        val a = prefs.account() as? Account.Xtream ?: return VodInfo()
        val json = JSONObject(get(api(a, "action" to "get_vod_info", "vod_id" to id)))
        val info = json.optJSONObject("info") ?: return VodInfo()
        return VodInfo(
            plot = info.str("plot") ?: info.str("description"),
            genre = info.str("genre"),
            duration = info.str("duration"),
            year = (info.str("releasedate") ?: info.str("release_date"))?.take(4),
            cast = info.str("cast") ?: info.str("actors"),
            backdrop = info.optJSONArray("backdrop_path")?.optString(0)?.ifBlank { null },
        )
    }

    suspend fun seriesInfo(id: String): SeriesInfo {
        seriesCache[id]?.let { return it }
        val a = prefs.account() as? Account.Xtream ?: throw IOException("Séries disponibles uniquement avec Xtream Codes")
        val body = get(api(a, "action" to "get_series_info", "series_id" to id))
        val result = withContext(Dispatchers.Default) {
            val json = JSONObject(body)
            val info = json.optJSONObject("info")
            val seasons = sortedMapOf<Int, MutableList<Episode>>()

            fun addArray(arr: JSONArray, seasonHint: Int?) {
                arr.forEachObject { e ->
                    val epInfo = e.optJSONObject("info")
                    val season = e.optInt("season", seasonHint ?: 1)
                    val ep = Episode(
                        id = e.str("id") ?: return@forEachObject,
                        title = e.str("title") ?: "Épisode",
                        season = season,
                        number = e.optInt("episode_num", 0),
                        ext = e.str("container_extension") ?: "mp4",
                        image = epInfo?.str("movie_image"),
                        plot = epInfo?.str("plot"),
                    )
                    seasons.getOrPut(season) { mutableListOf() }.add(ep)
                }
            }

            when (val eps = json.opt("episodes")) {
                is JSONObject -> eps.keys().forEach { k -> eps.optJSONArray(k)?.let { addArray(it, k.toIntOrNull()) } }
                is JSONArray -> for (i in 0 until eps.length()) eps.optJSONArray(i)?.let { addArray(it, null) }
            }
            seasons.values.forEach { list -> list.sortBy { it.number } }

            SeriesInfo(
                plot = info?.str("plot"),
                cover = info?.str("cover"),
                genre = info?.str("genre"),
                year = (info?.str("releaseDate") ?: info?.str("release_date"))?.take(4),
                backdrop = info?.optJSONArray("backdrop_path")?.optString(0)?.ifBlank { null },
                seasons = seasons,
            )
        }
        seriesCache[id] = result
        return result
    }

    // ---------------------------------------------------------------- Guide TV

    /** Programmes en cours et à venir d'une chaîne (API Xtream). Vide pour les playlists M3U. */
    suspend fun epg(s: Stream, limit: Int = 6): List<EpgEntry> {
        if (s.kind != Kind.LIVE) return emptyList()
        val a = prefs.account() as? Account.Xtream ?: return emptyList()
        val now = System.currentTimeMillis()
        epgCache[s.id]?.let { (at, list) -> if (now - at < 600_000) return list.filter { it.end > now } }
        val body = try {
            get(api(a, "action" to "get_short_epg", "stream_id" to s.id, "limit" to "$limit"), fallback = false)
        } catch (e: Exception) {
            return emptyList()
        }
        val list = withContext(Dispatchers.Default) {
            val out = ArrayList<EpgEntry>()
            val arr = runCatching { JSONObject(body).optJSONArray("epg_listings") }.getOrNull() ?: JSONArray()
            arr.forEachObject { e ->
                val start = e.str("start_timestamp")?.toLongOrNull() ?: return@forEachObject
                val end = (e.str("stop_timestamp") ?: e.str("end_timestamp"))?.toLongOrNull() ?: return@forEachObject
                out += EpgEntry(
                    title = decode64(e.str("title")) ?: "Programme",
                    desc = decode64(e.str("description")),
                    start = start * 1000,
                    end = end * 1000,
                )
            }
            out.sortedBy { it.start }
        }
        epgCache[s.id] = now to list
        return list.filter { it.end > now }
    }

    private fun decode64(v: String?): String? {
        if (v == null) return null
        return runCatching { String(Base64.decode(v, Base64.DEFAULT), Charsets.UTF_8).trim() }
            .getOrNull()?.takeIf { it.isNotEmpty() } ?: v
    }

    // ---------------------------------------------------------------- URLs de lecture

    fun playUrl(s: Stream, format: String? = null): String {
        s.url?.let { return it }
        val a = prefs.account() as? Account.Xtream ?: throw IllegalStateException("Compte Xtream requis")
        return when (s.kind) {
            Kind.LIVE -> "${a.base}/live/${a.user}/${a.pass}/${s.id}.${format ?: prefs.liveFormat}"
            Kind.MOVIE -> "${a.base}/movie/${a.user}/${a.pass}/${s.id}.${s.ext ?: "mp4"}"
            Kind.SERIES -> throw IllegalArgumentException("Une série se lit épisode par épisode")
        }
    }

    fun episodeUrl(e: Episode): String {
        val a = prefs.account() as? Account.Xtream ?: throw IllegalStateException("Compte Xtream requis")
        return "${a.base}/series/${a.user}/${a.pass}/${e.id}.${e.ext}"
    }

    fun liveQueue(channels: List<Stream>): List<PlayItem> = channels.mapIndexed { i, s ->
        PlayItem(key = s.key, title = s.name, url = playUrl(s), isLive = true, subtitle = "${i + 1}")
    }

    // ---------------------------------------------------------------- Recherche

    suspend fun search(query: String): List<Stream> {
        val q = normalize(query.trim())
        if (q.length < 2) return emptyList()
        val index = searchIndex ?: buildIndex().also { searchIndex = it }
        return withContext(Dispatchers.Default) {
            index.asSequence()
                .filter { it.first.contains(q) }
                .sortedBy { if (it.first.startsWith(q)) 0 else 1 }
                .take(300)
                .map { it.second }
                .toList()
        }
    }

    private suspend fun buildIndex(): List<Pair<String, Stream>> {
        val all = ArrayList<Stream>()
        for (k in Kind.entries) runCatching { all += catalog(k).streams }
        return withContext(Dispatchers.Default) { all.map { normalize(it.name) to it } }
    }

    private val marks = Regex("\\p{Mn}+")
    private fun normalize(s: String): String =
        marks.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase()

    // ---------------------------------------------------------------- Diagnostic

    /** Temps de réponse en ms (null si injoignable) et code HTTP. */
    suspend fun ping(url: String): Pair<Long?, Int> = withContext(Dispatchers.IO) {
        val client = http.newBuilder().connectTimeout(8, TimeUnit.SECONDS).readTimeout(8, TimeUnit.SECONDS).build()
        val t0 = System.currentTimeMillis()
        try {
            client.newCall(Request.Builder().url(url).header("User-Agent", Net.userAgent).build()).execute().use { r ->
                (System.currentTimeMillis() - t0) to r.code
            }
        } catch (e: Exception) {
            null to 0
        }
    }

    val serverTestUrl: String?
        get() = when (val a = prefs.account()) {
            is Account.Xtream -> api(a)
            is Account.M3u -> a.url
            null -> null
        }

    /** Débit réel sur un flux en Mbit/s : télécharge quelques secondes puis coupe. */
    suspend fun throughput(url: String, seconds: Int = 4): Double? = withContext(Dispatchers.IO) {
        val client = http.newBuilder().readTimeout(seconds + 6L, TimeUnit.SECONDS).build()
        try {
            client.newCall(Request.Builder().url(url).header("User-Agent", Net.userAgent).build()).execute().use { r ->
                if (!r.isSuccessful) return@withContext null
                val src = r.body?.byteStream() ?: return@withContext null
                val buf = ByteArray(64 * 1024)
                var total = 0L
                val t0 = System.currentTimeMillis()
                while (System.currentTimeMillis() - t0 < seconds * 1000L) {
                    val n = src.read(buf)
                    if (n < 0) break
                    total += n
                }
                val elapsed = ((System.currentTimeMillis() - t0).coerceAtLeast(500)) / 1000.0
                if (total < 50_000) null else total * 8 / elapsed / 1_000_000
            }
        } catch (e: Exception) {
            null
        }
    }

    // ---------------------------------------------------------------- Réseau

    private fun api(a: Account.Xtream, vararg params: Pair<String, String>): String {
        val b = "${a.base}/player_api.php".toHttpUrl().newBuilder()
            .addQueryParameter("username", a.user)
            .addQueryParameter("password", a.pass)
        params.forEach { (k, v) -> b.addQueryParameter(k, v) }
        return b.build().toString()
    }

    /**
     * Télécharge une adresse. Si le serveur refuse l'identifiant de l'app (403, 884…),
     * on réessaie avec un identifiant de lecteur standard et on retient celui qui marche.
     */
    private suspend fun get(url: String, fallback: Boolean = true): String = withContext(Dispatchers.IO) {
        val agents = if (fallback) listOf(Net.userAgent) + Net.candidates.filter { it != Net.userAgent } else listOf(Net.userAgent)
        var lastCode = 0
        for (agent in agents) {
            val request = try {
                Request.Builder().url(url).header("User-Agent", agent).build()
            } catch (e: IllegalArgumentException) {
                throw IOException("Adresse invalide : $url")
            }
            try {
                http.newCall(request).execute().use { r ->
                    if (r.isSuccessful) {
                        if (agent != Net.userAgent) prefs.rememberUserAgent(agent)
                        return@withContext r.body?.string().orEmpty()
                    }
                    lastCode = r.code
                }
            } catch (e: java.net.UnknownHostException) {
                throw IOException("Serveur introuvable. Vérifie l'adresse et ta connexion.")
            } catch (e: java.net.SocketTimeoutException) {
                throw IOException("Le serveur met trop de temps à répondre.")
            } catch (e: java.net.ConnectException) {
                throw IOException("Impossible de joindre le serveur. Vérifie l'adresse et le port.")
            }
            if (lastCode == 404) break
        }
        throw IOException(httpMessage(lastCode))
    }

    private fun httpMessage(code: Int): String = when (code) {
        401, 403, 884 -> "Le serveur refuse la connexion (code $code). Vérifie que ton abonnement est actif et qu'il n'est pas déjà utilisé sur un autre appareil."
        404 -> "Adresse introuvable sur le serveur (404). Vérifie le lien."
        in 500..599 -> "Le serveur de ton fournisseur a un problème (code $code). Réessaie dans quelques minutes."
        else -> "Le serveur a répondu avec le code $code."
    }
}

// ---------------------------------------------------------------- Helpers JSON

private fun parseArray(body: String): JSONArray = try { JSONArray(body) } catch (e: JSONException) { JSONArray() }

private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
    for (i in 0 until length()) {
        val o = optJSONObject(i) ?: continue
        block(o)
    }
}

private fun JSONObject.str(key: String): String? =
    if (isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
