package com.example.data.remote

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class FrappeMessageResponse(
    @param:Json(name = "message") val message: String? = null
)

@JsonClass(generateAdapter = true)
data class FrappeListEnvelope(
    @param:Json(name = "data") val data: List<Map<String, Any?>> = emptyList()
)

@JsonClass(generateAdapter = true)
data class FrappeDocEnvelope(
    @param:Json(name = "data") val data: Map<String, Any?> = emptyMap()
)

@JsonClass(generateAdapter = true)
data class FrappeUploadEnvelope(
    @param:Json(name = "message") val message: FrappeUploadedFile? = null
)

@JsonClass(generateAdapter = true)
data class FrappeUploadedFile(
    @param:Json(name = "name") val name: String? = null,
    @param:Json(name = "file_name") val fileName: String? = null,
    @param:Json(name = "file_url") val fileUrl: String? = null,
    @param:Json(name = "is_private") val isPrivate: Int? = null
)

@JsonClass(generateAdapter = true)
data class FrappeLoginRequest(
    @param:Json(name = "usr") val usr: String,
    @param:Json(name = "pwd") val pwd: String
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

private val errorMoshi: com.squareup.moshi.Moshi by lazy {
    com.squareup.moshi.Moshi.Builder().build()
}

fun extractFrappeError(httpCode: Int, errorBody: String?): String {
    if (errorBody.isNullOrBlank()) return "HTTP $httpCode"
    return runCatching {
        val mapAdapter = errorMoshi.adapter<Map<String, Any?>>(
            com.squareup.moshi.Types.newParameterizedType(
                Map::class.java,
                String::class.java,
                Any::class.java
            )
        )
        val json = mapAdapter.fromJson(errorBody) ?: return@runCatching "HTTP $httpCode: ${errorBody.take(160)}"
        val serverMsgs = json["_server_messages"]?.toString()
        if (!serverMsgs.isNullOrBlank()) {
            val listAdapter = errorMoshi.adapter<List<Any?>>(
                com.squareup.moshi.Types.newParameterizedType(List::class.java, Any::class.java)
            )
            val arr = listAdapter.fromJson(serverMsgs)
            val first = arr?.firstOrNull()?.toString()
            if (!first.isNullOrBlank()) {
                val innerMap = runCatching { mapAdapter.fromJson(first) }.getOrNull()
                val innerMsg = innerMap?.get("message")?.toString()
                if (!innerMsg.isNullOrBlank()) {
                    val clean = innerMsg.replace(Regex("<[^>]*>"), "").trim()
                    if (clean.isNotBlank()) return "HTTP $httpCode: $clean"
                }
            }
        }
        val excType = json["exc_type"]?.toString()?.trim().orEmpty()
        val msg = json["message"]?.toString()?.trim().orEmpty()
        val exception = json["exception"]?.toString()?.trim().orEmpty()
        val detail = listOf(excType, msg, exception).firstOrNull { it.isNotBlank() && it != "null" }
        if (!detail.isNullOrBlank()) "HTTP $httpCode: ${detail.take(180)}" else "HTTP $httpCode"
    }.getOrElse {
        "HTTP $httpCode: ${errorBody.take(160)}"
    }
}
