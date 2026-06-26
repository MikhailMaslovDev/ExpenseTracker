package com.familyexpensetracker.prototype.categorization

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCategoriesTest {
    @Test
    fun `built in categories are unique and keep other as fallback`() {
        assertEquals(ExpenseCategories.all.size, ExpenseCategories.all.distinct().size)
        assertTrue(ExpenseCategories.all.isNotEmpty())
        assertEquals(ExpenseCategories.OTHER, ExpenseCategories.all.last())
    }
}
