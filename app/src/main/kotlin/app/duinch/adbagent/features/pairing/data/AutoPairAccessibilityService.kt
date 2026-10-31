package app.duinch.adbagent.features.pairing.data

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import app.duinch.adbagent.AdbAgentApp
import app.duinch.adbagent.MainActivity
import app.duinch.adbagent.features.pairing.domain.AdbPairingState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class AutoPairAccessibilityService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onInterrupt() {
        isAutoPairingActive.set(false)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (!isAutoPairingActive.get()) return
        val root = rootInActiveWindow ?: return

        // Step 1: Detect pairing dialog and extract 6-digit code and pairing port
        val (code, port) = extractPairingCodeAndPort(root)
        if (code != null && port != null) {
            isAutoPairingActive.set(false)
            performGlobalAction(GLOBAL_ACTION_BACK)
            returnToMainActivity()
            executePairing(code, port)
            return
        }

        // Step 2: If on Wireless Debugging page, click "Pair device with pairing code"
        findAndClickPairOption(root)
    }

    private fun findAndClickPairOption(root: AccessibilityNodeInfo): Boolean {
        val targets = listOf("ghép nối bằng mã", "pair with pairing code", "pair device with pairing code")
        for (target in targets) {
            val nodes = root.findAccessibilityNodeInfosByText(target)
            for (node in nodes) {
                if (performClickOnNodeOrParent(node)) {
                    return true
                }
            }
        }
        return false
    }

    private fun performClickOnNodeOrParent(node: AccessibilityNodeInfo?): Boolean {
        var cur = node
        while (cur != null) {
            if (cur.isClickable) {
                return cur.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            cur = cur.parent
        }
        return false
    }

    private fun extractPairingCodeAndPort(root: AccessibilityNodeInfo): Pair<String?, Int?> {
        val texts = mutableListOf<String>()
        collectAllTexts(root, texts)

        var foundCode: String? = null
        var foundPort: Int? = null

        val codeRegex = Regex("""\b(\d{6})\b""")
        val portRegex = Regex("""(?::|cổng\s*)(\d{4,5})\b""", RegexOption.IGNORE_CASE)

        for (text in texts) {
            val clean = text.trim()
            if (clean.contains(":") && portRegex.containsMatchIn(clean)) {
                portRegex.find(clean)?.groupValues?.getOrNull(1)?.toIntOrNull()?.let {
                    if (it in 1024..65535) foundPort = it
                }
            }
            if (codeRegex.matches(clean) || (codeRegex.containsMatchIn(clean) && !clean.contains("."))) {
                codeRegex.find(clean)?.groupValues?.getOrNull(1)?.let {
                    foundCode = it
                }
            }
        }
        return foundCode to foundPort
    }

    private fun collectAllTexts(node: AccessibilityNodeInfo?, out: MutableList<String>, depth: Int = 0) {
        if (node == null || depth > 12) return
        val t = node.text?.toString()
        if (!t.isNullOrBlank()) out.add(t)
        val d = node.contentDescription?.toString()
        if (!d.isNullOrBlank()) out.add(d)

        for (i in 0 until node.childCount) {
            collectAllTexts(node.getChild(i), out, depth + 1)
        }
    }

    private fun returnToMainActivity() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT
        }
        startActivity(intent)
    }

    private fun executePairing(code: String, port: Int) {
        val container = (application as? AdbAgentApp)?.container ?: return
        container.pairingNotificationGateway.reportPairingState(AdbPairingState.PairingInProgress)
        val connectPort = container.tunnelConfigRepository.getConfig().localAdbPort

        serviceScope.launch {
            container.pairLocalAdb(port, code, connectPort).fold(
                onSuccess = { paired ->
                    container.pairingNotificationGateway.reportPairingState(paired)
                },
                onFailure = { err ->
                    container.pairingNotificationGateway.reportPairingState(
                        AdbPairingState.Failed(err.message ?: "Ghép đôi tự động thất bại")
                    )
                }
            )
        }
    }

    companion object {
        var instance: AutoPairAccessibilityService? = null
            private set

        val isServiceEnabled: Boolean
            get() = instance != null

        val isAutoPairingActive = AtomicBoolean(false)

        fun startAutoPairing(context: Context): Boolean {
            if (!isServiceEnabled) return false
            isAutoPairingActive.set(true)
            val intent = Intent("android.settings.WIRELESS_DEBUGGING_SETTINGS").apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            runCatching { context.startActivity(intent) }
                .onFailure {
                    val fallback = Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(fallback)
                }
            return true
        }

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        }
    }
}
