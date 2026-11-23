package com.gastoscasa.domain

import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId

enum class StatsScope { GROUP_TOTAL, MY_SHARE }

/** Estadísticas de gasto real (excluye los pagos de liquidación entre personas). */
class Stats(
    expenses: List<Expense>,
    private val scope: StatsScope,
    private val me: String,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val spending = expenses.filter { !it.isSettlement }

    private fun amount(e: Expense): Long = when (scope) {
        StatsScope.GROUP_TOTAL -> e.amountCents
        StatsScope.MY_SHARE -> e.shareOf(me)
    }

    private fun Instant.ym(): YearMonth = YearMonth.from(atZone(zone))

    fun inMonth(month: YearMonth): List<Expense> = spending.filter { it.createdAt.ym() == month }
    fun inYear(year: Int): List<Expense> = spending.filter { it.createdAt.ym().year == year }

    fun total(list: List<Expense>): Long = list.sumOf { amount(it) }

    /** categoryId (null = sin categoría) -> importe, ordenado de mayor a menor y sin ceros. */
    fun byCategory(list: List<Expense>): List<Pair<String?, Long>> =
        list.groupBy { it.categoryId }
            .map { (cat, l) -> cat to l.sumOf { amount(it) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }

    /** Los 12 meses de [year] con su importe (0 si no hubo gastos). */
    fun monthsOfYear(year: Int): List<Pair<YearMonth, Long>> =
        (1..12).map { m -> YearMonth.of(year, m).let { ym -> ym to total(inMonth(ym)) } }

    /** [count] meses terminando en [end] (incluido). */
    fun lastMonths(end: YearMonth, count: Int): List<Pair<YearMonth, Long>> =
        (count - 1 downTo 0).map { end.minusMonths(it.toLong()) }.map { it to total(inMonth(it)) }

    /** año -> importe, de más reciente a más antiguo. */
    fun byYear(): List<Pair<Int, Long>> =
        spending.groupBy { it.createdAt.ym().year }
            .map { (y, l) -> y to l.sumOf { amount(it) } }
            .filter { it.second > 0 }
            .sortedByDescending { it.first }
}
