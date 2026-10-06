package com.ahmad.netguard.ui

import android.content.Context
import com.ahmad.netguard.model.Device
import com.ahmad.netguard.network.RouterAdapterFactory
import com.ahmad.netguard.network.RouterCredentialStore
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
        val ok = try {
            RouterAdapterFactory.getAdapter().login(ip, user, pass)
        } catch (e: Exception) {
            false
        }
        expired = !ok
        ok
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
