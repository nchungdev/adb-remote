package app.duinch.adbagent.features.sharing.application

import app.duinch.adbagent.features.sharing.domain.FileSendGateway
import app.duinch.adbagent.features.sharing.domain.ShareTarget
import app.duinch.adbagent.features.sharing.domain.SharedFile
import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import app.duinch.adbagent.features.tunnel.domain.normalizedServerAddress

/**
 * One target per machine. A file travels over plain HTTP to the server, not through the ADB
 * tunnel, so a saved address is enough. LAN goes first: Quick Tunnel is slower and caps uploads.
 */
fun shareTargets(connections: List<SavedConnection>): List<ShareTarget> =
    connections.filter { it.serverUrl.isNotBlank() }
        .groupBy { it.server?.id ?: it.id }
        .map { (id, group) ->
            val addresses = group.sortedBy { isTunnelAddress(it.serverUrl) }.map { it.serverUrl }
            val title = group.firstNotNullOfOrNull { it.server?.name?.ifBlank { null } }
                ?: group.firstNotNullOfOrNull { it.name }
                ?: normalizedServerAddress(addresses.first())
            ShareTarget(id, title, addresses)
        }

private fun isTunnelAddress(url: String) = url.startsWith("https://") || url.contains("trycloudflare.com")

class SendSharedFilesUseCase(
    private val gateway: FileSendGateway,
    private val isReachable: suspend (String) -> Boolean
) {
    /** Returns where the last file was saved. Stops at the first failure. */
    suspend operator fun invoke(
        target: ShareTarget,
        files: List<SharedFile>,
        onProgress: (fileIndex: Int, bytesSent: Long) -> Unit
    ): Result<String> {
        val address = target.addresses.firstOrNull { isReachable(it) }
            ?: return Result.failure(IllegalStateException("unreachable"))
        var saved = ""
        files.forEachIndexed { index, file ->
            saved = gateway.send(address, file) { sent -> onProgress(index, sent) }
                .getOrElse { return Result.failure(it) }
        }
        return Result.success(saved)
    }
}
