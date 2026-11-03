package app.duinch.adbagent.features.tunnel.data

import android.content.Context
import app.duinch.adbagent.features.tunnel.domain.DeviceSecretProvider
import java.security.SecureRandom

class SharedPrefsDeviceSecretProvider(context: Context) : DeviceSecretProvider {
    private val prefs = context.applicationContext.getSharedPreferences("server_pairing", Context.MODE_PRIVATE)

    @Synchronized
    override fun deviceSecret(): String {
        prefs.getString(KEY_SECRET, null)?.let { return it }
        val bytes = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val secret = bytes.joinToString("") { "%02x".format(it) }
        prefs.edit().putString(KEY_SECRET, secret).apply()
        return secret
    }

    private companion object {
        const val KEY_SECRET = "device_secret"
    }
}
