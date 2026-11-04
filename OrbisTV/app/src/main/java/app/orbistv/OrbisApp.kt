package app.orbistv

import android.app.Application
import android.content.Context
import android.content.Intent
import app.orbistv.data.PlayItem
import app.orbistv.data.Prefs
import app.orbistv.data.Repository
import app.orbistv.player.PlayerActivity

class OrbisApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Graph.init(this)
    }
}

/** Petit conteneur de dépendances. */
object Graph {
    lateinit var prefs: Prefs
        private set
    lateinit var repo: Repository
        private set

    fun init(context: Context) {
        prefs = Prefs(context.applicationContext)
        repo = Repository(prefs)
    }
}

/** File de lecture partagée avec le lecteur (évite de faire transiter des milliers d'URLs par Intent). */
object Playback {
    var items: List<PlayItem> = emptyList()
    var index: Int = 0

    fun start(context: Context, items: List<PlayItem>, index: Int) {
        if (items.isEmpty()) return
        this.items = items
        this.index = index.coerceIn(0, items.lastIndex)
        context.startActivity(Intent(context, PlayerActivity::class.java))
    }
}
