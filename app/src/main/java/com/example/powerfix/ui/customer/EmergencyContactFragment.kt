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
import com.example.powerfix.databinding.FragmentEmergencyContactBinding
import kotlinx.coroutines.launch
import java.time.Instant

class EmergencyContactFragment : Fragment(R.layout.fragment_emergency_contact) {
    private var _binding: FragmentEmergencyContactBinding? = null
    private val binding get() = _binding!!
    private val supabase = AppContainer.supabase

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

            val uid = supabase.auth.currentUserOrNull()?.id ?: ""
            val request = mapOf(
                "user_id" to uid,
                "message" to message,
                "status" to "Open",
                "created_at" to Instant.now().toString()
            )

            viewLifecycleOwner.lifecycleScope.launch {
                binding.sendRequestButton.isEnabled = false
                binding.sendProgress.visibility = View.VISIBLE
                try {
                    supabase.postgrest.from("emergency_requests").insert(request)
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
