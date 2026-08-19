package com.example.powerfix.data

import com.google.firebase.firestore.PropertyName
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Complaint(
    val id: String = "",
    @get:PropertyName("customer_id") @set:PropertyName("customer_id") @SerialName("customer_id") var customerId: String = "",
    @get:PropertyName("customer_name") @set:PropertyName("customer_name") @SerialName("customer_name") var customerName: String = "",
    val mobile: String = "",
    val address: String = "",
    @get:PropertyName("complaint_type") @set:PropertyName("complaint_type") @SerialName("complaint_type") var complaintType: String = "",
    val description: String = "",
    val priority: String = "Medium",
    val status: String = "Pending",
    val category: String = "Technical",
    @get:PropertyName("assigned_worker_id") @set:PropertyName("assigned_worker_id") @SerialName("assigned_worker_id") var assignedWorkerId: String? = null,
    val location: String = "",
    @get:PropertyName("created_at") @set:PropertyName("created_at") @SerialName("created_at") var createdAt: String? = null,
    @get:PropertyName("updated_at") @set:PropertyName("updated_at") @SerialName("updated_at") var updatedAt: String? = null,
    val remarks: List<String> = emptyList(),
    @get:PropertyName("admin_reply") @set:PropertyName("admin_reply") @SerialName("admin_reply") var adminReply: String = "",
    @get:PropertyName("emergency_request") @set:PropertyName("emergency_request") @SerialName("emergency_request") var emergencyRequest: Boolean = false
) {
    /**
     * Helper to compute estimated ETA minutes based on priority and status.
     * Newly added feature for PowerFix tracking without altering backend schema.
     */
    fun estimatedEtaMinutes(): Int {
        return when (status.lowercase()) {
            "resolved", "closed" -> 0
            "in progress" -> 15
            "assigned" -> when (priority.lowercase()) {
                "urgent" -> 20
                "high" -> 35
                "medium" -> 60
                else -> 120
            }
            else -> 180 // Pending triage
        }
    }
}
