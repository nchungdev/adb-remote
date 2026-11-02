package app.duinch.adbagent.features.tunnel.data

import java.net.InetSocketAddress
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.WebSocket
import okio.ByteString

class WebSocketStreamBridge(
    private val scope: CoroutineScope,
    private val bytesBridged: AtomicLong
) {
    private val activeStreams = ConcurrentHashMap<Int, Socket>()
    private val streamWriteChannels = ConcurrentHashMap<Int, Channel<ByteArray>>()
    private val streamJobs = ConcurrentHashMap<Int, Job>()

    fun handleBinaryFrame(ws: WebSocket, bytes: ByteString, localAdbPort: Int) {
        when (val frame = TunnelStreamProtocol.decode(bytes)) {
            is TunnelStreamProtocol.Frame.Open -> openStream(ws, frame.streamId, localAdbPort)
            is TunnelStreamProtocol.Frame.Data -> {
                bytesBridged.addAndGet(frame.payload.size.toLong())
                streamWriteChannels[frame.streamId]?.trySend(frame.payload)
            }
            is TunnelStreamProtocol.Frame.Close -> closeStream(frame.streamId)
            null -> Unit
        }
    }

    private fun openStream(ws: WebSocket, streamId: Int, localAdbPort: Int) {
        closeStream(streamId)
        val channel = Channel<ByteArray>(Channel.UNLIMITED)
        streamWriteChannels[streamId] = channel

        val job = scope.launch {
            var socket: Socket? = null
            try {
                socket = Socket().apply {
                    tcpNoDelay = true
                    connect(InetSocketAddress("127.0.0.1", localAdbPort), 4000)
                }
                activeStreams[streamId] = socket

                val writeJob = launch {
                    val out = socket.getOutputStream()
                    for (chunk in channel) {
                        out.write(chunk)
                        out.flush()
                    }
                }

                val buf = ByteArray(16384)
                val input = socket.getInputStream()
                while (isActive) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    bytesBridged.addAndGet(n.toLong())
                    val data = TunnelStreamProtocol.encodeData(streamId, buf, n)
                    ws.send(data)
                }
                writeJob.cancel()
            } catch (_: Exception) {
            } finally {
                socket?.close()
                closeStream(streamId)
                val closeFrame = TunnelStreamProtocol.encodeClose(streamId)
                ws.send(closeFrame)
            }
        }
        streamJobs[streamId] = job
    }

    fun closeStream(streamId: Int) {
        streamJobs.remove(streamId)?.cancel()
        streamWriteChannels.remove(streamId)?.close()
        activeStreams.remove(streamId)?.runCatching { close() }
    }

    fun closeAll() {
        streamJobs.values.forEach { it.cancel() }
        streamJobs.clear()
        streamWriteChannels.values.forEach { it.close() }
        streamWriteChannels.clear()
        activeStreams.values.forEach { runCatching { it.close() } }
        activeStreams.clear()
    }
}
