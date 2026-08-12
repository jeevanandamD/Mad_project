package com.example.sucs.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.sucs.R
import com.example.sucs.data.Complaint
import com.example.sucs.databinding.FragmentRegisterComplaintBinding
import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class RegisterComplaintFragment : Fragment(R.layout.fragment_register_complaint) {
    private var _binding: FragmentRegisterComplaintBinding? = null
    private val binding get() = _binding!!
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterComplaintBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.submitComplaintButton.setOnClickListener {
            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            val complaint = Complaint(
                id = db.collection("complaints").document().id,
                customerId = userId,
                customerName = binding.nameInput.text?.toString()?.trim().orEmpty(),
                mobile = binding.mobileInput.text?.toString()?.trim().orEmpty(),
                address = binding.addressInput.text?.toString()?.trim().orEmpty(),
                complaintType = binding.typeInput.text?.toString()?.trim().orEmpty(),
                description = binding.descriptionInput.text?.toString()?.trim().orEmpty(),
                priority = binding.priorityInput.selectedItem?.toString() ?: "Medium",
                status = "Pending",
                category = "Technical",
                createdAt = Timestamp.now(),
                updatedAt = Timestamp.now(),
                location = binding.addressInput.text?.toString()?.trim().orEmpty()
            )

            if (complaint.customerName.isEmpty() || complaint.mobile.isEmpty() || complaint.address.isEmpty() || complaint.complaintType.isEmpty() || complaint.description.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all required complaint details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            db.collection("complaints").document(complaint.id).set(complaint)
                .addOnSuccessListener {
                    Toast.makeText(requireContext(), "Complaint submitted successfully", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                }
                .addOnFailureListener { e ->
                    Toast.makeText(requireContext(), e.localizedMessage ?: "Submission failed", Toast.LENGTH_SHORT).show()
                }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
