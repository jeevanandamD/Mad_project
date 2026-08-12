package com.example.sucs.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.sucs.AppContainer
import com.example.sucs.R
import com.example.sucs.data.UserProfile
import com.example.sucs.databinding.FragmentRegisterBinding
import io.github.jan.supabase.gotrue.auth
import io.github.jan.supabase.gotrue.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

class RegisterFragment : Fragment(R.layout.fragment_register) {
    private var _binding: FragmentRegisterBinding? = null
    private val binding get() = _binding!!
    private val supabase = AppContainer.supabase

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

        val roles = arrayOf("Customer", "Worker", "Admin")
        val spinnerAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, roles)
        binding.roleSpinner.adapter = spinnerAdapter

        binding.registerButton.setOnClickListener {
            val name = binding.nameInput.text?.toString()?.trim().orEmpty()
            val email = binding.emailInput.text?.toString()?.trim().orEmpty()
            val phone = binding.phoneInput.text?.toString()?.trim().orEmpty()
            val address = binding.addressInput.text?.toString()?.trim().orEmpty()
            val password = binding.passwordInput.text?.toString().orEmpty()
            val role = binding.roleSpinner.selectedItem.toString().lowercase()

            if (name.isEmpty() || email.isEmpty() || phone.isEmpty() || address.isEmpty() || password.length < 6) {
                Toast.makeText(requireContext(), "Fill out the required fields correctly", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            viewLifecycleOwner.lifecycleScope.launch {
                try {
                    supabase.auth.signUpWith(Email) {
                        this.email = email
                        this.password = password
                    }

                    val uid = supabase.auth.currentUserOrNull()?.id ?: run {
                        Toast.makeText(requireContext(), "Registration failed", Toast.LENGTH_SHORT).show()
                        return@launch
                    }

                    val profile = UserProfile(
                        uid = uid,
                        email = email,
                        name = name,
                        role = role,
                        phone = phone,
                        address = address
                    )

                    supabase.postgrest.from("profiles").insert(profile)
                    Toast.makeText(requireContext(), "Registration successful", Toast.LENGTH_SHORT).show()
                    parentFragmentManager.popBackStack()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), e.localizedMessage ?: "Registration failed", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
