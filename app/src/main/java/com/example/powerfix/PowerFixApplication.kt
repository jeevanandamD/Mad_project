package com.example.powerfix

import android.app.Application
import android.content.Context
import com.example.powerfix.data.AuthRepository
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

class PowerFixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val supabase = createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_ANON_KEY
        ) {
            install(Auth)
            install(Postgrest)
            install(Realtime)
            install(Storage)
        }
        AppContainer.initialize(applicationContext, supabase)
    }
}

object AppContainer {
    lateinit var supabase: io.github.jan.supabase.SupabaseClient
        private set

    lateinit var authRepository: AuthRepository
        private set

    fun initialize(context: Context, supabase: io.github.jan.supabase.SupabaseClient) {
        this.supabase = supabase
        this.authRepository = AuthRepository(context)
    }
}

/**
 * Backward compatibility alias for legacy SUCS code references.
 */
@Deprecated("Use PowerFixApplication instead", ReplaceWith("PowerFixApplication"))
typealias SucsApplication = PowerFixApplication
