package com.ahmad.netguard.network

object RouterAdapterFactory {

    enum class Brand { HUAWEI }

    @Volatile
    private var instance: RouterAdapter? = null

    // Sab activities/adapters isi ek shared instance ko use karte hain
    // taake login ke baad session (cookie/token) sab jagah zinda rahe.
    fun getAdapter(): RouterAdapter {
        return instance ?: synchronized(this) {
            instance ?: create().also { instance = it }
        }
    }

    fun create(brand: Brand = Brand.HUAWEI): RouterAdapter {
        return when (brand) {
            Brand.HUAWEI -> HuaweiRouterAdapter()
        }
    }
}
