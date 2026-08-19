package com.example.powerfix.data

import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class EmergencyRequest(
    val id: String = "",
    @get:PropertyName("user_id") @set:PropertyName("user_id") @SerialName("user_id") var userId: String = "",
    var message: String = "",
    var status: String = "Open",
    @get:PropertyName("created_at") @set:PropertyName("created_at") @SerialName("created_at") var createdAt: String? = null
)
