package com.gastoscasa.domain

object SplitCalculator {
    /**
     * Reparte [amountCents] a partes iguales. Los céntimos sobrantes se reparten de uno en uno
     * siguiendo el orden de [participants], de modo que la suma siempre cuadra con el total.
     */
    fun equalSplit(amountCents: Long, participants: List<String>): List<Split> {
        require(amountCents > 0) { "El importe debe ser positivo" }
        val people = participants.distinct()
        require(people.isNotEmpty()) { "Elige al menos una persona" }
        val base = amountCents / people.size
        val remainder = (amountCents % people.size).toInt()
        return people.mapIndexed { i, id -> Split(id, base + if (i < remainder) 1 else 0) }
    }
}
