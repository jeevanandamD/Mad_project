package com.example.powerfix.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.powerfix.AppContainer
import com.example.powerfix.R
import com.example.powerfix.data.EmergencyRequest
import com.example.powerfix.databinding.FragmentEmergencyContactBinding
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch
import java.time.Instant

class EmergencyContactFragment : Fragment(R.layout.fragment_emergency_contact) {
    private var _binding: FragmentEmergencyContactBinding? = null
    private val binding get() = _binding!!
    private val emergencyRepository get() = AppContainer.emergencyRepository

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

            val user = FirebaseAuth.getInstance().currentUser
            val request = EmergencyRequest(
                userId = user?.uid ?: "anonymous",
                message = message,
                status = "Open",
                createdAt = Instant.now().toString()
            )

            viewLifecycleOwner.lifecycleScope.launch {
                binding.sendRequestButton.isEnabled = false
                binding.sendProgress.visibility = View.VISIBLE
                try {
                    emergencyRepository.createEmergencyRequest(request)
                    Toast.makeText(requireContext(), "Emergency SOS request sent", Toast.LENGTH_SHORT).show()
                    binding.messageInput.setText("")
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.localizedMessage ?: "Request failed", Toast.LENGTH_SHORT).show()
                } finally {
                    binding.sendRequestButton.isEnabled = true
                    binding.sendProgress.visibility = View.GONE
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
