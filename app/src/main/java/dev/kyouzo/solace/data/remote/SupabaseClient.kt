package dev.kyouzo.solace.data.remote

import dev.kyouzo.solace.BuildConfig
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

/** Single configured client. It is deliberately unavailable until local.properties is configured. */
object SolaceSupabase {
    val isConfigured: Boolean get() = BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
    val client by lazy {
        require(isConfigured) { "Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties." }
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth) {
                scheme = "solace"
                host = "auth-callback"
            }
            install(Postgrest)
            install(Realtime)
        }
    }
}
