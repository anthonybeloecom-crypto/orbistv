package app.orbistv.ui.screens

import android.graphics.Paint
import android.graphics.Typeface
import android.media.MediaPlayer
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import app.orbistv.R
import app.orbistv.ui.theme.OrbisColors
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

private const val TOTAL = 2.6f

/**
 * Animation d'ouverture « orbite » :
 * 0,0–1,0 s  un arc lumineux tourne et se referme en anneau (souffle orbital)
 * 1,0–1,7 s  onde de lumière qui part de l'anneau (accord)
 * 0,2–1,35 s le satellite violet arrive en spirale (étincelle)
 * 1,2–1,9 s  « ORBIS » apparaît en resserrant ses lettres
 * 2,15–2,55 s fondu vers l'app
 */
@Composable
fun SplashScreen(playSound: Boolean, onFinished: () -> Unit) {
    val ctx = LocalContext.current
    val time = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (playSound) {
            runCatching {
                MediaPlayer.create(ctx, R.raw.orbis_intro)?.apply {
                    setOnCompletionListener { it.release() }
                    start()
                }
            }
        }
        time.animateTo(TOTAL, tween((TOTAL * 1000).toInt(), easing = LinearEasing))
        onFinished()
    }

    val textPaint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
    }

    Canvas(Modifier.fillMaxSize().background(Color.Black)) {
        drawOrbis(time.value, textPaint)
    }
}

private fun clamp(x: Float) = x.coerceIn(0f, 1f)
private fun easeOut(x: Float): Float = 1f - (1f - clamp(x)).pow(3)
private fun easeInOut(x: Float): Float {
    val v = clamp(x)
    return if (v < 0.5f) 4f * v * v * v else 1f - (-2f * v + 2f).pow(3) / 2f
}

private fun DrawScope.drawOrbis(t: Float, textPaint: Paint) {
    val m = size.minDimension
    val r = 0.12f * m
    val c = Offset(size.width / 2f, size.height / 2f - 0.25f * r)
    val sw = 0.18f * r
    val exit = clamp((t - 2.15f) / 0.4f)
    val ga = 1f - exit

    scale(1f + 0.06f * exit, pivot = c) {
        // Anneaux d'orbite discrets
        val oa = 0.12f * clamp((t - 0.1f) / 0.5f) * (1f - clamp((t - 1.5f) / 0.6f)) * ga
        if (oa > 0f) {
            for (k in listOf(1.9f, 2.8f)) {
                drawCircle(Color.White.copy(alpha = oa), radius = r * k, center = c, style = Stroke(width = 1.5f))
            }
        }

        // Arc lumineux qui tourne puis se referme
        val head = -90f + 540f * easeOut(t / 1.0f)
        val length = 20f + 340f * easeInOut((t - 0.1f) / 0.9f)
        val topLeft = Offset(c.x - r, c.y - r)
        val arcSize = Size(2 * r, 2 * r)
        for ((w, a) in listOf(sw * 3.2f to 0.06f, sw * 2.2f to 0.12f, sw * 1.5f to 0.22f)) {
            drawArc(Color.White.copy(alpha = a * ga), head - length, length, false, topLeft, arcSize,
                style = Stroke(width = w, cap = StrokeCap.Round))
        }
        drawArc(Color.White.copy(alpha = ga), head - length, length, false, topLeft, arcSize,
            style = Stroke(width = sw, cap = StrokeCap.Round))

        // Onde quand l'anneau se ferme
        if (t in 1.0f..1.7f) {
            val e = easeOut((t - 1.0f) / 0.7f)
            val rr = r * (1f + 1.7f * e)
            drawCircle(Color.White.copy(alpha = 0.18f * (1f - e) * ga), rr, c, style = Stroke(width = sw * 2.5f * (1f - e) + 1f))
            drawCircle(Color.White.copy(alpha = 0.55f * (1f - e) * ga), rr, c, style = Stroke(width = sw * (1f - e) + 1f))
        }

        // Satellite violet en spirale
        val p = easeOut((t - 0.2f) / 1.15f)
        val da = clamp((t - 0.2f) / 0.25f) * ga
        if (da > 0f) {
            val ang = Math.toRadians((-45.0 - 720.0 * (1f - p))).toFloat()
            val dist = 1.16f * r + (2.8f * r - 1.16f * r) * (1f - p)
            val pos = Offset(c.x + dist * cos(ang), c.y + dist * sin(ang))
            val dr = 0.2f * r
            drawCircle(
                Brush.radialGradient(listOf(OrbisColors.Brand.copy(alpha = 0.55f * da), Color.Transparent), center = pos, radius = dr * 3.2f),
                radius = dr * 3.2f, center = pos,
            )
            drawCircle(OrbisColors.Brand.copy(alpha = da), dr, pos)
        }

        // Mot ORBIS
        val ta = clamp((t - 1.2f) / 0.5f) * ga
        if (ta > 0f) {
            val spacing = 0.32f + 0.6f * (1f - easeOut((t - 1.2f) / 0.7f))
            textPaint.textSize = 0.46f * r
            textPaint.letterSpacing = spacing
            textPaint.alpha = (255 * ta).toInt()
            drawContext.canvas.nativeCanvas.drawText(
                "ORBIS",
                c.x + spacing * textPaint.textSize / 2f,
                c.y + 2.1f * r + textPaint.textSize * 0.35f,
                textPaint,
            )
        }
    }
}
