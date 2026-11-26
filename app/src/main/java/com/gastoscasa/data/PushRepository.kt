package com.gastoscasa.data

import com.google.firebase.messaging.FirebaseMessaging
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.coroutines.tasks.await
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

/** Registra el token de FCM de este móvil para recibir avisos de gastos. */
@Singleton
class PushRepository @Inject constructor(private val supabase: SupabaseClient) {

    suspend fun register(token: String? = null) {
        supabase.auth.awaitInitialization()
        if (supabase.auth.currentSessionOrNull() == null) return
        val t = token ?: FirebaseMessaging.getInstance().token.await()
        supabase.postgrest.rpc("register_device", buildJsonObject { put("p_token", t) })
    }

    /** Al cerrar sesión: este móvil deja de recibir avisos de esa cuenta. */
    suspend fun unregister() {
        val t = runCatching { FirebaseMessaging.getInstance().token.await() }.getOrNull() ?: return
        runCatching { supabase.from("device_tokens").delete { filter { eq("token", t) } } }
    }
}
