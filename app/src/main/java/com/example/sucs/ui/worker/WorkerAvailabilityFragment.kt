package com.example.sucs.ui.worker

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sucs.R
import com.example.sucs.databinding.FragmentWorkerAvailabilityBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class WorkerAvailabilityFragment : Fragment(R.layout.fragment_worker_availability) {
    private var _binding: FragmentWorkerAvailabilityBinding? = null
    private val binding get() = _binding!!
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentWorkerAvailabilityBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.activeButton.setOnClickListener { setAvailability(true) }
        binding.inactiveButton.setOnClickListener { setAvailability(false) }
    }

    private fun setAvailability(available: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val prefs = requireActivity().getSharedPreferences("sucs_prefs", Context.MODE_PRIVATE)
        prefs.edit().putBoolean("worker_available", available).apply()

        db.collection("users").document(uid).update("available", available)
            .addOnSuccessListener {
                Toast.makeText(requireContext(), if (available) "Worker marked active" else "Worker marked inactive", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { e ->
                Toast.makeText(requireContext(), e.localizedMessage ?: "Update failed", Toast.LENGTH_SHORT).show()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
