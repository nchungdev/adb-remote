package app.duinch.adbagent.core.model

enum class NetworkType(val label: String) {
    WIFI("Wi-Fi"),
    CELLULAR("4G / 5G"),
    ETHERNET("Ethernet"),
    NONE("Ngoại tuyến"),
    UNKNOWN("Mạng khác")
}
