package com.gastoscasa.domain

import java.time.LocalDate

enum class Frequency(val key: String, val label: String, val every: String) {
    WEEKLY("weekly", "Semanal", "cada semana"),
    MONTHLY("monthly", "Mensual", "cada mes"),
    YEARLY("yearly", "Anual", "cada año");

    companion object {
        fun of(key: String) = entries.firstOrNull { it.key == key } ?: MONTHLY
    }
}

/** Mismo cálculo que `recurring_date` / `recurring_total` en la base de datos. */
object Recurrence {
    /** Fecha de la ocurrencia n (0 = inicio), siempre desde el inicio: el 31 vuelve a ser 31. */
    fun date(start: LocalDate, f: Frequency, n: Int): LocalDate = when (f) {
        Frequency.WEEKLY -> start.plusWeeks(n.toLong())
        Frequency.MONTHLY -> start.plusMonths(n.toLong())
        Frequency.YEARLY -> start.plusYears(n.toLong())
    }

    fun total(start: LocalDate, end: LocalDate, f: Frequency): Int {
        if (end < start) return 0
        var n = 0
        while (!date(start, f, n).isAfter(end) && n < 10_000) n++
        return n
    }

    /** Mensaje de error del formulario, o null si es válido. */
    fun validate(start: LocalDate, end: LocalDate?, f: Frequency, today: LocalDate): String? = when {
        end == null -> "Elige la fecha de fin"
        start.isBefore(today) -> "El inicio no puede ser anterior a hoy"
        end.isBefore(start) -> "La fecha de fin debe ser posterior al inicio"
        total(start, end, f) < 2 -> "Con esas fechas solo habría un gasto: alarga el fin"
        else -> null
    }
}

data class RecurringExpense(
    val id: String,
    val groupId: String,
    val createdBy: String,
    val description: String,
    val amountCents: Long,
    val categoryId: String?,
    val paidBy: String,
    val participants: List<String>,
    val frequency: Frequency,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val occurrence: Int,
    val nextDate: LocalDate?,
    val active: Boolean,
) {
    val total: Int get() = Recurrence.total(startDate, endDate, frequency)
    fun isPending(today: LocalDate) = active && nextDate != null && !nextDate.isAfter(today)
}
