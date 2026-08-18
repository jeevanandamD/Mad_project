package com.example.powerfix.ui.admin

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.powerfix.R
import com.example.powerfix.databinding.FragmentAdminDashboardBinding
import com.example.powerfix.ui.common.AuthUtil
import kotlinx.coroutines.launch

class AdminDashboardFragment : Fragment(R.layout.fragment_admin_dashboard) {
    private var _binding: FragmentAdminDashboardBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAdminDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.viewComplaintsButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, ComplaintsListFragment())
                .addToBackStack(null)
                .commit()
        }
        binding.emergencyRequestsButton.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.nav_host_fragment, EmergencyRequestsFragment())
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
