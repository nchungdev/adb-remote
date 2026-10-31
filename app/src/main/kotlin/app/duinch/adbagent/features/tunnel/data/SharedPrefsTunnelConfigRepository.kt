package app.duinch.adbagent.features.tunnel.data

import android.content.Context
import android.os.Build
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository

class SharedPrefsTunnelConfigRepository(
    context: Context
) : TunnelConfigRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getConfig(): TunnelConfig {
        val defaultAlias = "${Build.MANUFACTURER}-${Build.MODEL}".replace(" ", "-")
        return TunnelConfig(
            serverUrl = prefs.getString(KEY_SERVER_URL, TunnelConfig.DEFAULT_TUNNEL_URL)
                ?: TunnelConfig.DEFAULT_TUNNEL_URL,
            deviceAlias = prefs.getString(KEY_DEVICE_ALIAS, defaultAlias) ?: defaultAlias,
            localAdbPort = prefs.getInt(KEY_LOCAL_PORT, 5555),
            cfAccessClientId = prefs.getString(KEY_CF_CLIENT_ID, "") ?: "",
            cfAccessClientSecret = prefs.getString(KEY_CF_CLIENT_SECRET, "") ?: "",
            autoReconnectOnNetworkChange = prefs.getBoolean(KEY_AUTO_RECONNECT, true)
        )
    }

    override fun saveConfig(config: TunnelConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, config.serverUrl)
            .putString(KEY_DEVICE_ALIAS, config.deviceAlias)
            .putInt(KEY_LOCAL_PORT, config.localAdbPort)
            .putString(KEY_CF_CLIENT_ID, config.cfAccessClientId)
            .putString(KEY_CF_CLIENT_SECRET, config.cfAccessClientSecret)
            .putBoolean(KEY_AUTO_RECONNECT, config.autoReconnectOnNetworkChange)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "adb_agent_tunnel_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ALIAS = "device_alias"
        private const val KEY_LOCAL_PORT = "local_adb_port"
        private const val KEY_CF_CLIENT_ID = "cf_access_client_id"
        private const val KEY_CF_CLIENT_SECRET = "cf_access_client_secret"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
    }
}
