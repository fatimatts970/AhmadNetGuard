package com.ahmad.netguard.network

object RouterAdapterFactory {
    enum class Brand { HUAWEI }

    fun create(brand: Brand): RouterAdapter = when (brand) {
        Brand.HUAWEI -> HuaweiRouterAdapter()
    }
}
