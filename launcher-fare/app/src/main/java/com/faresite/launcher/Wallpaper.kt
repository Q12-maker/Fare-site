package com.faresite.launcher

import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.SurfaceTexture
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.view.Surface
import android.view.TextureView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cuánto se ha desplazado el usuario en el launcher (carrusel, cambio de pantalla y cajón),
 * en "pasos" (una ventana del carrusel o una pantalla = 1). Los fondos por scroll lo siguen.
 */
object ScrollBus {
    var value by mutableFloatStateOf(0f)
    var lastMove by mutableLongStateOf(0L)

    fun add(d: Float) {
        if (d == 0f || d.isNaN()) return
        value += d
        if (abs(d) > .0005f) lastMove = System.currentTimeMillis()
    }
}

/**
 * Fondo de la pantalla principal.
 * base: "red" (red animada), "simple", "imagen" o "video". baseScroll: el video avanza con el scroll en vez de repetirse.
 * layer: capa transparente que aparece encima mientras haces scroll: "no", "hex" (hexágonos Fare) o "video".
 */
data class BgConfig(
    val base: String,
    val baseUri: String,
    val baseScroll: Boolean,
    val layer: String,
    val layerUri: String,
    /** Cuadros del video que avanza cada paso de scroll (una ventana del carrusel o una pantalla). */
    val frames: Int = 18,
)

fun Prefs.bg() = BgConfig(
    str("fondo", "red"), str("fondo_uri", ""), on("fondo_scroll", false),
    str("capa", "hex"), str("capa_uri", ""), int("cuadros_scroll", 18),
)

fun Prefs.putBg(b: BgConfig) {
    putStr("fondo", b.base); putStr("fondo_uri", b.baseUri); putOn("fondo_scroll", b.baseScroll)
    putStr("capa", b.layer); putStr("capa_uri", b.layerUri); putInt("cuadros_scroll", b.frames)
}

/** onHome: el video por scroll solo se mueve y se ve en la pantalla del carrusel. */
@Composable
fun Wallpaper(cfg: BgConfig, onHome: Boolean = true) {
    val base = cfg.baseUri.takeIf { it.isNotEmpty() }?.let(Uri::parse)
    when {
        cfg.base == "imagen" && base != null -> {
            ImageLayer(base)
            Shade()
        }
        cfg.base == "video" && base != null -> {
            VideoLayer(base, scrub = cfg.baseScroll, Modifier.fillMaxSize(), cfg.frames)
            Shade()
        }
        else -> Backdrop()
    }

    // Capa transparente que aparece al hacer scroll y se desvanece al soltar
    if (cfg.layer == "no" || !onHome) return
    var scrolling by remember { mutableStateOf(false) }
    LaunchedEffect(ScrollBus.lastMove) {
        if (ScrollBus.lastMove == 0L) return@LaunchedEffect
        scrolling = true
        delay(650)
        scrolling = false
    }
    val a by animateFloatAsState(if (scrolling) 1f else 0f, tween(if (scrolling) 180 else 700), label = "capa")
    val layerUri = cfg.layerUri.takeIf { it.isNotEmpty() }?.let(Uri::parse)
    if (cfg.layer == "video" && layerUri != null) {
        VideoLayer(layerUri, scrub = true, Modifier.fillMaxSize().alpha(a * .55f), cfg.frames)
    } else if (a > 0f) {
        HexLayer(Modifier.fillMaxSize().alpha(a))
    }
}

/** Oscurece un poco las fotos y videos para que la hora y las ventanas se lean bien. */
@Composable
private fun Shade() {
    val pal = LocalPal.current
    Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(pal.amb.copy(alpha = .35f), pal.amb.copy(alpha = .15f), pal.amb.copy(alpha = .45f)))))
}

@Composable
private fun ImageLayer(uri: Uri) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, uri) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val dm = ctx.resources.displayMetrics
                val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
                var sample = 1
                while (opts.outWidth / (sample * 2) >= dm.widthPixels && opts.outHeight / (sample * 2) >= dm.heightPixels) sample *= 2
                ctx.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
                }?.asImageBitmap()
            }.getOrNull()
        }
    }
    img?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) } ?: Backdrop()
}

/**
 * Video sin sonido que llena la pantalla. Si scrub es true no se reproduce solo:
 * avanza o retrocede según el scroll (ScrollBus). Si no, se repite en bucle.
 */
@Composable
fun VideoLayer(uri: Uri, scrub: Boolean, modifier: Modifier, frames: Int = 18) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val mp = remember(uri, scrub) { MediaPlayer() }
    val st = remember(uri, scrub) { VideoState() }

    fun seek(ms: Int) {
        if (!st.ready) return
        if (st.seeking) { st.pending = ms; return }
        st.seeking = true
        runCatching {
            if (Build.VERSION.SDK_INT >= 26) mp.seekTo(ms.toLong(), MediaPlayer.SEEK_CLOSEST) else mp.seekTo(ms)
        }.onFailure { st.seeking = false }
    }

    DisposableEffect(uri, scrub) {
        mp.setOnPreparedListener {
            st.ready = true
            st.duration = it.duration.coerceAtLeast(1)
            it.setVolume(0f, 0f)
            it.isLooping = !scrub
            it.start()
            if (scrub) it.pause()
        }
        mp.setOnSeekCompleteListener {
            st.seeking = false
            val p = st.pending
            if (p >= 0) { st.pending = -1; seek(p) }
        }
        mp.setOnVideoSizeChangedListener { _, w, h -> st.vw = w; st.vh = h; st.view?.let { crop(it, w, h) } }
        mp.setOnErrorListener { _, _, _ -> true }
        val obs = LifecycleEventObserver { _, e ->
            if (!st.ready || scrub) return@LifecycleEventObserver
            when (e) {
                Lifecycle.Event.ON_STOP -> runCatching { mp.pause() }
                Lifecycle.Event.ON_START -> runCatching { mp.start() }
                else -> {}
            }
        }
        owner.lifecycle.addObserver(obs)
        onDispose {
            owner.lifecycle.removeObserver(obs)
            runCatching { mp.release() }
        }
    }

    if (scrub) {
        // Cuadros por segundo del video (si el archivo no lo dice, se asume 30)
        val fps by produceState(30f, uri) {
            value = withContext(Dispatchers.IO) {
                runCatching {
                    val r = android.media.MediaMetadataRetriever()
                    try {
                        r.setDataSource(ctx, uri)
                        val n = if (Build.VERSION.SDK_INT >= 28) r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_FRAME_COUNT)?.toFloatOrNull() else null
                        val ms = r.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toFloatOrNull()
                        if (n != null && ms != null && ms > 0) n / (ms / 1000f) else null
                    } finally { r.release() }
                }.getOrNull()?.takeIf { it in 5f..240f } ?: 30f
            }
        }
        val msPerStep = frames.coerceAtLeast(1) * 1000f / fps
        LaunchedEffect(uri, msPerStep) {
            snapshotFlow { ScrollBus.value }.collect { v ->
                val d = st.duration
                if (d > 1) {
                    val ms = (v * msPerStep).toInt() % d
                    seek(if (ms < 0) ms + d else ms)
                }
            }
        }
    }

    AndroidView(
        factory = { c ->
            TextureView(c).apply {
                st.view = this
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, h: Int) {
                        runCatching {
                            mp.setSurface(Surface(s))
                            mp.setDataSource(ctx, uri)
                            mp.prepareAsync()
                        }
                    }
                    override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, h: Int) { crop(this@apply, st.vw, st.vh) }
                    override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
                    override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
                }
            }
        },
        modifier = modifier,
    )
}

private class VideoState {
    var ready = false
    var duration = 1
    var seeking = false
    var pending = -1
    var vw = 0
    var vh = 0
    var view: TextureView? = null
}

/** Escala el video para llenar la pantalla sin deformarse (recorta lo que sobra). */
private fun crop(tv: TextureView, vw: Int, vh: Int) {
    val w = tv.width.toFloat()
    val h = tv.height.toFloat()
    if (w == 0f || h == 0f || vw == 0 || vh == 0) return
    val s = max(w / vw, h / vh)
    tv.setTransform(Matrix().apply { setScale(vw * s / w, vh * s / h, w / 2, h / 2) })
}

/** Capa predeterminada al hacer scroll: panal de hexágonos Fare que se desliza y brilla con el scroll. */
@Composable
private fun HexLayer(modifier: Modifier) {
    val pal = LocalPal.current
    Canvas(modifier) {
        val v = ScrollBus.value
        val r = 46.dp.toPx()
        val hw = sqrt(3f) * r
        val shiftX = (v * hw * 1.4f) % hw
        val shiftY = (v * r * .9f) % (r * 3f)
        val glowX = size.width * (.5f + .5f * sin(v * .9f))
        val path = Path()
        var row = -2
        while (row * r * 1.5f < size.height + r * 3) {
            val y = row * r * 1.5f + shiftY
            val odd = row % 2 != 0
            var col = -2
            while (col * hw < size.width + hw * 2) {
                val cx = col * hw + (if (odd) hw / 2 else 0f) + shiftX
                path.reset()
                for (k in 0..5) {
                    val ang = Math.toRadians((60.0 * k) - 30.0)
                    val px = cx + r * .92f * cos(ang).toFloat()
                    val py = y + r * .92f * sin(ang).toFloat()
                    if (k == 0) path.moveTo(px, py) else path.lineTo(px, py)
                }
                path.close()
                val dist = abs(cx - glowX) / size.width
                val alpha = (.42f - dist * .55f).coerceIn(.06f, .42f)
                drawPath(path, pal.pri.copy(alpha = alpha), style = Stroke(1.6.dp.toPx()))
                col++
            }
            row++
        }
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.Transparent, pal.pri.copy(alpha = .10f), Color.Transparent),
                startX = glowX - size.width * .35f, endX = glowX + size.width * .35f
            )
        )
    }
}

/** Ajustes del fondo: base (red, simple, foto o video), cómo se reproduce el video y la capa del scroll. */
@Composable
fun BackgroundSettings(cfg: BgConfig, onChange: (BgConfig) -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    fun keep(u: Uri) = runCatching {
        ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { keep(u); onChange(cfg.copy(base = "imagen", baseUri = u.toString())) }
    }
    val pickVideo = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { keep(u); onChange(cfg.copy(base = "video", baseUri = u.toString())) }
    }
    val pickLayer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { u ->
        if (u != null) { keep(u); onChange(cfg.copy(layer = "video", layerUri = u.toString())) }
    }

    Column(Modifier.padding(top = 14.dp)) {
        T("Fondo", 15.sp, Modifier.padding(bottom = 8.dp), weight = FontWeight.SemiBold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Red animada", cfg.base == "red") { onChange(cfg.copy(base = "red")) }
            Chip("Simple", cfg.base == "simple") { onChange(cfg.copy(base = "simple")) }
        }
        Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Foto…", cfg.base == "imagen") { pickImage.launch(arrayOf("image/*")) }
            Chip("Video…", cfg.base == "video") { pickVideo.launch(arrayOf("video/*")) }
        }
        if (cfg.base == "video") {
            Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Video", 14.sp, Modifier.padding(end = 4.dp), color = pal.pri.copy(alpha = .7f))
                Chip("En bucle", !cfg.baseScroll) { onChange(cfg.copy(baseScroll = false)) }
                Chip("Por scroll", cfg.baseScroll) { onChange(cfg.copy(baseScroll = true)) }
            }
        }
        if ((cfg.base == "video" && cfg.baseScroll) || cfg.layer == "video") {
            var ask by remember { mutableStateOf(false) }
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().glass(pal, cutShape(10.dp)).clickable { ask = true }
                    .padding(horizontal = 12.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                T("Cuadros por scroll", 15.sp, Modifier.weight(1f))
                T("${cfg.frames}", 16.sp, weight = FontWeight.Bold, mono = true)
            }
            if (ask) FramesDialog(cfg.frames, { onChange(cfg.copy(frames = it)); ask = false }) { ask = false }
        }
        T("Capa al hacer scroll", 14.sp, Modifier.padding(top = 12.dp, bottom = 6.dp), color = pal.pri.copy(alpha = .7f))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Hexágonos", cfg.layer == "hex") { onChange(cfg.copy(layer = "hex")) }
            Chip("Video…", cfg.layer == "video") { pickLayer.launch(arrayOf("video/*")) }
            Chip("Ninguna", cfg.layer == "no") { onChange(cfg.copy(layer = "no")) }
        }
    }
}

/** Ventana con un cuadro para escribir cuántos cuadros del video avanza cada scroll. */
@Composable
private fun FramesDialog(current: Int, onSave: (Int) -> Unit, onDismiss: () -> Unit) {
    val pal = LocalPal.current
    var text by remember { mutableStateOf(current.toString()) }
    val n = text.toIntOrNull()?.takeIf { it in 1..600 }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    Dialog(onDismiss) {
        Column(
            Modifier.fillMaxWidth().glass(pal, cutShape(22.dp), strong = true).background(pal.amb.copy(alpha = .95f)).padding(20.dp)
        ) {
            T("Cuadros por scroll", 20.sp, weight = FontWeight.SemiBold)
            Box(
                Modifier.padding(top = 14.dp).fillMaxWidth().glass(pal, cutShape(10.dp)).padding(horizontal = 14.dp, vertical = 12.dp),
                contentAlignment = Alignment.Center
            ) {
                BasicTextField(
                    text, { t -> if (t.length <= 3 && t.all { it.isDigit() }) { Sfx.typed(text, t); text = t } },
                    Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                    textStyle = TextStyle(color = pal.pri, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, fontFamily = FontFamily.Monospace),
                    cursorBrush = SolidColor(pal.pri),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { n?.let(onSave) }),
                )
            }
            Row(Modifier.padding(top = 14.dp).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                Chip("Cancelar", false, onDismiss)
                Chip("Guardar", n != null) { n?.let(onSave) }
            }
        }
    }
}
