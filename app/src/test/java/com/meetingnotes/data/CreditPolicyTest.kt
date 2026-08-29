package com.meetingnotes.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreditPolicyTest {

    @Test
    fun `same year-month does not require reset`() {
        assertFalse(CreditPolicy.shouldReset("2026-08", "2026-08"))
    }

    @Test
    fun `different year-month requires reset`() {
        assertTrue(CreditPolicy.shouldReset("2026-09", "2026-08"))
    }

    @Test
    fun `year change requires reset`() {
        assertTrue(CreditPolicy.shouldReset("2027-01", "2026-12"))
    }

    @Test
    fun `currentYearMonth matches yyyy-MM pattern`() {
        assertTrue(CreditPolicy.currentYearMonth().matches(Regex("\\d{4}-\\d{2}")))
    }
}
