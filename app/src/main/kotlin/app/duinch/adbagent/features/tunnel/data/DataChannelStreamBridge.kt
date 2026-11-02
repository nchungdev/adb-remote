package app.duinch.adbagent.features.tunnel.data

import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okio.ByteString
import org.webrtc.DataChannel

class DataChannelStreamBridge(
    private val scope: CoroutineScope,
    private val bytesBridged: AtomicLong
) {
    private val activeStreams = ConcurrentHashMap<Int, Socket>()
    private val streamWriteChannels = ConcurrentHashMap<Int, Channel<ByteArray>>()
    private val streamJobs = ConcurrentHashMap<Int, Job>()

    fun handleBinaryFrame(dc: DataChannel, bytes: ByteString, localAdbPort: Int) {
        when (val frame = TunnelStreamProtocol.decode(bytes)) {
            is TunnelStreamProtocol.Frame.Open -> openStream(dc, frame.streamId, localAdbPort)
            is TunnelStreamProtocol.Frame.Data -> {
                bytesBridged.addAndGet(frame.payload.size.toLong())
                val ch = streamWriteChannels[frame.streamId] ?: return
                if (ch.trySend(frame.payload).isFailure) {
                    scope.launch { ch.send(frame.payload) }
                }
            }
            is TunnelStreamProtocol.Frame.Close -> closeStream(frame.streamId)
            null -> Unit
        }
    }

    private fun openStream(dc: DataChannel, streamId: Int, localAdbPort: Int) {
        closeStream(streamId)
        val channel = Channel<ByteArray>(capacity = 64)
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
                    while (isActive && dc.state() == DataChannel.State.OPEN && dc.bufferedAmount() > MAX_BUFFERED_BYTES) {
                        kotlinx.coroutines.delay(15L)
                    }
                    val n = input.read(buf)
                    if (n <= 0) break
                    bytesBridged.addAndGet(n.toLong())
                    val data = TunnelStreamProtocol.encodeData(streamId, buf, n)
                    sendBinary(dc, data.toByteArray())
                }
                writeJob.cancel()
            } catch (_: Exception) {
            } finally {
                socket?.close()
                closeStream(streamId)
                val closeFrame = TunnelStreamProtocol.encodeClose(streamId)
                sendBinary(dc, closeFrame.toByteArray())
            }
        }
        streamJobs[streamId] = job
    }

    fun closeStream(streamId: Int) {
        streamJobs.remove(streamId)?.cancel()
        streamWriteChannels.remove(streamId)?.close()
        activeStreams.remove(streamId)?.runCatching { close() }
    }

    fun sendBinary(dc: DataChannel, payload: ByteArray) {
        if (dc.state() == DataChannel.State.OPEN) {
            val buf = ByteBuffer.wrap(payload)
            dc.send(DataChannel.Buffer(buf, true))
        }
    }

    fun sendText(dc: DataChannel, text: String) {
        if (dc.state() == DataChannel.State.OPEN) {
            val buf = ByteBuffer.wrap(text.toByteArray(Charsets.UTF_8))
            dc.send(DataChannel.Buffer(buf, false))
        }
    }

    fun closeAll() {
        streamJobs.values.forEach { it.cancel() }
        streamJobs.clear()
        streamWriteChannels.values.forEach { it.close() }
        streamWriteChannels.clear()
        activeStreams.values.forEach { runCatching { it.close() } }
        activeStreams.clear()
    }

    companion object {
        private const val MAX_BUFFERED_BYTES = 1_048_576L // 1MB SCTP backpressure threshold
    }
}
