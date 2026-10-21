package com.faresite.launcher

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Busca en GitHub si hay una versión nueva del launcher y la instala (Android siempre pide confirmar). */
object Updater {
    private const val RELEASE = "https://api.github.com/repos/Q12-maker/Fare-site/releases/tags/launcher-fare"

    class Update(val version: Int, val url: String)

    fun currentVersion(ctx: Context): Int =
        PackageInfoCompat.getLongVersionCode(ctx.packageManager.getPackageInfo(ctx.packageName, 0)).toInt()

    /** Devuelve la versión publicada si es más nueva que la instalada. */
    fun check(ctx: Context): Update? = runCatching {
        val c = URL(RELEASE).openConnection() as HttpURLConnection
        c.connectTimeout = 8000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        val json = try { JSONObject(c.inputStream.bufferedReader().readText()) } finally { c.disconnect() }
        val version = Regex("""versión (\d+)""").find(json.getString("name"))?.groupValues?.get(1)?.toInt() ?: return null
        val assets = json.getJSONArray("assets")
        val url = (0 until assets.length()).map { assets.getJSONObject(it) }
            .firstOrNull { it.getString("name").endsWith(".apk") }?.getString("browser_download_url") ?: return null
        if (version > currentVersion(ctx)) Update(version, url) else null
    }.getOrNull()

    /** Si el launcher aún no puede instalar apps, abre el permiso y devuelve false. */
    fun canInstall(ctx: Context): Boolean {
        if (Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()) return true
        runCatching {
            ctx.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
        return false
    }

    /** Descarga el APK y abre la instalación. Corre en un hilo de fondo. */
    fun install(ctx: Context, u: Update): Boolean = runCatching {
        val file = File(ctx.cacheDir, "launcher-fare-update.apk")
        val c = URL(u.url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.instanceFollowRedirects = true
        try { c.inputStream.use { inp -> file.outputStream().use { inp.copyTo(it) } } } finally { c.disconnect() }

        val pi = ctx.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        params.setAppPackageName(ctx.packageName)
        val id = pi.createSession(params)
        pi.openSession(id).use { s ->
            s.openWrite("launcher-fare.apk", 0, file.length()).use { out ->
                file.inputStream().use { it.copyTo(out) }
                s.fsync(out)
            }
            val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            val pending = PendingIntent.getBroadcast(ctx, id, Intent(ctx, InstallReceiver::class.java), flags)
            s.commit(pending.intentSender)
        }
        true
    }.getOrDefault(false)
}

/** Recibe la respuesta del instalador; cuando Android pide confirmar, muestra su ventana. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(ctx: Context, intent: Intent) {
        if (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, -999) == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT) ?: return
            confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { ctx.startActivity(confirm) }
        }
    }
}
