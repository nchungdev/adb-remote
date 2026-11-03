package app.duinch.adbagent.features.tunnel.data

import android.content.Context
import android.os.Build
import app.duinch.adbagent.core.security.KeystoreCipher
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository

class SharedPrefsTunnelConfigRepository(
    context: Context
) : TunnelConfigRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getConfig(): TunnelConfig {
        val defaultAlias = "${Build.MANUFACTURER}-${Build.MODEL}".replace(" ", "-")
        val rawUrl = KeystoreCipher.decrypt(prefs.getString(KEY_SERVER_URL, "") ?: "")
        return TunnelConfig(
            serverUrl = rawUrl,
            deviceAlias = prefs.getString(KEY_DEVICE_ALIAS, defaultAlias) ?: defaultAlias,
            localAdbPort = prefs.getInt(KEY_LOCAL_PORT, 5555),
            runInBackground = prefs.getBoolean(KEY_RUN_IN_BACKGROUND, true),
            notifyConnectionChange = prefs.getBoolean(KEY_NOTIFY_CONNECTION_CHANGE, true),
            themeMode = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        )
    }

    override fun saveConfig(config: TunnelConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, KeystoreCipher.encrypt(config.serverUrl))
            .putString(KEY_DEVICE_ALIAS, config.deviceAlias)
            .putInt(KEY_LOCAL_PORT, config.localAdbPort)
            .putBoolean(KEY_RUN_IN_BACKGROUND, config.runInBackground)
            .putBoolean(KEY_NOTIFY_CONNECTION_CHANGE, config.notifyConnectionChange)
            .putString(KEY_THEME_MODE, config.themeMode)
            .apply()
    }

    override fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

    override fun setHasSeenOnboarding(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, seen).apply()
    }

    companion object {
        private const val PREFS_NAME = "adb_agent_tunnel_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ALIAS = "device_alias"
        private const val KEY_LOCAL_PORT = "local_adb_port"
        private const val KEY_RUN_IN_BACKGROUND = "run_in_background"
        private const val KEY_NOTIFY_CONNECTION_CHANGE = "notify_connection_change"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding_guide"
    }
}
