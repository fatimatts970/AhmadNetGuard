package com.ahmad.netguard.network

object RouterAdapterFactory {
    val instance: HuaweiRouterAdapter by lazy {
        HuaweiRouterAdapter()
    }

    fun create(): HuaweiRouterAdapter {
        return HuaweiRouterAdapter()
    }
}
