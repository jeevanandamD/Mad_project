package com.example.sucs

import android.app.Application
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.gotrue.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

class SucsApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.initialize(
            supabase = createSupabaseClient(
                supabaseUrl = BuildConfig.SUPABASE_URL,
                supabaseKey = BuildConfig.SUPABASE_ANON_KEY
            ) {
                install(Auth)
                install(Postgrest)
                install(Realtime)
                install(Storage)
            }
        )
    }
}

object AppContainer {
    lateinit var supabase: io.github.jan.supabase.SupabaseClient
        private set

    fun initialize(supabase: io.github.jan.supabase.SupabaseClient) {
        this.supabase = supabase
    }
}
