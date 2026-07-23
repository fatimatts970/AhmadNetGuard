package com.ahmad.netguard.network

// Single shared adapter instance for the whole app process. LoginActivity logs
// in through this instance, so its session cookie stays alive when Dashboard
// and Devices screens use it too. Before this, every screen created its own
// HuaweiRouterAdapter(), so the login session was thrown away immediately
// after a successful login.
object RouterSession {
    val adapter: RouterAdapter by lazy {
        RouterAdapterFactory.create(RouterAdapterFactory.Brand.HUAWEI)
    }
}
