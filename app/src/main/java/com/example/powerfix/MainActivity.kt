package com.example.powerfix

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.powerfix.data.AuthResult
import com.example.powerfix.data.AuthRole
import com.example.powerfix.data.dashboard
import com.example.powerfix.databinding.ActivityMainBinding
import com.example.powerfix.ui.auth.LoginFragment
import com.example.powerfix.ui.customer.CustomerDashboardFragment
import com.example.powerfix.ui.admin.AdminDashboardFragment
import com.example.powerfix.ui.worker.WorkerDashboardFragment
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // On configuration changes (rotation) the fragment manager restores the UI itself.
        if (savedInstanceState != null) return

        lifecycleScope.launch {
            when (val result = AppContainer.authRepository.restoreSession()) {
                is AuthResult.Success -> navigateTo(result.session.role.dashboard())
                else -> navigateTo(LoginFragment())
            }
        }
    }

    private fun navigateTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
