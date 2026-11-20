package com.platinum20.dashboard.work

import android.content.Context
import androidx.core.app.NotificationManagerCompat
import com.platinum20.dashboard.trips.LiveDemo
import com.platinum20.dashboard.trips.LiveTrip
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Fa girare [LiveDemo] come notifica vera, un fotogramma ogni pochi secondi; una seconda volta la ferma. */
object TripLiveDemo {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null
    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    fun toggle(context: Context) {
        val app = context.applicationContext
        if (job?.isActive == true) { stop(app); return }
        job = scope.launch {
            _running.value = true
            try {
                val base = System.currentTimeMillis()
                val event = LiveDemo.event(base)
                for (frame in LiveDemo.frames(base)) {
                    LiveTrip.card(event, frame.now, frame.status, checked = true)?.let { TripLiveWorker.post(app, event, it, frame.now) }
                    delay(LiveDemo.FRAME_MS)
                }
            } finally {
                NotificationManagerCompat.from(app).cancel(TripLiveWorker.notificationId(LiveDemo.EVENT_ID))
                _running.value = false
            }
        }
    }

    private fun stop(context: Context) {
        job?.cancel()
        NotificationManagerCompat.from(context).cancel(TripLiveWorker.notificationId(LiveDemo.EVENT_ID))
    }
}
