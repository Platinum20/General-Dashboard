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

    @Test fun soloPaccoEAbitudineHannoIlBottone() {
        val lines = listOf(
            BriefLine(BriefKind.TASK, "Bollette scadute", action = BriefAction.TASK_DONE, targetIds = listOf(1)),
            BriefLine(BriefKind.PARCEL, "Cuffie arrivano oggi", action = BriefAction.PARCEL_ARRIVED, targetIds = listOf(7)),
            BriefLine(BriefKind.HABIT, "Ancora da fare: 2 abitudini", action = BriefAction.HABITS, targetIds = listOf(3, 4)),
            BriefLine(BriefKind.WEATHER, "Pioggia dalle 17"),
        )
        assertEquals(
            listOf(
                WidgetBrief("Cuffie arrivano oggi", BriefAction.PARCEL_ARRIVED, 7),
                // Più abitudini insieme si spuntano nell'app: niente bottone.
                WidgetBrief("Ancora da fare: 2 abitudini"),
            ),
            WidgetRows.briefEntries(lines),
        )
    }

    @Test fun andataERitornoEFormatoVecchio() {
        val entries = listOf(WidgetBrief("Leggere 20 minuti", BriefAction.HABIT_DONE, 5), WidgetBrief("Pioggia dalle 17"))
        assertEquals(entries, WidgetRows.decodeBrief(WidgetRows.encodeBrief(entries)))
        // Fino alla 1.110 si salvavano solo i testi.
        assertEquals(listOf(WidgetBrief("Pioggia dalle 17"), WidgetBrief("Compleanno di Marco")), WidgetRows.decodeBrief("[\"Pioggia dalle 17\",\"Compleanno di Marco\"]"))
        assertEquals(emptyList<WidgetBrief>(), WidgetRows.decodeBrief("non json"))
        assertEquals(emptyList<WidgetBrief>(), WidgetRows.decodeBrief(null))
    }

    @Test fun dopoLAzioneLaRigaSparisce() {
        val entries = listOf(WidgetBrief("Cuffie", BriefAction.PARCEL_ARRIVED, 7), WidgetBrief("Pioggia"))
        assertEquals(listOf(WidgetBrief("Pioggia")), WidgetRows.withoutTarget(entries, BriefAction.PARCEL_ARRIVED, 7))
        assertEquals(entries, WidgetRows.withoutTarget(entries, BriefAction.HABIT_DONE, 7))
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
