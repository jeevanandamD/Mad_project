package com.example.powerfix.ui.common

import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.example.powerfix.AppContainer
import com.example.powerfix.R
import com.example.powerfix.ui.auth.LoginFragment

object AuthUtil {

    suspend fun signOutAndNavigateToLogin(activity: FragmentActivity) {
        AppContainer.authRepository.logout()
        val fm = activity.supportFragmentManager
        fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        fm.beginTransaction()
            .replace(R.id.nav_host_fragment, LoginFragment())
            .commit()
    }
}
