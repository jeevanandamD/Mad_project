package com.example.powerfix.ui.auth

import android.graphics.Color
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
import com.example.powerfix.data.CustomerRegisterRequest
import com.example.powerfix.data.VerifyResult
import com.example.powerfix.data.WorkerRegisterRequest
import com.example.powerfix.data.dashboard
import com.example.powerfix.databinding.FragmentRegisterBinding
import kotlinx.coroutines.launch

class RegisterFragment : Fragment(R.layout.fragment_register) {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val authRepository get() = AppContainer.authRepository

    private var verifiedTnebId: String? = null
    private var verifyInProgress = false

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentRegisterBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Customers and workers can self-register. Admin roles are granted server-side.
        val roles = arrayOf("Customer", "Worker")
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
                verifiedTnebId = null
                binding.tnebVerifyStatus.visibility = View.GONE
            }

            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
        }

        binding.verifyButton.setOnClickListener { verifyTnebId() }

        binding.registerButton.setOnClickListener { attemptRegistration() }
    }

    private fun selectedRole(): AuthRole =
        AuthRole.fromDisplay(binding.roleSpinner.selectedItem?.toString()) ?: AuthRole.CUSTOMER

    private fun enteredTnebId(): String =
        binding.tnebIdInput.text?.toString()?.trim().orEmpty()

    private fun verifyTnebId() {
        if (verifyInProgress) return

        val role = selectedRole()
        val tnebId = enteredTnebId()

        if (tnebId.isEmpty()) {
            showVerifyStatus(AuthResult.Failure(
                AuthError.EMPTY_TNEB_ID,
                if (role == AuthRole.CUSTOMER) "Please enter your TNEB Customer ID." else "Please enter your TNEB Worker ID."
            ))
            return
        }

        verifyInProgress = true
        binding.verifyButton.isEnabled = false
        binding.tnebVerifyStatus.visibility = View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = authRepository.verifyTnebId(role, tnebId)
            verifyInProgress = false
            binding.verifyButton.isEnabled = true

            when (result) {
                is VerifyResult.Verified -> {
                    verifiedTnebId = tnebId
                    showVerifiedStatus()
                }
                is VerifyResult.Failed -> {
                    verifiedTnebId = null
                    showVerifyStatus(AuthResult.Failure(result.error, result.message))
                }
            }
        }
    }

    private fun showVerifiedStatus() {
        binding.tnebVerifyStatus.text = "TNEB ID verified"
        binding.tnebVerifyStatus.setTextColor(Color.parseColor("#2e7d32"))
        binding.tnebVerifyStatus.visibility = View.VISIBLE
    }

    private fun showVerifyStatus(result: AuthResult.Failure) {
        binding.tnebVerifyStatus.text = result.message
        binding.tnebVerifyStatus.setTextColor(Color.parseColor("#bf360c"))
        binding.tnebVerifyStatus.visibility = View.VISIBLE
        binding.tnebIdInput.error = result.message
    }

    private fun attemptRegistration() {
        val name = binding.nameInput.text?.toString()?.trim().orEmpty()
        val email = binding.emailInput.text?.toString()?.trim().orEmpty()
        val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()
        val address = binding.addressInput.text?.toString()?.trim().orEmpty()
        val password = binding.passwordInput.text?.toString().orEmpty()
        val role = selectedRole()
        val tnebId = enteredTnebId()

        if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || address.isEmpty()) {
            Toast.makeText(requireContext(), "Please fill out all fields", Toast.LENGTH_SHORT).show()
            return
        }

        if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            Toast.makeText(requireContext(), "Invalid email format", Toast.LENGTH_SHORT).show()
            binding.emailInput.error = "Invalid format"
            return
        }

        if (password.length < 8) {
            Toast.makeText(requireContext(), "Password must be at least 8 characters", Toast.LENGTH_SHORT).show()
            binding.passwordInput.error = "Minimum 8 characters"
            return
        }

        if (!password.contains(Regex(".*[A-Z].*"))) {
            Toast.makeText(requireContext(), "Password must contain at least one uppercase letter", Toast.LENGTH_SHORT).show()
            binding.passwordInput.error = "Must contain uppercase"
            return
        }

        if (!password.contains(Regex(".*[a-z].*"))) {
            Toast.makeText(requireContext(), "Password must contain at least one lowercase letter", Toast.LENGTH_SHORT).show()
            binding.passwordInput.error = "Must contain lowercase"
            return
        }

        if (!password.contains(Regex(".*[0-9].*"))) {
            Toast.makeText(requireContext(), "Password must contain at least one number", Toast.LENGTH_SHORT).show()
            binding.passwordInput.error = "Must contain number"
            return
        }

        if (!password.contains(Regex(".*[!@#$%^&*].*"))) {
            Toast.makeText(requireContext(), "Password must contain at least one special character (!@#$%^&*)", Toast.LENGTH_SHORT).show()
            binding.passwordInput.error = "Must contain special char"
            return
        }

        if (verifiedTnebId != tnebId) {
            val hint = if (role == AuthRole.WORKER) "TNEB Worker ID" else "TNEB Customer ID"
            Toast.makeText(requireContext(), "Please verify your $hint before registering.", Toast.LENGTH_LONG).show()
            binding.tnebIdInput.error = "Verification required"
            return
        }

        binding.registerButton.isEnabled = false
        binding.registerProgress.visibility = View.VISIBLE

        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (role == AuthRole.WORKER) {
                authRepository.registerWorker(
                    WorkerRegisterRequest(
                        name = name,
                        email = email,
                        phone = phone,
                        address = address,
                        password = password,
                        workerTnebId = tnebId
                    )
                )
            } else {
                authRepository.registerCustomer(
                    CustomerRegisterRequest(
                        name = name,
                        email = email,
                        phone = phone,
                        address = address,
                        password = password,
                        customerTnebId = tnebId
                    )
                )
            }

            when (result) {
                is AuthResult.Success -> {
                    Toast.makeText(requireContext(), "Registration successful", Toast.LENGTH_SHORT).show()
                    // Navigate directly to the dashboard and clear the auth backstack
                    navigateToDashboard(result.session.role)
                }
                is AuthResult.NeedsEmailConfirmation -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                    // Navigate to email confirmation screen - user must confirm before proceeding
                    navigateToEmailConfirmation()
                }
                is AuthResult.Failure -> {
                    Toast.makeText(requireContext(), result.message, Toast.LENGTH_LONG).show()
                }
            }

            binding.registerButton.isEnabled = true
            binding.registerProgress.visibility = View.GONE
        }
    }

    private fun updateTnebHint() {
        val selectedRole = binding.roleSpinner.selectedItem?.toString() ?: "Customer"
        binding.tnebIdInput.hint = if (selectedRole.equals("Worker", ignoreCase = true)) {
            "TNEB Worker ID"
        } else {
            "TNEB Customer ID"
        }
    }

    private fun navigateToDashboard(role: AuthRole) {
        parentFragmentManager.popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE)
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, role.dashboard())
            .commit()
    }

    private fun navigateToEmailConfirmation() {
        // Show message and return to login screen - user must check email
        Toast.makeText(requireContext(), "Registration complete! Check your email to confirm, then log in.", Toast.LENGTH_LONG).show()
        // Navigate back to login to allow email confirmation
        val loginFragment = LoginFragment()
        parentFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, loginFragment)
            .addToBackStack(null)
            .commit()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
