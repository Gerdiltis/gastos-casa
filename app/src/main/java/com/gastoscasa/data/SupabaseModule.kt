package com.gastoscasa.data

import com.gastoscasa.BuildConfig
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import javax.inject.Singleton

object AppConfig {
    val isSupabaseConfigured: Boolean
        get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
}

@Module
@InstallIn(SingletonComponent::class)
object SupabaseModule {

    @Provides
    @Singleton
    fun provideSupabase(): SupabaseClient = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL.ifBlank { "https://not-configured.supabase.co" },
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY.ifBlank { "not-configured" },
    ) {
        install(Auth)
        install(Postgrest)
        install(Realtime)
    }

    @Provides
    @Singleton
    fun provideHttpClient(): HttpClient = HttpClient(OkHttp)
}
