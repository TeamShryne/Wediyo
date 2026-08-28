package com.teamshryne.wediyo.util

import org.json.JSONArray

data class Thumb(val url: String, val width: Int, val height: Int)

fun parseThumbs(json: String): List<Thumb> {
    if (json.isBlank() || json == "[]") return emptyList()
    return try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val u = o.optString("url", "")
            if (u.isEmpty()) null else Thumb(u, o.optInt("width", 0), o.optInt("height", 0))
        }
    } catch (_: Exception) { emptyList() }
}

fun pickThumb(json: String, quality: String): String {
    val list = parseThumbs(json)
    if (list.isEmpty()) return ""
    return when (quality) {
        "360p" -> list.minByOrNull { kotlin.math.abs(it.width - 480) }?.url ?: list.first().url
        "720p" -> list.maxByOrNull { it.width }?.url ?: list.last().url
        "low" -> list.minByOrNull { it.width }?.url ?: list.first().url
        "high" -> list.maxByOrNull { it.width }?.url ?: list.last().url
        else -> list.maxByOrNull { it.width }?.url ?: list.last().url
    }
}

fun bestThumbUrl(thumbsJson: String, fallback: String, quality: String): String {
    val picked = pickThumb(thumbsJson, quality)
    return if (picked.isNotEmpty()) picked else fallback
}
