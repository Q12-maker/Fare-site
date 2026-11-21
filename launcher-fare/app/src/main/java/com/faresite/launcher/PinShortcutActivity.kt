package com.faresite.launcher

import android.app.Activity
import android.content.Context
import android.content.pm.LauncherApps
import android.os.Bundle
import android.os.Process
import android.widget.Toast
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap

/**
 * Recibe los atajos que otra app quiere fijar en el inicio (por ejemplo, Chrome al instalar una app web
 * como Armario Fare). Los acepta, los guarda y aparecen en Todas las apps como una app más.
 */
class PinShortcutActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val la = getSystemService(LauncherApps::class.java)
        val req = runCatching { la.getPinItemRequest(intent) }.getOrNull()
        val info = req?.shortcutInfo
        if (req != null && req.requestType == LauncherApps.PinItemRequest.REQUEST_TYPE_SHORTCUT && info != null && req.isValid) {
            if (runCatching { req.accept() }.getOrDefault(false)) {
                Pins.add(this, info.`package`, info.id)
                Toast.makeText(this, "${info.shortLabel ?: "App"} añadida a Launcher Fare", Toast.LENGTH_SHORT).show()
            }
        }
        finish()
    }
}

/** Atajos fijados (apps web y similares): se guardan como "paquete|id". */
object Pins {
    private const val PREFIX = "shortcut:"
    private fun sp(ctx: Context) = ctx.getSharedPreferences("fare_pins", Context.MODE_PRIVATE)
    private fun all(ctx: Context) = sp(ctx).getStringSet("pins", emptySet())!!.toSet()

    fun add(ctx: Context, pkg: String, id: String) {
        sp(ctx).edit().putStringSet("pins", all(ctx) + "$pkg|$id").apply()
    }

    fun isPin(app: AppEntry) = app.activity.startsWith(PREFIX)
    private fun id(app: AppEntry) = app.activity.removePrefix(PREFIX)

    /** Atajos fijados que siguen existiendo, como entradas del cajón. */
    fun load(ctx: Context): List<AppEntry> {
        val la = ctx.getSystemService(LauncherApps::class.java)
        if (!runCatching { la.hasShortcutHostPermission() }.getOrDefault(false)) return emptyList()
        val user = Process.myUserHandle()
        return all(ctx).map { it.substringBefore('|') to it.substringAfter('|') }.groupBy({ it.first }, { it.second })
            .flatMap { (pkg, ids) ->
                val q = LauncherApps.ShortcutQuery().setPackage(pkg).setShortcutIds(ids)
                    .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
                runCatching { la.getShortcuts(q, user) }.getOrNull().orEmpty().mapNotNull { s ->
                    runCatching {
                        val bmp = la.getShortcutIconDrawable(s, ctx.resources.displayMetrics.densityDpi).toBitmap(192, 192)
                        AppEntry(
                            (s.shortLabel ?: s.longLabel ?: pkg).toString(), pkg, PREFIX + s.id, bmp.asImageBitmap(),
                            runCatching { iconColors(bmp) }.getOrDefault(emptyList())
                        )
                    }.getOrNull()
                }
            }
    }

    fun launch(ctx: Context, app: AppEntry) {
        val la = ctx.getSystemService(LauncherApps::class.java)
        runCatching { la.startShortcut(app.pkg, id(app), null, null, Process.myUserHandle()) }
    }

    /** Quita el atajo del launcher (no desinstala la app que lo creó). */
    fun remove(ctx: Context, app: AppEntry) {
        val left = all(ctx) - "${app.pkg}|${id(app)}"
        sp(ctx).edit().putStringSet("pins", left).apply()
        val la = ctx.getSystemService(LauncherApps::class.java)
        val ids = left.filter { it.startsWith("${app.pkg}|") }.map { it.substringAfter('|') }
        runCatching { la.pinShortcuts(app.pkg, ids, Process.myUserHandle()) }
    }
}
