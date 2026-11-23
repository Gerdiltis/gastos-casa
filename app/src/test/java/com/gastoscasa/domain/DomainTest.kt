package com.gastoscasa.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneOffset

class DomainTest {

    private fun expense(
        paidBy: String, amount: Long, participants: List<String>,
        at: String = "2026-03-10T12:00:00Z", category: String? = "c1",
        settlement: Boolean = false, archived: Boolean = false,
    ) = Expense(
        id = "e${amount}${paidBy}", groupId = "g", paidBy = paidBy, description = "x",
        amountCents = amount, categoryId = category, isSettlement = settlement, createdBy = paidBy,
        createdAt = Instant.parse(at), archivedAt = if (archived) Instant.parse(at) else null,
        splits = SplitCalculator.equalSplit(amount, participants),
    )

    @Test fun equalSplitDistributesRemainderCents() {
        val s = SplitCalculator.equalSplit(1000, listOf("a", "b", "c"))
        assertEquals(listOf(334L, 333L, 333L), s.map { it.shareCents })
        assertEquals(1000L, s.sumOf { it.shareCents })
    }

    @Test fun singleParticipantGetsEverything() {
        assertEquals(listOf(Split("b", 999)), SplitCalculator.equalSplit(999, listOf("b")))
    }

    @Test fun balancesIgnoreArchived() {
        val list = listOf(
            expense("a", 3000, listOf("a", "b", "c")),
            expense("b", 600, listOf("a", "b")),
            expense("c", 5000, listOf("a", "b", "c"), archived = true),
        )
        val bal = Balances.compute(list, listOf("a", "b", "c"))
        assertEquals(1700L, bal["a"])   // 3000 - 1000 - 300
        assertEquals(-700L, bal["b"])   // 600 - 1000 - 300
        assertEquals(-1000L, bal["c"])
        assertEquals(0L, bal.values.sum())
    }

    @Test fun settleUpZeroesEveryBalance() {
        val bal = mapOf("a" to 1700L, "b" to -700L, "c" to -1000L, "d" to 0L)
        val t = Balances.settleUp(bal)
        assertEquals(2, t.size)
        val after = bal.toMutableMap()
        t.forEach { after[it.from] = after[it.from]!! + it.amountCents; after[it.to] = after[it.to]!! - it.amountCents }
        assertTrue(Balances.isSettled(after))
    }

    @Test fun settlementExpenseClearsDebt() {
        val list = listOf(
            expense("a", 1000, listOf("a", "b")),
            expense("b", 500, listOf("a"), settlement = true),
        )
        assertTrue(Balances.isSettled(Balances.compute(list, listOf("a", "b"))))
    }

    @Test fun statsGroupByCategoryMonthYearAndSkipSettlements() {
        val list = listOf(
            expense("a", 1000, listOf("a", "b"), at = "2026-03-10T12:00:00Z", category = "food"),
            expense("b", 400, listOf("a", "b"), at = "2026-03-20T12:00:00Z", category = "home"),
            expense("a", 200, listOf("a", "b"), at = "2026-05-01T12:00:00Z", category = "food"),
            expense("a", 9999, listOf("b"), at = "2026-03-21T12:00:00Z", settlement = true),
            expense("a", 300, listOf("a"), at = "2025-12-31T12:00:00Z", category = null),
        )
        val total = Stats(list, StatsScope.GROUP_TOTAL, "a", ZoneOffset.UTC)
        val march = total.inMonth(YearMonth.of(2026, 3))
        assertEquals(1400L, total.total(march))
        assertEquals(listOf("food" to 1000L, "home" to 400L), total.byCategory(march))
        assertEquals(listOf(2026 to 1600L, 2025 to 300L), total.byYear())
        assertEquals(200L, total.monthsOfYear(2026)[4].second)

        val mine = Stats(list, StatsScope.MY_SHARE, "b", ZoneOffset.UTC)
        assertEquals(700L, mine.total(mine.inMonth(YearMonth.of(2026, 3))))
        assertEquals(listOf(2026 to 800L), mine.byYear())
        assertEquals(3, mine.lastMonths(YearMonth.of(2026, 5), 3).size)
    }

    @Test fun moneyParseAndFormat() {
        assertEquals(1250L, Money.parse("12,5"))
        assertEquals(1250L, Money.parse("12.50"))
        assertEquals(123456L, Money.parse("1.234,56"))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("abc"))
        assertNull(Money.parse("1,234"))
        assertEquals("1.234,56 €", Money.format(123456))
        assertEquals("-0,05 €", Money.format(-5))
    }
}
