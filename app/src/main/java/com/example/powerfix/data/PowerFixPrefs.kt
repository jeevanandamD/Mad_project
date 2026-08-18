package com.example.powerfix.data

import android.content.Context
import android.content.SharedPreferences

/**
 * PowerFixPrefs provides centralized SharedPreferences management with
 * automatic backward-compatibility migration from legacy "sucs_prefs".
 */
object PowerFixPrefs {
    private const val PREFS_NEW = "powerfix_prefs"
    private const val PREFS_LEGACY = "sucs_prefs"

    private const val KEY_ROLE = "role"
    private const val KEY_TNEB_ID = "tneb_id"
    private const val KEY_WORKER_AVAILABLE = "worker_available"
    private const val KEY_USER_ID = "user_id"
    private const val KEY_AUTH_TOKEN = "auth_token"

    fun getPrefs(context: Context): SharedPreferences {
        val newPrefs = context.getSharedPreferences(PREFS_NEW, Context.MODE_PRIVATE)
        val legacyPrefs = context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE)

        // Migrate legacy keys if new prefs are empty but legacy exists
        if (!newPrefs.contains(KEY_ROLE) && legacyPrefs.contains(KEY_ROLE)) {
            val legacyRole = legacyPrefs.getString(KEY_ROLE, "customer") ?: "customer"
            val legacyAvailable = legacyPrefs.getBoolean(KEY_WORKER_AVAILABLE, true)
            newPrefs.edit()
                .putString(KEY_ROLE, legacyRole)
                .putBoolean(KEY_WORKER_AVAILABLE, legacyAvailable)
                .apply()
        }
        return newPrefs
    }

    fun getRole(context: Context): String? {
        return getPrefs(context).getString(KEY_ROLE, null)
            ?: context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).getString(KEY_ROLE, null)
    }

    fun setRole(context: Context, role: String) {
        getPrefs(context).edit().putString(KEY_ROLE, role).apply()
        // Dual-write to legacy prefs for backward compatibility
        context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).edit().putString(KEY_ROLE, role).apply()
    }

    fun isWorkerAvailable(context: Context): Boolean {
        return getPrefs(context).getBoolean(KEY_WORKER_AVAILABLE, true)
    }

    fun getTnebId(context: Context): String {
        return getPrefs(context).getString(KEY_TNEB_ID, null)
            ?: context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).getString(KEY_TNEB_ID, "")
            ?: ""
    }

    fun setTnebId(context: Context, tnebId: String) {
        getPrefs(context).edit().putString(KEY_TNEB_ID, tnebId).apply()
        // Dual-write to legacy prefs for backward compatibility
        context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).edit().putString(KEY_TNEB_ID, tnebId).apply()
    }

    fun setWorkerAvailable(context: Context, available: Boolean) {
        getPrefs(context).edit().putBoolean(KEY_WORKER_AVAILABLE, available).apply()
        context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).edit().putBoolean(KEY_WORKER_AVAILABLE, available).apply()
    }

    fun getUserId(context: Context): String {
        return getPrefs(context).getString(KEY_USER_ID, null)
            ?: context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).getString(KEY_USER_ID, "")
            ?: ""
    }

    fun setUserId(context: Context, userId: String) {
        getPrefs(context).edit().putString(KEY_USER_ID, userId).apply()
        context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).edit().putString(KEY_USER_ID, userId).apply()
    }

    fun getAuthToken(context: Context): String {
        return getPrefs(context).getString(KEY_AUTH_TOKEN, null)
            ?: context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).getString(KEY_AUTH_TOKEN, "")
            ?: ""
    }

    fun setAuthToken(context: Context, token: String?) {
        getPrefs(context).edit().putString(KEY_AUTH_TOKEN, token).apply()
        context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).edit().putString(KEY_AUTH_TOKEN, token).apply()
    }

    fun clear(context: Context) {
        getPrefs(context).edit().clear().apply()
        context.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
