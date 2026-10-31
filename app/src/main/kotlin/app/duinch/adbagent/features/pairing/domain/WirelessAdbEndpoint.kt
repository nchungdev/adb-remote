package app.duinch.adbagent.features.pairing.domain

data class DiscoveredAdbPorts(
    val pairingPort: Int? = null,
    val connectPort: Int? = null
) {
    val hasConnectPort: Boolean get() = connectPort != null && connectPort in 1..65535
    val hasPairingPort: Boolean get() = pairingPort != null && pairingPort in 1..65535
}

sealed interface AdbPairingState {
    data object Idle : AdbPairingState
    data object Discovering : AdbPairingState
    data class PairingPortFound(val port: Int) : AdbPairingState
    data object PairingInProgress : AdbPairingState
    data class PairedAndConnected(val connectPort: Int) : AdbPairingState
    data class Failed(val reason: String) : AdbPairingState
}

object PairingCodeValidator {
    private val SIX_DIGITS = Regex("^\\d{6}$")

    fun isValid(code: String): Boolean = SIX_DIGITS.matches(code.trim())

    fun isValidPort(port: Int): Boolean = port in 1..65535
}
