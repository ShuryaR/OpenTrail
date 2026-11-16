package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FrappeMessageResponse(
    @Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class FrappeListEnvelope(
    @Json(name = "data") val data: List<Map<String, Any?>> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FrappeDocEnvelope(
    @Json(name = "data") val data: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class FrappeUploadEnvelope(
    @Json(name = "message") val message: FrappeUploadedFile? = null
)

@JsonClass(generateAdapter = true)
data class FrappeUploadedFile(
    @Json(name = "name") val name: String? = null,
    @Json(name = "file_name") val fileName: String? = null,
    @Json(name = "file_url") val fileUrl: String? = null,
    @Json(name = "is_private") val isPrivate: Int? = null
)

@JsonClass(generateAdapter = true)
data class FrappeLoginRequest(
    @Json(name = "usr") val usr: String,
    @Json(name = "pwd") val pwd: String
)

fun Map<String, Any?>.str(vararg keys: String): String {
    for (k in keys) {
        val v = this[k]?.toString()?.trim()
        if (!v.isNullOrBlank() && v != "null") return v
    }
    return ""
}

fun Map<String, Any?>.dbl(vararg keys: String): Double {
    for (k in keys) {
        val raw = this[k] ?: continue
        when (raw) {
            is Number -> return raw.toDouble()
            is String -> raw.toDoubleOrNull()?.let { return it }
        }
    }
    return 0.0
}

fun Map<String, Any?>.integer(vararg keys: String): Int {
    for (k in keys) {
        val raw = this[k] ?: continue
        when (raw) {
            is Number -> return raw.toInt()
            is String -> raw.toDoubleOrNull()?.toInt()?.let { return it }
        }
    }
    return 0
}
