package com.faresite.launcher

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CutCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.sin
import kotlin.random.Random

/* ---------- Paleta 70 / 20 / 10 ---------- */

data class Palette(val name: String, val amb: Color, val pri: Color, val sec: Color)

val StandardPalettes = listOf(
    Palette("Fare", Color(0xFF050A06), Color(0xFF4BE36A), Color(0xFF1F6B2A)),
    Palette("Oscuro", Color(0xFF000000), Color(0xFFFFFFFF), Color(0xFF8A8A8A)),
    Palette("Claro", Color(0xFFFFFFFF), Color(0xFF000000), Color(0xFF8A8A8A)),
)

val LocalPal = compositionLocalOf { StandardPalettes[0] }

fun Color.hex(): String = String.format("#%06X", toArgb() and 0xFFFFFF)

fun parseHex(s: String): Color? {
    val t = s.trim().removePrefix("#")
    if (t.length != 6) return null
    return t.toLongOrNull(16)?.let { Color(0xFF000000 or it) }
}

/* ---------- Vidrio de esquinas cortadas ---------- */

fun cutShape(cut: Dp): Shape = CutCornerShape(topStart = cut, topEnd = 0.dp, bottomEnd = cut, bottomStart = 0.dp)

/** Vidrio claro: casi sin tinte, borde fino con brillo, esquinas cortadas. */
fun Modifier.glass(pal: Palette, shape: Shape, strong: Boolean = false, tint: Color? = null): Modifier = this
    .clip(shape)
    .background(
        Brush.linearGradient(
            if (tint == null) listOf(
                pal.pri.copy(alpha = if (strong) .16f else .09f),
                pal.pri.copy(alpha = .02f),
                pal.sec.copy(alpha = if (strong) .14f else .07f),
            ) else listOf(tint.copy(alpha = .42f), tint.copy(alpha = .10f), tint.copy(alpha = .28f))
        )
    )
    .border(
        if (tint == null) 1.dp else 1.5.dp,
        Brush.linearGradient(
            if (tint == null) listOf(pal.pri.copy(alpha = .60f), pal.pri.copy(alpha = .06f), pal.pri.copy(alpha = .40f))
            else listOf(Color.White.copy(alpha = .75f), tint.copy(alpha = .25f), tint.copy(alpha = .85f))
        ),
        shape
    )

/* ---------- Tilt 3D + glare + sway (la animación de Audio Fare) ---------- */

private class SwayKey(val rx: Float, val ry: Float, val gx: Float, val gy: Float)

// Mismos puntos que @keyframes sway de Audio Fare (8 s, ease-in-out).
private val SwayKeys = listOf(
    SwayKey(9f, -14f, .20f, .15f),
    SwayKey(-6f, -6f, .35f, .80f),
    SwayKey(-10f, 15f, .85f, .85f),
    SwayKey(7f, 8f, .75f, .20f),
    SwayKey(9f, -14f, .20f, .15f),
)

private fun swayAt(t: Float): SwayKey {
    val seg = (t * 4f).coerceIn(0f, 3.9999f)
    val i = seg.toInt()
    val f = seg - i
    val e = (1 - kotlin.math.cos(f * PI.toFloat())) / 2f
    val a = SwayKeys[i]
    val b = SwayKeys[i + 1]
    fun l(x: Float, y: Float) = x + (y - x) * e
    return SwayKey(l(a.rx, b.rx), l(a.ry, b.ry), l(a.gx, b.gx), l(a.gy, b.gy))
}

/**
 * Tarjeta con tilt 3D al tocarla, glare que sigue el dedo y, si [idle] es true,
 * el balanceo solo ("sway") cuando nadie la toca.
 */
@Composable
fun TiltCard(
    modifier: Modifier,
    shape: Shape,
    maxDeg: Float = 24f,
    idle: Boolean = false,
    onClick: () -> Unit = {},
    onLongClick: (() -> Unit)? = null,
    longDrag: LongDrag? = null,
    tint: Color? = null,
    liquid: Boolean = false,
    content: @Composable BoxScope.() -> Unit,
) {
    val pal = LocalPal.current
    val click by rememberUpdatedState(onClick)
    val long by rememberUpdatedState(onLongClick)
    val drag by rememberUpdatedState(longDrag)
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    var touching by remember { mutableStateOf(false) }
    var tx by remember { mutableFloatStateOf(.5f) }
    var ty by remember { mutableFloatStateOf(.5f) }
    val blend by animateFloatAsState(if (touching) 1f else 0f, spring(stiffness = 500f), label = "blend")
    val phase = if (idle) {
        val inf = rememberInfiniteTransition(label = "sway")
        inf.animateFloat(0f, 1f, infiniteRepeatable(tween(8000, easing = LinearEasing)), label = "p").value
    } else 0f
    val s = if (idle) swayAt(phase) else SwayKey(0f, 0f, .5f, .5f)

    val touchRx = (.5f - ty) * maxDeg
    val touchRy = -(tx - .5f) * maxDeg * 1.15f
    val rx = s.rx * (1 - blend) + touchRx * blend
    val ry = -s.ry * (1 - blend) + touchRy * blend
    val gx = s.gx * (1 - blend) + tx * blend
    val gy = s.gy * (1 - blend) + ty * blend
    val glareA = (if (idle) .55f else 0f) * (1 - blend) + .75f * blend
    // Liquid glass: se aplasta un poco al tocarlo
    val squash by animateFloatAsState(if (liquid && touching) 1f else 0f, spring(dampingRatio = .45f, stiffness = 600f), label = "squash")

    Box(
        modifier
            .graphicsLayer {
                rotationX = rx
                rotationY = ry
                scaleX = 1f + .04f * squash
                scaleY = 1f - .05f * squash
                cameraDistance = 10f * density
            }
            .onGloballyPositioned { coords[0] = it }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var moved = false
                    fun set(p: Offset) {
                        tx = (p.x / size.width).coerceIn(0f, 1f)
                        ty = (p.y / size.height).coerceIn(0f, 1f)
                    }
                    suspend fun AwaitPointerEventScope.track() {
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: return
                            set(c.position)
                            if ((c.position - down.position).getDistance() > slop || c.isConsumed) moved = true
                            if (ev.changes.none { it.pressed }) return
                        }
                    }
                    touching = true
                    set(down.position)
                    val released = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) { track(); true }
                    var longFired = false
                    val d = drag
                    if (released == null && !moved && d != null) {
                        // Mantener oprimido y arrastrar: sirve para unir apps en una carpeta
                        longFired = true
                        d.onStart()
                        var dragged = false
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            c.consume()
                            set(c.position)
                            if (!dragged && (c.position - down.position).getDistance() > slop) dragged = true
                            val lc = coords[0]
                            if (dragged && lc != null && lc.isAttached) d.onMove(lc.localToRoot(c.position))
                            if (ev.changes.none { it.pressed }) break
                        }
                        d.onEnd(dragged)
                    } else if (released == null) {
                        val l = long
                        if (!moved && l != null) {
                            longFired = true
                            l()
                        }
                        track()
                    }
                    touching = false
                    if (!moved && !longFired) { Sfx.play(Sfx.TAP); click() }
                }
            }
            .glass(pal, shape, tint = tint)
            .drawWithContent {
                drawContent()
                if (liquid) {
                    // Brillo de vidrio líquido: banda clara arriba y borde inferior iluminado
                    drawRect(
                        Brush.verticalGradient(
                            0f to Color.White.copy(alpha = .30f), .28f to Color.White.copy(alpha = .06f), .5f to Color.Transparent
                        )
                    )
                    drawRect(
                        Brush.verticalGradient(.82f to Color.Transparent, 1f to (tint ?: pal.pri).copy(alpha = .35f))
                    )
                }
                // Glare: luz que sigue el dedo (o el balanceo)
                drawRect(
                    Brush.radialGradient(
                        listOf(Color.White.copy(alpha = .30f * glareA), Color.Transparent),
                        center = Offset(gx * size.width, gy * size.height),
                        radius = size.maxDimension * .75f
                    )
                )
            },
        contentAlignment = Alignment.Center,
        content = content
    )
}

/** Qué hacer al mantener oprimida una tarjeta y moverla. */
class LongDrag(val onStart: () -> Unit, val onMove: (Offset) -> Unit, val onEnd: (dragged: Boolean) -> Unit)

/* ---------- Animación de impresora (recibo) ---------- */

data class Receipt(val title: String, val lines: List<String>, val id: Long = System.nanoTime())

@Composable
fun ReceiptPrinter(receipt: Receipt?, onDone: () -> Unit) {
    if (receipt == null) return
    val pal = LocalPal.current
    val slide = remember(receipt.id) { Animatable(0f) }
    val tear = remember(receipt.id) { Animatable(0f) }
    LaunchedEffect(receipt.id) {
        playMotorHum()
        slide.animateTo(1f, tween(900))
        delay(1800)
        tear.animateTo(1f, tween(380))
        onDone()
    }
    val bars = remember(receipt.id) { List(38) { Random.nextInt(1, 4) } }
    Box(Modifier.fillMaxWidth().padding(top = 56.dp), contentAlignment = Alignment.TopCenter) {
        // Ranura de la tarjeta oscura
        Box(
            Modifier.width(250.dp).height(14.dp).clip(cutShape(6.dp))
                .background(Color(0xFF0B0F0C)).border(1.dp, pal.pri.copy(alpha = .35f), cutShape(6.dp))
        )
        Box(
            Modifier.padding(top = 7.dp).width(220.dp).clip(androidx.compose.ui.graphics.RectangleShape)
        ) {
            Column(
                Modifier
                    .graphicsLayer {
                        translationY = -size.height * (1f - slide.value) + 30.dp.toPx() * tear.value
                        rotationZ = 3f * tear.value
                        alpha = 1f - tear.value
                    }
                    .fillMaxWidth()
                    .glass(pal, cutShape(10.dp), strong = true)
                    .background(pal.amb.copy(alpha = .55f))
                    .padding(14.dp)
            ) {
                val mono = TextStyle(fontFamily = FontFamily.Monospace, color = pal.pri, fontSize = 12.sp)
                BasicText(receipt.title, style = mono.copy(fontSize = 14.sp))
                Dashed(pal.pri)
                receipt.lines.forEach { BasicText(it, style = mono.copy(color = pal.pri.copy(alpha = .85f))) }
                Dashed(pal.pri)
                Canvas(Modifier.fillMaxWidth().height(30.dp)) {
                    var x = 0f
                    val u = size.width / (bars.sum() * 2f)
                    bars.forEachIndexed { i, w ->
                        if (i % 2 == 0) drawRect(pal.pri, Offset(x, 0f), androidx.compose.ui.geometry.Size(w * u, size.height))
                        x += w * u * 2f
                    }
                }
            }
        }
    }
}

@Composable
private fun Dashed(c: Color) {
    Canvas(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp)) {
        drawLine(
            c.copy(alpha = .6f), Offset(0f, 0f), Offset(size.width, 0f), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))
        )
    }
}

/** Zumbido suave de motor eléctrico moderno mientras sale el recibo. */
fun playMotorHum() {
    Thread {
        runCatching {
            val rate = 22050
            val n = (rate * .9).toInt()
            val pcm = ShortArray(n)
            for (i in 0 until n) {
                val t = i.toDouble() / rate
                val env = minOf(1.0, t / .08) * minOf(1.0, (.9 - t) / .15)
                val v = sin(2 * PI * 118 * t) * .6 + sin(2 * PI * 236 * t) * .25 + sin(2 * PI * 354 * t) * .1 +
                    (Random.nextDouble() - .5) * .06
                pcm[i] = (v * env * 0.12 * Short.MAX_VALUE).toInt().toShort()
            }
            val track = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
                )
                .setAudioFormat(
                    AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(rate)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build()
                )
                .setBufferSizeInBytes(n * 2)
                .setTransferMode(AudioTrack.MODE_STATIC)
                .build()
            track.write(pcm, 0, n)
            track.play()
            Thread.sleep(1100)
            track.release()
        }
    }.start()
}
