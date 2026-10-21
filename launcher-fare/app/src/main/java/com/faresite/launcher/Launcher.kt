package com.faresite.launcher

import android.app.Activity
import android.content.Intent
import android.provider.AlarmClock
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.geometry.Rect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sign
import kotlin.math.sin

private val ES = Locale.forLanguageTag("es-CO")
private const val HOME = 0
private const val DRAWER = 1

@Composable
fun LauncherApp(homePressed: StateFlow<Int>, resumed: StateFlow<Int>) {
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    val scope = rememberCoroutineScope()

    var apps by remember { mutableStateOf<List<AppEntry>>(emptyList()) }
    val carousel = remember { mutableStateListOf<String>().apply { prefs.list("carousel")?.let { addAll(it) } } }
    val quick = remember { mutableStateListOf<String>().apply { addAll((prefs.str("quick", ",,").split(",") + listOf("", "", "")).take(3)) } }
    var chosen by remember { mutableStateOf(prefs.list("carousel") != null) }

    val custom = remember { mutableStateListOf<Palette>().apply { addAll(prefs.customPalettes()) } }
    var palName by remember { mutableStateOf(prefs.str("palette", "Fare")) }
    var preview by remember { mutableStateOf<Palette?>(null) }
    val chosenPal = (StandardPalettes + custom).firstOrNull { it.name == palName } ?: StandardPalettes[0]
    val pal = preview ?: chosenPal

    var mode by remember { mutableStateOf(prefs.str("mode", "bloques")) }
    var cols by remember { mutableIntStateOf(prefs.int("cols", 3)) }
    var query by remember { mutableStateOf("") }

    var receipt by remember { mutableStateOf<Receipt?>(null) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var settings by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    val groups = remember { mutableStateListOf<AppGroup>().apply { addAll(prefs.groups()) } }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<Updater.Update?>(null) }
    var installing by remember { mutableStateOf(false) }
    var lastCheck by remember { mutableStateOf(0L) }
    val version = remember { runCatching { Updater.currentVersion(ctx) }.getOrDefault(0) }

    val pager = rememberPagerState(initialPage = HOME) { 2 }
    val focus = LocalFocusManager.current
    val tick by resumed.collectAsState()
    val home by homePressed.collectAsState()

    fun print(title: String, vararg lines: String) { receipt = Receipt(title, lines.toList()) }
    fun saveCarousel() { chosen = true; prefs.putList("carousel", carousel.toList()) }
    fun saveQuick() = prefs.putStr("quick", quick.joinToString(","))
    fun saveGroups() = prefs.putGroups(groups.toList())
    fun setGroup(g: AppGroup) {
        val i = groups.indexOfFirst { it.id == g.id }
        if (g.keys.size < 2) { if (i >= 0) groups.removeAt(i) } else if (i >= 0) groups[i] = g else groups.add(g)
        saveGroups()
    }
    fun drop(dragged: String, target: String) {
        if (target.startsWith("g:")) {
            val g = groups.firstOrNull { "g:" + it.id == target } ?: return
            setGroup(g.copy(keys = g.keys + dragged))
            print("CARPETA", "Añadida a:", g.name)
        } else {
            val name = "Carpeta ${groups.size + 1}"
            setGroup(AppGroup(System.currentTimeMillis().toString(36), name, listOf(target, dragged)))
            print("CARPETA CREADA", name, "Tócala para cambiarle el nombre")
        }
    }
    fun doUpdate(u: Updater.Update) {
        if (installing) return
        if (!Updater.canInstall(ctx)) {
            print("PERMISO", "Activa \"Permitir de esta fuente\"", "y vuelve a tocar Actualizar")
            return
        }
        installing = true
        print("ACTUALIZANDO", "Versión ${u.version}", "Descargando...")
        scope.launch {
            val ok = withContext(Dispatchers.IO) { Updater.install(ctx, u) }
            installing = false
            if (!ok) print("ERROR", "No se pudo descargar", "Revisa el internet")
        }
    }
    suspend fun checkUpdate(manual: Boolean) {
        lastCheck = System.currentTimeMillis()
        update = withContext(Dispatchers.IO) { Updater.check(ctx) }
        if (manual && update == null) print("AL DÍA", "Versión $version", "No hay nada nuevo")
    }

    LaunchedEffect(tick) {
        apps = withContext(Dispatchers.Default) { loadApps(ctx) }
        if (!chosen && carousel.isEmpty()) {
            carousel.addAll(suggest(apps, listOf("camera", "whatsapp", "chrome", "photos", "gallery", "music", "youtube"), 6))
        }
        if (quick.all { it.isEmpty() }) {
            val s = suggest(apps, listOf("dialer", "phone", "messag", "mms", "settings"), 3)
            s.forEachIndexed { i, k -> quick[i] = k }
        }
    }
    // Igual que Audio Fare: busca versión nueva al abrir, al volver y cada 30 min
    LaunchedEffect(tick) {
        while (true) {
            if (System.currentTimeMillis() - lastCheck > 30 * 60 * 1000L || tick <= 1) checkUpdate(false)
            delay(30 * 60 * 1000L)
        }
    }
    LaunchedEffect(tick) {
        while (true) {
            weather = withContext(Dispatchers.IO) { fetchWeather() } ?: weather
            delay(30 * 60 * 1000L)
        }
    }
    LaunchedEffect(home) {
        menuApp = null; settings = false; openGroup = null; preview = null; query = ""; focus.clearFocus()
        pager.animateScrollToPage(HOME)
    }
    LaunchedEffect(pager.currentPage) { if (pager.currentPage == HOME) { query = ""; focus.clearFocus() } }

    BackHandler {
        when {
            menuApp != null -> menuApp = null
            openGroup != null -> openGroup = null
            settings -> { settings = false; preview = null }
            pager.currentPage != HOME -> scope.launch { pager.animateScrollToPage(HOME) }
        }
    }

    val view = LocalView.current
    SideEffect {
        val w = (view.context as? Activity)?.window ?: return@SideEffect
        val light = pal.amb.luminance() > .5f
        WindowCompat.getInsetsController(w, view).apply {
            isAppearanceLightStatusBars = light
            isAppearanceLightNavigationBars = light
        }
    }

    val byKey = apps.associateBy { it.key }
    val open: (AppEntry) -> Unit = { launchApp(ctx, it) }

    CompositionLocalProvider(LocalPal provides pal) {
        Box(Modifier.fillMaxSize().background(pal.amb)) {
            Backdrop()
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
              // Cada pantalla recorta lo suyo: nada del carrusel se cuela en el cajón
              Box(Modifier.fillMaxSize().clipToBounds()) {
                if (page == DRAWER) {
                    Drawer(
                        apps, groups, query, { query = it }, mode,
                        { mode = it; prefs.putStr("mode", it) }, cols,
                        { cols = it; prefs.putInt("cols", it) },
                        open, { menuApp = it },
                        onOpenGroup = { openGroup = it.id },
                        onDrop = { d, t -> drop(d, t) },
                        onSettings = { settings = true }
                    )
                } else {
                    Home(
                        weather,
                        carousel.mapNotNull { byKey[it] },
                        quick.map { byKey[it] },
                        open, { menuApp = it },
                        onEmptyQuick = { scope.launch { pager.animateScrollToPage(DRAWER) } },
                        onClock = {
                            runCatching {
                                ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        onWeather = { scope.launch { weather = withContext(Dispatchers.IO) { fetchWeather() } ?: weather } },
                        onSettings = { settings = true },
                    )
                }
              }
            }

            update?.let { u ->
                if (menuApp == null && !settings && openGroup == null) {
                    Box(Modifier.fillMaxSize().navigationBarsPadding().padding(bottom = 34.dp), contentAlignment = Alignment.BottomCenter) {
                        Row(
                            Modifier.glass(pal, cutShape(12.dp), strong = true).background(pal.amb.copy(alpha = .75f))
                                .padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            T("Versión ${u.version} lista", 14.sp, weight = FontWeight.SemiBold)
                            Spacer(Modifier.width(12.dp))
                            Chip(if (installing) "Descargando..." else "Actualizar", true) { doUpdate(u) }
                        }
                    }
                }
            }

            val g = openGroup?.let { id -> groups.firstOrNull { it.id == id } }
            if (g != null) {
                FolderSheet(
                    g, g.keys.mapNotNull { byKey[it] },
                    onRename = { n -> if (n.isNotBlank()) setGroup(g.copy(name = n.trim())) },
                    onOpen = { open(it) },
                    onRemove = { app ->
                        setGroup(g.copy(keys = g.keys - app.key))
                        print("CARPETA", "Sacada:", app.label)
                        if (g.keys.size <= 2) openGroup = null
                    },
                    onDismiss = { openGroup = null },
                )
            }

            menuApp?.let { app ->
                AppMenu(
                    app,
                    inCarousel = app.key in carousel,
                    quickSlot = quick.indexOf(app.key),
                    onDismiss = { menuApp = null },
                    onToggleCarousel = {
                        if (app.key in carousel) {
                            carousel.remove(app.key); print("CARRUSEL", "Quitada:", app.label)
                        } else {
                            carousel.add(app.key); print("CARRUSEL", "Añadida:", app.label)
                        }
                        saveCarousel(); menuApp = null
                    },
                    onQuick = { slot ->
                        val i = quick.indexOf(app.key)
                        if (i >= 0) quick[i] = ""
                        if (slot >= 0) quick[slot] = app.key
                        saveQuick(); menuApp = null
                        print("BLOQUE PEQUEÑO", if (slot >= 0) "Bloque ${slot + 1}:" else "Quitada:", app.label)
                    },
                    onInfo = { openAppInfo(ctx, app); menuApp = null },
                    onUninstall = { uninstallApp(ctx, app); menuApp = null },
                )
            }

            if (settings) {
                SettingsPanel(
                    current = chosenPal,
                    custom = custom,
                    onPreview = { preview = it },
                    onPick = { preview = null; palName = it.name; prefs.putStr("palette", it.name); print("PALETA", "Aplicada:", it.name) },
                    onSave = { p ->
                        preview = null
                        custom.removeAll { it.name == p.name }
                        custom.add(p); prefs.putCustomPalettes(custom)
                        palName = p.name; prefs.putStr("palette", p.name)
                        print("PALETA GUARDADA", p.name, "70 ${p.amb.hex()}", "20 ${p.pri.hex()}", "10 ${p.sec.hex()}")
                    },
                    onDelete = { p ->
                        custom.remove(p); prefs.putCustomPalettes(custom)
                        if (palName == p.name) { palName = "Fare"; prefs.putStr("palette", "Fare") }
                    },
                    version = version,
                    onCheckUpdate = { scope.launch { checkUpdate(true) } },
                    onHomeSettings = {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    onDismiss = { settings = false; preview = null },
                )
            }

            ReceiptPrinter(receipt) { receipt = null }
        }
    }
}

/* ---------- Texto ---------- */

@Composable
fun T(
    text: String,
    size: TextUnit = 15.sp,
    modifier: Modifier = Modifier,
    color: Color = LocalPal.current.pri,
    weight: FontWeight = FontWeight.Normal,
    mono: Boolean = false,
    align: TextAlign = TextAlign.Start,
    lines: Int = 1,
) {
    BasicText(
        text, modifier,
        style = TextStyle(
            color = color, fontSize = size, fontWeight = weight, textAlign = align,
            fontFamily = if (mono) FontFamily.Monospace else FontFamily.SansSerif, letterSpacing = if (size > 40.sp) (-1).sp else 0.sp
        ),
        maxLines = lines, overflow = TextOverflow.Ellipsis
    )
}

/* ---------- Fondo: luces suaves + logo Faresite discreto ---------- */

@Composable
fun Backdrop() {
    val pal = LocalPal.current
    Canvas(Modifier.fillMaxSize()) {
        drawCircle(
            Brush.radialGradient(listOf(pal.sec.copy(alpha = .45f), Color.Transparent), Offset(size.width * .15f, size.height * .2f), size.width * .9f),
            size.width * .9f, Offset(size.width * .15f, size.height * .2f)
        )
        drawCircle(
            Brush.radialGradient(listOf(pal.pri.copy(alpha = .16f), Color.Transparent), Offset(size.width * .9f, size.height * .78f), size.width),
            size.width, Offset(size.width * .9f, size.height * .78f)
        )
    }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(
            painterResource(R.drawable.logo_fare), null,
            Modifier.size(260.dp).alpha(.07f),
            colorFilter = ColorFilter.tint(pal.pri)
        )
    }
}

/* ---------- Pantalla principal ---------- */

@Composable
fun Home(
    weather: Weather?,
    carousel: List<AppEntry>,
    quick: List<AppEntry?>,
    onOpen: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
    onEmptyQuick: () -> Unit,
    onClock: () -> Unit,
    onWeather: () -> Unit,
    onSettings: () -> Unit,
) {
    val pal = LocalPal.current
    var now by remember { mutableStateOf(Date()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = Date()
            delay(60_000 - System.currentTimeMillis() % 60_000 + 50)
        }
    }
    val cal = Calendar.getInstance().apply { time = now }
    val hour = SimpleDateFormat("h:mm", ES).format(now)
    val ampm = if (cal.get(Calendar.AM_PM) == Calendar.AM) "A.M." else "P.M."
    val date = SimpleDateFormat("EEEE d 'de' MMMM", ES).format(now).replaceFirstChar { it.uppercase() }

    Column(
        Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 20.dp)
    ) {
        Row(Modifier.fillMaxWidth().padding(top = 14.dp), verticalAlignment = Alignment.Top) {
            Row(
                Modifier.weight(1f).clickable(remember { MutableInteractionSource() }, null) { onWeather() }.padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeatherIcon(weather?.code ?: 0, Modifier.size(38.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    T(weather?.let { "${it.temp}°" } ?: "--°", 22.sp, weight = FontWeight.SemiBold)
                    T(weather?.let { weatherText(it.code) } ?: "Clima", 12.sp, color = pal.pri.copy(alpha = .7f))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                quick.forEachIndexed { i, app ->
                    key(i, app?.key) {
                        TiltCard(
                            Modifier.size(56.dp), cutShape(12.dp), maxDeg = 18f,
                            onClick = { if (app != null) onOpen(app) else onEmptyQuick() },
                            onLongClick = app?.let { { onLong(it) } }
                        ) {
                            if (app != null) AppIcon(app.icon, 32.dp) else T("+", 22.sp, color = pal.pri.copy(alpha = .6f))
                        }
                    }
                }
            }
        }

        Spacer(Modifier.weight(.6f))
        Column(
            Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { onClock() },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(verticalAlignment = Alignment.Bottom) {
                T(hour, 76.sp, weight = FontWeight.Light)
                Spacer(Modifier.width(8.dp))
                T(ampm, 22.sp, Modifier.padding(bottom = 16.dp), weight = FontWeight.Medium, color = pal.pri.copy(alpha = .8f))
            }
            T(date, 15.sp, color = pal.pri.copy(alpha = .7f))
        }
        Spacer(Modifier.weight(1f))

        Carousel(carousel, onOpen, onLong)
        T(
            "Desliza hacia la izquierda para ver todas las apps", 11.sp,
            Modifier.fillMaxWidth().padding(top = 6.dp, bottom = 14.dp),
            color = pal.pri.copy(alpha = .45f), mono = true, align = TextAlign.Center
        )
    }
}

@Composable
fun AppIcon(icon: ImageBitmap, size: Dp) {
    Image(icon, null, Modifier.size(size))
}

/* ---------- Glassy carousel con tilt 3D, glare y sway ---------- */

@Composable
fun Carousel(items: List<AppEntry>, onOpen: (AppEntry) -> Unit, onLong: (AppEntry) -> Unit) {
    val pal = LocalPal.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            T("Mantén oprimida una app en el cajón\npara añadirla aquí", 13.sp, color = pal.pri.copy(alpha = .6f), align = TextAlign.Center, lines = 2)
        }
        return
    }
    val scope = rememberCoroutineScope()
    val pos = remember { Animatable(0f) }
    val lastDx = remember { floatArrayOf(0f) }
    val n = items.size
    LaunchedEffect(n) { if (pos.value > n - 1) pos.snapTo((n - 1).toFloat()) }
    val density = LocalDensity.current

    BoxWithConstraints(Modifier.fillMaxWidth().height(310.dp)) {
        val cardW = minOf(maxWidth * .56f, 250.dp)
        val cardPx = with(density) { cardW.toPx() }
        fun settle() {
            val target = (pos.value - sign(lastDx[0]) * .2f).roundToInt().coerceIn(0, n - 1)
            scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = .78f, stiffness = 180f)) }
        }
        Box(
            Modifier.fillMaxSize().pointerInput(n) {
                detectHorizontalDragGestures(onDragEnd = { settle() }, onDragCancel = { settle() }) { ch, dx ->
                    ch.consume()
                    lastDx[0] = dx
                    scope.launch { pos.snapTo((pos.value - dx / (cardPx * .62f)).coerceIn(-.35f, n - 1 + .35f)) }
                }
            },
            contentAlignment = Alignment.Center
        ) {
            items.forEachIndexed { i, app ->
                val off = i - pos.value
                val a = abs(off)
                if (a < 2.6f) key(app.key) {
                    val s = 1f - minOf(a, 2f) * .17f
                    TiltCard(
                        Modifier
                            .zIndex(-a)
                            .graphicsLayer {
                                translationX = off * cardPx * .62f
                                scaleX = s; scaleY = s
                                alpha = (1f - (a - 1.6f).coerceAtLeast(0f)).coerceIn(0f, 1f)
                            }
                            .width(cardW).height(cardW * 1.12f),
                        cutShape(26.dp),
                        maxDeg = 28f,
                        idle = a < .5f,
                        onClick = {
                            if (a < .5f) onOpen(app)
                            else scope.launch { pos.animateTo(i.toFloat(), spring(dampingRatio = .78f, stiffness = 180f)) }
                        },
                        onLongClick = { onLong(app) }
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIcon(app.icon, cardW * .42f)
                            Spacer(Modifier.height(16.dp))
                            T(app.label, 17.sp, Modifier.padding(horizontal = 14.dp), weight = FontWeight.SemiBold, align = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }
}

/* ---------- Clima dibujado ---------- */

@Composable
fun WeatherIcon(code: Int, modifier: Modifier) {
    val pal = LocalPal.current
    Canvas(modifier) {
        val w = size.width
        val st = Stroke(w * .06f)
        val sunny = code <= 2
        val cloudy = code >= 1
        if (sunny) {
            val c = if (cloudy) Offset(w * .38f, w * .38f) else Offset(w / 2, w / 2)
            val r = if (cloudy) w * .17f else w * .2f
            drawCircle(pal.pri, r, c, style = st)
            for (k in 0 until 8) {
                val ang = k * PI / 4
                val p1 = Offset(c.x + cos(ang).toFloat() * r * 1.5f, c.y + sin(ang).toFloat() * r * 1.5f)
                val p2 = Offset(c.x + cos(ang).toFloat() * r * 2.1f, c.y + sin(ang).toFloat() * r * 2.1f)
                drawLine(pal.pri, p1, p2, w * .06f)
            }
        }
        if (cloudy) {
            val y = w * .62f
            drawCircle(pal.amb, w * .2f, Offset(w * .45f, y - w * .06f))
            drawCircle(pal.pri, w * .2f, Offset(w * .45f, y - w * .06f), style = st)
            drawCircle(pal.amb, w * .15f, Offset(w * .7f, y))
            drawCircle(pal.pri, w * .15f, Offset(w * .7f, y), style = st)
            drawCircle(pal.amb, w * .13f, Offset(w * .25f, y + w * .02f))
            drawCircle(pal.pri, w * .13f, Offset(w * .25f, y + w * .02f), style = st)
        }
        if (code in 51..67 || code in 80..82 || code >= 95) {
            for (k in 0..2) {
                val x = w * (.3f + k * .2f)
                drawLine(pal.pri, Offset(x, w * .84f), Offset(x - w * .06f, w * .98f), w * .05f)
            }
        }
    }
}

/* ---------- Cajón de apps ---------- */

private sealed class Item {
    abstract val key: String
    abstract val label: String
}

private class AppItem(val app: AppEntry) : Item() {
    override val key get() = app.key
    override val label get() = app.label
}

private class FolderItem(val group: AppGroup, val apps: List<AppEntry>) : Item() {
    override val key get() = "g:" + group.id
    override val label get() = group.name
}

@Composable
fun Drawer(
    apps: List<AppEntry>,
    groups: List<AppGroup>,
    query: String,
    onQuery: (String) -> Unit,
    mode: String,
    onMode: (String) -> Unit,
    cols: Int,
    onCols: (Int) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
    onOpenGroup: (AppGroup) -> Unit,
    onDrop: (dragged: String, target: String) -> Unit,
    onSettings: () -> Unit,
) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val q = query.trim().lowercase()
    val byKey = apps.associateBy { it.key }
    val items: List<Item> = if (q.isNotEmpty()) {
        apps.filter { q in it.label.lowercase() }.map { AppItem(it) }
    } else {
        val inGroups = groups.flatMap { it.keys }.toSet()
        (groups.mapNotNull { g -> g.keys.mapNotNull { byKey[it] }.takeIf { it.isNotEmpty() }?.let { FolderItem(g, it) } } +
            apps.filter { it.key !in inGroups }.map { AppItem(it) })
            .sortedBy { it.label.lowercase() }
    }

    // Arrastre para formar carpetas
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableMapOf<String, Rect>() }
    fun targetAt(p: Offset, self: String) = bounds.entries.firstOrNull { it.key != self && it.value.contains(p) }?.key
    val hover = dragKey?.let { targetAt(dragPos, it) }

    @Composable
    fun Cell(item: Item, content: @Composable (Modifier, LongDrag?) -> Unit) {
        DisposableEffect(item.key) { onDispose { bounds.remove(item.key) } }
        val lifted = item.key == dragKey
        val over = item.key == hover
        Box(
            Modifier
                .onGloballyPositioned { bounds[item.key] = it.boundsInRoot() }
                .graphicsLayer {
                    val sc = if (over) 1.08f else 1f
                    scaleX = sc; scaleY = sc
                    alpha = if (lifted) .35f else 1f
                }
        ) {
            val drag = (item as? AppItem)?.let { ai ->
                LongDrag(
                    onStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragKey = ai.key; dragIcon = ai.app.icon
                        dragPos = bounds[ai.key]?.center ?: Offset.Zero
                    },
                    onMove = { dragPos = it },
                    onEnd = { dragged ->
                        val target = if (dragged) targetAt(dragPos, ai.key) else null
                        dragKey = null; dragIcon = null
                        when {
                            !dragged -> onLong(ai.app)
                            target != null -> onDrop(ai.key, target)
                        }
                    }
                )
            }
            content(Modifier, drag)
        }
    }

    Box(Modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            // Barra de búsqueda
            Row(
                Modifier.padding(top = 14.dp).fillMaxWidth().height(54.dp).glass(pal, cutShape(16.dp), strong = true).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(18.dp)) {
                    val r = size.width * .36f
                    drawCircle(pal.pri, r, Offset(r + 1, r + 1), style = Stroke(size.width * .12f))
                    drawLine(pal.pri, Offset(r * 1.75f, r * 1.75f), Offset(size.width, size.height), size.width * .12f)
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) T("Buscar apps", 16.sp, color = pal.pri.copy(alpha = .5f))
                    BasicTextField(
                        query, onQuery, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = TextStyle(color = pal.pri, fontSize = 16.sp),
                        cursorBrush = SolidColor(pal.pri),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { (items.firstOrNull() as? AppItem)?.let { onOpen(it.app) } })
                    )
                }
                if (query.isNotEmpty()) {
                    T("✕", 16.sp, Modifier.clickable { onQuery("") }.padding(8.dp))
                }
            }

            // Opciones: bloques / líneas, columnas, ajustes
            Row(
                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Chip("Bloques", mode == "bloques") { onMode("bloques") }
                Chip("Líneas", mode == "lineas") { onMode("lineas") }
                if (mode == "bloques") {
                    Spacer(Modifier.width(4.dp))
                    for (c in 2..4) Chip("$c", cols == c) { onCols(c) }
                }
                Spacer(Modifier.weight(1f))
                Chip("Ajustes", false, onSettings)
            }

            if (mode == "bloques") {
                LazyVerticalGrid(
                    GridCells.Fixed(cols),
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        Cell(item) { _, drag ->
                            TiltCard(
                                Modifier.aspectRatio(.88f), cutShape(if (cols == 2) 20.dp else 14.dp), maxDeg = 22f,
                                onClick = { if (item is AppItem) onOpen(item.app) else if (item is FolderItem) onOpenGroup(item.group) },
                                onLongClick = (item as? FolderItem)?.let { f -> { onOpenGroup(f.group) } },
                                longDrag = drag
                            ) {
                                val iconSize = if (cols == 2) 72.dp else if (cols == 3) 52.dp else 40.dp
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    when (item) {
                                        is AppItem -> AppIcon(item.app.icon, iconSize)
                                        is FolderItem -> FolderIcons(item.apps, iconSize)
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    T(item.label, if (cols == 4) 11.sp else 13.sp, Modifier.padding(horizontal = 6.dp), align = TextAlign.Center)
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        Cell(item) { _, drag ->
                            TiltCard(
                                Modifier.fillMaxWidth().height(62.dp), cutShape(12.dp), maxDeg = 5f,
                                onClick = { if (item is AppItem) onOpen(item.app) else if (item is FolderItem) onOpenGroup(item.group) },
                                onLongClick = (item as? FolderItem)?.let { f -> { onOpenGroup(f.group) } },
                                longDrag = drag
                            ) {
                                Row(Modifier.fillMaxSize().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    when (item) {
                                        is AppItem -> AppIcon(item.app.icon, 38.dp)
                                        is FolderItem -> FolderIcons(item.apps, 38.dp)
                                    }
                                    Spacer(Modifier.width(14.dp))
                                    T(item.label, 16.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // La app que se está arrastrando sigue al dedo
        val icon = dragIcon
        if (dragKey != null && icon != null) {
            val d = LocalDensity.current
            val half = with(d) { 30.dp.toPx() }
            Box(
                Modifier
                    .graphicsLayer {
                        translationX = dragPos.x - origin.x - half
                        translationY = dragPos.y - origin.y - half
                    }
                    .size(60.dp)
                    .glass(pal, cutShape(12.dp), strong = true),
                contentAlignment = Alignment.Center
            ) { AppIcon(icon, 44.dp) }
            T(
                if (hover != null) "Suelta para unir en una carpeta" else "Arrastra encima de otra app para hacer una carpeta",
                12.sp,
                Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
                    .glass(pal, cutShape(8.dp), strong = true).background(pal.amb.copy(alpha = .8f)).padding(horizontal = 14.dp, vertical = 10.dp),
                mono = true
            )
        }
    }
}

/** Ícono de carpeta: hasta 4 apps en miniatura. */
@Composable
fun FolderIcons(apps: List<AppEntry>, size: Dp) {
    val pal = LocalPal.current
    val mini = size * .44f
    Column(
        Modifier.size(size).glass(pal, cutShape(size * .18f), strong = true).padding(size * .04f),
        verticalArrangement = Arrangement.SpaceEvenly,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        apps.take(4).chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                row.forEach { AppIcon(it.icon, mini) }
                if (row.size == 1) Spacer(Modifier.size(mini))
            }
        }
    }
}

/* ---------- Carpeta abierta ---------- */

@Composable
fun FolderSheet(
    group: AppGroup,
    apps: List<AppEntry>,
    onRename: (String) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onRemove: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
) {
    val pal = LocalPal.current
    var name by remember(group.id) { mutableStateOf(group.name) }
    Sheet({ onRename(name); onDismiss() }) {
        Box(
            Modifier.fillMaxWidth().glass(pal, cutShape(10.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (name.isEmpty()) T("Nombre de la carpeta", 18.sp, color = pal.pri.copy(alpha = .5f))
            BasicTextField(
                name, { if (it.length <= 28) { name = it; onRename(it) } }, Modifier.fillMaxWidth(), singleLine = true,
                textStyle = TextStyle(color = pal.pri, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                cursorBrush = SolidColor(pal.pri)
            )
        }
        T("Mantén oprimida una app para sacarla de la carpeta", 12.sp, Modifier.padding(top = 8.dp, bottom = 12.dp), color = pal.pri.copy(alpha = .6f), mono = true, lines = 2)
        apps.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { app ->
                    key(app.key) {
                        TiltCard(
                            Modifier.weight(1f).aspectRatio(.9f), cutShape(14.dp), maxDeg = 20f,
                            onClick = { onOpen(app) }, onLongClick = { onRemove(app) }
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(app.icon, 46.dp)
                                Spacer(Modifier.height(6.dp))
                                T(app.label, 12.sp, Modifier.padding(horizontal = 4.dp), align = TextAlign.Center)
                            }
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier.glass(pal, cutShape(8.dp), strong = selected)
            .background(if (selected) pal.pri.copy(alpha = .18f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        T(label, 13.sp, weight = if (selected) FontWeight.SemiBold else FontWeight.Normal)
    }
}

/* ---------- Menú de una app (mantener oprimida) ---------- */

@Composable
fun Sheet(onDismiss: () -> Unit, content: @Composable () -> Unit) {
    val pal = LocalPal.current
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .45f))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().imePadding().padding(12.dp)
                .glass(pal, cutShape(22.dp), strong = true)
                .background(pal.amb.copy(alpha = .88f))
                .clickable(remember { MutableInteractionSource() }, null) {}
                .padding(20.dp)
        ) { content() }
    }
}

@Composable
fun MenuItem(label: String, onClick: () -> Unit) {
    T(label, 16.sp, Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 13.dp))
}

@Composable
fun AppMenu(
    app: AppEntry,
    inCarousel: Boolean,
    quickSlot: Int,
    onDismiss: () -> Unit,
    onToggleCarousel: () -> Unit,
    onQuick: (Int) -> Unit,
    onInfo: () -> Unit,
    onUninstall: () -> Unit,
) {
    val pal = LocalPal.current
    Sheet(onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AppIcon(app.icon, 44.dp)
            Spacer(Modifier.width(14.dp))
            T(app.label, 20.sp, weight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(10.dp))
        MenuItem(if (inCarousel) "Quitar del carrusel" else "Añadir al carrusel", onToggleCarousel)
        T("Bloques pequeños de arriba", 12.sp, Modifier.padding(top = 8.dp, bottom = 6.dp), color = pal.pri.copy(alpha = .6f), mono = true)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in 0..2) Chip("${s + 1}", quickSlot == s) { onQuick(s) }
            if (quickSlot >= 0) Chip("Quitar", false) { onQuick(-1) }
        }
        Spacer(Modifier.height(6.dp))
        MenuItem("Información de la app", onInfo)
        MenuItem("Desinstalar", onUninstall)
    }
}

/* ---------- Ajustes: paletas 70/20/10 ---------- */

@Composable
fun SettingsPanel(
    current: Palette,
    custom: List<Palette>,
    onPreview: (Palette?) -> Unit,
    onPick: (Palette) -> Unit,
    onSave: (Palette) -> Unit,
    onDelete: (Palette) -> Unit,
    version: Int,
    onCheckUpdate: () -> Unit,
    onHomeSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val pal = LocalPal.current
    var name by remember { mutableStateOf("") }
    val draft = remember { mutableStateListOf(current.amb, current.pri, current.sec) }
    var slot by remember { mutableIntStateOf(-1) }
    fun edit(i: Int, c: Color) {
        draft[i] = c
        onPreview(Palette("", draft[0], draft[1], draft[2]))
    }
    Sheet(onDismiss) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            T("Ajustes", 22.sp, weight = FontWeight.SemiBold)
            T("Paletas (70 ambiente · 20 principal · 10 secundario)", 12.sp, Modifier.padding(top = 4.dp, bottom = 10.dp), color = pal.pri.copy(alpha = .6f), lines = 2)
            (StandardPalettes + custom).forEach { p ->
                Row(
                    Modifier.fillMaxWidth().clickable { onPick(p) }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Swatch(p, Modifier.width(70.dp).height(22.dp))
                    Spacer(Modifier.width(12.dp))
                    T(p.name, 16.sp, Modifier.weight(1f), weight = if (p.name == current.name) FontWeight.Bold else FontWeight.Normal)
                    if (p in custom) T("Borrar", 13.sp, Modifier.clickable { onDelete(p) }.padding(6.dp), color = pal.pri.copy(alpha = .6f))
                }
            }

            T("Nueva paleta", 15.sp, Modifier.padding(top = 14.dp, bottom = 4.dp), weight = FontWeight.SemiBold)
            T("Toca un color para elegirlo. La pantalla cambia mientras eliges.", 12.sp, Modifier.padding(bottom = 10.dp), color = pal.pri.copy(alpha = .6f), lines = 2)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                listOf("Ambiente" to "70%", "Principal" to "20%", "Secundario" to "10%").forEachIndexed { i, (label, pct) ->
                    Column(
                        Modifier.weight(1f)
                            .glass(pal, cutShape(10.dp), strong = slot == i)
                            .clickable { slot = if (slot == i) -1 else i }
                            .padding(8.dp)
                    ) {
                        Box(Modifier.fillMaxWidth().height(38.dp).clip(cutShape(6.dp)).background(draft[i]).border(1.dp, pal.pri.copy(alpha = .3f), cutShape(6.dp)))
                        Spacer(Modifier.height(6.dp))
                        T(label, 12.sp, weight = FontWeight.SemiBold)
                        T(pct, 11.sp, color = pal.pri.copy(alpha = .6f), mono = true)
                    }
                }
            }
            if (slot >= 0) {
                key(slot) { ColorPicker(draft[slot]) { edit(slot, it) } }
            }

            Spacer(Modifier.height(12.dp))
            Box(
                Modifier.fillMaxWidth().glass(pal, cutShape(10.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (name.isEmpty()) T("Nombre de la paleta", 15.sp, color = pal.pri.copy(alpha = .5f))
                BasicTextField(
                    name, { if (it.length <= 28) name = it }, Modifier.fillMaxWidth(), singleLine = true,
                    textStyle = TextStyle(color = pal.pri, fontSize = 15.sp),
                    cursorBrush = SolidColor(pal.pri)
                )
            }
            val ok = name.isNotBlank()
            Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Swatch(Palette("", draft[0], draft[1], draft[2]), Modifier.width(70.dp).height(22.dp))
                Spacer(Modifier.weight(1f))
                Chip(if (ok) "Guardar" else "Ponle un nombre", ok) {
                    if (ok) { onSave(Palette(name.trim(), draft[0], draft[1], draft[2])); name = ""; slot = -1 }
                }
            }
            Spacer(Modifier.height(18.dp))
            MenuItem("Elegir Launcher Fare como pantalla de inicio", onHomeSettings)
            MenuItem("Buscar actualización", onCheckUpdate)
            T("Launcher Fare · versión $version", 12.sp, Modifier.padding(top = 4.dp), color = pal.pri.copy(alpha = .5f), mono = true)
        }
    }
}

/** Selector de color visual: cuadro de saturación/brillo, barra de tono y colores rápidos. */
@Composable
fun ColorPicker(initial: Color, onChange: (Color) -> Unit) {
    val pal = LocalPal.current
    val start = remember { FloatArray(3).also { android.graphics.Color.colorToHSV(initial.toArgb(), it) } }
    var h by remember { mutableFloatStateOf(start[0]) }
    var sat by remember { mutableFloatStateOf(start[1]) }
    var v by remember { mutableFloatStateOf(start[2]) }
    fun emit() = onChange(Color.hsv(h, sat, v))

    Column(Modifier.padding(top = 12.dp)) {
        // Cuadro: a la derecha más saturado, arriba más brillante
        Box(
            Modifier.fillMaxWidth().height(170.dp).clip(cutShape(12.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun set(p: Offset) {
                            sat = (p.x / size.width).coerceIn(0f, 1f)
                            v = 1f - (p.y / size.height).coerceIn(0f, 1f)
                            emit()
                        }
                        down.consume(); set(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            c.consume(); set(c.position)
                        }
                    }
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(listOf(Color.White, Color.hsv(h, 1f, 1f))))
                    drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                    val c = Offset(sat * size.width, (1f - v) * size.height)
                    drawCircle(Color.White, 11.dp.toPx(), c, style = Stroke(3.dp.toPx()))
                    drawCircle(Color.Black.copy(alpha = .5f), 13.dp.toPx(), c, style = Stroke(1.dp.toPx()))
                }
        )
        Spacer(Modifier.height(12.dp))
        // Barra de tono
        Box(
            Modifier.fillMaxWidth().height(30.dp).clip(cutShape(8.dp))
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        fun set(p: Offset) {
                            h = (p.x / size.width).coerceIn(0f, 1f) * 359.9f
                            emit()
                        }
                        down.consume(); set(down.position)
                        while (true) {
                            val ev = awaitPointerEvent()
                            val c = ev.changes.firstOrNull() ?: break
                            if (!c.pressed) break
                            c.consume(); set(c.position)
                        }
                    }
                }
                .drawBehind {
                    drawRect(Brush.horizontalGradient(List(7) { Color.hsv(it * 60f % 360f, 1f, 1f) }))
                    val x = h / 360f * size.width
                    drawRect(Color.White, Offset(x - 3.dp.toPx(), 0f), androidx.compose.ui.geometry.Size(6.dp.toPx(), size.height), style = Stroke(2.dp.toPx()))
                }
        )
        Spacer(Modifier.height(12.dp))
        // Colores rápidos
        val quick = listOf(
            Color.Black, Color(0xFF050A06), Color(0xFF1F6B2A), Color(0xFF4BE36A), Color(0xFF8A8A8A), Color.White,
            Color(0xFF0B1E3F), Color(0xFF3D7BFF), Color(0xFF7A1FA2), Color(0xFFE53935), Color(0xFFFF9800), Color(0xFFFFD54F),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            quick.forEach { q ->
                Box(
                    Modifier.size(22.dp).clip(cutShape(5.dp)).background(q)
                        .border(1.dp, pal.pri.copy(alpha = .4f), cutShape(5.dp))
                        .clickable {
                            val t = FloatArray(3).also { android.graphics.Color.colorToHSV(q.toArgb(), it) }
                            h = t[0]; sat = t[1]; v = t[2]; emit()
                        }
                )
            }
        }
        T(Color.hsv(h, sat, v).hex(), 12.sp, Modifier.padding(top = 8.dp), color = pal.pri.copy(alpha = .6f), mono = true)
    }
}

@Composable
fun Swatch(p: Palette, modifier: Modifier) {
    Row(modifier.glass(p, cutShape(5.dp))) {
        Box(Modifier.weight(7f).fillMaxSize().background(p.amb))
        Box(Modifier.weight(2f).fillMaxSize().background(p.pri))
        Box(Modifier.weight(1f).fillMaxSize().background(p.sec))
    }
}
