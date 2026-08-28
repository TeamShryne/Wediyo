package com.teamshryne.wediyo.util

import android.util.Base64

object FilterParamsBuilder {
    fun build(
        type: String,
        duration: String,
        uploadDate: String,
        features: List<String>,
        prioritize: String
    ): String {
        val inner = mutableListOf<ByteArray>()
        when (uploadDate) {
            "Today" -> inner.add(byteArrayOf(0x08, 0x02))
            "This week" -> inner.add(byteArrayOf(0x08, 0x03))
            "This month" -> inner.add(byteArrayOf(0x08, 0x04))
            "This year" -> inner.add(byteArrayOf(0x08, 0x05))
        }
        when (type) {
            "Videos" -> inner.add(byteArrayOf(0x10, 0x01))
            "Channels" -> inner.add(byteArrayOf(0x10, 0x02))
            "Playlists" -> inner.add(byteArrayOf(0x10, 0x03))
            "Movies" -> inner.add(byteArrayOf(0x10, 0x04))
            "Shorts" -> inner.add(byteArrayOf(0x10, 0x09))
        }
        when (duration) {
            "Under 3 minutes" -> inner.add(byteArrayOf(0x18, 0x04))
            "3 - 20 minutes" -> inner.add(byteArrayOf(0x18, 0x05))
            "Over 20 minutes" -> inner.add(byteArrayOf(0x18, 0x02))
        }
        val featMap = mapOf(
            "HD" to byteArrayOf(0x20, 0x01),
            "Subtitles/CC" to byteArrayOf(0x28, 0x01),
            "Subtitles" to byteArrayOf(0x28, 0x01),
            "Creative Commons" to byteArrayOf(0x30, 0x01),
            "3D" to byteArrayOf(0x38, 0x01),
            "Live" to byteArrayOf(0x40, 0x01),
            "Purchased" to byteArrayOf(0x48, 0x01),
            "4K" to byteArrayOf(0x70, 0x01),
            "360°" to byteArrayOf(0x78, 0x01),
            "HDR" to byteArrayOf(0xC8.toByte(), 0x01, 0x01),
            "VR180" to byteArrayOf(0xD0.toByte(), 0x01, 0x01),
            "Location" to byteArrayOf(0xB8.toByte(), 0x01, 0x01)
        )
        val sortedFeats = features.sorted()
        for (f in sortedFeats) {
            featMap[f]?.let { inner.add(it) }
        }
        inner.sortWith(compareBy<ByteArray>({ it[0].toInt() and 0xFF }, { if (it.size > 1) it[1].toInt() and 0xFF else 0 }, { it.size }))
        val outer = mutableListOf<Byte>()
        if (prioritize.equals("Popularity", true) || prioritize.equals("Popular", true)) {
            outer.add(0x08); outer.add(0x03)
        }
        if (inner.isNotEmpty()) {
            val flat = inner.flatten()
            outer.add(0x12)
            outer.add(flat.size.toByte())
            outer.addAll(flat)
        }
        if (outer.isEmpty()) return ""
        return Base64.encodeToString(outer.toByteArray(), Base64.NO_WRAP)
    }

    private fun List<ByteArray>.flatten(): List<Byte> = flatMap { it.toList() }
}
