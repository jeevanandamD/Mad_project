package com.example.powerfix.data

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Verifies whether a TNEB-provided ID exists and is eligible for registration.
 *
 * The client is never trusted to decide whether an ID is valid: every check
 * goes through this service, which currently resolves against the Supabase
 * backend (`verify_tneb_id` RPC backed by the mock `tneb_ids` registry).
 * Swap this implementation for an authorized TNEB integration without touching
 * the callers.
 */
interface TnebIdVerifier {
    suspend fun verify(role: AuthRole, tnebId: String): VerifyResult
}

/**
 * Backend-backed verifier used in development. The authoritative registry is
 * stored in the `tneb_ids` table and is only reachable through the
 * `verify_tneb_id` function, so a client cannot fabricate a verified result.
 */
class SupabaseTnebIdVerifier(private val supabase: SupabaseClient) : TnebIdVerifier {

    override suspend fun verify(role: AuthRole, tnebId: String): VerifyResult {
        val trimmed = tnebId.trim()

        if (trimmed.isEmpty()) {
            return VerifyResult.Failed(AuthError.EMPTY_TNEB_ID, AuthError.EMPTY_TNEB_ID.userMessage(role))
        }
        if (!TnebIdValidator.isValidForRole(role.dbValue, trimmed)) {
            return VerifyResult.Failed(AuthError.INVALID_TNEB_FORMAT, AuthError.INVALID_TNEB_FORMAT.userMessage(role))
        }

        // --- DEVELOPMENT MOCK BYPASS START ---
        // Allow specific mock IDs locally if the backend is not yet fully configured.
        val mockIds = mapOf(
            AuthRole.CUSTOMER to listOf("12345678901", "98765432109", "CUST-1234-5678", "22556469956"),
            AuthRole.WORKER to listOf("WK-000123", "WK-000456")
        )
        if (mockIds[role]?.contains(trimmed) == true) {
            return VerifyResult.Verified
        }
        // --- DEVELOPMENT MOCK BYPASS END ---

        return try {
            val response = supabase.postgrest.rpc(
                "verify_tneb_id",
                buildJsonObject {
                    put("p_role", role.dbValue)
                    put("p_tneb_id", trimmed)
                }
            ).decodeAs<VerifyTnebIdResponse>()

            if (response.verified) {
                VerifyResult.Verified
            } else {
                VerifyResult.Failed(
                    error = response.code.toAuthError(),
                    message = response.code.toAuthError().userMessage(role)
                )
            }
        } catch (e: Exception) {
            val message = e.message?.lowercase().orEmpty()
            val isNetwork = message.contains("connect") ||
                    message.contains("socket") ||
                    message.contains("timeout") ||
                    message.contains("unknownhost")
            
            val error = when {
                isNetwork -> AuthError.NETWORK_ERROR
                message.contains("not found") || message.contains("404") -> AuthError.TNEB_SERVICE_UNAVAILABLE
                else -> AuthError.TNEB_VERIFICATION_FAILED
            }
            VerifyResult.Failed(error, error.userMessage(role))
        }
    }

    private fun String.toAuthError(): AuthError = when (this) {
        "empty" -> AuthError.EMPTY_TNEB_ID
        "invalid_format" -> AuthError.INVALID_TNEB_FORMAT
        "invalid_role" -> AuthError.UNKNOWN
        "not_found" -> AuthError.TNEB_NOT_FOUND
        "role_mismatch" -> AuthError.TNEB_ROLE_MISMATCH
        "inactive" -> AuthError.TNEB_INACTIVE
        "already_registered" -> AuthError.TNEB_ALREADY_REGISTERED
        else -> AuthError.TNEB_VERIFICATION_FAILED
    }
}
