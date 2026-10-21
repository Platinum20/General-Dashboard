package com.platinum20.dashboard.ui.screens

import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer

/**
 * Finestrella video (picture-in-picture): se [enabled] è vero, uscendo dall'app con Home
 * il video resta visibile in un riquadro sopra le altre app. Restituisce true mentre si è nel riquadro,
 * così la schermata può mostrare solo il video.
 */
@Composable
fun rememberPictureInPicture(enabled: Boolean): Boolean {
    val activity = LocalContext.current.findActivity() as? ComponentActivity ?: return false
    var inPip by remember { mutableStateOf(activity.isInPictureInPictureMode) }

    DisposableEffect(activity) {
        val listener = Consumer<PictureInPictureModeChangedInfo> { inPip = it.isInPictureInPictureMode }
        activity.addOnPictureInPictureModeChangedListener(listener)
        onDispose { activity.removeOnPictureInPictureModeChangedListener(listener) }
    }

    DisposableEffect(activity, enabled) {
        val params = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .apply { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setAutoEnterEnabled(enabled) }
            .build()
        runCatching { activity.setPictureInPictureParams(params) }
        // Prima di Android 12 il riquadro va chiesto a mano quando l'utente esce dall'app.
        val leaveHint = Runnable {
            if (enabled && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                runCatching { activity.enterPictureInPictureMode(params) }
            }
        }
        activity.addOnUserLeaveHintListener(leaveHint)
        onDispose {
            activity.removeOnUserLeaveHintListener(leaveHint)
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                runCatching {
                    activity.setPictureInPictureParams(PictureInPictureParams.Builder().setAutoEnterEnabled(false).build())
                }
            }
        }
    }
    return inPip
}
