package dev.ayaan.tvremote.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.net.Inet4Address

data class Tv(val name: String, val host: String, val port: Int = 6466)

/**
 * Finds Android TV / Google TV devices advertising the remote service over mDNS.
 *
 * A multicast lock is required on many phones -- without it the Wi-Fi chip
 * filters the multicast packets and discovery silently returns nothing.
 */
class TvDiscovery(private val context: Context) {

    fun devices(): Flow<List<Tv>> = callbackFlow {
        val nsd = context.getSystemService(Context.NSD_SERVICE) as NsdManager
        val wifi = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("tvremote-discovery").apply {
            setReferenceCounted(true)
            acquire()
        }

        val found = linkedMapOf<String, Tv>()

        fun publish(tv: Tv) {
            found[tv.host] = tv
            trySend(found.values.toList())
        }

        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(type: String) = Unit
            override fun onDiscoveryStopped(type: String) = Unit
            override fun onStartDiscoveryFailed(type: String, code: Int) {
                close(IllegalStateException("Discovery failed to start ($code)"))
            }
            override fun onStopDiscoveryFailed(type: String, code: Int) = Unit
            override fun onServiceLost(info: NsdServiceInfo) {
                found.entries.removeAll { it.value.name == info.serviceName }
                trySend(found.values.toList())
            }

            override fun onServiceFound(info: NsdServiceInfo) {
                resolve(nsd, info) { publish(it) }
            }
        }

        nsd.discoverServices(SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, listener)

        awaitClose {
            runCatching { nsd.stopServiceDiscovery(listener) }
            runCatching { lock.release() }
        }
    }

    @Suppress("DEPRECATION")
    private fun resolve(nsd: NsdManager, info: NsdServiceInfo, onResolved: (Tv) -> Unit) {
        val callback = object : NsdManager.ResolveListener {
            override fun onResolveFailed(info: NsdServiceInfo, code: Int) = Unit
            override fun onServiceResolved(resolved: NsdServiceInfo) {
                val address = hostOf(resolved) ?: return
                onResolved(Tv(resolved.serviceName, address, resolved.port))
            }
        }
        // registerServiceInfoCallback exists on T+, but resolveService still works
        // everywhere and keeps one code path.
        nsd.resolveService(info, callback)
    }

    private fun hostOf(info: NsdServiceInfo): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            info.hostAddresses.firstOrNull { it is Inet4Address }?.let { return it.hostAddress }
        }
        @Suppress("DEPRECATION")
        return info.host?.hostAddress
    }

    companion object {
        const val SERVICE_TYPE = "_androidtvremote2._tcp."
        const val PAIRING_PORT = 6467
        const val REMOTE_PORT = 6466
    }
}
