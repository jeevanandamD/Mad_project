package com.example.sucs.ui.auth

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sucs.AppContainer
import com.example.sucs.R
import com.example.sucs.data.UserProfile
import com.example.sucs.databinding.FragmentLoginBinding
import com.example.sucs.ui.admin.AdminDashboardFragment
import com.example.sucs.ui.customer.CustomerDashboardFragment
import com.example.sucs.ui.worker.WorkerDashboardFragment
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val supabase = AppContainer.supabase

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginButton.setOnClickListener {
            val email = binding.emailInput.text?.toString()?.trim().orEmpty()
            val password = binding.passwordInput.text?.toString().orEmpty()

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(requireContext(), "Email and password are required", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    supabase.auth.signInWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    val user = supabase.auth.currentUserOrNull() ?: run {
                        Toast.makeText(requireContext(), "Login failed", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val profile = supabase.postgrest.from("profiles")
                        .select(columns = Columns.list("*")) {
                            filter {
                                eq("uid", user.id)
                            }
                        }
                        .decodeList<UserProfile>()
                        .firstOrNull()

                    val role = profile?.role ?: "customer"
                    requireActivity().getSharedPreferences("sucs_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putString("role", role)
                        .apply()

                    when (role.lowercase()) {
                        "admin" -> replaceFragment(AdminDashboardFragment())
                        "worker" -> replaceFragment(WorkerDashboardFragment())
                        else -> replaceFragment(CustomerDashboardFragment())
                    }
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.localizedMessage ?: "Login failed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        binding.registerText.setOnClickListener {
            replaceFragment(RegisterFragment())
        }
    }

    private fun replaceFragment(fragment: Fragment) {
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
