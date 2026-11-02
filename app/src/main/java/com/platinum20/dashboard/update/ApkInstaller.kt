package com.platinum20.dashboard.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

object ApkInstaller {
    const val EXTRA_FROM_UI = "from_ui"
    const val EXTRA_VERSION = "version"

    private val _failure = MutableStateFlow<String?>(null)

    /** L'ultimo errore di un'installazione partita dalla schermata Aggiornamenti. */
    val failure: StateFlow<String?> = _failure.asStateFlow()

    internal fun fail(message: String?) {
        _failure.value = message
    }

    /** Android chiede un permesso per app per installare APK da fuori dal Play Store. */
    fun canInstall(context: Context): Boolean =
        context.packageManager.canRequestPackageInstalls()

    /**
     * Da Android 12 un'app può aggiornare se stessa senza chiedere nulla, se è lei che l'ha
     * installata l'ultima volta (la prima volta Android chiede comunque la conferma).
     */
    fun canInstallSilently(context: Context): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && canInstall(context)

    fun openInstallPermissionSettings(context: Context) {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }

    /** Installa l'APK scaricato: in silenzio quando Android lo permette, altrimenti con la sua finestra. */
    fun install(context: Context, apk: File, version: String, fromUi: Boolean) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            openSystemInstaller(context, apk)
            return
        }
        _failure.value = null
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val id = installer.createSession(params)
        try {
            installer.openSession(id).use { session ->
                session.openWrite("canvas.apk", 0, apk.length()).use { out ->
                    apk.inputStream().use { it.copyTo(out) }
                    session.fsync(out)
                }
                // L'esito arriva a UpdateInstallReceiver; mutabile perché Android ci aggiunge lo stato.
                val result = Intent(context, UpdateInstallReceiver::class.java)
                    .setAction(UpdateInstallReceiver.ACTION_RESULT)
                    .putExtra(EXTRA_FROM_UI, fromUi)
                    .putExtra(EXTRA_VERSION, version)
                val pending = PendingIntent.getBroadcast(
                    context, id, result,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
                )
                session.commit(pending.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(id) }
            throw e
        }
    }

    /** Il vecchio modo: apre la finestra di installazione di Android sull'APK. */
    private fun openSystemInstaller(context: Context, apk: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apk)
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
