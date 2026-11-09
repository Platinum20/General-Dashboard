package com.platinum20.dashboard.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.RemoteViews
import com.platinum20.dashboard.MainActivity
import com.platinum20.dashboard.OpenRequest
import com.platinum20.dashboard.bank.RevolutStore
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.sim.SimStore
import com.platinum20.dashboard.work.RefreshWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Disegna un widget: gira fuori dal thread principale, quindi può leggere calendario e file. */
typealias WidgetRenderer = suspend (Context, WidgetSize) -> RemoteViews

/** Quanto è grande il widget sulla schermata Home (dp), per decidere quante righe ci stanno. */
data class WidgetSize(val widthDp: Int, val heightDp: Int) {
    companion object {
        fun of(options: Bundle?, fallback: WidgetSize): WidgetSize {
            // In verticale il launcher usa la larghezza minima e l'altezza massima.
            val w = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)?.takeIf { it > 0 } ?: fallback.widthDp
            val h = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)?.takeIf { it > 0 } ?: fallback.heightDp
            return WidgetSize(w, h)
        }
    }
}

/**
 * La base di tutti i widget Canvas: ogni aggiornamento passa da [Widgets], che li ridisegna
 * in background con i dati di adesso (niente copie salvate che restano indietro).
 */
abstract class CanvasWidget(internal val renderer: WidgetRenderer, internal val fallback: WidgetSize) : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        Widgets.refresh(context, this::class.java, runCatching { goAsync() }.getOrNull())
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // Ridimensionato: le righe si adattano alla nuova altezza.
        Widgets.refresh(context, this::class.java, runCatching { goAsync() }.getOrNull())
    }

    override fun onEnabled(context: Context) {
        // Meteo, notizie e il cambio di giorno arrivano dall'aggiornamento orario.
        RefreshWorker.schedule(context)
        RefreshWorker.runNow(context)
    }

    override fun onDisabled(context: Context) {
        if (!Widgets.hasAny(context)) RefreshWorker.cancel(context)
    }
}

/** Tutti i widget di Canvas: chi cambia dei dati chiama [refresh] e si ridisegnano da soli. */
object Widgets {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()

    /** Ogni widget con il suo disegno: il nome della classe resta quello di sempre, se no spariscono dalla Home. */
    internal val all: List<Pair<Class<out CanvasWidget>, CanvasWidget>> by lazy {
        listOf(DashboardWidget(), TasksWidget(), NewsWidget(), SpendWidget(), SimWidget(), WeatherWidget(), CaptureWidget())
            .map { it::class.java to it }
    }

    fun ids(context: Context, cls: Class<*>): IntArray = runCatching {
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, cls))
    }.getOrNull() ?: IntArray(0)

    fun hasAny(context: Context): Boolean = all.any { ids(context.applicationContext, it.first).isNotEmpty() }

    /** Ridisegna tutti i widget (o solo quelli di [only]) con i dati di adesso. */
    fun refresh(context: Context, only: Class<*>? = null, pending: android.content.BroadcastReceiver.PendingResult? = null) {
        val app = context.applicationContext
        val targets = all.filter { only == null || it.first == only }
            .map { it to ids(app, it.first) }
            .filter { it.second.isNotEmpty() }
        if (targets.isEmpty()) {
            pending?.finish()
            return
        }
        // I dati si caricano qui, sul thread di chi chiama, come fa il resto dell'app.
        runCatching { NotesStore.init(app) }
        runCatching { SimStore.init(app) }
        runCatching { RevolutStore.init(app) }
        scope.launch {
            try {
                lock.withLock {
                    val manager = AppWidgetManager.getInstance(app)
                    for ((entry, ids) in targets) {
                        val widget = entry.second
                        for (id in ids) {
                            val size = WidgetSize.of(runCatching { manager.getAppWidgetOptions(id) }.getOrNull(), widget.fallback)
                            runCatching { manager.updateAppWidget(id, widget.renderer(app, size)) }
                        }
                    }
                }
            } finally {
                pending?.finish()
            }
        }
    }

    /** Apre Canvas su una pagina: ogni tocco ha il suo [code], se no Android riusa l'intent di un altro. */
    internal fun open(context: Context, request: OpenRequest, code: Int): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        request.writeTo(intent)
        return PendingIntent.getActivity(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }

    /** Un'azione fatta dal widget senza aprire l'app (spunta, pacco arrivato). */
    internal fun broadcast(context: Context, cls: Class<*>, action: String, code: Int, extras: Intent.() -> Unit): PendingIntent {
        val intent = Intent(context, cls).setAction(action).apply(extras)
        return PendingIntent.getBroadcast(context, code, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    }
}
