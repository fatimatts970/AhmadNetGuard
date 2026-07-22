package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.ahmad.netguard.R
import com.ahmad.netguard.network.RouterCredentialStore

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        Handler(Looper.getMainLooper()).postDelayed({
            routeToNextScreen()
        }, 1800)
    }

    private fun routeToNextScreen() {
        val credentialStore = RouterCredentialStore(this)

        if (!credentialStore.hasSavedCredentials()) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        if (BiometricHelper.canUseBiometrics(this)) {
            BiometricHelper.prompt(
                activity = this,
                onSuccess = { goToDashboard() },
                onFailure = {
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                }
            )
        } else {
            goToDashboard()
        }
    }

    private fun goToDashboard() {
        startActivity(Intent(this, DashboardActivity::class.java))
        finish()
    }
}
