package com.ahmad.netguard.ui

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

/** Purani History screen ab Device Details screen mein shamil hai; yeh sirf redirect hai. */
class HistoryActivity : AppCompatActivity() {

    companion object {
        fun start(context: Context, mac: String, displayName: String) {
            DeviceDetailActivity.open(context, mac, displayName, "")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        finish()
    }
}
