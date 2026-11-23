package com.gastoscasa.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.StateFlow
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepository @Inject constructor(private val supabase: SupabaseClient) {

    val sessionStatus: StateFlow<SessionStatus> get() = supabase.auth.sessionStatus

    val currentUserId: String? get() = supabase.auth.currentUserOrNull()?.id
    val currentEmail: String? get() = supabase.auth.currentUserOrNull()?.email

    suspend fun signIn(email: String, password: String) {
        supabase.auth.signInWith(Email) {
            this.email = email.trim()
            this.password = password
        }
    }

    suspend fun signUp(name: String, email: String, password: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email.trim()
            this.password = password
            data = buildJsonObject { put("display_name", name.trim()) }
        }
        // Con "Confirm email" desactivado el alta ya devuelve sesión; si no, iniciamos sesión.
        if (supabase.auth.currentSessionOrNull() == null) signIn(email, password)
    }

    suspend fun signOut() = supabase.auth.signOut()

    suspend fun myName(): String? {
        val id = currentUserId ?: return null
        return supabase.from("profiles").select { filter { eq("id", id) } }
            .decodeSingleOrNull<ProfileDto>()?.display_name
    }

    suspend fun updateName(name: String) {
        val id = currentUserId ?: return
        supabase.from("profiles").update({ set("display_name", name.trim()) }) { filter { eq("id", id) } }
    }
}
