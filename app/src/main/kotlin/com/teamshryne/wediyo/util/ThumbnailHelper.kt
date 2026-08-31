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
            if (u.isEmpty()) null else Thumb(normalizeUrl(u), o.optInt("width", 0), o.optInt("height", 0))
        }
    } catch (_: Exception) { emptyList() }
}

fun normalizeUrl(u: String): String = if (u.startsWith("//")) "https:$u" else u

fun pickThumb(json: String, quality: String): String {
    val list = parseThumbs(json)
    if (list.isEmpty()) return ""
    // Deduplicate and sort by width descending for deterministic fallback
    val sorted = list.sortedByDescending { it.width }
    val url = when (quality.lowercase()) {
        "360p" -> list.minByOrNull { kotlin.math.abs(it.width - 480) }?.url ?: sorted.lastOrNull()?.url ?: sorted.first().url
        "480p" -> list.minByOrNull { kotlin.math.abs(it.width - 640) }?.url ?: sorted.getOrNull(1)?.url ?: sorted.lastOrNull()?.url ?: sorted.first().url
        "720p" -> {
            // 720p ≈ 1280x720 (maxres) or 640x480 scaled; pick closest to 854 or second-max to differ from high
            if (sorted.size == 1) sorted.first().url
            else {
                // Try closest to 1280, but if high also picks max, make 720p pick second distinct width
                val byWidth720 = list.minByOrNull { kotlin.math.abs(it.width - 854) }?.url
                val maxUrl = sorted.first().url
                if (byWidth720 != null && byWidth720 != maxUrl) byWidth720 else sorted.getOrNull(1)?.url ?: sorted.first().url
            }
        }
        "1080p" -> list.maxByOrNull { it.width }?.url ?: sorted.first().url
        "low" -> list.minByOrNull { it.width }?.url ?: sorted.last().url
        "high" -> list.maxByOrNull { it.width }?.url ?: sorted.first().url
        "auto" -> list.maxByOrNull { it.width }?.url ?: sorted.first().url
        else -> list.maxByOrNull { it.width }?.url ?: sorted.first().url
    }
    return normalizeUrl(url)
}

fun bestThumbUrl(thumbsJson: String, fallback: String, quality: String): String {
    val picked = pickThumb(thumbsJson, quality)
    if (picked.isNotEmpty()) return picked
    return normalizeUrl(fallback)
}
