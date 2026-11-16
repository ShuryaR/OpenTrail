package com.example.util

/** Phone-brand specific steps for keeping call capture alive; each OEM hides these in a different place. */
data class OemGuide(
    val brand: String,
    val steps: List<String>,
    val suggestsFolderWatcher: Boolean = false
) {
    companion object {
        fun forManufacturer(manufacturer: String): OemGuide {
            val m = manufacturer.trim().lowercase()
            return when {
                m in setOf("oneplus", "oppo", "realme") -> OemGuide(
                    brand = "OnePlus / OPPO / Realme",
                    steps = listOf(
                        "Open App info for OneBook Dialer, then Battery usage, and choose Unrestricted.",
                        "In Settings, search for Auto launch and switch OneBook Dialer on.",
                        "In Recent apps, long-press the OneBook Dialer card and tap Lock so it is not cleaned up.",
                        "If the other side is silent in recordings, set Audio Capture Source to VOICE_COMMUNICATION or MIC."
                    )
                )
                m == "samsung" -> OemGuide(
                    brand = "Samsung",
                    steps = listOf(
                        "Settings, Battery, Background usage limits: add OneBook Dialer to Never sleeping apps.",
                        "If the Samsung dialer records calls itself, turn on the OEM Recording Folder Watcher in app settings.",
                        "Set Audio Capture Source to VOICE_COMMUNICATION if the other side is silent."
                    ),
                    suggestsFolderWatcher = true
                )
                m in setOf("xiaomi", "redmi", "poco") -> OemGuide(
                    brand = "Xiaomi / Redmi / POCO",
                    steps = listOf(
                        "Security app, Permissions, Autostart: switch OneBook Dialer on.",
                        "App info, Battery saver: choose No restrictions.",
                        "In Recent apps, pull the OneBook Dialer card down and tap the lock icon.",
                        "If Xiaomi's own call recorder is on, enable the OEM Recording Folder Watcher in app settings."
                    ),
                    suggestsFolderWatcher = true
                )
                else -> OemGuide(
                    brand = manufacturer.trim().ifBlank { "your phone" },
                    steps = listOf(
                        "Set Battery usage for OneBook Dialer to Unrestricted in App info.",
                        "If your phone has an Autostart or Auto launch list, switch OneBook Dialer on.",
                        "Lock the app in Recent apps if your phone offers it."
                    )
                )
            }
        }
    }
}
