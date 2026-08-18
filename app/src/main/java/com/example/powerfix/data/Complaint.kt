package com.example.powerfix.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Complaint(
    val id: String = "",
    @SerialName("customer_id") val customerId: String = "",
    @SerialName("customer_name") val customerName: String = "",
    val mobile: String = "",
    val address: String = "",
    @SerialName("complaint_type") val complaintType: String = "",
    val description: String = "",
    val priority: String = "Medium",
    val status: String = "Pending",
    val category: String = "Technical",
    @SerialName("assigned_worker_id") val assignedWorkerId: String? = null,
    val location: String = "",
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    val remarks: List<String> = emptyList(),
    @SerialName("admin_reply") val adminReply: String = "",
    @SerialName("emergency_request") val emergencyRequest: Boolean = false
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
