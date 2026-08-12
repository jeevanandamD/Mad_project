package com.example.sucs.data

import kotlinx.serialization.Serializable

@Serializable
data class Complaint(
    val id: String = "",
    val customerId: String = "",
    val customerName: String = "",
    val mobile: String = "",
    val address: String = "",
    val complaintType: String = "",
    val description: String = "",
    val priority: String = "Medium",
    val status: String = "Pending",
    val category: String = "Technical",
    val assignedWorkerId: String? = null,
    val location: String = "",
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val remarks: List<String> = emptyList(),
    val adminReply: String = "",
    val emergencyRequest: Boolean = false
)
