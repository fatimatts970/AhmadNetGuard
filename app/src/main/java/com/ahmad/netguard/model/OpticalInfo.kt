package com.ahmad.netguard.model

data class OpticalInfo(
    val txPowerDbm: String,
    val rxPowerDbm: String,
    val voltageMv: String,
    val temperatureC: String,
    val biasMa: String,
    val vendor: String,
    val serialNumber: String,
    val txWaveLengthNm: String,
    val rxWaveLengthNm: String
)
