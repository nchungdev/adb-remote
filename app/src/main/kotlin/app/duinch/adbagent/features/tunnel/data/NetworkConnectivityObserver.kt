package app.duinch.adbagent.features.tunnel.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import app.duinch.adbagent.core.model.NetworkType

class NetworkConnectivityObserver(context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    fun detectCurrentNetwork(): NetworkType {
        val cm = connectivityManager ?: return NetworkType.UNKNOWN
        val net = cm.activeNetwork ?: return NetworkType.NONE
        val caps = cm.getNetworkCapabilities(net) ?: return NetworkType.NONE
        return mapCapabilities(caps)
    }

    fun register(onNetworkChange: (NetworkType) -> Unit) {
        val cm = connectivityManager ?: return
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    onNetworkChange(detectCurrentNetwork())
                }

                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    onNetworkChange(mapCapabilities(caps))
                }

                override fun onLost(network: Network) {
                    onNetworkChange(detectCurrentNetwork())
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
