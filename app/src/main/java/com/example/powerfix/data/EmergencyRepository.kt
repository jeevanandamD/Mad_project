package com.example.powerfix.data

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class EmergencyRepository(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    fun getEmergencyRequestsFlow(): Flow<List<EmergencyRequest>> = callbackFlow {
        val subscription = db.collection("emergency_requests")
            .orderBy("created_at", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    close(error)
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.mapNotNull { it.toObject(EmergencyRequest::class.java)?.copy(id = it.id) } ?: emptyList()
                trySend(requests)
            }
        awaitClose { subscription.remove() }
    }

    suspend fun createEmergencyRequest(request: EmergencyRequest) {
        db.collection("emergency_requests").add(request).await()
    }

    suspend fun updateEmergencyStatus(id: String, status: String) {
        db.collection("emergency_requests").document(id).update("status", status).await()
    }
}
