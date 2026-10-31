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
import app.duinch.adbagent.features.tunnel.application.ClearTunnelHistoryUseCase
import app.duinch.adbagent.features.tunnel.application.ComputeReconnectBackoffUseCase
import app.duinch.adbagent.features.tunnel.application.ObserveTunnelHistoryUseCase
import app.duinch.adbagent.features.tunnel.application.SaveTunnelConfigUseCase
import app.duinch.adbagent.features.tunnel.application.ToggleReverseTunnelUseCase
import app.duinch.adbagent.features.tunnel.data.SharedPrefsTunnelConfigRepository
import app.duinch.adbagent.features.tunnel.data.SharedPrefsTunnelHistoryRepository
import app.duinch.adbagent.features.tunnel.data.WebRtcReverseTunnelAdapter
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import app.duinch.adbagent.features.tunnel.domain.TunnelHistoryRepository
import app.duinch.adbagent.features.updater.application.CheckAppUpdateUseCase
import app.duinch.adbagent.features.updater.application.DownloadAndInstallUpdateUseCase
import app.duinch.adbagent.features.updater.data.OkHttpAppUpdateAdapter
import app.duinch.adbagent.features.updater.domain.AppUpdateGateway

class AppContainer(context: Context) {

    val appContext: Context = context.applicationContext

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

    val tunnelHistoryRepository: TunnelHistoryRepository =
        SharedPrefsTunnelHistoryRepository(appContext)

    val hostActivityRepository: app.duinch.adbagent.features.tunnel.domain.HostActivityRepository =
        app.duinch.adbagent.features.tunnel.data.InMemoryHostActivityRepository()

    val appUpdateGateway: AppUpdateGateway =
        OkHttpAppUpdateAdapter(appContext)

    private val computeReconnectBackoff = ComputeReconnectBackoffUseCase()

    val reverseTunnelGateway: ReverseTunnelGateway =
        WebRtcReverseTunnelAdapter(
            appContext,
            computeReconnectBackoff,
            tunnelHistoryRepository,
            hostActivityRepository
        )

    val ensureTermuxBootstrap = EnsureTermuxBootstrapUseCase(termuxRuntimeGateway)
    val runTermuxCommand = RunTermuxCommandUseCase(termuxRuntimeGateway)
    val pairLocalAdb = PairLocalAdbUseCase(adbPairingGateway)
    val activateTcpipMode = ActivateTcpipModeUseCase(adbPairingGateway)
    val saveTunnelConfig = SaveTunnelConfigUseCase(tunnelConfigRepository)
    val observeTunnelHistory = ObserveTunnelHistoryUseCase(tunnelHistoryRepository)
    val clearTunnelHistory = ClearTunnelHistoryUseCase(tunnelHistoryRepository)
    val toggleReverseTunnel = ToggleReverseTunnelUseCase(
        configRepository = tunnelConfigRepository,
        tunnelGateway = reverseTunnelGateway
    )
    val checkAppUpdate = CheckAppUpdateUseCase(appUpdateGateway, tunnelConfigRepository)
    val downloadAndInstallUpdate = DownloadAndInstallUpdateUseCase(appUpdateGateway)
}
