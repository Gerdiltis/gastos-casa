package com.gastoscasa.domain

object Balances {
    /**
     * Saldo neto por persona sobre los gastos NO archivados: lo que pagó menos lo que le toca.
     * Positivo = le deben dinero. Negativo = debe dinero.
     */
    fun compute(expenses: List<Expense>, memberIds: List<String>): Map<String, Long> {
        val result = memberIds.associateWith { 0L }.toMutableMap()
        expenses.filter { !it.isArchived }.forEach { e ->
            result[e.paidBy] = (result[e.paidBy] ?: 0L) + e.amountCents
            e.splits.forEach { s -> result[s.userId] = (result[s.userId] ?: 0L) - s.shareCents }
        }
        return result
    }

    /** Liquidación con el mínimo de pagos aproximado (greedy: mayor deudor ↔ mayor acreedor). */
    fun settleUp(balances: Map<String, Long>): List<Transfer> {
        val creditors = balances.filter { it.value > 0 }.map { it.key to it.value }
            .sortedByDescending { it.second }.toMutableList()
        val debtors = balances.filter { it.value < 0 }.map { it.key to -it.value }
            .sortedByDescending { it.second }.toMutableList()
        val transfers = mutableListOf<Transfer>()
        var i = 0
        var j = 0
        while (i < debtors.size && j < creditors.size) {
            val (debtor, owes) = debtors[i]
            val (creditor, owed) = creditors[j]
            val amount = minOf(owes, owed)
            if (amount > 0) transfers += Transfer(debtor, creditor, amount)
            debtors[i] = debtor to (owes - amount)
            creditors[j] = creditor to (owed - amount)
            if (debtors[i].second == 0L) i++
            if (creditors[j].second == 0L) j++
        }
        return transfers
    }

    fun isSettled(balances: Map<String, Long>): Boolean = balances.values.all { it == 0L }
}
