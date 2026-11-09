package com.platinum20.dashboard

import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.testing.WorkManagerTestInitHelper
import com.platinum20.dashboard.notes.NotesStore
import com.platinum20.dashboard.today.BriefAction
import com.platinum20.dashboard.today.BriefKind
import com.platinum20.dashboard.today.BriefLine
import com.platinum20.dashboard.widget.DashboardWidget
import com.platinum20.dashboard.widget.WidgetBrief
import com.platinum20.dashboard.widget.WidgetRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Voce 19: le righe di "Adesso" nel widget principale con il loro bottoncino. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WidgetBriefTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Before fun setUp() {
        runCatching { WorkManagerTestInitHelper.initializeTestWorkManager(context) }
        NotesStore.init(context)
    }

    @Test fun ilBottoneFattoSoloSuUnaCosaSola() {
        val lines = listOf(
            BriefLine(BriefKind.TASK, "Bollette scadute", urgent = true, action = BriefAction.TASK_DONE, targetIds = listOf(1)),
            BriefLine(BriefKind.PARCEL, "Cuffie arrivano oggi", action = BriefAction.PARCEL_ARRIVED, targetIds = listOf(7)),
            BriefLine(BriefKind.HABIT, "Ancora da fare: 2 abitudini", action = BriefAction.HABITS, targetIds = listOf(3, 4)),
            BriefLine(BriefKind.WEATHER, "Pioggia dalle 17"),
        )
        assertEquals(
            listOf(
                // I promemoria non si spuntano più: niente bottone.
                WidgetBrief("Bollette scadute", urgent = true),
                WidgetBrief("Cuffie arrivano oggi", action = BriefAction.PARCEL_ARRIVED, targetId = 7),
                // Più abitudini insieme si spuntano nell'app: niente bottone.
                WidgetBrief("Ancora da fare: 2 abitudini"),
            ),
            WidgetRows.briefEntries(lines, max = 3),
        )
    }

    @Test fun righeSecondoLAltezza() {
        assertEquals(0, WidgetRows.linesForHeight(110))
        assertEquals(2, WidgetRows.linesForHeight(180))
        assertEquals(5, WidgetRows.linesForHeight(600))
    }

    @Test fun ilBottoneSpuntaIlPromemoria() {
        val t = NotesStore.addTaskAt("Pagare il bollo dal widget", System.currentTimeMillis() - 60_000)
        tap(BriefAction.TASK_DONE, t.id)
        assertTrue(NotesStore.task(t.id)!!.done)
    }

    private fun tap(action: BriefAction, id: Long) = DashboardWidget().onReceive(
        context,
        Intent(context, DashboardWidget::class.java)
            .setAction(DashboardWidget.ACTION_BRIEF)
            .putExtra(DashboardWidget.EXTRA_ACTION, action.name)
            .putExtra(DashboardWidget.EXTRA_TARGET, id),
    )

    @Test fun ilBottoneSegnaArrivatoIlPaccoUnaVoltaSola() {
        val id = NotesStore.addParcel("Cuffie widget", "", LocalDate.now())
        tap(BriefAction.PARCEL_ARRIVED, id)
        assertTrue(NotesStore.data.value.parcels.first { it.id == id }.delivered)
        // Un secondo tocco (widget non ancora ridisegnato) non lo rimette "in arrivo".
        tap(BriefAction.PARCEL_ARRIVED, id)
        assertTrue(NotesStore.data.value.parcels.first { it.id == id }.delivered)
    }

    @Test fun ilBottoneSegnaFattaLAbitudine() {
        NotesStore.saveHabit(null, "Stretching dal widget")
        val habit = NotesStore.data.value.habits.first { it.title == "Stretching dal widget" }
        tap(BriefAction.HABIT_DONE, habit.id)
        assertTrue(NotesStore.habit(habit.id)!!.isDone(LocalDate.now()))
        tap(BriefAction.HABIT_DONE, habit.id)
        assertTrue(NotesStore.habit(habit.id)!!.isDone(LocalDate.now()))
    }
}
