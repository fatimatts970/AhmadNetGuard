package com.ahmad.netguard.ui

import android.content.Context
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
import com.ahmad.netguard.network.HuaweiRouterAdapter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Router ka login session kuch der baad expire ho jata hai (tab list khaali aati hai).
 * Yeh saved credentials se khud dobara login kar leta hai, taake user ko dobara login na karna pade.
 */
object SessionKeeper {

    private var appCtx: Context? = null
    private val mutex = Mutex()
    @Volatile private var lastTry = 0L

    /** true = auto re-login nahi ho saka (password saved nahi / galat). */
    @Volatile var expired = false

    fun init(context: Context) {
        appCtx = context.applicationContext
    }

    suspend fun relogin(force: Boolean = false): Boolean = mutex.withLock {
        val ctx = appCtx ?: return@withLock false
        val now = System.currentTimeMillis()
        if (!force && now - lastTry < 12_000L) return@withLock !expired
        lastTry = now
        val store = RouterCredentialStore(ctx)
        val ip = store.getGateway()
        val user = store.getUsername().ifBlank { "admin" }
        val pass = store.getPassword()
        if (ip.isBlank() || pass.isBlank()) {
            expired = true
            return@withLock false
        }
        val ad = RouterAdapterFactory.getAdapter()
        val hw = ad as? HuaweiRouterAdapter
        val saved = hw?.exportSession()
        val ok = try {
            ad.login(ip, user, pass)
        } catch (e: Exception) {
            false
        }
        // Agar login fail hua to purani (shayad zinda) session wapas rakho, taake wo bhi na toote.
        if (!ok && saved != null) hw.importSession(saved)
        expired = !ok
        ok
    }

    private val bgScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Router par likhne wale kaam (guest/wifi) ek waqt mein ek hi chalein, beech mein relogin na aaye. */
    private val opMutex = Mutex()
    @Volatile private var refreshJob: Job? = null

    suspend fun <T> withOp(block: suspend () -> T): T = opMutex.withLock { block() }

    fun cancelPendingRefresh() {
        refreshJob?.cancel()
        refreshJob = null
    }

    /** Kai baar koshish karke naya session leta hai (router ka WiFi restart hone mein der lagti hai). */
    suspend fun ensureSession(attempts: Int = 3): Boolean {
        for (i in 0 until attempts) {
            if (relogin(force = true)) return true
            if (i < attempts - 1) delay(2_500)
        }
        return false
    }

    /**
     * WiFi/router settings badalne ke baad router ka WiFi radio ~5 second restart hota hai aur
     * login session aksar toot jata hai. Thodi der baad khud naya session le lo.
     */
    fun refreshAfterWrite() {
        refreshJob?.cancel()
        refreshJob = bgScope.launch {
            delay(6_000)
            opMutex.withLock { ensureSession(4) }
        }
    }

    /** Device list; khaali aaye to (jo is network par kabhi nahi hona chahiye) session refresh karke dobara. */
    suspend fun devices(): List<Device> {
        val ad = RouterAdapterFactory.getAdapter()
        var list = try { ad.getDevices() } catch (e: Exception) { emptyList<Device>() }
        if (list.isEmpty()) {
            if (relogin()) {
                list = try { ad.getDevices() } catch (e: Exception) { emptyList<Device>() }
            }
        } else {
            expired = false
        }
        return list
    }
}
