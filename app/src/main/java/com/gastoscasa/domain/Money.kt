package com.gastoscasa.domain

import java.math.BigDecimal
import java.math.RoundingMode

object Money {
    /** 123456 -> "1.234,56 €" */
    fun format(cents: Long): String {
        // Formato manual: es-ES no agrupa los números de 4 cifras y queremos "1.234,56 €" siempre.
        val abs = Math.abs(cents)
        val units = abs / 100
        val dec = (abs % 100).toString().padStart(2, '0')
        val grouped = units.toString().reversed().chunked(3).joinToString(".").reversed()
        val sign = if (cents < 0) "-" else ""
        return "$sign$grouped,$dec €"
    }

    /** Acepta "12", "12,5", "12.50", "1.234,56". Devuelve null si no es válido o <= 0. */
    fun parse(input: String): Long? {
        var s = input.trim().replace("€", "").replace(" ", "")
        if (s.isEmpty()) return null
        if (s.contains(',')) s = s.replace(".", "").replace(',', '.')
        val bd = s.toBigDecimalOrNull() ?: return null
        if (bd.scale() > 2 && bd.setScale(2, RoundingMode.DOWN).compareTo(bd) != 0) return null
        val cents = bd.setScale(2, RoundingMode.HALF_UP).multiply(BigDecimal(100)).toLong()
        return cents.takeIf { it > 0 }
    }
}
