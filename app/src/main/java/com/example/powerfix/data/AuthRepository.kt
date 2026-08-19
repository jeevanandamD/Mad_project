package com.example.powerfix.data

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await
import java.lang.Exception

/**
 * Central authentication and registration API layer for PowerFix using Firebase.
 */
class AuthRepository(
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    /**
     * Verifies a TNEB Customer / Worker ID through the Firestore registry.
     */
    suspend fun verifyTnebId(role: AuthRole, tnebId: String): VerifyResult {
        val trimmedTneb = tnebId.trim()
        if (trimmedTneb.isEmpty()) return VerifyResult.Failed(AuthError.EMPTY_TNEB_ID, AuthError.EMPTY_TNEB_ID.userMessage(role))

        // --- DEVELOPMENT MOCK BYPASS START ---
        val mockIds = mapOf(
            AuthRole.CUSTOMER to listOf("12345678901", "98765432109", "CUST-1234-5678", "22556469956"),
            AuthRole.WORKER to listOf("WK-000123", "WK-000456")
        )
        if (mockIds[role]?.contains(trimmedTneb) == true) {
            return VerifyResult.Verified
        }
        // --- DEVELOPMENT MOCK BYPASS END ---

        return try {
            // Check if ID exists in the tneb_ids collection
            val doc = db.collection("tneb_ids").document(trimmedTneb).get().await()
            if (!doc.exists()) {
                VerifyResult.Failed(AuthError.TNEB_NOT_FOUND, AuthError.TNEB_NOT_FOUND.userMessage(role))
            } else {
                val registered = doc.getBoolean("is_registered") ?: false
                val idRole = doc.getString("role") ?: ""
                
                if (idRole != role.dbValue) {
                    VerifyResult.Failed(AuthError.TNEB_ROLE_MISMATCH, AuthError.TNEB_ROLE_MISMATCH.userMessage(role))
                } else if (registered) {
                    VerifyResult.Failed(AuthError.TNEB_ALREADY_REGISTERED, AuthError.TNEB_ALREADY_REGISTERED.userMessage(role))
                } else {
                    VerifyResult.Verified
                }
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Verification failed: ${e.message}")
            VerifyResult.Failed(AuthError.NETWORK_ERROR, AuthError.NETWORK_ERROR.userMessage(role))
        }
    }

    suspend fun registerCustomer(request: CustomerRegisterRequest): AuthResult =
        register(AuthRole.CUSTOMER, request.customerTnebId, request.name, request.email, request.phone, request.address, request.password)

    suspend fun registerWorker(request: WorkerRegisterRequest): AuthResult =
        register(AuthRole.WORKER, request.workerTnebId, request.name, request.email, request.phone, request.address, request.password)

    private suspend fun register(
        role: AuthRole, tnebId: String, name: String, email: String, phone: String, address: String, password: String
    ): AuthResult {
        return try {
            // 1. Create Firebase Auth User
            val authResult = auth.createUserWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("Auth failed")

            // 2. Create Profile in Firestore
            val profile = UserProfile(
                uid = user.uid,
                email = email,
                name = name,
                role = role.dbValue,
                phone = phone,
                address = address,
                tnebId = tnebId
            )
            db.collection("profiles").document(user.uid).set(profile).await()

            // 3. Mark TNEB ID as registered (Try-catch for mock IDs not present in Firestore)
            try {
                db.collection("tneb_ids").document(tnebId).update("is_registered", true).await()
            } catch (e: Exception) {
                Log.w("AuthRepository", "Could not update registration status for TNEB ID: ${e.message}")
            }

            val session = AuthSession(user.uid, role, tnebId, "")
            persistSession(session)
            AuthResult.Success(session)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Registration failed: ${e.message}")
            AuthResult.Failure(AuthError.SERVER_ERROR, e.localizedMessage ?: "Registration failed")
        }
    }

    suspend fun login(role: AuthRole, email: String, password: String, tnebId: String): AuthResult {
        return try {
            val authResult = auth.signInWithEmailAndPassword(email, password).await()
            val user = authResult.user ?: throw Exception("Login failed")

            val doc = db.collection("profiles").document(user.uid).get().await()
            val profile = doc.toObject(UserProfile::class.java) ?: throw Exception("Profile not found")

            if (profile.role != role.dbValue) {
                auth.signOut()
                return AuthResult.Failure(AuthError.INCORRECT_ROLE, "Incorrect role selected")
            }

            if (profile.tnebId != tnebId) {
                auth.signOut()
                return AuthResult.Failure(AuthError.TNEB_MISMATCH, AuthError.TNEB_MISMATCH.userMessage(role))
            }

            val session = AuthSession(user.uid, role, tnebId, "")
            persistSession(session)
            AuthResult.Success(session)
        } catch (e: Exception) {
            AuthResult.Failure(AuthError.INVALID_CREDENTIALS, "Login failed: ${e.localizedMessage}")
        }
    }

    suspend fun restoreSession(): AuthResult {
        val user = auth.currentUser ?: return AuthResult.Failure(AuthError.NO_SESSION, "No active session")
        val role = AuthRole.fromDb(PowerFixPrefs.getRole(context)) ?: AuthRole.CUSTOMER
        val tnebId = PowerFixPrefs.getTnebId(context)
        
        return AuthResult.Success(AuthSession(user.uid, role, tnebId, ""))
    }

    suspend fun logout() {
        auth.signOut()
        PowerFixPrefs.clear(context)
    }

    private fun persistSession(session: AuthSession) {
        PowerFixPrefs.setRole(context, session.role.dbValue)
        PowerFixPrefs.setTnebId(context, session.tnebId)
        PowerFixPrefs.setUserId(context, session.userId)
    }
}
