package com.example.powerfix.data

import androidx.fragment.app.Fragment
import com.example.powerfix.ui.admin.AdminDashboardFragment
import com.example.powerfix.ui.customer.CustomerDashboardFragment
import com.example.powerfix.ui.worker.WorkerDashboardFragment
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Canonical application roles. The persisted values in the database remain
 * lowercase ("customer" / "worker") for backward compatibility with existing
 * policies and triggers, while the canonical model uses CUSTOMER / WORKER.
 */
enum class AuthRole(val dbValue: String, val display: String) {
    CUSTOMER("customer", "Customer"),
    WORKER("worker", "Worker"),
    ADMIN("admin", "Admin");

    companion object {
        fun fromDb(value: String?): AuthRole? = when (value?.lowercase()) {
            "customer" -> CUSTOMER
            "worker" -> WORKER
            "admin" -> ADMIN
            else -> null
        }

        fun fromDisplay(value: String?): AuthRole? = when (value?.trim()) {
            "Customer" -> CUSTOMER
            "Worker" -> WORKER
            else -> null
        }
    }
}

/** Returns the fragment instance representing the dashboard for this role. */
fun AuthRole.dashboard(): Fragment = when (this) {
    AuthRole.ADMIN -> AdminDashboardFragment()
    AuthRole.WORKER -> WorkerDashboardFragment()
    AuthRole.CUSTOMER -> CustomerDashboardFragment()
}

/** Result of a backend TNEB ID verification. */
sealed class VerifyResult {
    data object Verified : VerifyResult()
    data class Failed(val error: AuthError, val message: String) : VerifyResult()
}

/** A fully resolved authenticated session. */
data class AuthSession(
    val userId: String,
    val role: AuthRole,
    val tnebId: String,
    val accessToken: String?
)

/** Outcome of an authentication/registration API call. */
sealed class AuthResult {
    data class Success(val session: AuthSession) : AuthResult()
    data class Failure(val error: AuthError, val message: String) : AuthResult()

    /** The auth user was created but email confirmation is still pending. */
    data class NeedsEmailConfirmation(val message: String) : AuthResult()
}

/** Error codes shared by the authentication and TNEB verification APIs. */
enum class AuthError {
    EMPTY_TNEB_ID,
    INVALID_TNEB_FORMAT,
    TNEB_NOT_FOUND,
    TNEB_INACTIVE,
    TNEB_ALREADY_REGISTERED,
    TNEB_ROLE_MISMATCH,
    TNEB_VERIFICATION_FAILED,
    TNEB_SERVICE_UNAVAILABLE,
    TNEB_MISMATCH,
    INVALID_CREDENTIALS,
    ACCOUNT_DISABLED,
    INCORRECT_ROLE,
    EMAIL_NOT_CONFIRMED,
    ACCOUNT_ALREADY_EXISTS,
    EXPIRED_SESSION,
    NO_SESSION,
    NETWORK_ERROR,
    SERVER_ERROR,
    VALIDATION_ERROR,
    UNKNOWN
}

/** User-facing messages for the documented error scenarios. */
fun AuthError.userMessage(role: AuthRole): String = when (this) {
    AuthError.EMPTY_TNEB_ID ->
        if (role == AuthRole.CUSTOMER) "Please enter your TNEB Customer ID."
        else "Please enter your TNEB Worker ID."
    AuthError.INVALID_TNEB_FORMAT -> "Invalid TNEB ID format."
    AuthError.TNEB_NOT_FOUND, AuthError.TNEB_VERIFICATION_FAILED ->
        if (role == AuthRole.CUSTOMER) "Customer ID could not be verified."
        else "Worker ID could not be verified."
    AuthError.TNEB_INACTIVE -> "This TNEB ID is inactive."
    AuthError.TNEB_ALREADY_REGISTERED -> "This TNEB ID is already registered."
    AuthError.TNEB_ROLE_MISMATCH ->
        if (role == AuthRole.CUSTOMER) "This ID belongs to a worker account, not a customer."
        else "This ID belongs to a customer account, not a worker."
    AuthError.TNEB_SERVICE_UNAVAILABLE -> "TNEB verification service is currently unavailable."
    AuthError.TNEB_MISMATCH -> "The TNEB ID does not match the one on this account."
    AuthError.INVALID_CREDENTIALS -> "Invalid login credentials."
    AuthError.ACCOUNT_DISABLED -> "Your account has been disabled. Please contact PowerFix support."
    AuthError.INCORRECT_ROLE -> "This account is registered under a different role. Please select the matching role."
    AuthError.EMAIL_NOT_CONFIRMED -> "Please confirm your email address before logging in."
    AuthError.ACCOUNT_ALREADY_EXISTS -> "An account with this email already exists."
    AuthError.EXPIRED_SESSION -> "Your session has expired."
    AuthError.NO_SESSION -> "You are not signed in."
    AuthError.NETWORK_ERROR -> "Network connection failed. Please check your internet connection and try again."
    AuthError.SERVER_ERROR -> "The server could not process the request. Please try again."
    AuthError.VALIDATION_ERROR -> "Please fill out all fields correctly."
    AuthError.UNKNOWN -> "Something went wrong. Please try again."
}

/** Request payload for customer registration (POST /auth/customer/register). */
@Serializable
data class CustomerRegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val address: String,
    val password: String,
    @SerialName("customerTnebId") val customerTnebId: String
)

/** Request payload for worker registration (POST /auth/worker/register). */
@Serializable
data class WorkerRegisterRequest(
    val name: String,
    val email: String,
    val phone: String,
    val address: String,
    val password: String,
    @SerialName("workerTnebId") val workerTnebId: String
)

@Serializable
internal data class VerifyTnebIdResponse(
    val verified: Boolean = false,
    val code: String = ""
)

@Serializable
internal data class CreateProfileResponse(
    val success: Boolean = false,
    val code: String = "",
    val existing: Boolean = false
)
