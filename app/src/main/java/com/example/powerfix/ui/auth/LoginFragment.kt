package com.example.powerfix.ui.auth

import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.powerfix.AppContainer
import com.example.powerfix.R
import com.example.powerfix.data.AuthError
import com.example.powerfix.data.AuthResult
import com.example.powerfix.data.AuthRole
import com.example.powerfix.data.TnebIdValidator
import com.example.powerfix.data.dashboard
import com.example.powerfix.databinding.FragmentLoginBinding
import com.example.powerfix.ui.admin.AdminDashboardFragment
import com.example.powerfix.ui.customer.CustomerDashboardFragment
import com.example.powerfix.ui.worker.WorkerDashboardFragment
import kotlinx.coroutines.launch

class LoginFragment : Fragment(R.layout.fragment_login) {
    private var _binding: FragmentLoginBinding? = null
    private val binding get() = _binding!!
    private val authRepository get() = AppContainer.authRepository

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

        val roles = arrayOf("Customer", "Worker", "Admin")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles)
        binding.roleSpinner.adapter = spinnerAdapter

        binding.roleSpinner.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(
                parent: android.widget.AdapterView<*>?,
                view: View?,
                position: Int,
                id: Long
            ) {
                updateTnebHint()
                // Clear the TNEB ID field whenever the user changes roles to prevent
                // associating a Customer ID with a Worker account or vice versa.
                binding.tnebIdInput.text?.clear()
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        binding.loginButton.setOnClickListener { attemptLogin() }

        binding.registerText.setOnClickListener {
            replaceFragment(RegisterFragment())
        }
    }

    private fun attemptLogin() {
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val role = AuthRole.fromDisplay(binding.roleSpinner.selectedItem?.toString()) ?: AuthRole.CUSTOMER
        val tnebId = binding.tnebIdInput.text?.toString()?.trim().orEmpty()

        if (email.isEmpty() || password.isEmpty()) {
            Toast.makeText(requireContext(), "Email and password are required", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), "Please enter a valid email address", Toast.LENGTH_SHORT).show()
            binding.emailInput.error = "Invalid format"
            return
        }

        binding.loginButton.isEnabled = false
        binding.loginProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            // Validate TNEB ID format before attempting login
            val trimmedTneb = tnebId.trim()
            if (trimmedTneb.isNotEmpty() && !TnebIdValidator.isValidForRole(role.dbValue, trimmedTneb)) {
                Toast.makeText(requireContext(), "Invalid TNEB ID format for selected role", Toast.LENGTH_LONG).show()
                binding.loginButton.isEnabled = true
                binding.loginProgress.visibility = View.GONE
                return@launch
            }

            val result = authRepository.login(role, email, password, tnebId)

            when (result) {
                is AuthResult.Success -> {
                    // Navigate to the appropriate dashboard and clear the auth backstack
                    // so the back button doesn't return to the login screen.
                    replaceFragment(result.session.role.dashboard(), addToBackStack = false)
                }
                is AuthResult.Failure -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
                is AuthResult.NeedsEmailConfirmation -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }

            binding.loginButton.isEnabled = true
            binding.loginProgress.visibility = View.GONE
        }
    }

    private fun updateTnebHint() {
        val selectedRole = binding.roleSpinner.selectedItem?.toString() ?: "Customer"
        binding.tnebIdInputLayout.hint = if (selectedRole.equals("Worker", ignoreCase = true)) {
            "TNEB Worker ID"
        } else {
            "TNEB Customer ID"
        }
    }

    private fun replaceFragment(fragment: Fragment, addToBackStack: Boolean = true) {
        val transaction = parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)

        if (addToBackStack) {
            transaction.addToBackStack(null)
        } else {
            // Clearing the backstack for role-based security and one-way navigation
            parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
        transaction.commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
