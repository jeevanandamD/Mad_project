package com.example.powerfix.ui.customer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.powerfix.R
import com.example.powerfix.databinding.FragmentCustomerDashboardBinding
import com.example.powerfix.ui.common.AuthUtil
import kotlinx.coroutines.launch

class CustomerDashboardFragment : Fragment(R.layout.fragment_customer_dashboard) {
    private var _binding: FragmentCustomerDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCustomerDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.registerComplaintCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, RegisterComplaintFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.trackComplaintCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, ComplaintTrackingFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.emergencyCard.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, EmergencyContactFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.logoutButton.setOnClickListener {
            binding.logoutButton.isEnabled = false
            lifecycleScope.launch { AuthUtil.signOutAndNavigateToLogin(requireActivity()) }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
