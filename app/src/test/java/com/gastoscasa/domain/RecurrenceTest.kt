package com.gastoscasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class RecurrenceTest {
    private fun d(s: String) = LocalDate.parse(s)

    @Test
    fun monthlyFrom31stDoesNotDrift() {
        val start = d("2027-01-31")
        assertEquals(d("2027-02-28"), Recurrence.date(start, Frequency.MONTHLY, 1))
        assertEquals(d("2027-03-31"), Recurrence.date(start, Frequency.MONTHLY, 2))
        assertEquals(d("2028-02-29"), Recurrence.date(start, Frequency.MONTHLY, 13))
    }

    @Test
    fun weeklyAndYearlyAndTotals() {
        assertEquals(d("2026-12-03"), Recurrence.date(d("2026-11-26"), Frequency.WEEKLY, 1))
        assertEquals(d("2029-02-28"), Recurrence.date(d("2028-02-29"), Frequency.YEARLY, 1))
        assertEquals(13, Recurrence.total(d("2026-11-26"), d("2027-11-26"), Frequency.MONTHLY))
        assertEquals(12, Recurrence.total(d("2026-11-26"), d("2027-11-25"), Frequency.MONTHLY))
        assertEquals(5, Recurrence.total(d("2026-11-26"), d("2026-12-24"), Frequency.WEEKLY))
        assertEquals(0, Recurrence.total(d("2026-11-26"), d("2026-11-01"), Frequency.WEEKLY))
    }

    @Test
    fun validation() {
        val today = d("2026-11-26")
        assertNull(Recurrence.validate(today, d("2027-11-26"), Frequency.MONTHLY, today))
        assertNotNull(Recurrence.validate(today, null, Frequency.MONTHLY, today))
        assertNotNull(Recurrence.validate(d("2026-11-25"), d("2027-11-26"), Frequency.MONTHLY, today))
        assertNotNull(Recurrence.validate(today, d("2026-11-01"), Frequency.MONTHLY, today))
        assertNotNull(Recurrence.validate(today, d("2026-12-10"), Frequency.MONTHLY, today))
    }
}
