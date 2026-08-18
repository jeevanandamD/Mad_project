package com.example.powerfix.data

import android.content.Context
import android.util.Log
import com.example.powerfix.AppContainer
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Central authentication and registration API layer for PowerFix.
 *
 * Every operation here is the Android-side counterpart of the documented
 * backend endpoints:
 *
 *   POST /auth/customer/register    -> [registerCustomer]
 *   POST /auth/worker/register      -> [registerWorker]
 *   POST /auth/customer/verify-tneb-id / /auth/worker/verify-tneb-id -> [verifyTnebId]
 *   POST /auth/login                -> [login]
 *   GET  /auth/session              -> [restoreSession]
 *   POST /auth/logout               -> [logout]
 *
 * The backend is Supabase (PostgREST + RPC). TNEB verification and profile
 * creation are delegated to security-definer RPC functions so a client can
 * never fabricate a verified TNEB ID or register under an arbitrary role.
 */
class AuthRepository(
    private val context: Context,
    private val supabase: SupabaseClient = AppContainer.supabase,
    private val tnebVerifier: TnebIdVerifier = SupabaseTnebIdVerifier(AppContainer.supabase)
) {

    /**
     * Verifies a TNEB Customer / Worker ID through the backend registry.
     */
    suspend fun verifyTnebId(role: AuthRole, tnebId: String): VerifyResult =
        tnebVerifier.verify(role, tnebId)

    /**
     * Registers a new Customer account. The TNEB Customer ID is verified before
     * the auth user is created, and the profile is only written through the
     * backend `create_profile_with_tneb` RPC (which re-verifies the ID).
     */
    suspend fun registerCustomer(request: CustomerRegisterRequest): AuthResult =
        register(
            role = AuthRole.CUSTOMER,
            tnebId = request.customerTnebId,
            name = request.name,
            email = request.email,
            phone = request.phone,
            address = request.address,
            password = request.password
        )

    /**
     * Registers a new Worker account. The TNEB Worker ID is verified before the
     * auth user is created, and the profile is only written through the backend
     * `create_profile_with_tneb` RPC (which re-verifies the ID).
     */
    suspend fun registerWorker(request: WorkerRegisterRequest): AuthResult =
        register(
            role = AuthRole.WORKER,
            tnebId = request.workerTnebId,
            name = request.name,
            email = request.email,
            phone = request.phone,
            address = request.address,
            password = request.password
        )

    private suspend fun register(
        role: AuthRole,
        tnebId: String,
        name: String,
        email: String,
        phone: String,
        address: String,
        password: String
    ): AuthResult {
        val trimmedTneb = tnebId.trim()

        val verification = verifyTnebId(role, trimmedTneb)
        if (verification !is VerifyResult.Verified) {
            return when (verification) {
                is VerifyResult.Failed -> AuthResult.Failure(verification.error, verification.message)
                else -> AuthResult.Failure(AuthError.TNEB_VERIFICATION_FAILED, AuthError.TNEB_VERIFICATION_FAILED.userMessage(role))
            }
        }

        try {
            signUpWithEmail(email, password)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Registration sign-up failed: ${e.message}", e)
            val error = mapAuthException(e)
            return AuthResult.Failure(error, error.userMessage(role))
        }

        val user = supabase.auth.currentUserOrNull()
        if (user == null) {
            // No session means email confirmation is enabled: the auth user
            // exists but cannot be used until the email is confirmed. The
            // profile is created (via the same RPC) on first login.
            return AuthResult.NeedsEmailConfirmation(
                "Account created. Check your email to confirm, then log in."
            )
        }

        val created = try {
            createProfileViaRpc(
                uid = user!!.id,
                email = email,
                name = name,
                phone = phone,
                address = address,
                role = role,
                tnebId = trimmedTneb
            )
        } catch (e: Exception) {
            Log.e("AuthRepository", "Registration RPC failed: ${e.message}", e)
            return AuthResult.Failure(
                AuthError.SERVER_ERROR,
                "Account created but profile setup failed. Please contact support. (${e.localizedMessage})"
            )
        }

        if (!created.success) {
            val error = mapRegistrationCode(created.code)
            return AuthResult.Failure(error, error.userMessage(role))
        }

        persistSession(
            AuthSession(
                userId = user!!.id,
                role = role,
                tnebId = trimmedTneb,
                accessToken = supabase.auth.currentAccessTokenOrNull()
            )
        )
        return AuthResult.Success(session())
    }

    /**
     * Signs an existing user in and validates that the role and TNEB ID the user
     * selected match the account they registered with. Legacy profiles without a
     * TNEB ID keep working and are backfilled best-effort.
     */
    suspend fun login(role: AuthRole, email: String, password: String, tnebId: String): AuthResult {
        val trimmedTneb = tnebId.trim()

        if (!TnebIdValidator.isValidForRole(role.dbValue, trimmedTneb)) {
            val error = if (trimmedTneb.isEmpty()) AuthError.EMPTY_TNEB_ID else AuthError.INVALID_TNEB_FORMAT
            return AuthResult.Failure(error, error.userMessage(role))
        }

        try {
            supabase.auth.signInWith(Email) {
                this.email = email
                this.password = password
            }
        } catch (e: Exception) {
            Log.e("AuthRepository", "Login sign-in failed: ${e.message}", e)
            val error = mapAuthException(e)
            return AuthResult.Failure(error, error.userMessage(role))
        }

        val user = supabase.auth.currentUserOrNull()
            ?: return AuthResult.Failure(AuthError.NO_SESSION, AuthError.NO_SESSION.userMessage(role))

        var profile = try {
            fetchProfile(user.id)
        } catch (e: Exception) {
            Log.e("AuthRepository", "Failed to fetch profile: ${e.message}", e)
            null
        }

        if (profile == null) {
            // Backward compatibility: accounts created before profiles existed or
            // that never got a profile row. Prefer the verified backend path and
            // only fall back to the legacy unverified insert for the rare users
            // whose TNEB ID is not in the registry yet.
            val created = try {
                createProfileViaRpc(
                    uid = user.id,
                    email = email,
                    name = "",
                    phone = "",
                    address = "",
                    role = role,
                    tnebId = trimmedTneb
                )
            } catch (e: Exception) {
                Log.w("AuthRepository", "Login profile RPC failed, trying legacy insert: ${e.message}")
                CreateProfileResponse(success = false, code = "rpc_error")
            }
            if (created.success) {
                profile = fetchProfile(user.id)
            } else {
                val legacyProfile = UserProfile(
                    uid = user.id,
                    email = email,
                    role = role.dbValue,
                    tnebId = trimmedTneb
                )
                try {
                    supabase.postgrest.from("profiles").insert(legacyProfile)
                    profile = legacyProfile
                } catch (e: Exception) {
                    Log.e("AuthRepository", "Legacy profile insert failed: ${e.message}", e)
                    return AuthResult.Failure(AuthError.SERVER_ERROR, AuthError.SERVER_ERROR.userMessage(role))
                }
            }
        }

        val storedProfile = profile ?: return AuthResult.Failure(AuthError.SERVER_ERROR, AuthError.SERVER_ERROR.userMessage(role))

        if (storedProfile.disabled) {
            bestEffortSignOutAndClear()
            return AuthResult.Failure(AuthError.ACCOUNT_DISABLED, AuthError.ACCOUNT_DISABLED.userMessage(role))
        }

        val storedRole = AuthRole.fromDb(storedProfile.role) ?: AuthRole.CUSTOMER
        if (storedRole != role) {
            return AuthResult.Failure(
                AuthError.INCORRECT_ROLE,
                "This account is registered as ${storedRole.display}. Please select the matching role."
            )
        }

        val storedTneb = storedProfile.tnebId.trim()
        if (storedTneb.isNotEmpty() && !storedTneb.equals(trimmedTneb, ignoreCase = true)) {
            return AuthResult.Failure(AuthError.TNEB_MISMATCH, AuthError.TNEB_MISMATCH.userMessage(role))
        }

        // Backfill the TNEB ID for legacy accounts that predate this field.
        if (storedTneb.isEmpty()) {
            try {
                supabase.postgrest.from("profiles")
                    .update(mapOf("tneb_id" to trimmedTneb)) {
                        filter { eq("uid", user.id) }
                    }
            } catch (_: Exception) {
                // Best effort - the session still proceeds.
            }
        }

        val session = AuthSession(
            userId = user.id,
            role = storedRole,
            tnebId = if (storedTneb.isEmpty()) trimmedTneb else storedTneb,
            accessToken = supabase.auth.currentAccessTokenOrNull()
        )
        persistSession(session)
        return AuthResult.Success(session)
    }

    /**
     * Restores a persisted session on app launch. Fails (and clears local state)
     * when the Supabase session has expired or been revoked.
     */
    suspend fun restoreSession(): AuthResult {
        val user = supabase.auth.currentUserOrNull()
        if (user == null) {
            PowerFixPrefs.clear(context)
            return AuthResult.Failure(AuthError.NO_SESSION, AuthError.NO_SESSION.userMessage(AuthRole.CUSTOMER))
        }

        val roleString = PowerFixPrefs.getRole(context)
        val role = roleString?.let { AuthRole.fromDb(it) }
        if (role == null) {
            // No role stored - need to go through login flow to select role
            PowerFixPrefs.clear(context)
            return AuthResult.Failure(AuthError.NO_SESSION, "Please select your role (Customer/Worker) to continue")
        }
        val tnebId = PowerFixPrefs.getTnebId(context)

        // Re-check the account state so a disabled user is kicked out promptly.
        try {
            val profile = fetchProfile(user.id)
            if (profile?.disabled == true) {
                bestEffortSignOutAndClear()
                return AuthResult.Failure(AuthError.ACCOUNT_DISABLED, AuthError.ACCOUNT_DISABLED.userMessage(role))
            }
        } catch (_: Exception) {
            // Offline restore: proceed with the cached session.
        }

        return AuthResult.Success(
            AuthSession(
                userId = user.id,
                role = role,
                tnebId = tnebId,
                accessToken = supabase.auth.currentAccessTokenOrNull()
            )
        )
    }

    /**
     * Signs the current user out and clears all persisted auth state.
     */
    suspend fun logout() {
        bestEffortSignOutAndClear()
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private suspend fun signUpWithEmail(email: String, password: String) {
        supabase.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    private suspend fun fetchProfile(uid: String): UserProfile? {
        return supabase.postgrest.from("profiles")
            .select(columns = Columns.list("*")) {
                filter { eq("uid", uid) }
            }
            .decodeList<UserProfile>()
            .firstOrNull()
    }

    private suspend fun createProfileViaRpc(
        uid: String,
        email: String,
        name: String,
        phone: String,
        address: String,
        role: AuthRole,
        tnebId: String
    ): CreateProfileResponse {
        return supabase.postgrest.rpc(
            "create_profile_with_tneb",
            buildJsonObject {
                put("p_uid", uid)
                put("p_email", email)
                put("p_name", name)
                put("p_phone", phone)
                put("p_address", address)
                put("p_role", role.dbValue)
                put("p_tneb_id", tnebId)
            }
        ).decodeAs<CreateProfileResponse>()
    }

    private fun persistSession(session: AuthSession) {
        PowerFixPrefs.setRole(context, session.role.dbValue)
        PowerFixPrefs.setTnebId(context, session.tnebId)
        PowerFixPrefs.setUserId(context, session.userId)
        PowerFixPrefs.setAuthToken(context, session.accessToken)
    }

    private fun session(): AuthSession {
        val user = supabase.auth.currentUserOrNull()
        return AuthSession(
            userId = user?.id ?: "",
            role = AuthRole.fromDb(PowerFixPrefs.getRole(context)) ?: AuthRole.CUSTOMER,
            tnebId = PowerFixPrefs.getTnebId(context),
            accessToken = supabase.auth.currentAccessTokenOrNull()
        )
    }

    private suspend fun bestEffortSignOutAndClear() {
        try {
            supabase.auth.signOut()
        } catch (_: Exception) {
            // Best effort - local state is still cleared below.
        }
        PowerFixPrefs.clear(context)
    }

    private fun mapAuthException(e: Exception): AuthError {
        val message = e.message?.lowercase().orEmpty()
        return when {
            message.contains("email not confirmed") -> AuthError.EMAIL_NOT_CONFIRMED
            message.contains("invalid login credentials") || 
            message.contains("invalid_credentials") || 
            message.contains("invalid grant") ||
            message.contains("400") -> AuthError.INVALID_CREDENTIALS
            message.contains("already registered") || 
            message.contains("user already") ||
            message.contains("409") -> AuthError.ACCOUNT_ALREADY_EXISTS
            message.contains("connect") || 
            message.contains("socket") || 
            message.contains("timeout") || 
            message.contains("unknownhost") -> AuthError.NETWORK_ERROR
            else -> AuthError.SERVER_ERROR
        }
    }

    private fun mapRegistrationCode(code: String): AuthError = when (code) {
        "unauthorized" -> AuthError.NO_SESSION
        "invalid_role" -> AuthError.UNKNOWN
        "empty" -> AuthError.EMPTY_TNEB_ID
        "invalid_format" -> AuthError.INVALID_TNEB_FORMAT
        "not_found" -> AuthError.TNEB_NOT_FOUND
        "role_mismatch" -> AuthError.TNEB_ROLE_MISMATCH
        "inactive" -> AuthError.TNEB_INACTIVE
        "already_registered" -> AuthError.TNEB_ALREADY_REGISTERED
        "verification_failed" -> AuthError.TNEB_VERIFICATION_FAILED
        else -> AuthError.SERVER_ERROR
    }
}
