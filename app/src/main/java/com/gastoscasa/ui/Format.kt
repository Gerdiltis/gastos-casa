package com.gastoscasa.ui

import com.gastoscasa.domain.Money
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

object Fmt {
    val es: Locale = Locale.forLanguageTag("es-ES")
    private val dateTime = DateTimeFormatter.ofPattern("d MMM · HH:mm", es)

    private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", es)
    fun date(d: java.time.LocalDate): String = dateFmt.format(d).replace(".", "")
    fun money(cents: Long) = Money.format(cents)
    fun signed(cents: Long) = if (cents > 0) "+" + Money.format(cents) else Money.format(cents)
    fun dateTime(i: Instant): String = dateTime.format(i.atZone(ZoneId.systemDefault()))
    /** "ahora", "hace 5 min", "hace 3 h", "hace 2 d" o la fecha si es más antiguo. */
    fun ago(i: Instant, now: Instant = Instant.now()): String {
        val min = java.time.Duration.between(i, now).toMinutes().coerceAtLeast(0)
        return when {
            min < 1 -> "ahora"
            min < 60 -> "hace $min min"
            min < 24 * 60 -> "hace ${min / 60} h"
            min < 7 * 24 * 60 -> "hace ${min / (24 * 60)} d"
            else -> dateTime(i).substringBefore(" ·")
        }
    }
    fun monthShort(ym: YearMonth): String = ym.month.getDisplayName(TextStyle.SHORT, es).trimEnd('.').replaceFirstChar { it.uppercase() }
    fun monthLong(ym: YearMonth): String =
        ym.month.getDisplayName(TextStyle.FULL_STANDALONE, es).replaceFirstChar { it.uppercase() } + " " + ym.year
}

private val knownServerErrors = listOf(
    "Código no válido", "No eres miembro del grupo", "El pagador no pertenece al grupo",
    "Elige al menos una persona", "El reparto no suma el total", "Una de las personas no pertenece al grupo",
    "Aún hay deudas pendientes en el grupo", "No hay gastos que archivar", "No autenticado",
    "No se puede editar un gasto archivado", "Importe no válido",
    "Solo quien creó la recurrencia puede confirmarla", "Solo quien creó la recurrencia puede cambiarla",
    "Solo quien creó la recurrencia puede detenerla", "Esta repetición ya se resolvió", "Aún no toca esta repetición",
    "La fecha de fin debe ser posterior al inicio", "El inicio no puede ser anterior a hoy", "Frecuencia no válida",
)

/** Convierte excepciones técnicas en mensajes entendibles. */
fun Throwable.friendly(): String {
    val m = message.orEmpty()
    knownServerErrors.firstOrNull { it in m }?.let { return it }
    return when {
        "Invalid login credentials" in m -> "Email o contraseña incorrectos"
        "already registered" in m || "already been registered" in m -> "Ese email ya está registrado"
        "Password should be" in m -> "La contraseña debe tener al menos 6 caracteres"
        "Email not confirmed" in m -> "Confirma tu email (o desactiva 'Confirm email' en Supabase)"
        "duplicate key" in m -> "Ya existe un elemento con ese nombre"
        this is java.net.UnknownHostException || "Unable to resolve host" in m -> "Sin conexión a internet"
        else -> m.lineSequence().firstOrNull { it.isNotBlank() }?.take(160) ?: "Error inesperado"
    }
}
