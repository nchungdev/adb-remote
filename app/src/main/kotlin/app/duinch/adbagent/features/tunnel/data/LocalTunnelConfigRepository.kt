package app.duinch.adbagent.features.tunnel.data

import app.duinch.adbagent.features.tunnel.domain.SavedConnection
import android.content.Context
import android.os.Build
import app.duinch.adbagent.core.security.KeystoreCipher
import app.duinch.adbagent.features.tunnel.domain.TunnelConfig
import app.duinch.adbagent.features.tunnel.domain.TunnelConfigRepository
import app.duinch.adbagent.features.tunnel.domain.normalizedServerAddress

/** Connections live in the database, one row each; app-wide settings stay in preferences. */
internal class LocalTunnelConfigRepository(
    context: Context,
    private val connections: SavedConnectionStore
) : TunnelConfigRepository {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    init {
        migrateLegacyConnections()
    }

    override fun getConfig(): TunnelConfig {
        val defaultAlias = "${Build.MANUFACTURER}-${Build.MODEL}".replace(" ", "-")
        return TunnelConfig(
            serverUrl = primaryConnection()?.serverUrl.orEmpty(),
            deviceAlias = prefs.getString(KEY_DEVICE_ALIAS, defaultAlias) ?: defaultAlias,
            localAdbPort = prefs.getInt(KEY_LOCAL_PORT, 5555),
            runInBackground = prefs.getBoolean(KEY_RUN_IN_BACKGROUND, true),
            notifyConnectionChange = prefs.getBoolean(KEY_NOTIFY_CONNECTION_CHANGE, true),
            themeMode = prefs.getString(KEY_THEME_MODE, "system") ?: "system"
        )
    }

    override fun saveConfig(config: TunnelConfig) {
        connections.update(SavedConnection.PRIMARY_ID) { current ->
            config.serverUrl.takeIf { it.isNotBlank() }?.let { url ->
                current?.copy(serverUrl = url) ?: SavedConnection(SavedConnection.PRIMARY_ID, url)
            }
        }
        prefs.edit()
            .putString(KEY_DEVICE_ALIAS, config.deviceAlias)
            .putInt(KEY_LOCAL_PORT, config.localAdbPort)
            .putBoolean(KEY_RUN_IN_BACKGROUND, config.runInBackground)
            .putBoolean(KEY_NOTIFY_CONNECTION_CHANGE, config.notifyConnectionChange)
            .putString(KEY_THEME_MODE, config.themeMode)
            .apply()
    }

    override fun getConnections(): List<SavedConnection> = connections.getAll()

    override fun upsertConnection(connection: SavedConnection) {
        connections.update(connection.id) { connection }
    }

    override fun removeConnection(id: String) {
        connections.update(id) { null }
    }

    override fun reorderConnections(orderedIds: List<String>) {
        connections.reorder(orderedIds)
    }

    override fun wantsConnected(): Boolean = primaryConnection()?.wantsConnected == true

    override fun setWantsConnected(wanted: Boolean) {
        connections.update(SavedConnection.PRIMARY_ID) { current ->
            current?.copy(wantsConnected = wanted)
                ?: SavedConnection(SavedConnection.PRIMARY_ID, serverUrl = "", wantsConnected = wanted)
        }
    }

    private fun primaryConnection() = getConnections().find { it.id == SavedConnection.PRIMARY_ID }

    private fun migrateLegacyConnections() {
        val legacyKeys = prefs.all.keys.filter { it.startsWith(LEGACY_NAME_PREFIX) || it in LEGACY_KEYS }
        if (legacyKeys.isEmpty()) return
        val url = KeystoreCipher.decrypt(prefs.getString(LEGACY_KEY_SERVER_URL, "") ?: "")
        legacyPrimaryConnection(
            url = url,
            wanted = prefs.getBoolean(LEGACY_KEY_WANTS_CONNECTED, false),
            name = prefs.getString(LEGACY_NAME_PREFIX + normalizedServerAddress(url), null)
        )?.let(connections::importBlocking)
        prefs.edit().apply { legacyKeys.forEach(::remove) }.apply()
    }

    override fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_HAS_SEEN_ONBOARDING, false)

    override fun setHasSeenOnboarding(seen: Boolean) {
        prefs.edit().putBoolean(KEY_HAS_SEEN_ONBOARDING, seen).apply()
    }

    companion object {
        private const val PREFS_NAME = "adb_agent_tunnel_prefs"
        private const val LEGACY_KEY_SERVER_URL = "server_url"
        private const val LEGACY_KEY_WANTS_CONNECTED = "wants_connected"
        private const val LEGACY_NAME_PREFIX = "server_name_"
        private val LEGACY_KEYS = setOf(
            LEGACY_KEY_SERVER_URL,
            LEGACY_KEY_WANTS_CONNECTED,
            "saved_connection_slots",
            "connections"
        )
        private const val KEY_DEVICE_ALIAS = "device_alias"
        private const val KEY_LOCAL_PORT = "local_adb_port"
        private const val KEY_RUN_IN_BACKGROUND = "run_in_background"
        private const val KEY_NOTIFY_CONNECTION_CHANGE = "notify_connection_change"
        private const val KEY_THEME_MODE = "app_theme_mode"
        private const val KEY_HAS_SEEN_ONBOARDING = "has_seen_onboarding_guide"
    }
}
