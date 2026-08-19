package com.example.powerfix

import android.app.Application
import android.content.Context
import com.example.powerfix.data.AuthRepository
import com.example.powerfix.data.ComplaintRepository
import com.example.powerfix.data.EmergencyRepository
import com.google.firebase.FirebaseApp

class PowerFixApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        AppContainer.initialize(applicationContext)
    }
}

object AppContainer {
    lateinit var authRepository: AuthRepository
        private set
    lateinit var complaintRepository: ComplaintRepository
        private set
    lateinit var emergencyRepository: EmergencyRepository
        private set

    fun initialize(context: Context) {
        this.authRepository = AuthRepository(context)
        this.complaintRepository = ComplaintRepository()
        this.emergencyRepository = EmergencyRepository()
    }
}

/**
 * Backward compatibility alias for legacy SUCS code references.
 */
@Deprecated("Use PowerFixApplication instead", ReplaceWith("PowerFixApplication"))
typealias SucsApplication = PowerFixApplication
