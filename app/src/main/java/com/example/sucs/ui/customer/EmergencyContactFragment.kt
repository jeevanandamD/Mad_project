package com.example.sucs.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sucs.R
import com.example.sucs.databinding.FragmentEmergencyContactBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class EmergencyContactFragment : Fragment(R.layout.fragment_emergency_contact) {
    private var _binding: FragmentEmergencyContactBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentEmergencyContactBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.sendRequestButton.setOnClickListener {
            val message = binding.messageInput.text?.toString()?.trim().orEmpty()
            if (message.isEmpty()) {
                Toast.makeText(requireContext(), "Please write a message first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val request = hashMapOf(
                "userId" to (auth.currentUser?.uid ?: ""),
                "message" to message,
                "status" to "Open",
                "createdAt" to Timestamp.now()
            )

            db.collection("emergency_requests").add(request)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Emergency request sent", Toast.LENGTH_SHORT).show()
                    binding.messageInput.setText("")
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), e.localizedMessage ?: "Request failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
