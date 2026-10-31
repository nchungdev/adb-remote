package app.duinch.adbagent.core.di

import android.content.Context
import app.duinch.adbagent.features.pairing.application.ActivateTcpipModeUseCase
import app.duinch.adbagent.features.pairing.application.PairLocalAdbUseCase
import app.duinch.adbagent.features.pairing.data.AndroidMdnsAdbDiscoveryAdapter
import app.duinch.adbagent.features.pairing.data.AndroidPairingNotificationAdapter
import app.duinch.adbagent.features.pairing.data.TermuxLocalAdbPairingAdapter
import app.duinch.adbagent.features.pairing.domain.AdbDiscoveryGateway
import app.duinch.adbagent.features.pairing.domain.AdbPairingGateway
import app.duinch.adbagent.features.pairing.domain.PairingNotificationGateway
import app.duinch.adbagent.features.termux.application.EnsureTermuxBootstrapUseCase
import app.duinch.adbagent.features.termux.application.RunTermuxCommandUseCase
import app.duinch.adbagent.features.termux.data.LocalTermuxRuntimeAdapter
import app.duinch.adbagent.features.termux.domain.TermuxRuntimeGateway
import app.duinch.adbagent.features.tunnel.application.ComputeReconnectBackoffUseCase
import app.duinch.adbagent.features.tunnel.application.SaveTunnelConfigUseCase
import app.duinch.adbagent.features.tunnel.application.ToggleReverseTunnelUseCase
import app.duinch.adbagent.features.tunnel.data.SharedPrefsTunnelConfigRepository
import app.duinch.adbagent.features.tunnel.data.WebSocketReverseTunnelAdapter
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository

class AppContainer(context: Context) {

    private val appContext = context.applicationContext

    val termuxRuntimeGateway: TermuxRuntimeGateway =
        LocalTermuxRuntimeAdapter(appContext)

    val adbDiscoveryGateway: AdbDiscoveryGateway =
        AndroidMdnsAdbDiscoveryAdapter(appContext)

    val adbPairingGateway: AdbPairingGateway =
        TermuxLocalAdbPairingAdapter(termuxRuntimeGateway)

    val pairingNotificationGateway: PairingNotificationGateway =
        AndroidPairingNotificationAdapter(appContext)

    val tunnelConfigRepository: TunnelConfigRepository =
        SharedPrefsTunnelConfigRepository(appContext)

    private val computeReconnectBackoff = ComputeReconnectBackoffUseCase()

    val reverseTunnelGateway: ReverseTunnelGateway =
        WebSocketReverseTunnelAdapter(appContext, computeReconnectBackoff)

    val ensureTermuxBootstrap = EnsureTermuxBootstrapUseCase(termuxRuntimeGateway)
    val runTermuxCommand = RunTermuxCommandUseCase(termuxRuntimeGateway)
    val pairLocalAdb = PairLocalAdbUseCase(adbPairingGateway)
    val activateTcpipMode = ActivateTcpipModeUseCase(adbPairingGateway)
    val saveTunnelConfig = SaveTunnelConfigUseCase(tunnelConfigRepository)
    val toggleReverseTunnel = ToggleReverseTunnelUseCase(
        configRepository = tunnelConfigRepository,
        tunnelGateway = reverseTunnelGateway
    )
}
