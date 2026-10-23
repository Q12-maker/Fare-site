package com.faresite.launcher

import android.content.ComponentName
import android.content.Context
import kotlin.math.abs
import androidx.compose.ui.graphics.toArgb
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Carpeta de apps: se forma arrastrando una app encima de otra en el cajón. */
data class AppGroup(val id: String, val name: String, val keys: List<String>)

class AppEntry(
    val label: String, val pkg: String, val activity: String, val icon: ImageBitmap,
    /** Colores predominantes del ícono (1 a 3), del que más pesa al que menos. */
    val colors: List<Color> = emptyList(),
) {
    val key get() = "$pkg/$activity"
    val color: Color? get() = colors.firstOrNull()
}

fun loadApps(ctx: Context): List<AppEntry> {
    val pm = ctx.packageManager
    val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    @Suppress("DEPRECATION")
    return pm.queryIntentActivities(intent, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map { ri ->
            val bmp = ri.loadIcon(pm).toBitmap(192, 192)
            AppEntry(
                ri.loadLabel(pm).toString(),
                ri.activityInfo.packageName,
                ri.activityInfo.name,
                bmp.asImageBitmap(),
                runCatching { iconColors(bmp) }.getOrDefault(emptyList())
            )
        }
        .distinctBy { it.key }
        .sortedBy { it.label.lowercase() }
}

/** Colores predominantes del ícono (los más vivos): uno si domina, o hasta tres si hay varios. */
fun iconColors(src: Bitmap): List<Color> {
    val b = Bitmap.createScaledBitmap(src, 24, 24, true)
    val hsv = FloatArray(3)
    val samples = ArrayList<Pair<FloatArray, Float>>()
    for (i in 0 until 24) for (j in 0 until 24) {
        val c = b.getPixel(i, j)
        if (android.graphics.Color.alpha(c) < 200) continue
        android.graphics.Color.colorToHSV(c, hsv)
        val w = hsv[1] * hsv[2]
        if (w < .15f) continue
        samples += hsv.copyOf() to w
    }
    if (samples.sumOf { it.second.toDouble() } < 3.0) return emptyList()
    return dominantHues(samples).map { c ->
        // Vivos y claros, para que el vidrio y el aura se noten
        val h = FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) }
        Color.hsv(h[0], h[1].coerceIn(.55f, 1f), h[2].coerceIn(.75f, 1f))
    }
}

/**
 * Agrupa colores (HSV + peso) por tono en 12 grupos de 30° y devuelve los predominantes:
 * solo uno si pesa la mayoría; si no, hasta [max] (los que pesan al menos un tercio del mayor).
 */
fun dominantHues(samples: List<Pair<FloatArray, Float>>, max: Int = 3): List<Color> {
    if (samples.isEmpty()) return emptyList()
    fun bin(h: Float) = (h / 30f).toInt().coerceIn(0, 11)
    val weight = FloatArray(12)
    samples.forEach { (h, w) -> weight[bin(h[0])] += w }
    // Junta cada grupo con sus vecinos para no partir un mismo color en dos
    val smooth = FloatArray(12) { weight[it] + .5f * (weight[(it + 1) % 12] + weight[(it + 11) % 12]) }
    val total = weight.sum()
    val order = smooth.indices.sortedByDescending { smooth[it] }
    val picked = ArrayList<Int>()
    for (k in order) {
        if (picked.size == max) break
        if (picked.any { p -> val d = abs(p - k); minOf(d, 12 - d) <= 1 }) continue
        if (picked.isNotEmpty() && smooth[k] < smooth[picked[0]] / 3f) break
        picked += k
        if (picked.size == 1 && smooth[k] >= total * .62f) break
    }
    return picked.map { k ->
        var x = 0.0; var y = 0.0; var s = 0.0; var v = 0.0; var t = 0.0
        samples.forEach { (h, w) ->
            val d = abs(bin(h[0]) - k)
            if (minOf(d, 12 - d) <= 1) {
                val rad = Math.toRadians(h[0].toDouble())
                x += cos(rad) * w; y += sin(rad) * w; s += h[1] * w; v += h[2] * w; t += w
            }
        }
        var hue = Math.toDegrees(atan2(y, x)).toFloat()
        if (hue < 0) hue += 360f
        Color.hsv(hue, (s / t).toFloat().coerceIn(0f, 1f), (v / t).toFloat().coerceIn(0f, 1f))
    }
}

/** Colores de una carpeta: los predominantes entre los colores de todas sus apps. */
fun folderColors(apps: List<AppEntry>): List<Color> = dominantHues(
    apps.flatMap { a ->
        a.colors.mapIndexed { i, c ->
            FloatArray(3).also { android.graphics.Color.colorToHSV(c.toArgb(), it) } to (if (i == 0) 1f else .55f)
        }
    }
)

fun launchApp(ctx: Context, app: AppEntry) {
    val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setComponent(ComponentName(app.pkg, app.activity))
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
    runCatching { ctx.startActivity(i) }
}

fun openAppInfo(ctx: Context, app: AppEntry) {
    runCatching {
        ctx.startActivity(
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${app.pkg}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

fun uninstallApp(ctx: Context, app: AppEntry) {
    @Suppress("DEPRECATION")
    runCatching {
        ctx.startActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.pkg}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

/** Primeras apps sugeridas si aún no has elegido las tuyas. */
fun suggest(apps: List<AppEntry>, hints: List<String>, n: Int): List<String> {
    val out = mutableListOf<String>()
    for (h in hints) {
        apps.firstOrNull { (h in it.pkg.lowercase() || h in it.label.lowercase()) && it.key !in out }?.let { out += it.key }
        if (out.size == n) return out
    }
    for (a in apps) {
        if (out.size == n) break
        if (a.key !in out) out += a.key
    }
    return out
}

/* ---------- Preferencias ---------- */

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("launcher_fare", Context.MODE_PRIVATE)

    fun list(k: String): List<String>? = sp.getString(k, null)?.split("|")?.filter { it.isNotEmpty() }
    fun putList(k: String, v: List<String>) = sp.edit().putString(k, v.joinToString("|")).apply()
    fun str(k: String, d: String) = sp.getString(k, d) ?: d
    fun putStr(k: String, v: String) = sp.edit().putString(k, v).apply()
    fun int(k: String, d: Int) = sp.getInt(k, d)
    fun putInt(k: String, v: Int) = sp.edit().putInt(k, v).apply()
    fun on(k: String, d: Boolean = true) = str(k, if (d) "si" else "no") == "si"
    fun putOn(k: String, v: Boolean) = putStr(k, if (v) "si" else "no")
    val raw get() = sp

    /** La paleta elegida en Ajustes (la usan también los paneles encima de otras apps). */
    fun palette(): Palette {
        val name = str("palette", "Fare")
        return (StandardPalettes + customPalettes()).firstOrNull { it.name == name } ?: StandardPalettes[0]
    }

    fun customPalettes(): List<Palette> = sp.getString("palettes", "")!!.lines().mapNotNull { line ->
        val p = line.split(";")
        if (p.size != 4) return@mapNotNull null
        val a = parseHex(p[1]); val b = parseHex(p[2]); val c = parseHex(p[3])
        if (a == null || b == null || c == null) null else Palette(p[0], a, b, c)
    }

    fun groups(): List<AppGroup> = sp.getString("groups", "")!!.lines().mapNotNull { line ->
        val p = line.split(";")
        if (p.size != 3) null else AppGroup(p[0], p[1], p[2].split("|").filter { it.isNotEmpty() })
    }

    fun putGroups(v: List<AppGroup>) = sp.edit().putString(
        "groups", v.joinToString("\n") { "${it.id};${it.name.replace(";", " ").replace("\n", " ")};${it.keys.joinToString("|")}" }
    ).apply()

    fun putCustomPalettes(v: List<Palette>) = sp.edit().putString(
        "palettes", v.joinToString("\n") { "${it.name.replace(";", " ")};${it.amb.hex()};${it.pri.hex()};${it.sec.hex()}" }
    ).apply()
}

/* ---------- Clima (Open-Meteo, sin llave) ---------- */

class Weather(val temp: Int, val code: Int)

// Mariquita, Tolima
private const val LAT = 5.1989
private const val LON = -74.8929

fun fetchWeather(): Weather? = runCatching {
    val url = URL("https://api.open-meteo.com/v1/forecast?latitude=$LAT&longitude=$LON&current=temperature_2m,weather_code&timezone=auto")
    val c = url.openConnection() as HttpURLConnection
    c.connectTimeout = 8000
    c.readTimeout = 8000
    try {
        val cur = JSONObject(c.inputStream.bufferedReader().readText()).getJSONObject("current")
        Weather(Math.round(cur.getDouble("temperature_2m")).toInt(), cur.getInt("weather_code"))
    } finally {
        c.disconnect()
    }
}.getOrNull()

fun weatherText(code: Int) = when (code) {
    0 -> "Despejado"
    1, 2 -> "Parcialmente nublado"
    3 -> "Nublado"
    45, 48 -> "Niebla"
    in 51..67, in 80..82 -> "Lluvia"
    in 71..77, 85, 86 -> "Nieve"
    in 95..99 -> "Tormenta"
    else -> "Clima"
}
