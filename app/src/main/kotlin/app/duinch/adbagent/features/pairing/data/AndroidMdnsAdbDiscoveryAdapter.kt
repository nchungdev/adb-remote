package app.duinch.adbagent.features.pairing.data

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import app.duinch.adbagent.features.pairing.domain.AdbDiscoveryGateway
import app.duinch.adbagent.features.pairing.domain.DiscoveredAdbPorts
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class AndroidMdnsAdbDiscoveryAdapter(
    context: Context
) : AdbDiscoveryGateway {

    private val nsdManager = context.applicationContext.getSystemService(Context.NSD_SERVICE) as? NsdManager
    private val portsFlow = MutableStateFlow(DiscoveredAdbPorts())
    private var pairingListener: NsdManager.DiscoveryListener? = null
    private var connectListener: NsdManager.DiscoveryListener? = null

    override fun observeDiscoveredPorts(): Flow<DiscoveredAdbPorts> = portsFlow.asStateFlow()

    override fun startDiscovery() {
        val manager = nsdManager ?: return
        stopDiscovery()

        pairingListener = createDiscoveryListener(
            manager = manager,
            serviceType = SERVICE_PAIRING,
            onPortResolved = { port -> portsFlow.update { it.copy(pairingPort = port) } }
        ).also { listener ->
            runCatching { manager.discoverServices(SERVICE_PAIRING, NsdManager.PROTOCOL_DNS_SD, listener) }
        }

        connectListener = createDiscoveryListener(
            manager = manager,
            serviceType = SERVICE_CONNECT,
            onPortResolved = { port -> portsFlow.update { it.copy(connectPort = port) } }
        ).also { listener ->
            runCatching { manager.discoverServices(SERVICE_CONNECT, NsdManager.PROTOCOL_DNS_SD, listener) }
        }
    }

    override fun stopDiscovery() {
        val manager = nsdManager ?: return
        pairingListener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        connectListener?.let { runCatching { manager.stopServiceDiscovery(it) } }
        pairingListener = null
        connectListener = null
    }

    private fun createDiscoveryListener(
        manager: NsdManager,
        serviceType: String,
        onPortResolved: (Int) -> Unit
    ): NsdManager.DiscoveryListener {
        return object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(regType: String) = Unit
            override fun onServiceLost(service: NsdServiceInfo) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit

            override fun onServiceFound(service: NsdServiceInfo) {
                if (!service.serviceType.contains(serviceType.trimEnd('.'))) return
                @Suppress("DEPRECATION")
                manager.resolveService(
                    service,
                    object : NsdManager.ResolveListener {
                        override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = Unit
                        override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                            if (serviceInfo.port in 1..65535) {
                                onPortResolved(serviceInfo.port)
                            }
                        }
                    }
                )
            }
        }
    }

    companion object {
        private const val SERVICE_PAIRING = "_adb-tls-pairing._tcp."
        private const val SERVICE_CONNECT = "_adb-tls-connect._tcp."
    }
}
