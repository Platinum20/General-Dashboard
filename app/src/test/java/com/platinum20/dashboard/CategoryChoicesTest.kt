package com.platinum20.dashboard

import com.platinum20.dashboard.notes.ExpenseCategory
import com.platinum20.dashboard.ui.screens.categoryChoices
import org.junit.Assert.assertEquals
import org.junit.Test

class CategoryChoicesTest {
    @Test
    fun tutteLeCategorieQuellaDiAdessoSegnata() {
        var picked: ExpenseCategory? = null
        val choices = categoryChoices(ExpenseCategory.FOOD) { picked = it }
        assertEquals(ExpenseCategory.entries.map { it.label }, choices.map { it.title })
        assertEquals("adesso", choices.first { it.title == ExpenseCategory.FOOD.label }.detail)
        choices.first { it.title == ExpenseCategory.FOOD.label }.onClick()
        assertEquals(null, picked)
        choices.first { it.title == ExpenseCategory.HEALTH.label }.onClick()
        assertEquals(ExpenseCategory.HEALTH, picked)
    }

    @Test
    fun fumettoDellaSpesaCategorieInChip() {
        val e = com.platinum20.dashboard.notes.Expense(id = 1, cents = 1250, text = "Pizza", category = ExpenseCategory.EATING_OUT, date = java.time.LocalDate.of(2026, 11, 10))
        val bubble = com.platinum20.dashboard.ui.screens.expenseBubble(e, onOpen = {}, onDelete = {})
        assertEquals(listOf("Apri", "Elimina"), bubble.drop(1).map { it.title })
        val chips = bubble.first().chips
        assertEquals(ExpenseCategory.entries.size, chips.size)
        assertEquals(listOf(ExpenseCategory.EATING_OUT.label), chips.filter { it.selected }.map { it.label })
        assertEquals(true, bubble.first().title.startsWith("Pizza · "))
    }
}
