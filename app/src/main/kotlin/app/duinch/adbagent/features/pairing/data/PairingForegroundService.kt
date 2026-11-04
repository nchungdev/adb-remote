package app.duinch.adbagent.features.pairing.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.widget.Toast
import app.duinch.adbagent.AdbAgentApp
import app.duinch.adbagent.MainActivity
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import app.duinch.adbagent.features.pairing.domain.DiscoveredAdbPorts
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class PairingForegroundService : Service() {

    private var hadLivePort = false
    private var pairingInFlight = false

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var discoveryJob: Job? = null
    private var latestPorts = DiscoveredAdbPorts()
    private var screenshotWatcher: ScreenshotPairingWatcher? = null
    private var latestScreenshotUri: android.net.Uri? = null
    private var pendingOcrCode: String? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ensureChannel()
        when (intent?.action) {
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }
        }

        val initial = buildWaitingNotification()
        startAsSpecialUseForeground(initial)
        scope.launch {
            delay(WAITING_TIMEOUT_MS)
            if (!hadLivePort) stopAndRemove()
        }
        observeMdnsPorts()
        startScreenshotWatcher()
        return START_STICKY
    }

    private fun startScreenshotWatcher() {
        screenshotWatcher?.stop()
        screenshotWatcher = ScreenshotPairingWatcher(this, scope) { code, extractedPort, uri ->
            latestScreenshotUri = uri
            // mDNS reports the live pairing port; OCR is the fallback when it has not resolved yet.
            val pairPort = latestPorts.pairingPort ?: extractedPort
            if (pairPort != null && pairPort > 0) {
                pendingOcrCode = null
                handleSubmittedCode(pairPort, code, latestPorts.connectPort)
            } else {
                pendingOcrCode = code
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
                if (pairPort == null) {
                    // The pairing dialog closed: the prompt would otherwise linger on unrelated screens.
                    if (hadLivePort && !pairingInFlight) stopAndRemove()
                } else {
                    hadLivePort = true
                    val code = pendingOcrCode
                    if (code != null) {
                        pendingOcrCode = null
                        handleSubmittedCode(pairPort, code, ports.connectPort)
                    }
                }
            }
        }
    }

    private fun stopAndRemove() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleSubmittedCode(pairPort: Int, code: String, connectPort: Int?) {
        val container = (application as AdbAgentApp).container
        pairingInFlight = true
        container.pairingNotificationGateway.reportPairingState(AdbPairingState.PairingInProgress)

        scope.launch {
            val res = container.pairLocalAdb(pairPort, code, connectPort)
            res.fold(
                onSuccess = { state ->
                    container.pairingNotificationGateway.reportPairingState(state)
                    val homeIntent = Intent(this@PairingForegroundService, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                        latestScreenshotUri?.let {
                            putExtra(MainActivity.EXTRA_DELETE_SCREENSHOT_URI, it.toString())
                        }
                    }
                    startActivity(homeIntent)
                    stopAndRemove()
                },
                onFailure = { err ->
                    val msg = err.message ?: "Sai mã hoặc cổng đã đổi"
                    container.pairingNotificationGateway.reportPairingState(AdbPairingState.Failed(msg))
                    pairingInFlight = false
                    Toast.makeText(this@PairingForegroundService, "$msg. Hãy chụp lại màn hình.", Toast.LENGTH_LONG).show()
                    if (latestPorts.pairingPort == null) stopAndRemove()
                }
            )
        }
    }

    // Android requires a notification for a foreground service; it stays silent and out of the way.
    private fun buildWaitingNotification(): Notification {
        val stopPending = PendingIntent.getService(
            this,
            2,
            Intent(this, PairingForegroundService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return baseBuilder()
            .setContentTitle("Đang chờ ảnh chụp mã ghép nối")
            .setContentText("Mở mã ghép nối trong Gỡ lỗi không dây rồi chụp màn hình")
            .addAction(Notification.Action.Builder(null, "Huỷ", stopPending).build())
            .setOngoing(true)
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

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "ADB Wireless Pairing",
            NotificationManager.IMPORTANCE_MIN
        ).apply {
            description = "Hiển thị khi đang chờ ảnh chụp mã ghép nối"
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
        private const val WAITING_TIMEOUT_MS = 5 * 60 * 1000L
        private const val CHANNEL_ID = "adb_pairing_waiting"
        private const val NOTIFICATION_ID = 1043
        private const val ACTION_START = "app.duinch.adbagent.START_PAIRING"
        private const val ACTION_STOP = "app.duinch.adbagent.STOP_PAIRING"

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
