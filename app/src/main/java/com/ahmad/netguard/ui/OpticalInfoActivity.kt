package com.ahmad.netguard.ui

import android.os.Bundle
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.ahmad.netguard.R
import com.ahmad.netguard.network.RouterAdapterFactory
import kotlinx.coroutines.launch

class OpticalInfoActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_optical_info)

        findViewById<ImageButton>(R.id.btnBackOptical).setOnClickListener { finish() }

        lifecycleScope.launch {
            val router = RouterAdapterFactory.getAdapter()
            val info = router.getOpticalInfo()
            val statusText = findViewById<TextView>(R.id.text_optic_status)

            if (info == null) {
                statusText.text = "Could not read optical diagnostics from the router."
                return@launch
            }

            statusText.text = "Live GPON optical transceiver readings"
            findViewById<TextView>(R.id.text_tx_power).text = "${info.txPowerDbm} dBm"
            findViewById<TextView>(R.id.text_rx_power).text = "${info.rxPowerDbm} dBm"
            findViewById<TextView>(R.id.text_temperature).text = "${info.temperatureC} °C"
            findViewById<TextView>(R.id.text_voltage).text = "${info.voltageMv} mV"
            findViewById<TextView>(R.id.text_bias).text = "${info.biasMa} mA"
            findViewById<TextView>(R.id.text_vendor).text = info.vendor
            findViewById<TextView>(R.id.text_serial).text = info.serialNumber
            findViewById<TextView>(R.id.text_wavelength).text = "${info.txWaveLengthNm}nm / ${info.rxWaveLengthNm}nm"
        }
    }
}
