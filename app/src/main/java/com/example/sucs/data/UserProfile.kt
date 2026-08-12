package com.example.sucs.data

import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val name: String = "",
    val role: String = "customer",
    val phone: String = "",
    val address: String = "",
    val available: Boolean = true,
    val location: String = "",
    val createdAt: Long = System.currentTimeMillis()
)
