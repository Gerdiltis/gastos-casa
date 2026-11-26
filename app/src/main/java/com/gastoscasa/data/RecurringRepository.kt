package com.gastoscasa.data

import com.gastoscasa.domain.Frequency
import com.gastoscasa.domain.RecurringExpense
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Gastos recurrentes: se crean aquí y el creador confirma cada repetición. */
@Singleton
class RecurringRepository @Inject constructor(private val supabase: SupabaseClient) {

    suspend fun list(groupId: String? = null): List<RecurringExpense> =
        supabase.from("recurring_expenses").select {
            if (groupId != null) filter { eq("group_id", groupId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<RecurringDto>().map { it.toDomain() }

    suspend fun get(id: String): RecurringExpense? =
        supabase.from("recurring_expenses").select { filter { eq("id", id) } }
            .decodeSingleOrNull<RecurringDto>()?.toDomain()

    suspend fun create(
        groupId: String, description: String, amountCents: Long, categoryId: String?, paidBy: String,
        participants: List<String>, frequency: Frequency, start: LocalDate, end: LocalDate,
    ) {
        supabase.postgrest.rpc("create_recurring_expense", buildJsonObject {
            put("p_group_id", groupId)
            put("p_description", description)
            put("p_amount_cents", amountCents)
            put("p_category_id", categoryId)
            put("p_paid_by", paidBy)
            putJsonArray("p_participants") { participants.forEach { add(JsonPrimitive(it)) } }
            put("p_frequency", frequency.key)
            put("p_start", start.toString())
            put("p_end", end.toString())
        })
    }

    /** Apunta (accept) u omite la repetición de esa fecha. */
    suspend fun resolve(id: String, date: LocalDate, accept: Boolean, amountCents: Long? = null) {
        // Desde el botón de la notificación la app puede estar cerrada: esperar a cargar la sesión guardada.
        supabase.auth.awaitInitialization()
        supabase.postgrest.rpc("resolve_recurring", buildJsonObject {
            put("p_recurring_id", id)
            put("p_date", date.toString())
            put("p_accept", accept)
            if (amountCents != null) put("p_amount_cents", amountCents) else put("p_amount_cents", JsonNull)
        })
    }

    suspend fun update(
        id: String, description: String, amountCents: Long, categoryId: String?, paidBy: String,
        participants: List<String>, end: LocalDate,
    ) {
        supabase.postgrest.rpc("update_recurring", buildJsonObject {
            put("p_recurring_id", id)
            put("p_description", description)
            put("p_amount_cents", amountCents)
            put("p_category_id", categoryId)
            put("p_paid_by", paidBy)
            putJsonArray("p_participants") { participants.forEach { add(JsonPrimitive(it)) } }
            put("p_end", end.toString())
        })
    }

    suspend fun stop(id: String) {
        supabase.postgrest.rpc("stop_recurring", buildJsonObject { put("p_recurring_id", id) })
    }
}
