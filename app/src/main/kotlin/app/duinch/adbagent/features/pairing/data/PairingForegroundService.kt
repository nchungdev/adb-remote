package app.duinch.adbagent.features.pairing.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.RemoteInput
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import app.duinch.adbagent.AdbAgentApp
import app.duinch.adbagent.MainActivity
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.domain.DiscoveredAdbPorts
import app.duinch.adbagent.features.pairing.presentation.PairingPromptActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class PairingForegroundService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var discoveryJob: Job? = null
    private var latestPorts = DiscoveredAdbPorts()
    private var screenshotWatcher: ScreenshotPairingWatcher? = null
    private var latestScreenshotUri: android.net.Uri? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SUBMIT_CODE -> {
                val bundle = RemoteInput.getResultsFromIntent(intent)
                val code = bundle?.getCharSequence(KEY_PAIRING_CODE)?.toString()?.trim().orEmpty()
                val pairPort = intent.getIntExtra(EXTRA_PAIR_PORT, latestPorts.pairingPort ?: -1)
                val connPort = intent.getIntExtra(EXTRA_CONN_PORT, latestPorts.connectPort ?: -1)
                    .takeIf { it > 0 }
                handleSubmittedCode(pairPort, code, connPort)
                return START_NOT_STICKY
            }
        }

        val initial = buildSearchingNotification()
        startAsSpecialUseForeground(initial)
        observeMdnsPorts()
        startScreenshotWatcher()
        return START_STICKY
    }

    private fun startScreenshotWatcher() {
        screenshotWatcher?.stop()
        screenshotWatcher = ScreenshotPairingWatcher(this, scope) { code, uri ->
            latestScreenshotUri = uri
            val pairPort = latestPorts.pairingPort
            if (pairPort != null && pairPort > 0) {
                handleSubmittedCode(pairPort, code, latestPorts.connectPort)
            }
        }.apply { start() }
    }

    private fun observeMdnsPorts() {
        val container = (application as AdbAgentApp).container
        container.adbDiscoveryGateway.startDiscovery()
        discoveryJob?.cancel()
        discoveryJob = scope.launch {
            container.adbDiscoveryGateway.observeDiscoveredPorts().collect { ports ->
                latestPorts = ports
                val pairPort = ports.pairingPort
                if (pairPort != null) {
                    notifyUpdated(buildReplyNotification(pairPort, ports.connectPort))
                }
            }
        }
    }

    private fun handleSubmittedCode(pairPort: Int, code: String, connectPort: Int?) {
        val container = (application as AdbAgentApp).container
        notifyUpdated(buildStatusNotification("Đang ghép nối cổng $pairPort...", ongoing = true))
        container.pairingNotificationGateway.reportPairingState(AdbPairingState.PairingInProgress)

        scope.launch {
            val res = container.pairLocalAdb(pairPort, code, connectPort)
            res.fold(
                onSuccess = { state ->
                    container.pairingNotificationGateway.reportPairingState(state)
                    notifyUpdated(
                        buildStatusNotification(
                            "Ghép nối thành công! Đang mở ADB Agent...",
                            ongoing = false
                        )
                    )
                    val homeIntent = Intent(this@PairingForegroundService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        latestScreenshotUri?.let {
                            putExtra(MainActivity.EXTRA_DELETE_SCREENSHOT_URI, it.toString())
                        }
                    }
                    startActivity(homeIntent)
                    stopForeground(STOP_FOREGROUND_DETACH)
                    stopSelf()
                },
                onFailure = { err ->
                    val msg = err.message ?: "Sai mã hoặc cổng đã đổi"
                    container.pairingNotificationGateway.reportPairingState(AdbPairingState.Failed(msg))
                    notifyUpdated(buildStatusNotification("Lỗi: $msg. Vui lòng mở lại mã để thử lại.", ongoing = false))
                }
            )
        }
    }

    private fun buildSearchingNotification(): Notification {
        val stopPending = PendingIntent.getService(
            this,
            2,
            Intent(this, PairingForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val bigText = Notification.BigTextStyle()
            .setBigContentTitle("Đang tìm cổng ghép nối...")
            .bigText("Vào Cài đặt → Gỡ lỗi không dây → Chọn 'Ghép nối bằng mã ghép nối'.")

        return baseBuilder()
            .setContentTitle("Đang tìm cổng ghép nối...")
            .setContentText("Mở Cài đặt → Gỡ lỗi không dây → Ghép nối bằng mã")
            .setStyle(bigText)
            .addAction(Notification.Action.Builder(null, "Huỷ", stopPending).build())
            .setOngoing(true)
            .build()
    }

    private fun buildReplyNotification(pairPort: Int, connectPort: Int?): Notification {
        val remoteInput = RemoteInput.Builder(KEY_PAIRING_CODE)
            .setLabel("Nhập mã 6 chữ số")
            .build()
        val replyIntent = Intent(this, PairingForegroundService::class.java).apply {
            action = ACTION_SUBMIT_CODE
            putExtra(EXTRA_PAIR_PORT, pairPort)
            if (connectPort != null) putExtra(EXTRA_CONN_PORT, connectPort)
        }
        val flagMutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        val replyPending = PendingIntent.getService(
            this,
            1,
            replyIntent,
            flagMutable or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val replyAction = Notification.Action.Builder(null, "Nhập mã", replyPending)
            .addRemoteInput(remoteInput)
            .apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    setSemanticAction(Notification.Action.SEMANTIC_ACTION_REPLY)
                }
            }
            .build()

        val stopPending = PendingIntent.getService(
            this,
            2,
            Intent(this, PairingForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val cancelAction = Notification.Action.Builder(null, "Huỷ", stopPending).build()

        return baseBuilder()
            .setContentTitle("Tìm thấy cổng ghép nối: $pairPort")
            .setContentText("Chụp ảnh màn hình để tự động ghép đôi (hoặc bấm 'Nhập mã')")
            .addAction(replyAction)
            .addAction(cancelAction)
            .setOngoing(true)
            .build()
    }

    private fun buildStatusNotification(text: String, ongoing: Boolean): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return baseBuilder()
            .setContentTitle("ADB Agent • Ghép nối không dây")
            .setContentText(text)
            .setContentIntent(openApp)
            .setOngoing(ongoing)
            .setAutoCancel(!ongoing)
            .build()
    }

    private fun baseBuilder(): Notification.Builder {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder.setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
    }

    private fun startAsSpecialUseForeground(notification: Notification) {
        @Suppress("DEPRECATION")
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun notifyUpdated(notification: Notification) {
        @Suppress("DEPRECATION")
        notification.flags = notification.flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ADB Wireless Pairing",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Cho phép nhập mã ghép nối 6 chữ số ngay trên thanh thông báo"
        }
        nm.createNotificationChannel(channel)
    }

    override fun onDestroy() {
        screenshotWatcher?.stop()
        screenshotWatcher = null
        discoveryJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "adb_pairing_interactive_channel"
        private const val NOTIFICATION_ID = 1043
        private const val ACTION_START = "app.duinch.adbagent.START_PAIRING"
        private const val ACTION_STOP = "app.duinch.adbagent.STOP_PAIRING"
        private const val ACTION_SUBMIT_CODE = "app.duinch.adbagent.SUBMIT_PAIRING_CODE"
        private const val KEY_PAIRING_CODE = "key_pairing_code"
        private const val EXTRA_PAIR_PORT = "extra_pair_port"
        private const val EXTRA_CONN_PORT = "extra_conn_port"

        fun start(context: Context) {
            val intent = Intent(context, PairingForegroundService::class.java).setAction(ACTION_START)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
