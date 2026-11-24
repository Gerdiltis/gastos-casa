package com.gastoscasa.data

import com.gastoscasa.domain.ShoppingItem
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.realtime
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** Lista de la compra: una por grupo, compartida por todos sus miembros. */
@Singleton
class ShoppingRepository @Inject constructor(private val supabase: SupabaseClient) {

    suspend fun items(groupId: String): List<ShoppingItem> =
        supabase.from("shopping_items").select {
            filter { eq("group_id", groupId) }
            order("created_at", Order.DESCENDING)
        }.decodeList<ShoppingItemDto>().map { it.toDomain() }

    suspend fun add(groupId: String, name: String): ShoppingItem {
        val me = supabase.auth.currentUserOrNull()?.id ?: error("No autenticado")
        return supabase.from("shopping_items")
            .insert(ShoppingItemInsert(groupId, name.trim(), me)) { select() }
            .decodeSingle<ShoppingItemDto>().toDomain()
    }

    suspend fun remove(itemId: String) {
        supabase.from("shopping_items").delete { filter { eq("id", itemId) } }
    }

    /**
     * Avisa de cualquier cambio en la lista. Sin filtro por grupo porque Supabase no
     * filtra los DELETE; quien llama vuelve a cargar la lista del grupo que está viendo.
     */
    suspend fun observe(groupId: String, onChange: () -> Unit) = coroutineScope {
        val channel = supabase.channel("shopping-$groupId")
        val changes = channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "shopping_items" }
        val job = launch { changes.collect { onChange() } }
        try {
            channel.subscribe()
            job.join()
        } finally {
            withContext(NonCancellable) { supabase.realtime.removeChannel(channel) }
        }
    }
}
