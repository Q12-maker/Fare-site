package com.faresite.launcher

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.location.LocationManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.format.DateUtils
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/* ---------- Panel de notificaciones (deslizar hacia abajo) ---------- */

@Composable
fun NotificationPanel(
    apps: Map<String, AppEntry>,
    mode: String,
    onMode: (String) -> Unit,
    onClose: () -> Unit,
    onAction: () -> Unit = {},
) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val list by FareNotifications.items.collectAsState()
    var access by remember { mutableStateOf(FareNotifications.hasAccess(ctx)) }
    LaunchedEffect(Unit) { while (true) { access = FareNotifications.hasAccess(ctx); delay(1500) } }

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(320)) }

    Box(
        Modifier.fillMaxSize()
            .graphicsLayer { translationY = -size.height * .08f * (1f - enter.value); alpha = enter.value }
            .background(pal.amb.copy(alpha = .93f))
            .clickable(remember { MutableInteractionSource() }, null) {}
    ) {
        Backdrop()
        Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 16.dp)) {
            Row(
                Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 10.dp)
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12) onClose() } },
                verticalAlignment = Alignment.CenterVertically
            ) {
                T("Notificaciones", 24.sp, Modifier.weight(1f), weight = FontWeight.SemiBold)
                if (list.any { it.clearable }) Chip("Borrar todo", false) { FareNotifications.dismissAll(); Sfx.play(Sfx.SWIPE) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 12.dp)) {
                Chip("Bloques", mode == "bloques") { onMode("bloques") }
                Chip("Líneas", mode == "lineas") { onMode("lineas") }
            }

            if (!access) {
                Column(Modifier.fillMaxWidth().glass(pal, cutShape(16.dp), strong = true).padding(18.dp)) {
                    T("Falta un permiso", 17.sp, weight = FontWeight.SemiBold)
                    T(
                        "Para ver aquí tus notificaciones, activa Launcher Fare en \"Acceso a notificaciones\".",
                        13.sp, Modifier.padding(top = 6.dp, bottom = 12.dp), color = pal.pri.copy(alpha = .75f), lines = 3
                    )
                    Chip("Dar permiso", true) { FareNotifications.askAccess(ctx); onAction() }
                }
            } else if (list.isEmpty()) {
                Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                    T("No hay notificaciones", 15.sp, color = pal.pri.copy(alpha = .55f), mono = true)
                }
            } else if (mode == "bloques") {
                LazyVerticalGrid(
                    GridCells.Fixed(2), Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    itemsIndexed(list, key = { _, n -> n.key }) { i, n ->
                        NotifCard(n, apps[n.pkg], i, block = true, onAction)
                    }
                }
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(list, key = { _, n -> n.key }) { i, n ->
                        NotifCard(n, apps[n.pkg], i, block = false, onAction)
                    }
                }
            }

            // Asa para cerrar
            Box(
                Modifier.fillMaxWidth().height(36.dp)
                    .clickable(remember { MutableInteractionSource() }, null) { onClose() }
                    .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -12) onClose() } },
                contentAlignment = Alignment.Center
            ) {
                Box(Modifier.width(54.dp).height(5.dp).clip(cutShape(2.dp)).background(pal.pri.copy(alpha = .5f)))
            }
        }
    }
}

/** Tarjeta de notificación: sale impresa como un recibo de vidrio. */
@Composable
private fun NotifCard(n: Notif, app: AppEntry?, index: Int, block: Boolean, onAction: () -> Unit) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val print = remember(n.key) { Animatable(0f) }
    LaunchedEffect(n.key) {
        delay(60L * index.coerceAtMost(8))
        print.animateTo(1f, tween(520))
    }
    val ago = DateUtils.getRelativeTimeSpanString(n.time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

    Box(Modifier.fillMaxWidth().clipToBounds()) {
        TiltCard(
            Modifier.fillMaxWidth()
                .then(if (block) Modifier.aspectRatio(.85f) else Modifier)
                .graphicsLayer { translationY = -size.height * (1f - print.value) },
            cutShape(16.dp),
            maxDeg = if (block) 18f else 6f,
            tint = app?.color,
            liquid = true,
            onClick = { FareNotifications.open(ctx, n); onAction() },
            onLongClick = if (n.clearable) ({ FareNotifications.dismiss(n); Sfx.play(Sfx.SWIPE) }) else null,
        ) {
            Column(Modifier.fillMaxSize().padding(14.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (app != null) AppIcon(app.icon, 26.dp)
                    Spacer(Modifier.width(8.dp))
                    T(app?.label ?: n.pkg.substringAfterLast('.'), 12.sp, Modifier.weight(1f), mono = true)
                    T(ago, 11.sp, color = pal.pri.copy(alpha = .6f), mono = true)
                }
                Canvas(Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp)) {
                    drawLine(
                        pal.pri.copy(alpha = .45f), Offset.Zero, Offset(size.width, 0f), 2f,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
                    )
                }
                if (n.title.isNotBlank()) T(n.title, 15.sp, weight = FontWeight.SemiBold, lines = if (block) 2 else 1)
                if (n.text.isNotBlank()) T(n.text, 13.sp, Modifier.padding(top = 3.dp), color = pal.pri.copy(alpha = .8f), lines = if (block) 5 else 2)
            }
        }
    }
}

/* ---------- Centro de control pequeño (como en iPad) ---------- */

object Controls {
    fun open(ctx: Context, action: String) {
        runCatching { ctx.startActivity(Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    fun wifiOn(ctx: Context) = runCatching {
        (ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager).isWifiEnabled
    }.getOrDefault(false)

    fun airplaneOn(ctx: Context) = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1

    fun locationOn(ctx: Context) = runCatching {
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= 28) lm.isLocationEnabled else lm.isProviderEnabled(LocationManager.GPS_PROVIDER)
    }.getOrDefault(false)

    fun torchId(ctx: Context): String? = runCatching {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true }
    }.getOrNull()

    fun setTorch(ctx: Context, on: Boolean) {
        val id = torchId(ctx) ?: return
        runCatching { (ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager).setTorchMode(id, on) }
    }

    private fun audio(ctx: Context) = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun volume(ctx: Context): Float {
        val a = audio(ctx)
        return a.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / a.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    }

    fun setVolume(ctx: Context, v: Float) {
        val a = audio(ctx)
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        runCatching { a.setStreamVolume(AudioManager.STREAM_MUSIC, (v * max).roundToInt().coerceIn(0, max), 0) }
    }

    fun brightness(ctx: Context): Float =
        Settings.System.getInt(ctx.contentResolver, Settings.System.SCREEN_BRIGHTNESS, 128) / 255f

    fun canSetBrightness(ctx: Context) = Settings.System.canWrite(ctx)

    fun askBrightness(ctx: Context) {
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    fun setBrightness(ctx: Context, v: Float) {
        runCatching {
            val cr = ctx.contentResolver
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            Settings.System.putInt(cr, Settings.System.SCREEN_BRIGHTNESS, (v * 255).roundToInt().coerceIn(2, 255))
        }
    }
}

private enum class Glyph { WIFI, DATA, BT, PLANE, TORCH, PIN, MOON, GEAR, SUN, SPEAKER }

@Composable
fun ControlCenter(onClose: () -> Unit, onNotice: (String, String) -> Unit, onAction: () -> Unit = {}) {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    fun go(action: String) { Controls.open(ctx, action); onAction() }
    var tick by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { while (true) { delay(1200); tick++ } }
    val wifi = remember(tick) { Controls.wifiOn(ctx) }
    val plane = remember(tick) { Controls.airplaneOn(ctx) }
    val loc = remember(tick) { Controls.locationOn(ctx) }
    var torch by remember { mutableStateOf(false) }
    DisposableEffect(Unit) {
        val cm = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        val cb = object : CameraManager.TorchCallback() {
            override fun onTorchModeChanged(cameraId: String, enabled: Boolean) { torch = enabled }
        }
        runCatching { cm.registerTorchCallback(cb, Handler(Looper.getMainLooper())) }
        onDispose { runCatching { cm.unregisterTorchCallback(cb) } }
    }
    var vol by remember { mutableFloatStateOf(Controls.volume(ctx)) }
    var bright by remember { mutableFloatStateOf(Controls.brightness(ctx)) }

    val enter = remember { Animatable(0f) }
    LaunchedEffect(Unit) { enter.animateTo(1f, tween(260)) }

    // Fondo: tocar fuera cierra
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .35f * enter.value))
            .clickable(remember { MutableInteractionSource() }, null) { onClose() }
            .pointerInput(Unit) { detectVerticalDragGestures { _, dy -> if (dy < -14) onClose() } },
        contentAlignment = Alignment.TopEnd
    ) {
        BoxWithConstraints(Modifier.statusBarsPadding().padding(top = 8.dp, end = 12.dp)) {
            val w = minOf(maxWidth * .86f, 340.dp)
            val unit = (w - 12.dp * 3) / 4
            Column(
                Modifier.width(w)
                    .graphicsLayer {
                        val s = .9f + .1f * enter.value
                        scaleX = s; scaleY = s; alpha = enter.value
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 0f)
                    }
                    .glass(pal, cutShape(26.dp), strong = true)
                    .background(pal.amb.copy(alpha = .72f))
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    // Bloque de conexiones 2x2
                    Column(
                        Modifier.size(unit * 2 + 12.dp).glass(pal, cutShape(18.dp)).padding(10.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RoundToggle(Glyph.WIFI, wifi, unit * .78f) {
                                go(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_WIFI else Settings.ACTION_WIFI_SETTINGS)
                            }
                            RoundToggle(Glyph.DATA, !plane, unit * .78f) {
                                go(if (Build.VERSION.SDK_INT >= 29) Settings.Panel.ACTION_INTERNET_CONNECTIVITY else Settings.ACTION_WIRELESS_SETTINGS)
                            }
                        }
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            RoundToggle(Glyph.BT, false, unit * .78f) { go(Settings.ACTION_BLUETOOTH_SETTINGS) }
                            RoundToggle(Glyph.PLANE, plane, unit * .78f) { go(Settings.ACTION_AIRPLANE_MODE_SETTINGS) }
                        }
                    }
                    // Barras grandes de brillo y volumen
                    VSlider(Glyph.SUN, bright, Modifier.width(unit).height(unit * 2 + 12.dp)) { v ->
                        if (Controls.canSetBrightness(ctx)) {
                            bright = v; Controls.setBrightness(ctx, v)
                        } else {
                            Controls.askBrightness(ctx); onAction()
                            onNotice("BRILLO", "Activa \"Modificar ajustes del sistema\" para Launcher Fare")
                        }
                    }
                    VSlider(Glyph.SPEAKER, vol, Modifier.width(unit).height(unit * 2 + 12.dp)) { v ->
                        vol = v; Controls.setVolume(ctx, v)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Tile(Glyph.TORCH, torch, unit) { Controls.setTorch(ctx, !torch) }
                    Tile(Glyph.PIN, loc, unit) { go(Settings.ACTION_LOCATION_SOURCE_SETTINGS) }
                    Tile(Glyph.MOON, false, unit) { go("android.settings.ZEN_MODE_SETTINGS") }
                    Tile(Glyph.GEAR, false, unit) { go(Settings.ACTION_SETTINGS) }
                }
            }
        }
    }
}

@Composable
private fun RoundToggle(g: Glyph, on: Boolean, size: Dp, onClick: () -> Unit) {
    val pal = LocalPal.current
    TiltCard(Modifier.size(size), cutShape(size * .3f), maxDeg = 18f, tint = if (on) pal.pri else null, liquid = true, onClick = onClick) {
        GlyphIcon(g, if (on) pal.amb else pal.pri, Modifier.size(size * .46f))
    }
}

@Composable
private fun Tile(g: Glyph, on: Boolean, size: Dp, onClick: () -> Unit) {
    val pal = LocalPal.current
    TiltCard(Modifier.size(size), cutShape(14.dp), maxDeg = 20f, tint = if (on) pal.pri else null, liquid = true, onClick = onClick) {
        GlyphIcon(g, if (on) pal.amb else pal.pri, Modifier.size(size * .42f))
    }
}

/** Barra vertical grande: arrastra arriba o abajo (o toca) para cambiar el valor. */
@Composable
private fun VSlider(g: Glyph, value: Float, modifier: Modifier, onChange: (Float) -> Unit) {
    val pal = LocalPal.current
    Box(
        modifier.glass(pal, cutShape(18.dp))
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    fun set(y: Float) = onChange((1f - y / size.height).coerceIn(0f, 1f))
                    down.consume(); set(down.position.y)
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        if (!c.pressed) break
                        c.consume(); set(c.position.y)
                    }
                    Sfx.play(Sfx.TICK, .3f)
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(
            Modifier.fillMaxWidth().fillMaxHeight(value.coerceIn(0.02f, 1f))
                .background(Brush.verticalGradient(listOf(pal.pri.copy(alpha = .95f), pal.pri.copy(alpha = .75f))))
        )
        GlyphIcon(g, if (value > .12f) pal.amb else pal.pri, Modifier.padding(bottom = 14.dp).size(24.dp))
    }
}

@Composable
private fun GlyphIcon(g: Glyph, c: Color, modifier: Modifier) {
    Canvas(modifier) { drawGlyph(g, c) }
}

private fun DrawScope.drawGlyph(g: Glyph, c: Color) {
    val w = size.width
    val st = Stroke(w * .1f)
    when (g) {
        Glyph.WIFI -> {
            for (k in 1..3) {
                val r = w * .17f * k
                drawArc(c, 225f, 90f, false, Offset(w / 2 - r, w * .85f - r), androidx.compose.ui.geometry.Size(r * 2, r * 2), style = st)
            }
            drawCircle(c, w * .07f, Offset(w / 2, w * .85f))
        }
        Glyph.DATA -> {
            drawLine(c, Offset(w * .35f, w * .85f), Offset(w * .35f, w * .15f), w * .1f)
            drawLine(c, Offset(w * .35f, w * .15f), Offset(w * .18f, w * .35f), w * .1f)
            drawLine(c, Offset(w * .65f, w * .15f), Offset(w * .65f, w * .85f), w * .1f)
            drawLine(c, Offset(w * .65f, w * .85f), Offset(w * .82f, w * .65f), w * .1f)
        }
        Glyph.BT -> {
            val p = listOf(
                Offset(w * .3f, w * .3f), Offset(w * .7f, w * .68f), Offset(w * .5f, w * .88f), Offset(w * .5f, w * .12f),
                Offset(w * .7f, w * .32f), Offset(w * .3f, w * .7f)
            )
            for (i in 0 until p.size - 1) drawLine(c, p[i], p[i + 1], w * .09f)
        }
        Glyph.PLANE -> {
            drawLine(c, Offset(w * .5f, w * .08f), Offset(w * .5f, w * .9f), w * .11f)
            drawLine(c, Offset(w * .1f, w * .55f), Offset(w * .9f, w * .55f), w * .1f)
            drawLine(c, Offset(w * .3f, w * .88f), Offset(w * .7f, w * .88f), w * .09f)
        }
        Glyph.TORCH -> {
            drawRect(c, Offset(w * .3f, w * .08f), androidx.compose.ui.geometry.Size(w * .4f, w * .22f))
            drawLine(c, Offset(w * .3f, w * .3f), Offset(w * .4f, w * .5f), w * .09f)
            drawLine(c, Offset(w * .7f, w * .3f), Offset(w * .6f, w * .5f), w * .09f)
            drawRect(c, Offset(w * .4f, w * .5f), androidx.compose.ui.geometry.Size(w * .2f, w * .44f), style = Stroke(w * .08f))
        }
        Glyph.PIN -> {
            drawCircle(c, w * .28f, Offset(w / 2, w * .38f), style = st)
            drawLine(c, Offset(w * .3f, w * .55f), Offset(w / 2, w * .92f), w * .1f)
            drawLine(c, Offset(w * .7f, w * .55f), Offset(w / 2, w * .92f), w * .1f)
        }
        Glyph.MOON -> {
            drawArc(c, 70f, 220f, false, Offset(w * .12f, w * .12f), androidx.compose.ui.geometry.Size(w * .76f, w * .76f), style = st)
            drawArc(c, 110f, 140f, false, Offset(w * .35f, w * .1f), androidx.compose.ui.geometry.Size(w * .5f, w * .5f), style = st)
        }
        Glyph.GEAR -> {
            drawCircle(c, w * .26f, Offset(w / 2, w / 2), style = st)
            for (k in 0 until 8) {
                val a = k * PI / 4
                drawLine(
                    c, Offset(w / 2 + cos(a).toFloat() * w * .3f, w / 2 + sin(a).toFloat() * w * .3f),
                    Offset(w / 2 + cos(a).toFloat() * w * .45f, w / 2 + sin(a).toFloat() * w * .45f), w * .12f
                )
            }
        }
        Glyph.SUN -> {
            drawCircle(c, w * .2f, Offset(w / 2, w / 2))
            for (k in 0 until 8) {
                val a = k * PI / 4
                drawLine(
                    c, Offset(w / 2 + cos(a).toFloat() * w * .32f, w / 2 + sin(a).toFloat() * w * .32f),
                    Offset(w / 2 + cos(a).toFloat() * w * .46f, w / 2 + sin(a).toFloat() * w * .46f), w * .09f
                )
            }
        }
        Glyph.SPEAKER -> {
            drawRect(c, Offset(w * .1f, w * .36f), androidx.compose.ui.geometry.Size(w * .2f, w * .28f))
            drawLine(c, Offset(w * .3f, w * .36f), Offset(w * .55f, w * .12f), w * .09f)
            drawLine(c, Offset(w * .3f, w * .64f), Offset(w * .55f, w * .88f), w * .09f)
            drawLine(c, Offset(w * .55f, w * .12f), Offset(w * .55f, w * .88f), w * .09f)
            drawArc(c, -45f, 90f, false, Offset(w * .45f, w * .25f), androidx.compose.ui.geometry.Size(w * .5f, w * .5f), style = st)
        }
    }
}
