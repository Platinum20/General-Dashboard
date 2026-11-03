package com.platinum20.dashboard.update

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.security.MessageDigest

/**
 * Controlli sull'APK di un aggiornamento prima di installarlo. Android rifiuta già un APK firmato
 * con un'altra chiave; qui lo si scarta prima, con un messaggio chiaro, e si scarica solo dalle
 * release di questo repository su GitHub.
 */
object UpdateTrust {

    /** Solo HTTPS e solo i file allegati alle release di questo repository. */
    fun trustedUrl(url: String, repo: String): Boolean =
        url.startsWith("https://github.com/$repo/releases/download/", ignoreCase = true) &&
            !url.contains("..") && !url.contains('@')

    /**
     * Null se l'APK va bene, altrimenti il motivo. Se Android non riesce a leggere la firma
     * dell'APK non si blocca nulla: decide comunque lui all'installazione.
     */
    fun problem(myPackage: String, myCerts: Set<String>, apkPackage: String?, apkCerts: Set<String>): String? = when {
        apkPackage != null && apkPackage != myPackage -> "L'aggiornamento non è di Canvas: non installato."
        apkCerts.isNotEmpty() && myCerts.isNotEmpty() && apkCerts.intersect(myCerts).isEmpty() ->
            "L'aggiornamento non è firmato con la chiave di Canvas: non installato."
        else -> null
    }

    /** Controlla il file scaricato contro l'app installata. */
    fun check(context: Context, apk: File): String? = runCatching {
        val pm = context.packageManager
        val mine = certs(pm.packageInfo(context.packageName))
        val theirs = pm.archiveInfo(apk.absolutePath)
        problem(context.packageName, mine, theirs?.packageName, certs(theirs))
    }.getOrNull()

    private fun flags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) PackageManager.GET_SIGNING_CERTIFICATES
        else @Suppress("DEPRECATION") PackageManager.GET_SIGNATURES

    private fun PackageManager.packageInfo(name: String): PackageInfo? = getPackageInfo(name, flags())

    private fun PackageManager.archiveInfo(path: String): PackageInfo? = getPackageArchiveInfo(path, flags())

    @Suppress("DEPRECATION")
    private fun certs(info: PackageInfo?): Set<String> {
        info ?: return emptySet()
        val sigs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.signingInfo?.let { s ->
                if (s.hasMultipleSigners()) s.apkContentsSigners else s.signingCertificateHistory
            }
        } else info.signatures
        return sigs.orEmpty().map { sha256(it.toByteArray()) }.toSet()
    }

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
