package com.faresite.launcher

import android.app.ActivityOptions
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow

class Notif(
    val key: String,
    val pkg: String,
    val title: String,
    val text: String,
    val time: Long,
    val clearable: Boolean,
    val autoCancel: Boolean,
    val intent: PendingIntent?,
)

/**
 * Lee las notificaciones del teléfono (con el permiso de "Acceso a notificaciones")
 * para mostrarlas en el panel propio del launcher.
 */
class FareNotifications : NotificationListenerService() {
    companion object {
        val items = MutableStateFlow<List<Notif>>(emptyList())
        /** Notificaciones nuevas, para imprimirlas con la animación de impresora. */
        val posted = MutableSharedFlow<Notif>(extraBufferCapacity = 8)
        var instance: FareNotifications? = null

        fun hasAccess(ctx: Context) = NotificationManagerCompat.getEnabledListenerPackages(ctx).contains(ctx.packageName)

        fun askAccess(ctx: Context) {
            runCatching {
                ctx.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }

        fun dismiss(n: Notif) {
            runCatching { instance?.cancelNotification(n.key) }
        }

        fun dismissAll() {
            runCatching { instance?.cancelAllNotifications() }
        }

        fun open(ctx: Context, n: Notif) {
            val pi = n.intent
            val ok = pi != null && runCatching {
                if (Build.VERSION.SDK_INT >= 34) {
                    val opts = ActivityOptions.makeBasic()
                        .setPendingIntentBackgroundActivityStartMode(ActivityOptions.MODE_BACKGROUND_ACTIVITY_START_ALLOWED)
                    pi.send(ctx, 0, null, null, null, null, opts.toBundle())
                } else {
                    pi.send()
                }
            }.isSuccess
            if (!ok) {
                ctx.packageManager.getLaunchIntentForPackage(n.pkg)?.let {
                    runCatching { ctx.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                }
            }
            if (n.clearable && n.autoCancel) dismiss(n)
        }
    }

    override fun onListenerConnected() {
        instance = this
        refresh()
    }

    override fun onListenerDisconnected() {
        if (instance === this) instance = null
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val before = items.value.map { it.key }.toSet()
        refresh()
        val n = toNotif(sbn) ?: return
        if (n.key !in before && n.clearable) posted.tryEmit(n)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) = refresh()

    private fun refresh() {
        items.value = runCatching {
            activeNotifications.orEmpty().mapNotNull { toNotif(it) }.sortedByDescending { it.time }
        }.getOrDefault(emptyList())
    }

    private fun toNotif(sbn: StatusBarNotification): Notif? {
        val n = sbn.notification
        if (n.flags and Notification.FLAG_GROUP_SUMMARY != 0) return null
        val ex = n.extras
        val title = ex.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = (ex.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: ex.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return null
        return Notif(
            sbn.key, sbn.packageName, title, text, sbn.postTime, sbn.isClearable,
            n.flags and Notification.FLAG_AUTO_CANCEL != 0, n.contentIntent
        )
    }
}
