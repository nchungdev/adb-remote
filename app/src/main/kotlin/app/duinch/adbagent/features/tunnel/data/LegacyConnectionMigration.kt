package app.duinch.adbagent.features.tunnel.data

import app.duinch.adbagent.features.tunnel.domain.SavedConnection

/** Released versions kept only one connection, as separate preference keys. */
internal fun legacyPrimaryConnection(url: String, wanted: Boolean, name: String?): SavedConnection? =
    url.takeIf { it.isNotBlank() }?.let { SavedConnection(SavedConnection.PRIMARY_ID, it, wanted, name) }
