package com.example.powerfix.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "customer",
    val phone: String = "",
    val address: String = "",
    @SerialName("tneb_id") val tnebId: String = "",
    val available: Boolean = true,
    val location: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val disabled: Boolean = false
)
