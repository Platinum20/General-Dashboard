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
}
