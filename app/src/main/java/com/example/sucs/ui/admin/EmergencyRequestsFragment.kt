package com.example.sucs.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.sucs.R
import com.example.sucs.databinding.FragmentEmergencyRequestsBinding
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

class EmergencyRequestsFragment : Fragment(R.layout.fragment_emergency_requests) {
    private var _binding: FragmentEmergencyRequestsBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private var registration: ListenerRegistration? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyRequestsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        registration = db.collection("emergency_requests")
            .addSnapshotListener { snapshot, _ ->
                if (snapshot == null) return@addSnapshotListener
                val items = snapshot.documents
                binding.requestsText.text = items.joinToString("\n\n") { doc ->
                    val message = doc.getString("message") ?: "No message"
                    val status = doc.getString("status") ?: "Open"
                    "$status\n$message"
                }
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        registration?.remove()
        _binding = null
    }
}
