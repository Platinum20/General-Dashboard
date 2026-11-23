package com.platinum20.dashboard.trips

import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.platinum20.dashboard.settings.AppSettings
import com.platinum20.dashboard.ui.screens.CanvasButton
import com.platinum20.dashboard.ui.theme.GeneralDashboardTheme

/**
 * La sveglia veloce a tutto schermo, anche col telefono bloccato: l'ora grande, "Ferma" e "Ancora 5 min".
 * Il suono resta della notifica (canale sveglie), così chiudere questa schermata non lo lascia acceso per sbaglio.
 */
class QuickAlarmActivity : ComponentActivity() {
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
        val time = QuickAlarm.label(System.currentTimeMillis())
        setContent {
            val theme by AppSettings.theme.collectAsState()
            GeneralDashboardTheme(theme = theme) {
                Column(
                    Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("Sveglia", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(time, fontSize = 72.sp, color = MaterialTheme.colorScheme.onBackground)
                    Spacer(Modifier.height(48.dp))
                    CanvasButton("Ferma", onClick = { QuickAlarm.stop(this@QuickAlarmActivity); finish() }, modifier = Modifier.fillMaxWidth(), primary = true)
                    Spacer(Modifier.height(12.dp))
                    CanvasButton("Ancora ${QuickAlarm.SNOOZE_MINUTES} min", onClick = { QuickAlarm.set(this@QuickAlarmActivity, QuickAlarm.SNOOZE_MINUTES); finish() }, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}
