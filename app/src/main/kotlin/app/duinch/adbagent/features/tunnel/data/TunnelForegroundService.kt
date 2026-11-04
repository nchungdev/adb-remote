package app.duinch.adbagent.features.tunnel.data

import java.util.concurrent.ConcurrentHashMap
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import app.duinch.adbagent.AdbAgentApp
import app.duinch.adbagent.MainActivity
import app.duinch.adbagent.R
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

class TunnelForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var isObserving = false
    private var wifiLock: WifiManager.WifiLock? = null

    private fun updateWifiLock(isConnected: Boolean) {
        if (isConnected) {
            if (wifiLock == null) {
                val wm = applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
                @Suppress("DEPRECATION")
                val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    WifiManager.WIFI_MODE_FULL_LOW_LATENCY
                } else {
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF
                }
                wifiLock = wm?.createWifiLock(mode, "AdbRemote:TunnelWifiLock")?.apply {
                    setReferenceCounted(false)
                }
            }
            runCatching {
                if (wifiLock?.isHeld == false) wifiLock?.acquire()
            }
        } else {
            runCatching {
                if (wifiLock?.isHeld == true) wifiLock?.release()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = application as? AdbAgentApp
        val container = app?.container

        when (intent?.action) {
            ACTION_STOP -> {
                container?.tunnelConfigRepository?.setWantsConnected(false)
                serviceScope.launch {
                    container?.reverseTunnelGateway?.disconnect()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return START_NOT_STICKY
            }
            ACTION_DISCONNECT -> {
                container?.tunnelConfigRepository?.setWantsConnected(false)
                serviceScope.launch { container?.reverseTunnelGateway?.disconnect() }
                return START_STICKY
            }
            ACTION_CONNECT -> {
                serviceScope.launch {
                    val cfg = container?.tunnelConfigRepository?.getConfig()
                    if (cfg != null) container.toggleReverseTunnel(cfg)
                }
                return START_STICKY
            }
            ACTION_RESTORE_NOTIFICATION -> {
                updateNotification()
                return START_STICKY
            }
        }

        ensureNotificationChannel()
        val initialNotif = buildNotification(
            state = container?.reverseTunnelGateway?.observeConnectionState()?.let {
                (it as? kotlinx.coroutines.flow.StateFlow)?.value
            } ?: TunnelConnectionState.Disconnected,
            net = NetworkType.UNKNOWN,
            wifiIp = container?.reverseTunnelGateway?.getLocalWifiIp().orEmpty(),
            localPort = container?.tunnelConfigRepository?.getConfig()?.localAdbPort ?: 5555
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, initialNotif, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, initialNotif)
        }

        startObservingFlows()
        return START_STICKY
    }

    private fun startObservingFlows() {
        if (isObserving) return
        val container = (application as? AdbAgentApp)?.container ?: return
        isObserving = true

        serviceScope.launch {
            combine(
                container.reverseTunnelGateway.observeConnectionState(),
                container.reverseTunnelGateway.observeActiveNetwork()
            ) { conn, net ->
                // Byte counters tick every second; the notification does not show them.
                ((conn as? TunnelConnectionState.Connected)?.copy(bytesBridged = 0L) ?: conn) to net
            }.distinctUntilChanged().collect { (conn, net) ->
                updateWifiLock(conn is TunnelConnectionState.Connected)
                val ip = container.reverseTunnelGateway.getLocalWifiIp().orEmpty()
                val port = container.tunnelConfigRepository.getConfig().localAdbPort
                val notification = buildNotification(conn, net, ip, port)
                val manager = getSystemService(NotificationManager::class.java)
                manager?.notify(NOTIFICATION_ID, notification)
            }
        }
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Dịch vụ chạy ngầm ADB Remote",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Duy trì kết nối ADB nội bộ và đường hầm từ xa về Mac mini"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun updateNotification() {
        val container = (application as? AdbAgentApp)?.container ?: return
        val conn = (container.reverseTunnelGateway.observeConnectionState() as? kotlinx.coroutines.flow.StateFlow)?.value
            ?: TunnelConnectionState.Disconnected
        val ip = container.reverseTunnelGateway.getLocalWifiIp().orEmpty()
        val port = container.tunnelConfigRepository.getConfig().localAdbPort
        val notification = buildNotification(conn, NetworkType.UNKNOWN, ip, port)
        val manager = getSystemService(NotificationManager::class.java)
        manager?.notify(NOTIFICATION_ID, notification)
    }

    private fun buildNotification(
        state: TunnelConnectionState,
        net: NetworkType,
        wifiIp: String,
        localPort: Int
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val deleteIntent = PendingIntent.getService(
            this,
            4,
            Intent(this, TunnelForegroundService::class.java).apply { action = ACTION_RESTORE_NOTIFICATION },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val (title, text) = when (state) {
            is TunnelConnectionState.Connected -> {
                val mapped = state.mappedServerPort?.let { "127.0.0.1:$it" } ?: "Đã nhận cổng"
                "ADB Remote • Đang trực tuyến" to "Mac mini: $mapped • Wi-Fi: ${wifiIp.ifBlank { "LAN" }}:$localPort"
            }
            is TunnelConnectionState.Connecting ->
                "ADB Remote • Đang kết nối..." to "Đang thiết lập kênh bảo mật..."
            is TunnelConnectionState.Reconnecting ->
                "ADB Remote • Đang tự nối lại" to "Lần thử ${state.attempt} • Thử lại sau ${state.nextRetryDelayMs / 1000}s"
            is TunnelConnectionState.Error ->
                "ADB Remote • Gián đoạn kết nối" to (state.message.take(45))
            TunnelConnectionState.Disconnected -> {
                val ipDisplay = if (wifiIp.isNotBlank()) "Wi-Fi: $wifiIp:$localPort" else "Cổng ADB: $localPort"
                "ADB Remote • Sẵn sàng chạy ngầm" to "$ipDisplay • Đang duy trì kết nối"
            }
        }

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        builder
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_adb_tunnel)
            .setContentIntent(openIntent)
            .setDeleteIntent(deleteIntent)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        // Actions
        if (state is TunnelConnectionState.Connected) {
            val disconnectIntent = PendingIntent.getService(
                this,
                1,
                Intent(this, TunnelForegroundService::class.java).apply { action = ACTION_DISCONNECT },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(Notification.Action.Builder(null, "Ngắt kết nối", disconnectIntent).build())
        } else {
            val connectIntent = PendingIntent.getService(
                this,
                2,
                Intent(this, TunnelForegroundService::class.java).apply { action = ACTION_CONNECT },
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            builder.addAction(Notification.Action.Builder(null, "Bật đường hầm", connectIntent).build())
        }

        val stopIntent = PendingIntent.getService(
            this,
            3,
            Intent(this, TunnelForegroundService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        builder.addAction(Notification.Action.Builder(null, "Dừng dịch vụ", stopIntent).build())

        val notification = builder.build()
        @Suppress("DEPRECATION")
        notification.flags = notification.flags or
            Notification.FLAG_NO_CLEAR or
            Notification.FLAG_ONGOING_EVENT or
            Notification.FLAG_FOREGROUND_SERVICE
        return notification
    }

    override fun onDestroy() {
        updateWifiLock(false)
        serviceScope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "adb_reverse_tunnel_channel"
        private const val NOTIFICATION_ID = 1042
        const val ACTION_START = "app.duinch.adbagent.START_TUNNEL"
        const val ACTION_STOP = "app.duinch.adbagent.STOP_TUNNEL"
        const val ACTION_CONNECT = "app.duinch.adbagent.ACTION_CONNECT"
        const val ACTION_DISCONNECT = "app.duinch.adbagent.ACTION_DISCONNECT"
        const val ACTION_RESTORE_NOTIFICATION = "app.duinch.adbagent.ACTION_RESTORE_NOTIFICATION"

        private val owners = ConcurrentHashMap.newKeySet<Any>()

        /** Starts the service on behalf of a tunnel; it stays up until every owner released it. */
        fun acquire(context: Context, owner: Any) {
            owners.add(owner)
            start(context)
        }

        fun release(context: Context, owner: Any, keepRunning: Boolean) {
            owners.remove(owner)
            if (!keepRunning && owners.isEmpty()) stop(context)
        }

        fun start(context: Context) {
            val intent = Intent(context, TunnelForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, TunnelForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
