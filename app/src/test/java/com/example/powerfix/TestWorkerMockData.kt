package com.example.powerfix

import android.content.Context
import com.example.powerfix.data.PowerFixPrefs

object TestWorkerMockData {

    /** Sets up SharedPreferences to simulate a logged-in worker.
     *  Call this before launching activities that check auth state.
     */
    fun mockWorkerLogin(context: Context, tnebId: String = "WK-000123", userId: String = "worker-456", token: String = "fake-token-123") {
        PowerFixPrefs.setRole(context, AuthRole.WORKER.dbValue)
        PowerFixPrefs.setTnebId(context, tnebId)
        PowerFixPrefs.setUserId(context, userId)
        PowerFixPrefs.setAuthToken(context, token)
    }

    /** Sets up SharedPreferences to simulate a logged-in customer.
     *  Call this before launching activities that check auth state.
     */
    fun mockCustomerLogin(context: Context, tnebId: String = "12345678901", userId: String = "customer-789", token: String = "fake-token-456") {
        PowerFixPrefs.setRole(context, AuthRole.CUSTOMER.dbValue)
        PowerFixPrefs.setTnebId(context, tnebId)
        PowerFixPrefs.setUserId(context, userId)
        PowerFixPrefs.setAuthToken(context, token)
    }

    /** Clears all mock preferences (simulates logged-out state). */
    fun mockLogout(context: Context) {
        PowerFixPrefs.clear(context)
    }
}