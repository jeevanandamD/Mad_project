package com.example.sucs

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.NavHostFragment
import com.example.sucs.databinding.ActivityMainBinding
import com.example.sucs.ui.auth.LoginFragment
import com.example.sucs.ui.customer.CustomerDashboardFragment
import com.example.sucs.ui.admin.AdminDashboardFragment
import com.example.sucs.ui.worker.WorkerDashboardFragment

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
        val navController = navHost?.navController

        val user = AppContainer.supabase.auth.currentUserOrNull()
        if (user != null) {
            val role = getSharedPreferences("sucs_prefs", MODE_PRIVATE).getString("role", "customer")
            when (role) {
                "admin" -> navigateTo(AdminDashboardFragment())
                "worker" -> navigateTo(WorkerDashboardFragment())
                else -> navigateTo(CustomerDashboardFragment())
            }
        } else {
            navigateTo(LoginFragment())
        }
    }

    private fun navigateTo(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.nav_host_fragment, fragment)
            .commit()
    }
}
