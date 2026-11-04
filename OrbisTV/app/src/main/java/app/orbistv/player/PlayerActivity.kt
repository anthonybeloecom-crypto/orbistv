package app.orbistv.player

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.annotation.OptIn
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import app.orbistv.Graph
import app.orbistv.Playback
import app.orbistv.data.PlayItem
import app.orbistv.data.Net
import android.widget.ImageButton
import androidx.media3.ui.AspectRatioFrameLayout

/**
 * Lecteur plein écran.
 * - Direct : Haut/Bas ou CH+/CH- pour zapper, OK pour afficher la chaîne.
 * - Films/séries : contrôleur Media3 (pause, avance, pistes audio/sous-titres), reprise auto,
 *   épisode suivant avec compte à rebours.
 */
@OptIn(UnstableApi::class)
class PlayerActivity : ComponentActivity() {

    private lateinit var player: ExoPlayer
    private lateinit var playerView: PlayerView
    private lateinit var info: TextView
    private lateinit var nextBox: TextView

    private val handler = Handler(Looper.getMainLooper())
    private val prefs get() = Graph.prefs

    private var items: List<PlayItem> = emptyList()
    private var index = 0
    private var countdown = -1
    private var retried = false
    private var resizeIndex = 0
    private val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT to "Image : Adapter",
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM to "Image : Zoom",
        AspectRatioFrameLayout.RESIZE_MODE_FILL to "Image : Étirer",
    )

    private val current: PlayItem get() = items[index]

    private val hideInfo = Runnable { info.visibility = View.GONE }

    private val saveTick = object : Runnable {
        override fun run() {
            saveProgress()
            handler.postDelayed(this, 10_000)
        }
    }

    private val countdownTick = object : Runnable {
        override fun run() {
            if (countdown <= 0) {
                playNext()
                return
            }
            nextBox.text = "Épisode suivant dans $countdown s\n${items[index + 1].title}\nOK pour lancer maintenant"
            countdown--
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        items = Playback.items
        if (items.isEmpty()) {
            finish()
            return
        }
        index = Playback.index.coerceIn(0, items.lastIndex)

        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemBars()

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            controllerShowTimeoutMs = 4000
            setShowSubtitleButton(true)
            keepScreenOn = true
        }
        root.addView(playerView, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        info = label(22f).also {
            root.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.START).apply {
                setMargins(dp(40), dp(32), dp(40), 0)
            })
        }
        nextBox = label(18f).also {
            root.addView(it, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, dp(40), dp(40))
            })
        }
        // Bouton format d'image (tactile) ; sur TV : touche Menu ou Info de la télécommande.
        val aspect = ImageButton(this).apply {
            setImageResource(android.R.drawable.ic_menu_crop)
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x99000000.toInt())
            }
            contentDescription = "Format de l'image"
            isFocusable = false
            setOnClickListener { cycleResize() }
        }
        root.addView(aspect, FrameLayout.LayoutParams(dp(44), dp(44), Gravity.TOP or Gravity.END).apply {
            setMargins(0, dp(28), dp(28), 0)
        })
        setContentView(root)

        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(Net.userAgent)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .build()
        playerView.player = player

        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) retried = false
                if (state == Player.STATE_ENDED) onEnded()
            }

            override fun onPlayerError(error: PlaybackException) {
                if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                    player.seekToDefaultPosition()
                    player.prepare()
                    return
                }
                if (!retried) {
                    retried = true
                    showInfo("Reconnexion…")
                    handler.postDelayed({ player.prepare(); player.play() }, 2000)
                    return
                }
                showInfo("Lecture impossible (${error.errorCodeName})\nEssaie le format HLS dans Réglages.", sticky = true)
            }
        })

        playCurrent()
    }

    private fun playCurrent() {
        handler.removeCallbacks(countdownTick)
        countdown = -1
        nextBox.visibility = View.GONE
        retried = false
        Playback.index = index

        val item = current
        playerView.useController = !item.isLive
        val media = MediaItem.Builder().setUri(item.url).apply {
            if (item.url.contains(".m3u8", ignoreCase = true)) setMimeType(MimeTypes.APPLICATION_M3U8)
        }.build()

        val start = if (item.isLive) 0L else item.startAt ?: prefs.resume(item.key)
        if (start > 0) player.setMediaItem(media, start) else player.setMediaItem(media)
        player.prepare()
        player.playWhenReady = true

        showInfo(listOfNotNull(item.subtitle, item.title).joinToString("  ·  "))
    }

    private fun cycleResize() {
        resizeIndex = (resizeIndex + 1) % resizeModes.size
        val (mode, label) = resizeModes[resizeIndex]
        playerView.resizeMode = mode
        showInfo(label)
    }

    private fun zap(delta: Int) {
        if (items.size < 2) return
        index = (index + delta).mod(items.size)
        playCurrent()
    }

    private fun onEnded() {
        val item = current
        if (item.isLive) {
            // Un flux direct qui "se termine" est souvent une coupure : on relance.
            player.prepare()
            return
        }
        prefs.clearProgress(item.key)
        if (index < items.lastIndex) {
            countdown = 10
            nextBox.visibility = View.VISIBLE
            handler.post(countdownTick)
        } else {
            finish()
        }
    }

    private fun playNext() {
        if (index >= items.lastIndex) return
        index++
        playCurrent()
    }

    private fun saveProgress() {
        if (!::player.isInitialized || items.isEmpty()) return
        val item = current
        if (item.isLive) return
        val d = player.duration
        val p = player.currentPosition
        if (d <= 0 || d == C.TIME_UNSET) return
        when {
            p > d * 0.95 -> prefs.clearProgress(item.key)
            p > 10_000 -> prefs.saveProgress(item.key, p, d)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN && items.isNotEmpty()) {
            val live = current.isLive
            val ok = event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER
            when {
                countdown >= 0 && ok -> { playNext(); return true }
                event.keyCode == KeyEvent.KEYCODE_MENU || event.keyCode == KeyEvent.KEYCODE_INFO -> { cycleResize(); return true }
                event.keyCode == KeyEvent.KEYCODE_CHANNEL_UP -> { zap(-1); return true }
                event.keyCode == KeyEvent.KEYCODE_CHANNEL_DOWN -> { zap(+1); return true }
                live && event.keyCode == KeyEvent.KEYCODE_DPAD_UP -> { zap(-1); return true }
                live && event.keyCode == KeyEvent.KEYCODE_DPAD_DOWN -> { zap(+1); return true }
                live && ok -> {
                    showInfo(listOfNotNull(current.subtitle, current.title).joinToString("  ·  "))
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onStart() {
        super.onStart()
        if (::player.isInitialized) {
            if (items.isNotEmpty() && current.isLive) {
                player.seekToDefaultPosition()
                player.prepare()
            }
            player.playWhenReady = true
            handler.post(saveTick)
        }
    }

    override fun onStop() {
        super.onStop()
        if (::player.isInitialized) {
            saveProgress()
            player.pause()
        }
        handler.removeCallbacks(saveTick)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        if (::player.isInitialized) player.release()
        super.onDestroy()
    }

    // ---------------------------------------------------------------- UI helpers

    private fun showInfo(text: String, sticky: Boolean = false) {
        info.text = text
        info.visibility = View.VISIBLE
        handler.removeCallbacks(hideInfo)
        if (!sticky) handler.postDelayed(hideInfo, 4000)
    }

    private fun label(size: Float) = TextView(this).apply {
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        typeface = Typeface.DEFAULT_BOLD
        setPadding(dp(20), dp(12), dp(20), dp(12))
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            setColor(0xCC0C0D12.toInt())
        }
        visibility = View.GONE
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
