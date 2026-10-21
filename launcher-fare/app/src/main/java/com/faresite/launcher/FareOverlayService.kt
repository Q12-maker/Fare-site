package com.faresite.launcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.FrameLayout
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Lleva el estilo Fare encima de todas las apps (requiere activar Launcher Fare en Accesibilidad):
 * - Una franja arriba: deslizar hacia abajo abre el panel de notificaciones (izquierda) o el
 *   centro de control tipo iPad (derecha). Mantenerla oprimida abre la cortina normal de Android.
 * - Las notificaciones nuevas salen impresas como recibo encima de la app que estés usando.
 * - Sonidos de tecla al tocar, desplazar y escribir en cualquier app.
 */
class FareOverlayService : AccessibilityService() {
    companion object {
        fun enabled(ctx: Context): Boolean {
            val list = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            return list.split(':').any { it.startsWith(ctx.packageName + "/") }
        }

        fun askEnable(ctx: Context) {
            runCatching {
                ctx.startActivity(
                    android.content.Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private lateinit var wm: WindowManager
    private lateinit var prefs: Prefs
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val apps = MutableStateFlow<Map<String, AppEntry>>(emptyMap())

    private var strip: View? = null
    private var panel: View? = null
    private var panelOwner: OverlayOwner? = null
    private var receiptView: View? = null
    private var receiptOwner: OverlayOwner? = null
    private val receipt = MutableStateFlow<Receipt?>(null)
    private var lastScroll = 0L

    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k == "strip") main.post { syncStrip() }
    }

    override fun onServiceConnected() {
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        prefs = Prefs(this)
        Sfx.init(this)
        prefs.raw.registerOnSharedPreferenceChangeListener(prefListener)
        syncStrip()
        refreshApps()
        scope.launch {
            FareNotifications.posted.collect { n ->
                if (MainActivity.visible || panel != null || !prefs.on("recibos_global")) return@collect
                val label = apps.value[n.pkg]?.label ?: n.pkg.substringAfterLast('.')
                showReceipt(Receipt(label.uppercase().take(22), listOf(n.title, n.text).filter { it.isNotBlank() }.map { it.take(60) }))
            }
        }
    }

    override fun onAccessibilityEvent(e: AccessibilityEvent) {
        if (!::prefs.isInitialized || !prefs.on("sounds_global")) return
        if (e.packageName == packageName || panel != null) return
        when (e.eventType) {
            AccessibilityEvent.TYPE_VIEW_CLICKED -> Sfx.raw(Sfx.TAP, .4f)
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED -> Sfx.raw(Sfx.KEY, .45f)
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                val now = System.currentTimeMillis()
                if (now - lastScroll > 110) { lastScroll = now; Sfx.raw(Sfx.TICK, .22f) }
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        if (::prefs.isInitialized) prefs.raw.unregisterOnSharedPreferenceChangeListener(prefListener)
        closePanel()
        removeStrip()
        hideReceipt()
        scope.cancel()
        super.onDestroy()
    }

    private fun refreshApps() {
        scope.launch(Dispatchers.Default) {
            runCatching { apps.value = loadApps(this@FareOverlayService).associateBy { it.pkg } }
        }
    }

    /* ---------- Franja de arriba ---------- */

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else (24 * resources.displayMetrics.density).toInt()
    }

    private fun syncStrip() {
        if (prefs.on("strip")) addStrip() else removeStrip()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun addStrip() {
        if (strip != null) return
        val v = View(this)
        val density = resources.displayMetrics.density
        var downX = 0f
        var downY = 0f
        var fired = false
        val longPress = Runnable {
            fired = true
            performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        }
        v.setOnTouchListener { view, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY; fired = false
                    main.postDelayed(longPress, 600)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = ev.rawY - downY
                    if (abs(ev.rawX - downX) > 12 * density || dy > 12 * density) main.removeCallbacks(longPress)
                    if (!fired && dy > 28 * density) {
                        fired = true
                        val right = downX > view.rootView.resources.displayMetrics.widthPixels * .6f
                        openPanel(if (right) 1 else 0)
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> main.removeCallbacks(longPress)
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, statusBarHeight(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (runCatching { wm.addView(v, lp) }.isSuccess) strip = v
    }

    private fun removeStrip() {
        strip?.let { runCatching { wm.removeView(it) } }
        strip = null
    }

    /* ---------- Paneles encima de otras apps ---------- */

    /** which: 0 = notificaciones, 1 = centro de control. */
    private fun openPanel(which: Int) {
        if (panel != null) return
        hideReceipt()
        refreshApps()
        Sfx.raw(Sfx.OPEN, .5f)
        val owner = OverlayOwner()
        val frame = object : FrameLayout(this) {
            override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    if (event.action == KeyEvent.ACTION_UP) closePanel()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
        }
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val pal = remember { prefs.palette() }
                val animBg = remember { prefs.str("fondo", "red") != "simple" }
                val map by apps.collectAsState()
                var mode by remember { mutableStateOf(prefs.str("nmode", "bloques")) }
                var r by remember { mutableStateOf<Receipt?>(null) }
                CompositionLocalProvider(LocalPal provides pal, LocalAnimatedBg provides animBg) {
                    if (which == 0) {
                        NotificationPanel(map, mode, { mode = it; prefs.putStr("nmode", it) }, ::closePanel, ::closePanel)
                    } else {
                        ControlCenter(::closePanel, { t, l -> r = Receipt(t, listOf(l)) }, ::closePanel)
                    }
                    ReceiptPrinter(r) { r = null }
                }
            }
        }
        frame.addView(compose)
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        owner.start()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP
            if (Build.VERSION.SDK_INT >= 28) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        if (runCatching { wm.addView(frame, lp) }.isSuccess) {
            panel = frame; panelOwner = owner
            frame.isFocusableInTouchMode = true
            frame.requestFocus()
        } else {
            owner.stop()
        }
    }

    private fun closePanel() {
        val v = panel ?: return
        panel = null
        main.post {
            runCatching { wm.removeView(v) }
            panelOwner?.stop(); panelOwner = null
        }
    }

    /* ---------- Recibo de notificación nueva encima de otras apps ---------- */

    private fun showReceipt(r: Receipt) {
        receipt.value = r
        if (receiptView != null) return
        val owner = OverlayOwner()
        val compose = ComposeView(this).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnDetachedFromWindow)
            setContent {
                val pal = remember { prefs.palette() }
                val cur by receipt.collectAsState()
                CompositionLocalProvider(LocalPal provides pal) {
                    ReceiptPrinter(cur) { receipt.value = null; main.post { hideReceipt() } }
                }
            }
        }
        val frame = FrameLayout(this)
        frame.addView(compose)
        frame.setViewTreeLifecycleOwner(owner)
        frame.setViewTreeSavedStateRegistryOwner(owner)
        owner.start()
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP }
        if (runCatching { wm.addView(frame, lp) }.isSuccess) {
            receiptView = frame; receiptOwner = owner
        } else {
            owner.stop()
        }
    }

    private fun hideReceipt() {
        receiptView?.let { runCatching { wm.removeView(it) } }
        receiptView = null
        receiptOwner?.stop(); receiptOwner = null
        receipt.value = null
    }
}

/** Ciclo de vida mínimo para poder dibujar Compose en una ventana que no es de una Activity. */
private class OverlayOwner : LifecycleOwner, SavedStateRegistryOwner {
    private val registry = LifecycleRegistry(this)
    private val saved = SavedStateRegistryController.create(this)
    override val lifecycle: Lifecycle get() = registry
    override val savedStateRegistry: SavedStateRegistry get() = saved.savedStateRegistry

    fun start() {
        saved.performRestore(null)
        registry.currentState = Lifecycle.State.RESUMED
    }

    fun stop() {
        registry.currentState = Lifecycle.State.DESTROYED
    }
}
