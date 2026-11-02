package app.duinch.adbagent.features.tunnel.data

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.content.ContextCompat
import app.duinch.adbagent.MainActivity
import app.duinch.adbagent.R
import app.duinch.adbagent.core.model.NetworkType
import app.duinch.adbagent.features.tunnel.domain.ConnectionAlertGateway
import app.duinch.adbagent.features.tunnel.domain.ConnectionChangeAlertEvaluator
import app.duinch.adbagent.features.tunnel.domain.ReverseTunnelGateway
import app.duinch.adbagent.features.tunnel.domain.TunnelConnectionState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class AndroidConnectionAlertNotifier(
    private val context: Context,
    private val reverseTunnelGateway: ReverseTunnelGateway,
    private val isForegroundProvider: () -> Boolean,
    private val isEnabledProvider: () -> Boolean = { true },
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
) : ConnectionAlertGateway {

    private val _alertsFlow = MutableSharedFlow<String>(extraBufferCapacity = 16)
    override val alertsFlow: SharedFlow<String> = _alertsFlow.asSharedFlow()

    private var previousConnState: TunnelConnectionState? = null
    private var previousUsbState: Boolean? = null
    private var previousNetworkType: NetworkType? = null
    private var isStarted = false

    override fun start() {
        if (isStarted) return
        isStarted = true

        ensureAlertChannel()

        scope.launch {
            reverseTunnelGateway.observeConnectionState().collect { current ->
                val prev = previousConnState
                previousConnState = current
                val alert = ConnectionChangeAlertEvaluator.evaluateServerStateChange(prev, current)
                if (alert != null) {
                    notifyAlert(alert)
                }
            }
        }

        scope.launch {
            reverseTunnelGateway.observeUsbConnected().collect { current ->
                val prev = previousUsbState
                previousUsbState = current
                val alert = ConnectionChangeAlertEvaluator.evaluateUsbChange(prev, current)
                if (alert != null) {
                    notifyAlert(alert)
                }
            }
        }

        scope.launch {
            reverseTunnelGateway.observeActiveNetwork().collect { current ->
                val prev = previousNetworkType
                previousNetworkType = current
                val alert = ConnectionChangeAlertEvaluator.evaluateNetworkChange(prev, current)
                if (alert != null) {
                    notifyAlert(alert)
                }
            }
        }
    }

    override fun onForegroundChanged(isInForeground: Boolean) {
        if (isInForeground) {
            val manager = context.getSystemService(NotificationManager::class.java)
            runCatching { manager?.cancel(ALERT_NOTIFICATION_ID) }
        }
    }

    override fun notifyAlert(message: String) {
        if (!isEnabledProvider()) return
        _alertsFlow.tryEmit(message)
        if (isForegroundProvider()) {
            showInAppToast(message)
        } else {
            postSystemAlertNotification(message)
        }
    }

    private fun showInAppToast(message: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun postSystemAlertNotification(message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return
        }
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        ensureAlertChannel()

        val openIntent = PendingIntent.getActivity(
            context,
            ALERT_PENDING_INTENT_REQ,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(context, ALERT_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(context)
        }

        val notif = builder
            .setContentTitle("ADB Remote")
            .setContentText(message)
            .setSmallIcon(R.drawable.ic_stat_adb_tunnel)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setOnlyAlertOnce(false)
            .build()

        runCatching {
            manager.notify(ALERT_NOTIFICATION_ID, notif)
        }
    }

    private fun ensureAlertChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            ALERT_CHANNEL_ID,
            "Cảnh báo kết nối",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Thông báo khi trạng thái kết nối máy chủ, Wi-Fi hoặc USB thay đổi"
            setShowBadge(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val ALERT_CHANNEL_ID = "adb_remote_connection_alerts"
        const val ALERT_NOTIFICATION_ID = 1044
        const val ALERT_PENDING_INTENT_REQ = 1044
    }
}
