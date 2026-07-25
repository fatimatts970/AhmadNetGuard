package com.ahmad.netguard.hostport

import com.ahmad.netguard.history.ConnectionEvent
import com.ahmad.netguard.model.Device

enum class HostportRisk { NONE, LOW, MEDIUM, HIGH }

data class HostportAssessment(
    val risk: HostportRisk,
    val reasons: List<String>
)

object HostportSuspicion {

    fun assess(device: Device, history: List<ConnectionEvent>): HostportAssessment {
        val reasons = mutableListOf<String>()
        var score = 0

        if (isLocallyAdministeredMac(device.macAddress)) {
            score += 1
            reasons.add("Randomized/locally-administered MAC address")
        }

        val longestSessionHours = longestContinuousSessionHours(history)
        when {
            longestSessionHours >= 12 -> {
                score += 2
                reasons.add("Continuously online for ${longestSessionHours}+ hours straight")
            }
            longestSessionHours >= 6 -> {
                score += 1
                reasons.add("Long continuous session (${longestSessionHours}h+)")
            }
        }

        val reconnectCount = history.count { it.eventType == "connected" }
        if (reconnectCount >= 15) {
            score += 1
            reasons.add("Reconnected $reconnectCount times in recorded history")
        }

        val risk = when {
            score >= 3 -> HostportRisk.HIGH
            score == 2 -> HostportRisk.MEDIUM
            score == 1 -> HostportRisk.LOW
            else -> HostportRisk.NONE
        }
        return HostportAssessment(risk, reasons)
    }

    private fun isLocallyAdministeredMac(mac: String): Boolean {
        val firstOctet = mac.split(":").firstOrNull() ?: return false
        val value = firstOctet.toIntOrNull(16) ?: return false
        return (value and 0x02) != 0
    }

    private fun longestContinuousSessionHours(history: List<ConnectionEvent>): Long {
        val chronological = history.sortedBy { it.timestampMillis }
        var longestMs = 0L
        var connectStart: Long? = null

        for (event in chronological) {
            if (event.eventType == "connected") {
                connectStart = event.timestampMillis
            } else if (event.eventType == "disconnected" && connectStart != null) {
                val duration = event.timestampMillis - connectStart
                if (duration > longestMs) longestMs = duration
                connectStart = null
            }
        }
        if (connectStart != null) {
            val duration = System.currentTimeMillis() - connectStart
            if (duration > longestMs) longestMs = duration
        }
        return longestMs / (1000 * 60 * 60)
    }
}
