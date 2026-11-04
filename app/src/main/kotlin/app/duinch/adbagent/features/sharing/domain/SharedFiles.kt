package app.duinch.adbagent.features.sharing.domain

/** A file another app handed over. [reference] is opaque here; only the data layer can open it. */
data class SharedFile(val reference: String, val name: String, val sizeBytes: Long?)

/** A machine to send to. Its addresses are in the order to try them. */
data class ShareTarget(val id: String, val title: String, val addresses: List<String>)

enum class FileSendFailure { NOT_APPROVED, TOO_LARGE, OTHER }

class FileSendException(val failure: FileSendFailure, message: String) : Exception(message)

interface FileSendGateway {
    /** Returns where the server saved the file. */
    suspend fun send(serverUrl: String, file: SharedFile, onBytesSent: (Long) -> Unit): Result<String>
}
