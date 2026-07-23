package com.ahmad.netguard.network

object RouterAdapterFactory {

    enum class Brand { HUAWEI }

    fun create(brand: Brand = Brand.HUAWEI, routerIp: String = "192.168.100.1"): RouterAdapter {
        return when (brand) {
            Brand.HUAWEI -> HuaweiRouterAdapter(routerIp)
        }
    }
}
