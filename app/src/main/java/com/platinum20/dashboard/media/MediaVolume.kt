package com.platinum20.dashboard.media

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Il volume multimediale del telefono è a zero? Serve ai player: a volume zero YouTube accende da
 * solo i sottotitoli e la musica mostra i testi, e li tolgono quando il volume risale.
 * Si ascoltano sia le impostazioni di sistema (dove Android salva i volumi) sia il broadcast del
 * volume, che arriva subito: uno dei due basta.
 */
object MediaVolume {
    private val _muted = MutableStateFlow(false)
    val muted: StateFlow<Boolean> = _muted.asStateFlow()
    private var started = false

    /** Da chiamare quando un player parte; più chiamate non fanno nulla. */
    fun init(context: Context) {
        if (started) return
        started = true
        val app = context.applicationContext
        val audio = app.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        val read = {
            _muted.value = runCatching {
                audio.getStreamVolume(AudioManager.STREAM_MUSIC) == 0 || audio.isStreamMute(AudioManager.STREAM_MUSIC)
            }.getOrDefault(false)
        }
        read()
        runCatching {
            app.contentResolver.registerContentObserver(
                Settings.System.CONTENT_URI, true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) = read()
                },
            )
        }
        runCatching {
            ContextCompat.registerReceiver(
                app,
                object : BroadcastReceiver() {
                    override fun onReceive(c: Context?, intent: Intent?) = read()
                },
                IntentFilter("android.media.VOLUME_CHANGED_ACTION").apply {
                    addAction("android.media.STREAM_MUTE_CHANGED_ACTION")
                },
                ContextCompat.RECEIVER_EXPORTED,
            )
        }
    }
}
