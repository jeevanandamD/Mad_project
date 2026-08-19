package com.example.powerfix.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class ComplaintRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun getComplaintsFlow(): Flow<List<Complaint>> = callbackFlow {
        val subscription = db.collection("complaints")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val complaints = snapshot?.documents?.mapNotNull { it.toObject(Complaint::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(complaints)
            }
        awaitClose { subscription.remove() }
    }

    fun getCustomerComplaintsFlow(customerId: String): Flow<List<Complaint>> = callbackFlow {
        val subscription = db.collection("complaints")
            .whereEqualTo("customer_id", customerId)
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val complaints = snapshot?.documents?.mapNotNull { it.toObject(Complaint::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(complaints)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun createComplaint(complaint: Complaint) {
        db.collection("complaints").add(complaint).await()
    }

    suspend fun updateComplaintStatus(id: String, status: String, adminReply: String? = null) {
        val updates = mutableMapOf<String, Any>("status" to status, "updated_at" to System.currentTimeMillis())
        adminReply?.let { updates["admin_reply"] = it }
        db.collection("complaints").document(id).update(updates).await()
    }

    suspend fun assignWorker(complaintId: String, workerId: String) {
        db.collection("complaints").document(complaintId).update(
            "assigned_worker_id", workerId,
            "status", "Assigned",
            "updated_at", System.currentTimeMillis()
        ).await()
    }
}
