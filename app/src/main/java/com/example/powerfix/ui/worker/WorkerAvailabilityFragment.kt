package com.example.powerfix.ui.worker

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.postgrest
import com.example.powerfix.AppContainer
import com.example.powerfix.R
import com.example.powerfix.data.PowerFixPrefs
import com.example.powerfix.data.UserProfile
import com.example.powerfix.databinding.FragmentWorkerAvailabilityBinding
import kotlinx.coroutines.launch

class WorkerAvailabilityFragment : Fragment(R.layout.fragment_worker_availability) {
    private var _binding: FragmentWorkerAvailabilityBinding? = null
    private val binding get() = _binding!!
    private val supabase = AppContainer.supabase

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

        loadCurrentAvailability()

        binding.activeButton.setOnClickListener { setAvailability(true) }
        binding.inactiveButton.setOnClickListener { setAvailability(false) }
    }

    private fun loadCurrentAvailability() {
        val uid = supabase.auth.currentUserOrNull()?.id ?: return
        val cachedAvailable = PowerFixPrefs.isWorkerAvailable(requireActivity())
        updateStatusDisplay(cachedAvailable)

        binding.availabilityProgress.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val profile = supabase.postgrest.from("profiles")
                    .select {
                        filter { eq("uid", uid) }
                    }
                    .decodeList<UserProfile>()
                    .firstOrNull()

                val isAvailable = profile?.available ?: cachedAvailable
                PowerFixPrefs.setWorkerAvailable(requireActivity(), isAvailable)
                updateStatusDisplay(isAvailable)
            } catch (_: Exception) {
                // Keep displaying cached state
            } finally {
                binding.availabilityProgress.visibility = View.GONE
            }
        }
    }

    private fun updateStatusDisplay(isAvailable: Boolean) {
        if (isAvailable) {
            binding.currentStatusText.text = "Active (Available for Tasks)"
            binding.currentStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_resolved))
            binding.statusCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.status_resolved)
        } else {
            binding.currentStatusText.text = "Inactive (Off-Duty)"
            binding.currentStatusText.setTextColor(ContextCompat.getColor(requireContext(), R.color.status_closed))
            binding.statusCard.strokeColor = ContextCompat.getColor(requireContext(), R.color.status_closed)
        }
    }

    private fun setAvailability(available: Boolean) {
        val uid = supabase.auth.currentUserOrNull()?.id
        if (uid == null) {
            Toast.makeText(requireContext(), "User not logged in", Toast.LENGTH_SHORT).show()
            return
        }

        binding.activeButton.isEnabled = false
        binding.inactiveButton.isEnabled = false
        binding.availabilityProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                supabase.postgrest.from("profiles").update(mapOf("available" to available)) {
                    filter { eq("uid", uid) }
                }

                PowerFixPrefs.setWorkerAvailable(requireActivity(), available)
                updateStatusDisplay(available)
                Toast.makeText(
                    requireContext(),
                    if (available) "Status updated: Active" else "Status updated: Inactive",
                    Toast.LENGTH_SHORT
                ).show()
            } catch (e: Exception) {
                Toast.makeText(requireContext(), e.localizedMessage ?: "Update failed", Toast.LENGTH_SHORT).show()
            } finally {
                binding.activeButton.isEnabled = true
                binding.inactiveButton.isEnabled = true
                binding.availabilityProgress.visibility = View.GONE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
