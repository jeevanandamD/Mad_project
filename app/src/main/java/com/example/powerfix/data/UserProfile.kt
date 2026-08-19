package com.example.powerfix.data

import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class UserProfile(
    var uid: String = "",
    var email: String = "",
    var name: String = "",
    var role: String = "customer",
    var phone: String = "",
    var address: String = "",
    @get:PropertyName("tneb_id") @set:PropertyName("tneb_id") @SerialName("tneb_id") var tnebId: String = "",
    var available: Boolean = true,
    var location: String = "",
    @get:PropertyName("created_at") @set:PropertyName("created_at") @SerialName("created_at") var createdAt: String? = null,
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") @SerialName("updated_at") var updatedAt: String? = null,
    var disabled: Boolean = false
)
