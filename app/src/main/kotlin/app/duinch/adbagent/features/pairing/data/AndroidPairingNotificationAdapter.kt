package app.duinch.adbagent.features.pairing.data

import android.content.Context
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.domain.PairingNotificationGateway
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

class AndroidPairingNotificationAdapter(
    context: Context
) : PairingNotificationGateway {

    private val appContext = context.applicationContext
    private val events = MutableSharedFlow<AdbPairingState>(extraBufferCapacity = 8)

    override fun observeNotificationPairing(): Flow<AdbPairingState> = events.asSharedFlow()

    override fun startInteractivePairing() {
        PairingForegroundService.start(appContext)
    }

    override fun reportPairingState(state: AdbPairingState) {
        events.tryEmit(state)
    }
}
