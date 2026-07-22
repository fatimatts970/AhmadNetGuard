package com.ahmad.netguard.network

object RouterAdapterFactory {
    enum class Brand { HUAWEI }

    // Single shared instance per app process, so the login session (cookie)
    // from one screen is reused by every other screen instead of each
    // Activity silently re-logging-in with its own throwaway instance.
    private val huaweiInstance: RouterAdapter by lazy { HuaweiRouterAdapter() }

    fun create(brand: Brand): RouterAdapter = when (brand) {
        Brand.HUAWEI -> huaweiInstance
    }
}
