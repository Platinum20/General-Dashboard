package com.platinum20.dashboard.tickets

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.asImageBitmap
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.ui.screens.TicketSheet
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme

/**
 * Il biglietto aperto dal tasto "Biglietto" della notifica del viaggio (1.467): si apre anche col telefono bloccato,
 * così ai tornelli basta un tocco. Mostra solo i codici già salvati: niente rete, niente altro dell'app.
 */
class TicketActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        AppSettings.init(this)
        val id = intent.getLongExtra(EXTRA_EVENT, 0L)
        val codes = TicketQrStore.cached(this, id).mapNotNull { f ->
            runCatching { BitmapFactory.decodeFile(f.path)?.asImageBitmap() }.getOrNull()
        }
        if (codes.isEmpty()) {
            finish()
            return
        }
        setContent {
            val theme by AppSettings.theme.collectAsState()
            GeneralDashboardTheme(theme = theme) {
                TicketSheet(codes, onClose = ::finish)
            }
        }
    }

    companion object {
        private const val EXTRA_EVENT = "event"

        /** Lo stesso biglietto aperto da dentro l'app (tenendo premuta la riga del treno). */
        fun intent(context: Context, eventId: Long): Intent = Intent(context, TicketActivity::class.java)
            .putExtra(EXTRA_EVENT, eventId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)

        fun pending(context: Context, eventId: Long): PendingIntent {
            val intent = intent(context, eventId)
            return PendingIntent.getActivity(
                context,
                (eventId % 100_000).toInt(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}
