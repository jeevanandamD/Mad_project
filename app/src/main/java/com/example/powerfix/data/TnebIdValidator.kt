package com.example.powerfix.data

/**
 * Validates TNEB-provided IDs (customer service connection numbers and worker IDs).
 *
 * Format rules: 6-20 characters, allowing letters, digits and the separators
 * ':' '.' '_' '-'. An empty value is always invalid.
 */
object TnebIdValidator {

    private val ID_PATTERN = Regex("^[A-Za-z0-9:._-]{6,20}$")

    fun isValidCustomerId(id: String): Boolean = ID_PATTERN.matches(id.trim())

    fun isValidWorkerId(id: String): Boolean = ID_PATTERN.matches(id.trim())

    /**
     * Returns true when [id] is non-empty and matches the format rule for [role].
     * Unknown roles fall back to the customer rule.
     */
    fun isValidForRole(role: String, id: String): Boolean {
        val trimmed = id.trim()
        if (trimmed.isEmpty()) return false
        return when (role.lowercase()) {
            "worker" -> isValidWorkerId(trimmed)
            else -> isValidCustomerId(trimmed)
        }
    }
}
