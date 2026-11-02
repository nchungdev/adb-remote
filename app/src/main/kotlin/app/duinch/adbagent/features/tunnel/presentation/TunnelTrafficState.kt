package app.duinch.adbagent.features.tunnel.presentation

import android.net.TrafficStats
import android.os.Process
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import kotlinx.coroutines.delay

@Composable
fun produceConnectedEpochNow(activeConnectedEpoch: Long): State<Long> = produceState(
    initialValue = System.currentTimeMillis(),
    key1 = activeConnectedEpoch
) {
    if (activeConnectedEpoch > 0L) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
}

@Composable
fun produceTunnelTrafficMetrics(
    activeConnectedEpoch: Long,
    bridgeBytes: Long
): State<Pair<Long, Long>> = produceState(
    initialValue = 0L to 0L,
    key1 = activeConnectedEpoch
) {
    if (activeConnectedEpoch > 0L) {
        var lastBytes = 0L
        while (true) {
            val currentBytes = runCatching {
                val uid = Process.myUid()
                val rx = TrafficStats.getUidRxBytes(uid)
                val tx = TrafficStats.getUidTxBytes(uid)
                val socketBytes = if (rx >= 0 && tx >= 0) rx + tx else 0L
                maxOf(socketBytes, bridgeBytes)
            }.getOrDefault(0L)

            val speed = if (lastBytes > 0L && currentBytes >= lastBytes) currentBytes - lastBytes else 0L
            lastBytes = currentBytes
            value = currentBytes to speed
            delay(1_000L)
        }
    } else {
        value = 0L to 0L
    }
}
