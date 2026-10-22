package com.platinum20.dashboard.autofill

import android.app.Notification
import android.content.Context
import android.content.pm.ApplicationInfo
import android.provider.Telephony
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Legge le notifiche delle altre app (banche, Revolut, corrieri, Gmail, SMS) e passa il testo
 * ad [AutoFill]. Non manda niente da nessuna parte: il testo resta sul telefono e viene scartato.
 */
class AutoFillListener : NotificationListenerService() {

    override fun onListenerConnected() {
        AutoFill.init(this)
        if (!AutoFill.enabled.value) return
        // Appena acceso: guarda anche le notifiche ancora nella tendina degli ultimi due giorni.
        val since = System.currentTimeMillis() - 2 * 24 * 3600_000L
        runCatching { activeNotifications }.getOrNull().orEmpty()
            .filter { it.postTime >= since }
            .forEach { handle(it, notify = false) }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        AutoFill.init(this)
        if (!AutoFill.enabled.value) return
        handle(sbn, notify = true)
    }

    private fun handle(sbn: StatusBarNotification, notify: Boolean) {
        runCatching { messagesOf(this, sbn) }.getOrNull().orEmpty().forEach { AutoFill.offer(it, notify) }
    }

    companion object {
        fun messagesOf(context: Context, sbn: StatusBarNotification): List<Incoming> {
            if (sbn.packageName == context.packageName) return emptyList()
            val n = sbn.notification
            if (n.flags and (Notification.FLAG_ONGOING_EVENT or Notification.FLAG_GROUP_SUMMARY or Notification.FLAG_FOREGROUND_SERVICE) != 0) return emptyList()
            if (n.category in setOf(Notification.CATEGORY_CALL, Notification.CATEGORY_TRANSPORT, Notification.CATEGORY_PROGRESS, Notification.CATEGORY_ALARM)) return emptyList()
            val pkg = sbn.packageName
            var kind = Extractor.kindOf(pkg)
            if (pkg == Telephony.Sms.getDefaultSmsPackage(context)) kind = SourceKind.SMS
            if (kind == SourceKind.CHAT) return emptyList()
            val extras = n.extras
            val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
            val text = (extras.getCharSequence(Notification.EXTRA_BIG_TEXT) ?: extras.getCharSequence(Notification.EXTRA_TEXT))?.toString().orEmpty()
            val sub = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString().orEmpty()
            val app = appLabel(context, pkg, extras)
            val at = LocalDateTime.ofInstant(Instant.ofEpochMilli(sbn.postTime), ZoneId.systemDefault())
            // Gmail con più mail insieme le mette in righe: ognuna è un messaggio a sé.
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.map { it.toString() }.orEmpty()
            if (lines.size > 1) return lines.map { Incoming(app, kind, title, it, at) }
            if (title.isBlank() && text.isBlank()) return emptyList()
            return listOf(Incoming(app, kind, title, if (sub.isNotBlank() && kind == SourceKind.MAIL) "$text\n$sub" else text, at))
        }

        private val labels = HashMap<String, String>()

        /** Il nome dell'app che ha mandato la notifica: Android lo allega, senza dover vedere le altre app installate. */
        private fun appLabel(context: Context, pkg: String, extras: android.os.Bundle): String = labels.getOrPut(pkg) {
            val info = runCatching {
                @Suppress("DEPRECATION")
                extras.getParcelable<ApplicationInfo>("android.appInfo")
            }.getOrNull()
            val label = info?.let { runCatching { context.packageManager.getApplicationLabel(it).toString() }.getOrNull() }
                ?: pkg.substringAfterLast('.').replaceFirstChar { it.uppercase() }
            if (pkg.contains("amazon") && !label.contains("amazon", true)) "Amazon" else label
        }
    }
}
