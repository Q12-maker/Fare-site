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
import androidx.compose.runtime.withFrameMillis
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.runtime.withFrameNanos
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.layout.requiredSize
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.distinctUntilChanged
import androidx.compose.runtime.snapshotFlow
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
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.foundation.layout.heightIn
import android.content.res.Configuration
import androidx.compose.ui.platform.LocalConfiguration
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
import androidx.compose.ui.graphics.Shadow
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
private const val FOLDERS = 1
private const val ALL = 2

/** Dónde está el dedo en toda la pantalla (sirve para seguir arrastrando una app que sale de una carpeta). */
object Finger {
    var pos by mutableStateOf(Offset.Zero)
    var down by mutableStateOf(false)
}

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
    // La pantalla de Carpetas tiene su propia vista (bloques o líneas), aparte de Todas las apps
    var fmode by remember { mutableStateOf(prefs.str("fmode", mode)) }
    var fcols by remember { mutableIntStateOf(prefs.int("fcols", cols)) }
    var query by remember { mutableStateOf("") }

    var receipt by remember { mutableStateOf<Receipt?>(null) }
    var menuApp by remember { mutableStateOf<AppEntry?>(null) }
    var settings by remember { mutableStateOf(false) }
    var weather by remember { mutableStateOf<Weather?>(null) }
    var notifOpen by remember { mutableStateOf(false) }
    var lastOpened by remember { mutableStateOf<String?>(null) }
    var controlOpen by remember { mutableStateOf(false) }
    var nmode by remember { mutableStateOf(prefs.str("nmode", "bloques")) }
    var sounds by remember { mutableStateOf(prefs.str("sounds", "si") == "si") }
    var bg by remember { mutableStateOf(prefs.bg()) }
    remember { Sfx.init(ctx); 0 }
    Sfx.enabled = sounds
    val groups = remember { mutableStateListOf<AppGroup>().apply { addAll(prefs.groups()) } }
    var openGroup by remember { mutableStateOf<String?>(null) }
    var update by remember { mutableStateOf<Updater.Update?>(null) }
    var installing by remember { mutableStateOf(false) }
    var lastCheck by remember { mutableStateOf(0L) }
    val version = remember { runCatching { Updater.currentVersion(ctx) }.getOrDefault(0) }

    var carry by remember { mutableStateOf<AppEntry?>(null) }
    val pager = rememberPagerState(initialPage = HOME) { 3 }
    val focus = LocalFocusManager.current
    val tick by resumed.collectAsState()
    val home by homePressed.collectAsState()

    fun print(title: String, vararg lines: String) { receipt = Receipt(title, lines.toList()) }
    fun saveCarousel() { chosen = true; prefs.putList("carousel", carousel.toList()) }
    fun saveQuick() = prefs.putStr("quick", quick.joinToString(","))
    fun saveGroups() = prefs.putGroups(groups.toList())
    fun setGroup(g: AppGroup) {
        val i = groups.indexOfFirst { it.id == g.id }
        if (g.keys.isEmpty()) { if (i >= 0) groups.removeAt(i) } else if (i >= 0) groups[i] = g else groups.add(g)
        saveGroups()
    }
    fun moveGroup(from: String, to: String) {
        val i = groups.indexOfFirst { "g:" + it.id == from }
        val j = groups.indexOfFirst { "g:" + it.id == to }
        if (i < 0 || j < 0 || i == j) return
        val g = groups.removeAt(i)
        groups.add(j, g)
        saveGroups()
    }
    fun drop(dragged: String, target: String) {
        // Cada app vive en una sola carpeta: se saca de la que estuviera
        groups.filter { dragged in it.keys && "g:" + it.id != target }.forEach { setGroup(it.copy(keys = it.keys - dragged)) }
        val home = groups.firstOrNull { target in it.keys }
        if (home != null) {
            if (dragged !in home.keys) setGroup(home.copy(keys = home.keys + dragged))
            print("CARPETA", "Añadida a:", home.name)
        } else if (target.startsWith("g:")) {
            val g = groups.firstOrNull { "g:" + it.id == target } ?: return
            if (dragged !in g.keys) setGroup(g.copy(keys = g.keys + dragged))
            print("CARPETA", "Añadida a:", g.name)
        } else {
            val used = groups.mapNotNull { it.name.removePrefix("Carpeta ").toIntOrNull() }.toSet()
            val name = "Carpeta ${generateSequence(1) { it + 1 }.first { it !in used }}"
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
        // Actualización automática: una vez por versión, sin tocar nada
        val u = update
        if (u != null && Updater.allowed(ctx) && prefs.int("autoTried", 0) < u.version) {
            prefs.putInt("autoTried", u.version)
            doUpdate(u)
        }
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
        menuApp = null; settings = false; openGroup = null; notifOpen = false; controlOpen = false; preview = null; query = ""; focus.clearFocus()
        pager.animateScrollToPage(HOME)
    }
    LaunchedEffect(Unit) {
        var last = pager.currentPage + pager.currentPageOffsetFraction
        snapshotFlow { pager.currentPage + pager.currentPageOffsetFraction }.collect { last = it }
    }
    LaunchedEffect(pager.currentPage) { if (pager.currentPage == HOME) { query = ""; focus.clearFocus() } }
    LaunchedEffect(Unit) {
        snapshotFlow { pager.currentPage }.drop(1).collect { Sfx.play(Sfx.SWIPE, .5f) }
    }
    // Notificaciones nuevas salen impresas como recibo
    LaunchedEffect(Unit) {
        FareNotifications.posted.collect { n ->
            val label = apps.firstOrNull { it.pkg == n.pkg }?.label ?: n.pkg.substringAfterLast('.')
            print(label.uppercase().take(22), *listOf(n.title, n.text).filter { it.isNotBlank() }.map { it.take(60) }.toTypedArray())
        }
    }

    BackHandler {
        when {
            controlOpen -> controlOpen = false
            notifOpen -> notifOpen = false
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
    val open: (AppEntry) -> Unit = { lastOpened = it.key; launchApp(ctx, it) }

    CompositionLocalProvider(LocalPal provides pal, LocalAnimatedBg provides (bg.base != "simple")) {
        Box(
            Modifier.fillMaxSize().background(pal.amb).pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        ev.changes.firstOrNull()?.let { Finger.pos = it.position }
                        Finger.down = ev.changes.any { it.pressed }
                    }
                }
            }
        ) {
            Wallpaper(bg, onHome = pager.currentPage == HOME && pager.currentPageOffsetFraction == 0f)
            HorizontalPager(pager, Modifier.fillMaxSize(), beyondViewportPageCount = 1) { page ->
              // Cada pantalla recorta lo suyo: nada del carrusel se cuela en el cajón
              Box(Modifier.fillMaxSize().clipToBounds()) {
                if (page == FOLDERS || page == ALL) {
                    Drawer(
                        apps, groups, query, { if (it.length != query.length) Sfx.play(Sfx.KEY, .35f); query = it },
                        if (page == FOLDERS) fmode else mode,
                        { if (page == FOLDERS) { fmode = it; prefs.putStr("fmode", it) } else { mode = it; prefs.putStr("mode", it) } },
                        if (page == FOLDERS) fcols else cols,
                        { if (page == FOLDERS) { fcols = it; prefs.putInt("fcols", it) } else { cols = it; prefs.putInt("cols", it) } },
                        open, { menuApp = it },
                        onOpenGroup = { openGroup = it.id },
                        onDrop = { d, t -> drop(d, t) },
                        onSettings = { settings = true },
                        selectedKey = menuApp?.key ?: lastOpened,
                        section = page,
                        external = if (page == FOLDERS) carry else null,
                        onExternalDone = { app, placed -> carry = null; if (!placed) print("CARPETA", "Sacada:", app.label) },
                        onReorder = { f, t -> moveGroup(f, t) },
                    )
                } else {
                    Home(
                        weather,
                        carousel.mapNotNull { byKey[it] },
                        open, { menuApp = it },
                        onMove = { f, t ->
                            val i = carousel.indexOf(f.key); val j = carousel.indexOf(t.key)
                            if (i >= 0 && j >= 0) { carousel[i] = t.key; carousel[j] = f.key; saveCarousel() }
                        },
                        onClock = {
                            runCatching {
                                ctx.startActivity(Intent(AlarmClock.ACTION_SHOW_ALARMS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                            }
                        },
                        onWeather = { scope.launch { weather = withContext(Dispatchers.IO) { fetchWeather() } ?: weather } },
                        onSettings = { settings = true },
                        onNotifs = { notifOpen = true; Sfx.play(Sfx.OPEN, .45f) },
                        onControl = { controlOpen = true; Sfx.play(Sfx.OPEN, .45f) },
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
                    allApps = apps,
                    others = groups.filter { it.id != g.id }.map { o -> o to o.keys.mapNotNull { byKey[it] } },
                    onAdd = { app -> drop(app.key, "g:" + g.id) },
                    onMerge = { o ->
                        groups.removeAll { it.id == o.id }
                        setGroup(g.copy(keys = (g.keys + o.keys).distinct()))
                        print("CARPETAS FUSIONADAS", g.name, "+ " + o.name)
                    },
                    onRename = { n -> if (n.isNotBlank()) setGroup(g.copy(name = n.trim())) },
                    onOpen = { open(it) },
                    onLong = { menuApp = it },
                    onDragOut = { app ->
                        setGroup(g.copy(keys = g.keys - app.key))
                        openGroup = null
                        carry = app
                    },
                    onDismiss = { openGroup = null },
                )
            }

            menuApp?.let { app ->
                AppMenu(
                    app,
                    inCarousel = app.key in carousel,
                    quickSlot = quick.indexOf(app.key),
                    canNewFolder = app.key !in carousel && groups.none { app.key in it.keys },
                    folderName = groups.firstOrNull { app.key in it.keys }?.name,
                    onRemoveFromFolder = {
                        groups.filter { app.key in it.keys }.forEach { setGroup(it.copy(keys = it.keys - app.key)) }
                        if (openGroup != null && groups.none { it.id == openGroup }) openGroup = null
                        menuApp = null
                        print("CARPETA", "Sacada:", app.label)
                    },
                    onNewFolder = {
                        val used = groups.mapNotNull { it.name.removePrefix("Carpeta ").toIntOrNull() }.toSet()
                        val name = "Carpeta ${generateSequence(1) { it + 1 }.first { it !in used }}"
                        val g = AppGroup(System.currentTimeMillis().toString(36), name, listOf(app.key))
                        setGroup(g)
                        menuApp = null
                        openGroup = g.id
                        print("CARPETA CREADA", name, app.label)
                    },
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
                    onUninstall = { uninstallApp(ctx, app); if (Pins.isPin(app)) apps = apps.filter { it.key != app.key }; menuApp = null },
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
                    bg = bg,
                    onBg = { bg = it; prefs.putBg(it) },
                    sounds = sounds,
                    onSounds = { sounds = it; prefs.putStr("sounds", if (it) "si" else "no") },
                    version = version,
                    onCheckUpdate = { scope.launch { checkUpdate(true) } },
                    onHomeSettings = {
                        runCatching { ctx.startActivity(Intent(Settings.ACTION_HOME_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                    },
                    onDismiss = { settings = false; preview = null },
                )
            }

            if (notifOpen) {
                NotificationPanel(byKey.values.associateBy { it.pkg }, nmode, { nmode = it; prefs.putStr("nmode", it) }, onClose = { notifOpen = false })
            }
            if (controlOpen) {
                ControlCenter(onClose = { controlOpen = false }, onNotice = { t, l -> print(t, l) })
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

/** true = fondo animado de red (predeterminado); false = fondo simple. */
val LocalAnimatedBg = compositionLocalOf { true }

@Composable
fun Backdrop() {
    val pal = LocalPal.current
    if (LocalAnimatedBg.current) {
        NetworkBackground(pal.pri)
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Image(
                painterResource(R.drawable.logo_fare), null,
                Modifier.size(260.dp).alpha(.05f),
                colorFilter = ColorFilter.tint(pal.pri)
            )
        }
        return
    }
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

/**
 * Fondo animado como el de la app Fare: puntos que flotan despacio y se unen
 * con líneas finas cuando se acercan, en el color principal de la paleta.
 */
@Composable
fun NetworkBackground(color: Color) {
    val n = 44
    val pts = remember { FloatArray(n * 2) { kotlin.random.Random.nextFloat() } }
    val vel = remember { FloatArray(n * 2) { (kotlin.random.Random.nextFloat() - .5f) * .00007f } }
    val bright = remember { BooleanArray(n) { kotlin.random.Random.nextFloat() < .22f } }
    var frame by remember { mutableLongStateOf(0L) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameMillis { t ->
                val dt = if (last == 0L) 16L else (t - last).coerceIn(0L, 50L)
                last = t
                for (i in 0 until n * 2) {
                    pts[i] += vel[i] * dt
                    if (pts[i] < -.08f || pts[i] > 1.08f) vel[i] = -vel[i]
                }
                frame = t
            }
        }
    }
    Canvas(Modifier.fillMaxSize()) {
        frame
        val w = size.width
        val h = size.height
        val maxD = w * .36f
        val line = 1.dp.toPx()
        for (i in 0 until n) {
            val ax = pts[i * 2] * w
            val ay = pts[i * 2 + 1] * h
            for (j in i + 1 until n) {
                val bx = pts[j * 2] * w
                val by = pts[j * 2 + 1] * h
                val dx = ax - bx
                val dy = ay - by
                val d = kotlin.math.sqrt(dx * dx + dy * dy)
                if (d < maxD) drawLine(color.copy(alpha = (1f - d / maxD) * .32f), Offset(ax, ay), Offset(bx, by), line)
            }
        }
        for (i in 0 until n) {
            val c = Offset(pts[i * 2] * w, pts[i * 2 + 1] * h)
            if (bright[i]) {
                drawCircle(color.copy(alpha = .14f), 9.dp.toPx(), c)
                drawCircle(color.copy(alpha = .95f), 2.6.dp.toPx(), c)
            } else {
                drawCircle(color.copy(alpha = .45f), 1.6.dp.toPx(), c)
            }
        }
    }
}

/* ---------- Pantalla principal ---------- */

@Composable
fun Home(
    weather: Weather?,
    carousel: List<AppEntry>,
    onOpen: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
    onMove: (AppEntry, AppEntry) -> Unit,
    onClock: () -> Unit,
    onWeather: () -> Unit,
    onSettings: () -> Unit,
    onNotifs: () -> Unit,
    onControl: () -> Unit,
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
            .pointerInput(Unit) {
                // Deslizar hacia abajo: a la derecha abre el centro de control, en el resto las notificaciones
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val limit = 56.dp.toPx()
                    while (true) {
                        val ev = awaitPointerEvent()
                        val c = ev.changes.firstOrNull() ?: break
                        if (!c.pressed || c.isConsumed) break
                        val d = c.position - down.position
                        if (d.y > limit && d.y > abs(d.x) * 1.5f) {
                            if (down.position.x > size.width * .6f) onControl() else onNotifs()
                            break
                        }
                    }
                }
            }
    ) {
        val land = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
        @Composable
        fun WeatherRow() {
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
            }
        }
        @Composable
        fun Clock() {
            Column(
                Modifier.fillMaxWidth().clickable(remember { MutableInteractionSource() }, null) { onClock() },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(contentAlignment = Alignment.Center) {
                    // Halo oscuro detrás para que la hora se lea sobre cualquier fondo
                    Box(
                        Modifier.requiredSize(340.dp, 210.dp)
                            .background(Brush.radialGradient(listOf(pal.amb.copy(alpha = .9f), pal.amb.copy(alpha = .5f), Color.Transparent)))
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        BasicText(
                            hour,
                            style = TextStyle(
                                color = pal.pri,
                                fontSize = 104.sp, fontWeight = FontWeight.Black, letterSpacing = (-4).sp,
                                shadow = Shadow(pal.pri, Offset.Zero, 64f),
                            )
                        )
                        Spacer(Modifier.width(10.dp))
                        Box(
                            Modifier.clip(cutShape(8.dp)).background(pal.pri).padding(horizontal = 9.dp, vertical = 5.dp)
                        ) {
                            T(ampm, 17.sp, weight = FontWeight.Bold, color = pal.amb)
                        }
                    }
                }
                T(date, 16.sp, Modifier.padding(top = 2.dp), weight = FontWeight.Medium, color = pal.pri.copy(alpha = .85f))
            }
        }
        if (land) {
            // Horizontal: hora a la izquierda y carrusel a la derecha, a toda la altura
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(1f).fillMaxHeight().zIndex(1f)) {
                    WeatherRow()
                    Spacer(Modifier.weight(.5f))
                    Clock()
                    Spacer(Modifier.weight(1f))
                }
                Carousel(carousel, onOpen, onLong, onMove, Modifier.weight(1.2f).fillMaxHeight().padding(vertical = 8.dp))
            }
        } else {
            // Vertical: carrusel pequeño abajo; al moverlo se agranda y se centra sobre un fondo de vidrio
            val ctl = remember { CarouselCtl() }
            val scope = rememberCoroutineScope()
            var focused by remember { mutableStateOf(false) }
            var holding by remember { mutableStateOf(false) }
            var tick by remember { mutableIntStateOf(0) }
            LaunchedEffect(focused, holding, tick) {
                if (focused && !holding) { delay(3500); focused = false }
            }
            val f by animateFloatAsState(if (focused) 1f else 0f, spring(dampingRatio = .82f, stiffness = 220f), label = "focus")
            val small = .48f
            val carH = 440.dp
            val gap = 14.dp
            val touch: (Boolean) -> Unit = { h -> focused = true; holding = h; tick++ }
            BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
                val boxH = maxHeight
                Column(Modifier.fillMaxSize().blur((22 * f).dp)) {
                    WeatherRow()
                    Spacer(Modifier.weight(.6f))
                    Clock()
                    Spacer(Modifier.weight(1f))
                    Spacer(Modifier.height(carH * small + gap))
                }
                if (f > 0.01f) {
                    // Lo demás queda como fondo transparente de liquid glass; deslizar aquí también mueve el carrusel
                    Box(
                        Modifier.align(Alignment.Center)
                            .requiredSize(maxWidth + 40.dp, maxHeight + 240.dp)
                            .alpha(f)
                            .background(pal.amb.copy(alpha = .38f))
                            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = .10f), Color.Transparent, Color.White.copy(alpha = .03f), Color.White.copy(alpha = .09f))))
                            .background(Brush.radialGradient(listOf(pal.pri.copy(alpha = .07f), Color.Transparent)))
                            .pointerInput(Unit) { detectTapGestures { focused = false; holding = false } }
                            .pointerInput(Unit) {
                                detectHorizontalDragGestures(
                                    onDragStart = { touch(true) },
                                    onDragEnd = { ctl.settle(scope); touch(false) },
                                    onDragCancel = { ctl.settle(scope); touch(false) },
                                ) { ch, dx -> ch.consume(); ctl.drag(scope, dx) }
                            }
                    )
                }
                val lift = with(LocalDensity.current) { ((boxH - gap) - (boxH / 2 + carH / 2)).coerceAtLeast(0.dp).toPx() }
                Carousel(
                    carousel, onOpen, onLong, onMove,
                    Modifier.align(Alignment.BottomCenter).padding(bottom = gap).fillMaxWidth().height(carH)
                        .graphicsLayer {
                            val sc = small + (1f - small) * f
                            scaleX = sc; scaleY = sc
                            transformOrigin = TransformOrigin(.5f, 1f)
                            translationY = -lift * f
                        },
                    ctl = ctl,
                    onInteract = touch,
                )
            }
        }
    }
}

@Composable
fun AppIcon(icon: ImageBitmap, size: Dp) {
    Image(icon, null, Modifier.size(size))
}

/* ---------- Carrusel en abanico: paneles de vidrio inclinados con tilt 3D, glare y sway ---------- */

/** Posición del carrusel, compartida para que otras zonas también lo puedan deslizar. */
class CarouselCtl {
    val pos = Animatable(0f)
    val lastDx = floatArrayOf(0f)
    var step = 1f
    var n = 0
    fun drag(scope: kotlinx.coroutines.CoroutineScope, dx: Float) {
        if (n == 0) return
        lastDx[0] = dx
        scope.launch { pos.snapTo((pos.value - dx / step).coerceIn(-.35f, n - 1 + .35f)) }
    }
    fun settle(scope: kotlinx.coroutines.CoroutineScope) {
        if (n == 0) return
        val target = (pos.value - sign(lastDx[0]) * .2f).roundToInt().coerceIn(0, n - 1)
        scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = .78f, stiffness = 180f)) }
    }
}

/** Panel inclinado como "/" (paralelogramo), estilo del carrusel. */
val SlantShape = GenericShape { size, _ ->
    val s = size.width * .16f
    moveTo(s, 0f); lineTo(size.width, 0f); lineTo(size.width - s, size.height); lineTo(0f, size.height); close()
}

@Composable
fun Carousel(
    items: List<AppEntry>,
    onOpen: (AppEntry) -> Unit,
    onLong: (AppEntry) -> Unit,
    onMove: (from: AppEntry, to: AppEntry) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth().height(440.dp),
    ctl: CarouselCtl = remember { CarouselCtl() },
    onInteract: (holding: Boolean) -> Unit = {},
) {
    val pal = LocalPal.current
    if (items.isEmpty()) {
        Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            T("Carrusel vacío", 13.sp, color = pal.pri.copy(alpha = .6f), align = TextAlign.Center)
        }
        return
    }
    val scope = rememberCoroutineScope()
    val pos = ctl.pos
    val lastDx = ctl.lastDx
    val n = items.size
    ctl.n = n
    val list by rememberUpdatedState(items)
    // Ventana que se está moviendo de lugar (mantener oprimida y arrastrar)
    var moving by remember { mutableStateOf<String?>(null) }
    var moveDx by remember { mutableFloatStateOf(0f) }
    val moveStart = remember { floatArrayOf(Float.NaN) }
    val swapped = remember { booleanArrayOf(false) }
    LaunchedEffect(n) { if (pos.value > n - 1) pos.snapTo((n - 1).toFloat()) }
    LaunchedEffect(Unit) {
        var last = pos.value
        snapshotFlow { pos.value }.collect { ScrollBus.add(it - last); last = it }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { pos.value.roundToInt() }.distinctUntilChanged().drop(1).collect { Sfx.play(Sfx.TICK, .35f) }
    }
    val density = LocalDensity.current

    BoxWithConstraints(modifier) {
        // Lo más ancha posible sin pasarse del alto disponible
        // Vertical: alto como antes y más ancho. Horizontal: lo que quepa en el alto.
        val tall = maxHeight > maxWidth * .8f
        val cardH = if (tall) minOf(maxWidth * .66f, 290.dp) * 1.3f else maxHeight * .94f
        val cardW = if (tall) minOf(maxWidth * .82f, 340.dp) else cardH / 1.25f
        val cardPx = with(density) { cardW.toPx() }
        val step = cardPx * .7f
        ctl.step = step
        Box(
            Modifier.fillMaxSize().pointerInput(n) {
                detectHorizontalDragGestures(
                    onDragStart = { onInteract(true) },
                    onDragEnd = { ctl.settle(scope); onInteract(false) },
                    onDragCancel = { ctl.settle(scope); onInteract(false) },
                ) { ch, dx ->
                    ch.consume()
                    ctl.drag(scope, dx)
                }
            },
            contentAlignment = Alignment.Center
        ) {
            items.forEachIndexed { i, app ->
                val off = i - pos.value
                val a = abs(off)
                val isMoving = moving == app.key
                if (a < 4.2f) key(app.key) {
                    val near = minOf(a, 1f)
                    val far = (a - 1f).coerceAtLeast(0f)
                    val sc = 1f - near * .2f - far * .07f
                    val tint = app.color ?: pal.pri
                    Box(
                        Modifier
                            .zIndex(if (isMoving) 10f else -a)
                            .graphicsLayer {
                                translationX = sign(off) * (near * .7f + far * .26f) * cardPx + if (isMoving) moveDx else 0f
                                translationY = if (isMoving) -18.dp.toPx() else 0f
                                scaleX = sc * (1f - near * .1f); scaleY = sc
                                rotationY = -off.coerceIn(-1f, 1f) * 16f
                                cameraDistance = 14f * density.density
                                alpha = (1f - (a - 3.2f).coerceAtLeast(0f)).coerceIn(0f, 1f)
                            }
                            .width(cardW).height(cardH),
                        contentAlignment = Alignment.Center
                    ) {
                        // Aura del color de la app detrás de la ventana del centro
                        val auraCols = app.colors.ifEmpty { listOf(tint) }
                        auraCols.forEachIndexed { k, col ->
                            val n2 = auraCols.size
                            Box(
                                Modifier.requiredSize(cardW * (if (n2 > 1) 1.5f else 1.8f))
                                    .graphicsLayer {
                                        if (n2 > 1) {
                                            val ang = 2.0 * Math.PI * k / n2 - Math.PI / 2
                                            translationX = (cos(ang) * cardW.toPx() * .3f).toFloat()
                                            translationY = (sin(ang) * cardW.toPx() * .3f).toFloat()
                                        }
                                    }
                                    .alpha((1f - a).coerceIn(0f, 1f) * .9f)
                                    .background(Brush.radialGradient(listOf(col.copy(alpha = if (n2 > 1) .45f else .55f), col.copy(alpha = .15f), Color.Transparent)))
                            )
                        }
                        TiltCard(
                            Modifier.fillMaxSize(),
                            SlantShape,
                            maxDeg = 22f,
                            idle = a < .5f,
                            tint = app.color,
                            tints = app.colors,
                            liquid = true,
                            selected = isMoving,
                            onClick = {
                                if (a < .5f) onOpen(app)
                                else {
                                    onInteract(false)
                                    scope.launch { pos.animateTo(i.toFloat(), spring(dampingRatio = .78f, stiffness = 180f)) }
                                }
                            },
                            longDrag = LongDrag(
                                onStart = { moving = app.key; moveDx = 0f; moveStart[0] = Float.NaN; swapped[0] = false; Sfx.play(Sfx.OPEN, .3f) },
                                onMove = { p ->
                                    if (moveStart[0].isNaN()) moveStart[0] = p.x - moveDx
                                    moveDx = p.x - moveStart[0]
                                    // Al pasar media ventana cambia de lugar con la vecina
                                    val cur = list.indexOfFirst { it.key == app.key }
                                    val dir = if (moveDx > step * .5f) 1 else if (moveDx < -step * .5f) -1 else 0
                                    val to = cur + dir
                                    if (dir != 0 && cur >= 0 && to in list.indices) {
                                        onMove(list[cur], list[to])
                                        moveStart[0] += dir * step
                                        swapped[0] = true
                                        moveDx -= dir * step
                                    }
                                },
                                onEnd = { dragged ->
                                    moving = null; moveDx = 0f
                                    // Si no cambió de lugar, se abre el menú (con "Quitar del carrusel")
                                    if (!swapped[0]) onLong(app)
                                    val at = list.indexOfFirst { it.key == app.key }.coerceAtLeast(0)
                                    scope.launch { pos.animateTo(at.toFloat(), spring(dampingRatio = .78f, stiffness = 180f)) }
                                }
                            ),
                        ) {
                            // "Foto" del panel: degradado del color de la app con el ícono grande
                            Box(
                                Modifier.fillMaxSize().background(
                                    if (app.colors.size > 1) Brush.linearGradient(app.colors.map { it.copy(alpha = .38f) } + pal.amb.copy(alpha = .35f))
                                    else Brush.verticalGradient(listOf(tint.copy(alpha = .5f), tint.copy(alpha = .22f), pal.amb.copy(alpha = .35f)))
                                )
                            )
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AppIcon(app.icon, cardW * .42f)
                                Spacer(Modifier.height(16.dp))
                                T(app.label, 18.sp, Modifier.padding(horizontal = 22.dp), weight = FontWeight.SemiBold, align = TextAlign.Center)
                            }
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

/** Colores del aura: los del ícono, o los predominantes entre las apps de la carpeta. */
private fun itemTints(item: Item): List<Color> = when (item) {
    is AppItem -> item.app.colors
    is FolderItem -> folderColors(item.apps)
}

private fun itemTint(item: Item): Color? = itemTints(item).firstOrNull()

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
    selectedKey: String? = null,
    section: Int = ALL,
    external: AppEntry? = null,
    onExternalDone: (AppEntry, placed: Boolean) -> Unit = { _, _ -> },
    onReorder: (from: String, to: String) -> Unit = { _, _ -> },
) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    val q = query.trim().lowercase()
    val byKey = apps.associateBy { it.key }
    val folders = section == FOLDERS
    // Carpetas: solo carpetas, en el orden que elijas. Todas las apps: cada app en orden alfabético.
    val items: List<Item> = when {
        // Al buscar aparecen primero las carpetas cuyo nombre coincide y luego las apps
        q.isNotEmpty() -> groups.filter { q in it.name.lowercase() }
            .mapNotNull { g -> g.keys.mapNotNull { byKey[it] }.takeIf { it.isNotEmpty() }?.let { FolderItem(g, it) } } +
            apps.filter { q in it.label.lowercase() }.map { AppItem(it) }
        folders -> groups.mapNotNull { g -> g.keys.mapNotNull { byKey[it] }.takeIf { it.isNotEmpty() }?.let { FolderItem(g, it) } }
        else -> apps.sortedBy { it.label.lowercase() }.map { AppItem(it) }
    }

    // Arrastre para formar carpetas
    var dragKey by remember { mutableStateOf<String?>(null) }
    var dragIcon by remember { mutableStateOf<ImageBitmap?>(null) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var origin by remember { mutableStateOf(Offset.Zero) }
    val bounds = remember { mutableMapOf<String, Rect>() }
    fun targetAt(p: Offset, self: String) = bounds.entries.firstOrNull { it.key != self && it.value.contains(p) }?.key
    val hover = dragKey?.let { targetAt(dragPos, it) }
    // El arrastre se sigue desde el cajón (no desde la app), así funciona aunque la app salga de la pantalla
    val dragApp = remember { arrayOfNulls<AppEntry>(1) }
    val dragMoved = remember { booleanArrayOf(false) }
    val downPos = remember { arrayOf(Offset.Zero) }
    var listRect by remember { mutableStateOf(Rect.Zero) }
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    val rowPx = with(LocalDensity.current) { 110.dp.toPx() }
    LaunchedEffect(mode, cols) {
        var last = Float.NaN
        snapshotFlow {
            if (mode == "bloques") gridState.firstVisibleItemIndex / cols.coerceAtLeast(1) + gridState.firstVisibleItemScrollOffset / rowPx
            else listState.firstVisibleItemIndex * .6f + listState.firstVisibleItemScrollOffset / rowPx
        }.collect { v -> last = v }
    }
    val density = LocalDensity.current
    val longNow by rememberUpdatedState(onLong)
    val dropNow by rememberUpdatedState(onDrop)
    val reorderNow by rememberUpdatedState(onReorder)
    val externalDone by rememberUpdatedState(onExternalDone)
    val openGroupNow by rememberUpdatedState(onOpenGroup)
    val dragFolder = remember { arrayOfNulls<AppGroup>(1) }
    val dragExternal = remember { booleanArrayOf(false) }
    fun finishDrag() {
        val k = dragKey ?: return
        val app = dragApp[0]
        val folder = dragFolder[0]
        val target = if (dragMoved[0]) targetAt(dragPos, k) else null
        val moved = dragMoved[0]
        val ext = dragExternal[0]
        dragKey = null; dragIcon = null; dragApp[0] = null; dragFolder[0] = null; dragExternal[0] = false
        when {
            folder != null -> when {
                !moved -> openGroupNow(folder)
                target != null && target.startsWith("g:") -> { reorderNow(k, target); Sfx.play(Sfx.TICK, .4f) }
            }
            app == null -> {}
            ext -> {
                if (target != null) dropNow(k, target)
                externalDone(app, target != null)
            }
            !moved -> longNow(app)
            target != null -> dropNow(k, target)
        }
    }
    // App que sacaste de una carpeta: la sigues llevando con el dedo hasta soltarla encima de otra carpeta
    LaunchedEffect(external) {
        val app = external ?: return@LaunchedEffect
        dragApp[0] = app; dragFolder[0] = null; dragExternal[0] = true
        dragMoved[0] = true
        dragKey = app.key; dragIcon = app.icon
        dragPos = Finger.pos
        while (Finger.down) {
            withFrameNanos { }
            dragPos = Finger.pos
        }
        finishDrag()
    }
    // Mientras arrastras cerca del borde de abajo (o de arriba) la lista se mueve sola
    LaunchedEffect(dragKey) {
        if (dragKey == null) return@LaunchedEffect
        val edge = with(density) { 110.dp.toPx() }
        val maxSpeed = with(density) { 14.dp.toPx() }
        while (dragKey != null) {
            withFrameNanos { }
            if (!dragMoved[0] || listRect.isEmpty) continue
            val y = dragPos.y
            val speed = when {
                y > listRect.bottom - edge -> ((y - (listRect.bottom - edge)) / edge).coerceIn(0f, 1f) * maxSpeed
                y < listRect.top + edge -> -((listRect.top + edge - y) / edge).coerceIn(0f, 1f) * maxSpeed
                else -> 0f
            }
            if (speed != 0f) {
                if (mode == "bloques") gridState.scrollBy(speed) else listState.scrollBy(speed)
            }
        }
    }

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
            val drag = if (item is FolderItem) {
                LongDrag(
                    onStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragMoved[0] = false
                        dragApp[0] = null; dragFolder[0] = item.group
                        dragKey = item.key; dragIcon = item.apps.first().icon
                        dragPos = bounds[item.key]?.center ?: Offset.Zero
                    },
                    onMove = {},
                    onEnd = { finishDrag() }
                )
            } else (item as? AppItem)?.let { ai ->
                LongDrag(
                    onStart = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        dragMoved[0] = false
                        dragApp[0] = ai.app; dragFolder[0] = null
                        dragKey = ai.key; dragIcon = ai.app.icon
                        dragPos = bounds[ai.key]?.center ?: Offset.Zero
                    },
                    onMove = {},
                    onEnd = { finishDrag() }
                )
            }
            content(Modifier, drag)
        }
    }

    Box(
        Modifier.fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .pointerInput(Unit) {
                val slop = viewConfiguration.touchSlop
                awaitPointerEventScope {
                    while (true) {
                        val ev = awaitPointerEvent(PointerEventPass.Initial)
                        val c = ev.changes.firstOrNull() ?: continue
                        val p = c.position + origin
                        if (c.changedToDown()) downPos[0] = p
                        if (dragKey != null) {
                            if (!dragMoved[0] && (p - downPos[0]).getDistance() > slop) dragMoved[0] = true
                            if (dragMoved[0]) dragPos = p
                            if (ev.changes.none { it.pressed }) finishDrag()
                        }
                    }
                }
            }
    ) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            T(if (folders) "Carpetas" else "Todas las apps", 24.sp, Modifier.padding(top = 12.dp), weight = FontWeight.SemiBold)
            // Barra de búsqueda
            Row(
                Modifier.padding(top = 10.dp).fillMaxWidth().height(54.dp).glass(pal, cutShape(16.dp), strong = true).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Canvas(Modifier.size(18.dp)) {
                    val r = size.width * .36f
                    drawCircle(pal.pri, r, Offset(r + 1, r + 1), style = Stroke(size.width * .12f))
                    drawLine(pal.pri, Offset(r * 1.75f, r * 1.75f), Offset(size.width, size.height), size.width * .12f)
                }
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) T("Buscar apps o carpetas", 16.sp, color = pal.pri.copy(alpha = .5f))
                    BasicTextField(
                        query, onQuery, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = TextStyle(color = pal.pri, fontSize = 16.sp),
                        cursorBrush = SolidColor(pal.pri),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = {
                            when (val first = items.firstOrNull()) {
                                is AppItem -> onOpen(first.app)
                                is FolderItem -> onOpenGroup(first.group)
                                else -> {}
                            }
                        })
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

            if (items.isEmpty() && folders && q.isEmpty()) {
                T(
                    "Aún no hay carpetas",
                    14.sp, Modifier.padding(top = 30.dp), color = pal.pri.copy(alpha = .6f), align = TextAlign.Center, lines = 4
                )
            } else if (mode == "bloques") {
                LazyVerticalGrid(
                    GridCells.Fixed(cols),
                    Modifier.fillMaxSize().onGloballyPositioned { listRect = it.boundsInRoot() },
                    state = gridState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        Cell(item) { _, drag ->
                            TiltCard(
                                Modifier.aspectRatio(.88f), cutShape(if (cols == 2) 20.dp else 14.dp), maxDeg = 22f,
                                onClick = { if (item is AppItem) onOpen(item.app) else if (item is FolderItem) onOpenGroup(item.group) },
                                longDrag = drag,
                                tint = itemTint(item), tints = itemTints(item),
                                liquid = true,
                                aura = true,
                                selected = item.key == selectedKey || item.key == dragKey,
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
                    Modifier.fillMaxSize().onGloballyPositioned { listRect = it.boundsInRoot() },
                    state = listState,
                    contentPadding = PaddingValues(top = 4.dp, bottom = 90.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(items, key = { it.key }) { item ->
                        Cell(item) { _, drag ->
                            TiltCard(
                                Modifier.fillMaxWidth().height(62.dp), cutShape(12.dp), maxDeg = 5f,
                                onClick = { if (item is AppItem) onOpen(item.app) else if (item is FolderItem) onOpenGroup(item.group) },
                                longDrag = drag,
                                tint = itemTint(item), tints = itemTints(item),
                                liquid = true,
                                aura = true,
                                selected = item.key == selectedKey || item.key == dragKey,
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
    allApps: List<AppEntry>,
    others: List<Pair<AppGroup, List<AppEntry>>>,
    onAdd: (AppEntry) -> Unit,
    onMerge: (AppGroup) -> Unit,
    onRename: (String) -> Unit,
    onOpen: (AppEntry) -> Unit,
    onDragOut: (AppEntry) -> Unit,
    onDismiss: () -> Unit,
    onLong: (AppEntry) -> Unit = {},
) {
    val pal = LocalPal.current
    val haptic = LocalHapticFeedback.current
    var name by remember(group.id) { mutableStateOf(group.name) }
    // "main": la carpeta · "add": elegir apps · "merge": elegir carpeta · "name": nombre de la fusionada
    var step by remember(group.id) { mutableStateOf("main") }
    var area by remember { mutableStateOf(Rect.Zero) }
    var holding by remember { mutableStateOf<String?>(null) }
    Sheet({ onRename(name); onDismiss() }) {
        when (step) {
            "add" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Añadir apps", 20.sp, Modifier.weight(1f), weight = FontWeight.SemiBold)
                    Chip("Listo", true) { step = "main" }
                }
                val inside = apps.map { it.key }.toSet()
                var find by remember { mutableStateOf("") }
                Row(
                    Modifier.padding(top = 12.dp).fillMaxWidth().height(48.dp).glass(pal, cutShape(12.dp), strong = true).padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Canvas(Modifier.size(16.dp)) {
                        val r = size.width * .36f
                        drawCircle(pal.pri, r, Offset(r + 1, r + 1), style = Stroke(size.width * .12f))
                        drawLine(pal.pri, Offset(r * 1.75f, r * 1.75f), Offset(size.width, size.height), size.width * .12f)
                    }
                    Spacer(Modifier.width(10.dp))
                    Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        if (find.isEmpty()) T("Buscar apps", 15.sp, color = pal.pri.copy(alpha = .5f))
                        BasicTextField(
                            find, { Sfx.typed(find, it); find = it }, Modifier.fillMaxWidth(), singleLine = true,
                            textStyle = TextStyle(color = pal.pri, fontSize = 15.sp),
                            cursorBrush = SolidColor(pal.pri)
                        )
                    }
                    if (find.isNotEmpty()) T("✕", 15.sp, Modifier.clickable { find = "" }.padding(6.dp))
                }
                val f = find.trim().lowercase()
                LazyColumn(Modifier.padding(top = 10.dp).heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(allApps.filter { it.key !in inside && (f.isEmpty() || f in it.label.lowercase()) }.sortedBy { it.label.lowercase() }, key = { it.key }) { app ->
                        Row(
                            Modifier.fillMaxWidth().glass(pal, cutShape(10.dp), tint = app.color, tints = app.colors)
                                .clickable { onAdd(app); Sfx.play(Sfx.TAP) }.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AppIcon(app.icon, 34.dp)
                            Spacer(Modifier.width(12.dp))
                            T(app.label, 15.sp, Modifier.weight(1f))
                            T("+", 20.sp, weight = FontWeight.Bold)
                        }
                    }
                }
            }
            "merge" -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    T("Fusionar con…", 20.sp, Modifier.weight(1f), weight = FontWeight.SemiBold)
                    Chip("Cancelar", false) { step = "main" }
                }
                if (others.isEmpty()) T("No hay otras carpetas", 14.sp, Modifier.padding(top = 14.dp), color = pal.pri.copy(alpha = .6f))
                LazyColumn(Modifier.padding(top = 12.dp).heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    items(others, key = { it.first.id }) { (o, oApps) ->
                        Row(
                            Modifier.fillMaxWidth().glass(pal, cutShape(10.dp))
                                .clickable { onMerge(o); name = ""; step = "name"; Sfx.play(Sfx.OPEN, .4f) }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (oApps.isNotEmpty()) FolderIcons(oApps, 38.dp)
                            Spacer(Modifier.width(12.dp))
                            T(o.name, 15.sp, Modifier.weight(1f))
                        }
                    }
                }
            }
            "name" -> {
                T("Nombre de la carpeta fusionada", 18.sp, weight = FontWeight.SemiBold)
                val focus = remember { FocusRequester() }
                LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                Box(
                    Modifier.padding(top = 12.dp).fillMaxWidth().glass(pal, cutShape(10.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (name.isEmpty()) T(group.name, 18.sp, color = pal.pri.copy(alpha = .4f))
                    BasicTextField(
                        name, { if (it.length <= 28) { Sfx.typed(name, it); name = it } }, Modifier.fillMaxWidth().focusRequester(focus), singleLine = true,
                        textStyle = TextStyle(color = pal.pri, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(pal.pri)
                    )
                }
                Row(Modifier.padding(top = 12.dp).fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Chip("Guardar", true) {
                        if (name.isBlank()) name = group.name
                        onRename(name); step = "main"
                    }
                }
            }
            else -> {
                Box(
                    Modifier.fillMaxWidth().glass(pal, cutShape(10.dp)).padding(horizontal = 12.dp, vertical = 11.dp),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (name.isEmpty()) T("Nombre de la carpeta", 18.sp, color = pal.pri.copy(alpha = .5f))
                    BasicTextField(
                        name, { if (it.length <= 28) { Sfx.typed(name, it); name = it; onRename(it) } }, Modifier.fillMaxWidth(), singleLine = true,
                        textStyle = TextStyle(color = pal.pri, fontSize = 18.sp, fontWeight = FontWeight.SemiBold),
                        cursorBrush = SolidColor(pal.pri)
                    )
                }
                Row(Modifier.padding(vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Chip("+ Añadir app", false) { step = "add" }
                    Chip("Fusionar carpeta", false) { step = "merge" }
                }
                Column(
                    Modifier.heightIn(max = 460.dp).onGloballyPositioned { area = it.boundsInRoot() }
                        .verticalScroll(rememberScrollState())
                ) {
                    apps.chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            row.forEach { app ->
                                key(app.key) {
                                    TiltCard(
                                        Modifier.weight(1f).aspectRatio(.9f), cutShape(14.dp), maxDeg = 20f,
                                        tint = app.color, tints = app.colors, liquid = true,
                                        selected = holding == app.key,
                                        onClick = { onOpen(app) },
                                        // Mantener oprimida no la saca: solo sale si la arrastras fuera de la carpeta
                                        longDrag = LongDrag(
                                            onStart = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); holding = app.key },
                                            onMove = { p ->
                                                if (holding == app.key && !area.isEmpty && !area.inflate(24f).contains(p)) {
                                                    holding = null
                                                    onDragOut(app)
                                                }
                                            },
                                            onEnd = {
                                                // Soltarla sin sacarla abre su menú (carrusel, información, etc.)
                                                if (holding == app.key) onLong(app)
                                                holding = null
                                            }
                                        )
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
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = .55f))
            .clickable(remember { MutableInteractionSource() }, null) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier.widthIn(max = 520.dp).fillMaxWidth().statusBarsPadding().navigationBarsPadding().imePadding().padding(16.dp)
                .glass(pal, cutShape(22.dp), strong = true)
                .background(pal.amb.copy(alpha = .95f))
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
    canNewFolder: Boolean = false,
    onNewFolder: () -> Unit = {},
    folderName: String? = null,
    onRemoveFromFolder: () -> Unit = {},
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
        if (canNewFolder) MenuItem("Crear carpeta con esta app", onNewFolder)
        if (folderName != null) MenuItem("Quitar de la carpeta \"$folderName\"", onRemoveFromFolder)
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
    bg: BgConfig,
    onBg: (BgConfig) -> Unit,
    sounds: Boolean,
    onSounds: (Boolean) -> Unit,
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
                    name, { if (it.length <= 28) { Sfx.typed(name, it); name = it } }, Modifier.fillMaxWidth(), singleLine = true,
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
            BackgroundSettings(bg, onBg)
            Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                T("Sonidos", 16.sp, Modifier.weight(1f))
                Chip("Sí", sounds) { onSounds(true) }
                Chip("No", !sounds) { onSounds(false) }
            }
            OtherAppsSection()
            MenuItem("Buscar actualización", onCheckUpdate)
            T("Launcher Fare · versión $version", 12.sp, Modifier.padding(top = 4.dp), color = pal.pri.copy(alpha = .5f), mono = true)
        }
    }
}

/** Ajustes del panel, los recibos y los sonidos encima de otras apps. */
@Composable
private fun OtherAppsSection() {
    val pal = LocalPal.current
    val ctx = LocalContext.current
    val prefs = remember { Prefs(ctx) }
    var active by remember { mutableStateOf(FareOverlayService.enabled(ctx)) }
    LaunchedEffect(Unit) { while (true) { active = FareOverlayService.enabled(ctx); delay(1500) } }
    var strip by remember { mutableStateOf(prefs.on("strip")) }
    var snd by remember { mutableStateOf(prefs.on("sounds_global")) }
    var rec by remember { mutableStateOf(prefs.on("recibos_global")) }

    T("En todas las apps", 15.sp, Modifier.padding(top = 14.dp, bottom = 4.dp), weight = FontWeight.SemiBold)
    T(
        if (active) "Activado" else "Desactivado",
        12.sp, Modifier.padding(bottom = 8.dp), color = pal.pri.copy(alpha = .6f), lines = 4
    )
    if (!active) MenuItem("Activar en Accesibilidad") { FareOverlayService.askEnable(ctx) }
    @Composable
    fun row(label: String, v: Boolean, set: (Boolean) -> Unit) {
        Row(Modifier.padding(vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            T(label, 16.sp, Modifier.weight(1f))
            Chip("Sí", v) { set(true) }
            Chip("No", !v) { set(false) }
        }
    }
    row("Panel arriba", strip) { strip = it; prefs.putOn("strip", it) }
    row("Recibos de notificaciones", rec) { rec = it; prefs.putOn("recibos_global", it) }
    row("Sonidos al tocar y escribir", snd) { snd = it; prefs.putOn("sounds_global", it) }
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
