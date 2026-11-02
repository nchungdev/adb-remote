package app.duinch.adbagent.features.tunnel.domain

import kotlinx.coroutines.flow.SharedFlow

interface ConnectionAlertGateway {
    val alertsFlow: SharedFlow<String>
    fun start()
    fun onForegroundChanged(isInForeground: Boolean)
    fun notifyAlert(message: String)
}
