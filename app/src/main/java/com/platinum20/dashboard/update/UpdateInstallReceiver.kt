package com.platinum20.dashboard.update

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import com.platinum20.dashboard.work.UpdateCheckWorker

/** L'esito dell'installazione e, a nuova versione installata, l'avviso "Canvas aggiornata". */
class UpdateInstallReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            UpdateCheckWorker.installed(context)
            return
        }
        if (intent.action != ACTION_RESULT) return
        val fromUi = intent.getBooleanExtra(ApkInstaller.EXTRA_FROM_UI, false)
        val version = intent.getStringExtra(ApkInstaller.EXTRA_VERSION).orEmpty()
        when (intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_SUCCESS -> Unit
            // Android vuole una conferma (la prima volta, o Play Protect vuole controllare).
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (fromUi && confirm != null) {
                    runCatching { context.startActivity(confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                        .onFailure { ApkInstaller.fail("Impossibile avviare l'installazione. Riprova tra poco.") }
                } else {
                    UpdateCheckWorker.notifyManual(context, version)
                }
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> if (fromUi) ApkInstaller.fail("Installazione annullata.")
            else -> {
                if (fromUi) ApkInstaller.fail("Installazione non riuscita. Riprova tra poco.")
                else UpdateCheckWorker.notifyManual(context, version)
            }
        }
    }

    companion object {
        const val ACTION_RESULT = "com.platinum20.dashboard.UPDATE_INSTALL_RESULT"
    }
}
