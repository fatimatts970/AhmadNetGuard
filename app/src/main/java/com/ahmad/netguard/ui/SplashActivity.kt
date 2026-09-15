package com.ahmad.netguard.ui

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.R
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_splash)

        lifecycleScope.launch {
            delay(800)
            routeToNextScreen()
        }
    }

    private suspend fun routeToNextScreen() {
        val credStore = RouterCredentialStore(this)

        if (credStore.isRememberMeEnabled() && credStore.getPassword().isNotBlank()) {
            val adapter = RouterAdapterFactory.getAdapter()
            val success = adapter.login(credStore.getGateway(), credStore.getUsername(), credStore.getPassword())

            if (success) {
                // Dashboard khulne se pehle hi real WiFi name aur router model
                // fetch kar lete hain, taake Dashboard khulte hi "Loading..." na dikhe
                val ssid = adapter.getWifiSsidName()
                val model = adapter.getRouterModel()

                val intent = Intent(this, DashboardActivity::class.java)
                if (!ssid.isNullOrBlank()) intent.putExtra(DashboardActivity.EXTRA_WIFI_NAME, ssid)
                if (!model.isNullOrBlank()) intent.putExtra(DashboardActivity.EXTRA_ROUTER_MODEL, model)
                startActivity(intent)
                finish()
                return
            }
            // Auto-login fail ho gaya (router offline ya password badal gaya) —
            // safe fallback: normal Login screen dikhao
        }

        startActivity(Intent(this, LoginActivity::class.java))
        finish()
    }
}
