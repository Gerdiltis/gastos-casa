package com.gastoscasa.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyInputTest {
    @Test
    fun editFormRoundTrip() {
        assertEquals("1234,50", Money.toInput(123450))
        assertEquals("0,05", Money.toInput(5))
        listOf(1L, 99L, 100L, 3001L, 123456789L).forEach { assertEquals(it, Money.parse(Money.toInput(it))) }
    }
}
