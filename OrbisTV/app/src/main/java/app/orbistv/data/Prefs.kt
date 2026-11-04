package app.orbistv.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Stockage local : compte, profils, favoris et reprise par profil, réglages. Tout reste sur l'appareil. */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("orbis", Context.MODE_PRIVATE)

    init {
        sp.getString("user_agent", null)?.let { Net.userAgent = it }
    }

    fun rememberUserAgent(ua: String) {
        Net.userAgent = ua
        sp.edit().putString("user_agent", ua).apply()
    }

    // ---------- Compte ----------

    fun account(): Account? = when (sp.getString("type", null)) {
        "xtream" -> Account.Xtream(
            sp.getString("server", "").orEmpty(),
            sp.getString("user", "").orEmpty(),
            sp.getString("pass", "").orEmpty(),
        )
        "m3u" -> Account.M3u(sp.getString("url", "").orEmpty())
        else -> null
    }

    fun saveAccount(a: Account) {
        val e = sp.edit()
        when (a) {
            is Account.Xtream -> e.putString("type", "xtream")
                .putString("server", a.server.trim())
                .putString("user", a.user.trim())
                .putString("pass", a.pass)
            is Account.M3u -> e.putString("type", "m3u").putString("url", a.url.trim())
        }
        e.apply()
    }

    /** Déconnexion : efface le compte, les favoris et l'historique de tous les profils (les profils restent). */
    fun clearAccount() {
        val e = sp.edit()
        sp.all.keys.filter {
            it in setOf("type", "server", "user", "pass", "url") ||
                it.contains("resume_") || it.contains("dur_") || it.endsWith("favorites")
        }.forEach { e.remove(it) }
        e.apply()
        favorites.clear()
    }

    // ---------- Profils ----------

    val profiles = mutableStateListOf<Profile>().also { list ->
        runCatching {
            val arr = JSONArray(sp.getString("profiles", "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list.add(Profile(o.getString("id"), o.optString("name", "Profil"), o.optInt("color", 0)))
            }
        }
        if (list.isEmpty()) list.add(Profile("default", "Moi", 0))
    }

    var activeProfileId by mutableStateOf(
        sp.getString("active_profile", "default").let { saved -> if (profiles.any { it.id == saved }) saved!! else profiles[0].id },
    )
        private set

    /** Vrai une fois le profil choisi pour cette session (écran « Qui regarde ? »). */
    var profileChosen by mutableStateOf(profiles.size < 2)

    /** Incrémenté quand la progression change, pour rafraîchir l'affichage. */
    var progressTick by mutableIntStateOf(0)
        private set

    val activeProfile: Profile get() = profiles.firstOrNull { it.id == activeProfileId } ?: profiles[0]

    /** Le profil d'origine garde les anciennes clés ; les autres sont préfixés. */
    private fun prefixOf(id: String) = if (id == "default") "" else "p_${id}_"
    private val p: String get() = prefixOf(activeProfileId)

    fun selectProfile(id: String) {
        activeProfileId = id
        sp.edit().putString("active_profile", id).apply()
        loadFavorites()
        profileChosen = true
        progressTick++
    }

    fun addProfile(name: String, color: Int): Profile {
        val profile = Profile(UUID.randomUUID().toString().take(8), name.trim().ifEmpty { "Profil" }, color)
        profiles.add(profile)
        saveProfiles()
        return profile
    }

    fun updateProfile(id: String, name: String, color: Int) {
        val i = profiles.indexOfFirst { it.id == id }
        if (i < 0) return
        profiles[i] = profiles[i].copy(name = name.trim().ifEmpty { profiles[i].name }, color = color)
        saveProfiles()
    }

    fun deleteProfile(id: String) {
        if (profiles.size < 2) return
        profiles.removeAll { it.id == id }
        val pre = prefixOf(id)
        val e = sp.edit()
        sp.all.keys.filter {
            if (pre.isEmpty()) it == "favorites" || it.startsWith("resume_") || it.startsWith("dur_") else it.startsWith(pre)
        }.forEach { e.remove(it) }
        e.apply()
        saveProfiles()
        if (activeProfileId == id) selectProfile(profiles[0].id)
    }

    private fun saveProfiles() {
        val arr = JSONArray()
        profiles.forEach { arr.put(JSONObject().put("id", it.id).put("name", it.name).put("color", it.color)) }
        sp.edit().putString("profiles", arr.toString()).apply()
    }

    // ---------- Réglages ----------

    var hideAdult: Boolean
        get() = sp.getBoolean("hide_adult", true)
        set(v) = sp.edit().putBoolean("hide_adult", v).apply()

    var introSound: Boolean
        get() = sp.getBoolean("intro_sound", true)
        set(v) = sp.edit().putBoolean("intro_sound", v).apply()

    var liveGrid: Boolean
        get() = sp.getBoolean("live_grid", true)
        set(v) = sp.edit().putBoolean("live_grid", v).apply()

    /** "ts" ou "m3u8" pour les chaînes Xtream. */
    var liveFormat: String
        get() = sp.getString("live_format", "ts") ?: "ts"
        set(v) = sp.edit().putString("live_format", v).apply()

    // ---------- Reprise de lecture (par profil) ----------

    fun resume(key: String): Long = sp.getLong("${p}resume_$key", 0L)
    fun duration(key: String): Long = sp.getLong("${p}dur_$key", 0L)

    fun saveProgress(key: String, position: Long, duration: Long) {
        sp.edit().putLong("${p}resume_$key", position).putLong("${p}dur_$key", duration).apply()
    }

    fun clearProgress(key: String) {
        sp.edit().remove("${p}resume_$key").remove("${p}dur_$key").apply()
    }

    fun progress(key: String): Float {
        val d = duration(key)
        return if (d > 0) (resume(key).toFloat() / d).coerceIn(0f, 1f) else 0f
    }

    fun bumpProgress() { progressTick++ }

    // ---------- Favoris (par profil, observables par Compose) ----------

    val favorites = mutableStateListOf<Stream>()

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        favorites.clear()
        runCatching {
            val arr = JSONArray(sp.getString("${p}favorites", "[]"))
            for (i in 0 until arr.length()) favorites.add(Stream.fromJson(arr.getJSONObject(i)))
        }
    }

    fun isFavorite(s: Stream): Boolean = favorites.any { it.key == s.key }

    fun toggleFavorite(s: Stream) {
        val i = favorites.indexOfFirst { it.key == s.key }
        if (i >= 0) favorites.removeAt(i) else favorites.add(s)
        val arr = JSONArray()
        favorites.forEach { arr.put(it.toJson()) }
        sp.edit().putString("${p}favorites", arr.toString()).apply()
    }
}
