package com.example.powerfix.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmergencyRequest(
    val id: String = "",
    @SerialName("user_id") val userId: String = "",
    val message: String = "",
    val status: String = "Open",
    @SerialName("created_at") val createdAt: String? = null
)
