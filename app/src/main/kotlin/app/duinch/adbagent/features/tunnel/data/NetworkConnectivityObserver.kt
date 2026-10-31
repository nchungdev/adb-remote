package app.duinch.adbagent.features.tunnel.data

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import app.duinch.adbagent.core.model.NetworkType
import java.net.Inet4Address
import java.net.NetworkInterface

class NetworkConnectivityObserver(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun detectCurrentNetwork(): NetworkType {
        val cm = connectivityManager ?: return NetworkType.UNKNOWN
        val net = cm.activeNetwork ?: return NetworkType.NONE
        val caps = cm.getNetworkCapabilities(net) ?: return NetworkType.NONE
        return mapCapabilities(caps)
    }

    fun findWifiIpAddress(): String? {
        runCatching {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return null
            for (iface in interfaces) {
                if (iface.isLoopback || !iface.isUp) continue
                val addresses = iface.inetAddresses
                for (addr in addresses) {
                    if (addr is Inet4Address && !addr.isLoopbackAddress) {
                        val host = addr.hostAddress ?: continue
                        if (host.startsWith("192.168.") || host.startsWith("10.") || host.startsWith("172.")) {
                            return host
                        }
                    }
                }
            }
        }
        return null
    }

    fun detectCurrentWifiSsid(): String? {
        val cm = connectivityManager ?: return null
        val net = cm.activeNetwork ?: return null
        val caps = cm.getNetworkCapabilities(net) ?: return null
        if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) return null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val transportInfo = caps.transportInfo
            if (transportInfo is android.net.wifi.WifiInfo) {
                val raw = transportInfo.ssid
                if (!raw.isNullOrBlank() && raw != "<unknown ssid>") {
                    return raw.removePrefix("\"").removeSuffix("\"")
                }
            }
        }

        val wm = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
        val info = runCatching { wm?.connectionInfo }.getOrNull()
        val raw = info?.ssid
        if (!raw.isNullOrBlank() && raw != "<unknown ssid>") {
            return raw.removePrefix("\"").removeSuffix("\"")
        }
        return null
    }

    fun isUsbConnected(): Boolean {
        return runCatching {
            val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
            val intent = context.applicationContext.registerReceiver(null, filter)
            val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) ?: -1
            plugged == BatteryManager.BATTERY_PLUGGED_USB
        }.getOrDefault(false)
    }

    @Volatile private var lastKnownType: NetworkType = NetworkType.UNKNOWN

    fun register(onNetworkChange: (NetworkType) -> Unit) {
        val cm = connectivityManager ?: return
        lastKnownType = detectCurrentNetwork()
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val current = detectCurrentNetwork()
                    if (current != lastKnownType) {
                        lastKnownType = current
                        onNetworkChange(current)
                    }
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    val current = mapCapabilities(caps)
                    if (current != lastKnownType) {
                        lastKnownType = current
                        onNetworkChange(current)
                    }
                }

                override fun onLost(network: Network) {
                    val current = detectCurrentNetwork()
                    if (current != lastKnownType) {
                        lastKnownType = current
                        onNetworkChange(current)
                    }
                }
            })
        }
    }

    private fun mapCapabilities(caps: NetworkCapabilities): NetworkType = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkType.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkType.CELLULAR
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkType.ETHERNET
        else -> NetworkType.UNKNOWN
    }
}
