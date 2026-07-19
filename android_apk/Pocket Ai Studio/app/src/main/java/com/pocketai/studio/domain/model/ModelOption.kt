package com.pocketai.studio.domain.model

/**
 * A unified selectable chat target — either a cloud model (served by a provider's API)
 * or a locally-on-device GGUF model.
 *
 * `key` is the stable identifier used in the UI state and persistence:
 *   - cloud: "cloud/<providerId>/<modelId>"
 *   - local: "local/<model.name>"
 */
sealed class ModelOption {
    abstract val key: String
    abstract val displayName: String
    abstract val subtitle: String

    data class Cloud(
        val providerId: String,
        val providerName: String,
        val modelId: String,
        val modelName: String,
        val isVision: Boolean = false,
        val description: String = ""
    ) : ModelOption() {
        override val key: String = "cloud/$providerId/$modelId"
        override val displayName: String = modelName
        override val subtitle: String = "$providerName · cloud"
    }

    data class Local(
        val name: String,
        val filePath: String?
    ) : ModelOption() {
        override val key: String = "local/$name"
        override val displayName: String = name
        override val subtitle: String = "On-device"
    }

    companion object {
        fun fromKey(key: String): Pair<String, String>? {
            // returns (providerId or "local", modelId) — used to disambiguate on load
            val parts = key.split("/", limit = 3)
            if (parts.size < 2) return null
            return when (parts[0]) {
                "cloud" -> if (parts.size == 3) "cloud" to "${parts[1]}/${parts[2]}" else null
                "local" -> "local" to parts[1]
                else -> null
            }
        }
    }
}
