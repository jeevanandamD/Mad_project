package com.example.powerfix.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import com.example.powerfix.AppContainer
import com.example.powerfix.R
import com.example.powerfix.data.Complaint
import com.example.powerfix.data.UserProfile
import com.example.powerfix.databinding.FragmentRegisterComplaintBinding
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.UUID

class RegisterComplaintFragment : Fragment(R.layout.fragment_register_complaint) {
    private var _binding: FragmentRegisterComplaintBinding? = null
    private val binding get() = _binding!!
    private val supabase = AppContainer.supabase

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

        // Preload customer profile info if available
        val currentUid = supabase.auth.currentUserOrNull()?.id
        if (currentUid != null) {
            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    val profile = supabase.postgrest.from("profiles")
                        .select {
                            filter { eq("uid", currentUid) }
                        }
                        .decodeList<UserProfile>()
                        .firstOrNull()

                    if (profile != null) {
                        if (binding.nameInput.text.isNullOrBlank() && profile.name.isNotBlank()) {
                            binding.nameInput.setText(profile.name)
                        }
                        if (binding.mobileInput.text.isNullOrBlank() && profile.phone.isNotBlank()) {
                            binding.mobileInput.setText(profile.phone)
                        }
                        if (binding.addressInput.text.isNullOrBlank() && profile.address.isNotBlank()) {
                            binding.addressInput.setText(profile.address)
                        }
                    }
                } catch (_: Exception) {
                    // Non-critical, user can still type manually
                }
            }
        }

        binding.submitComplaintButton.setOnClickListener {
            val userId = supabase.auth.currentUserOrNull()?.id ?: return@setOnClickListener
            val now = Instant.now().toString()
            val complaint = Complaint(
                id = UUID.randomUUID().toString(),
                customerId = userId,
                customerName = binding.nameInput.text?.toString()?.trim().orEmpty(),
                mobile = binding.mobileInput.text?.toString()?.trim().orEmpty(),
                address = binding.addressInput.text?.toString()?.trim().orEmpty(),
                complaintType = binding.typeInput.text?.toString()?.trim().orEmpty(),
                description = binding.descriptionInput.text?.toString()?.trim().orEmpty(),
                priority = binding.priorityInput.selectedItem?.toString() ?: "Medium",
                status = "Pending",
                category = "Technical",
                createdAt = now,
                updatedAt = now,
                location = binding.addressInput.text?.toString()?.trim().orEmpty()
            )

            if (complaint.customerName.isEmpty() || complaint.mobile.isEmpty() || complaint.address.isEmpty() || complaint.complaintType.isEmpty() || complaint.description.isEmpty()) {
                Toast.makeText(requireContext(), "Please fill in all required complaint details", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                binding.submitComplaintButton.isEnabled = false
                binding.submitProgress.visibility = View.VISIBLE
                try {
                    supabase.postgrest.from("complaints").insert(complaint)
                    Toast.makeText(requireContext(), "PowerFix ticket submitted successfully", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.localizedMessage ?: "Submission failed", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.submitComplaintButton.isEnabled = true
                    binding.submitProgress.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
