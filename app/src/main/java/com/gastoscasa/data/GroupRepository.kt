package com.gastoscasa.data

import com.gastoscasa.domain.Category
import com.gastoscasa.domain.Expense
import com.gastoscasa.domain.Group
import com.gastoscasa.domain.Member
import com.gastoscasa.domain.Split
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.postgrest.query.filter.FilterOperator
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonArray
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GroupRepository @Inject constructor(private val supabase: SupabaseClient) {

    private val expenseColumns = Columns.raw("*,expense_splits(user_id,share_cents)")

    /** Todos los grupos del usuario (RLS solo devuelve aquellos de los que es miembro). */
    suspend fun myGroups(): List<Group> {
        val groups = supabase.from("groups").select { order("created_at", Order.DESCENDING) }
            .decodeList<GroupDto>()
        if (groups.isEmpty()) return emptyList()
        val members = supabase.from("group_members")
            .select(Columns.raw("group_id,user_id,profiles(display_name)"))
            .decodeList<MemberDto>()
            .groupBy { it.group_id }
        return groups.map { g ->
            Group(
                id = g.id, name = g.name, inviteCode = g.invite_code, createdBy = g.created_by,
                members = members[g.id].orEmpty().map { Member(it.user_id, it.profiles?.display_name ?: "¿?") },
            )
        }
    }

    suspend fun group(groupId: String): Group? = myGroups().firstOrNull { it.id == groupId }

    suspend fun createGroup(name: String): String =
        supabase.postgrest.rpc("create_group", buildJsonObject { put("p_name", name) })
            .decodeAs<GroupDto>().id

    suspend fun joinGroup(code: String): String =
        supabase.postgrest.rpc("join_group", buildJsonObject { put("p_code", code) })
            .decodeAs<GroupDto>().id

    suspend fun leaveGroup(groupId: String, userId: String) {
        supabase.from("group_members").delete {
            filter { eq("group_id", groupId); eq("user_id", userId) }
        }
    }

    suspend fun renameGroup(groupId: String, name: String) {
        supabase.from("groups").update({ set("name", name.trim()) }) { filter { eq("id", groupId) } }
    }

    // ---------- Categorías ----------

    suspend fun categories(groupId: String? = null): List<Category> =
        supabase.from("categories").select {
            if (groupId != null) filter { eq("group_id", groupId) }
            order("name", Order.ASCENDING)
        }.decodeList<CategoryDto>().map { it.toDomain() }

    suspend fun addCategory(groupId: String, name: String, icon: String, color: String) {
        supabase.from("categories").insert(CategoryInsert(groupId, name.trim(), icon, color))
    }

    suspend fun deleteCategory(categoryId: String) {
        supabase.from("categories").delete { filter { eq("id", categoryId) } }
    }

    // ---------- Gastos ----------

    suspend fun expenses(groupId: String? = null): List<Expense> =
        supabase.from("expenses").select(expenseColumns) {
            if (groupId != null) filter { eq("group_id", groupId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<ExpenseDto>().map { it.toDomain() }

    suspend fun addExpense(
        groupId: String,
        description: String,
        amountCents: Long,
        categoryId: String?,
        paidBy: String,
        splits: List<Split>,
        isSettlement: Boolean = false,
    ) {
        supabase.postgrest.rpc("add_expense", buildJsonObject {
            put("p_group_id", groupId)
            put("p_description", description)
            put("p_amount_cents", amountCents)
            put("p_category_id", categoryId)
            put("p_paid_by", paidBy)
            putJsonArray("p_splits") {
                splits.forEach { s ->
                    add(buildJsonObject { put("user_id", s.userId); put("share_cents", s.shareCents) })
                }
            }
            put("p_is_settlement", isSettlement)
        })
    }

    suspend fun deleteExpense(expenseId: String) {
        supabase.from("expenses").delete { filter { eq("id", expenseId) } }
    }

    suspend fun archiveSettled(groupId: String) {
        supabase.postgrest.rpc("archive_group_expenses", buildJsonObject { put("p_group_id", groupId) })
    }

    /** Escucha cambios en los gastos del grupo hasta que se cancele la corrutina. */
    suspend fun observeExpenses(groupId: String, onChange: () -> Unit) = coroutineScope {
        val channel = supabase.channel("expenses-$groupId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") {
            table = "expenses"
            filter("group_id", FilterOperator.EQ, groupId)
        }
        val job = launch { changes.collect { onChange() } }
        try {
            channel.subscribe()
            job.join()
        } finally {
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }
}
