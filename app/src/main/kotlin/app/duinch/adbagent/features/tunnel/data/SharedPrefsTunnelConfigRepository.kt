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
            autoReconnectOnNetworkChange = prefs.getBoolean(KEY_AUTO_RECONNECT, true),
            trustedHomeSsid = prefs.getString(KEY_TRUSTED_HOME_SSID, "") ?: "",
            autoSwitchOnHomeWifi = prefs.getBoolean(KEY_AUTO_SWITCH_HOME_WIFI, true),
            runInBackground = prefs.getBoolean(KEY_RUN_IN_BACKGROUND, true),
            remoteRoomCode = prefs.getString(KEY_REMOTE_ROOM_CODE, "") ?: ""
        )
    }

    override fun saveConfig(config: TunnelConfig) {
        prefs.edit()
            .putString(KEY_SERVER_URL, config.serverUrl)
            .putString(KEY_DEVICE_ALIAS, config.deviceAlias)
            .putInt(KEY_LOCAL_PORT, config.localAdbPort)
            .putBoolean(KEY_AUTO_RECONNECT, config.autoReconnectOnNetworkChange)
            .putString(KEY_TRUSTED_HOME_SSID, config.trustedHomeSsid)
            .putBoolean(KEY_AUTO_SWITCH_HOME_WIFI, config.autoSwitchOnHomeWifi)
            .putBoolean(KEY_RUN_IN_BACKGROUND, config.runInBackground)
            .putString(KEY_REMOTE_ROOM_CODE, config.remoteRoomCode)
            .apply()
    }

    companion object {
        private const val PREFS_NAME = "adb_agent_tunnel_prefs"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_DEVICE_ALIAS = "device_alias"
        private const val KEY_LOCAL_PORT = "local_adb_port"
        private const val KEY_AUTO_RECONNECT = "auto_reconnect"
        private const val KEY_TRUSTED_HOME_SSID = "trusted_home_ssid"
        private const val KEY_AUTO_SWITCH_HOME_WIFI = "auto_switch_home_wifi"
        private const val KEY_RUN_IN_BACKGROUND = "run_in_background"
        private const val KEY_REMOTE_ROOM_CODE = "remote_room_code"
    }
}
