package app.duinch.adbagent.core.designsystem

enum class AppThemeMode(val storageValue: String) {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark");

    companion object {
        fun fromValue(value: String): AppThemeMode =
            entries.firstOrNull { it.storageValue.equals(value, ignoreCase = true) } ?: SYSTEM
    }
}
