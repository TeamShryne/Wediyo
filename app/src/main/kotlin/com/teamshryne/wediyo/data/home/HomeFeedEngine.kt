package com.teamshryne.wediyo.data.home

import com.teamshryne.wediyo.data.model.UiVideo
import kotlin.random.Random

/** Source tag for ranking + local chip filtering. */
enum class FeedSource { SUBS, QUEUE, SEARCH, HISTORY }

/**
 * Pure ranking/merging for the home feed. No Android, no network —
 * trivially testable and deterministic per [seed] so every
 * start / reload orders differently.
 */
object HomeFeedEngine {

    data class Scored(val video: UiVideo, val source: FeedSource, val score: Double)

    fun rankAndMix(
        subs: List<UiVideo> = emptyList(),
        queue: List<UiVideo> = emptyList(),
        search: List<UiVideo> = emptyList(),
        historyFallback: List<UiVideo> = emptyList(),
        subIds: Set<String> = emptySet(),
        topChannelIds: Set<String> = emptySet(),
        watchedIds: Set<String> = emptySet(),
        seed: Long = System.currentTimeMillis(),
    ): Pair<List<UiVideo>, Map<String, FeedSource>> {
        // Dedupe by id, first occurrence wins (SUBS > QUEUE > SEARCH > HISTORY).
        val ordered = LinkedHashMap<String, Scored>()
        fun put(list: List<UiVideo>, src: FeedSource) {
            for (v in list) {
                if (v.id.isBlank() || ordered.containsKey(v.id)) continue
                ordered[v.id] = Scored(v, src, score(v, src, subIds, topChannelIds, watchedIds))
            }
        }
        put(subs, FeedSource.SUBS)
        put(queue, FeedSource.QUEUE)
        put(search, FeedSource.SEARCH)
        put(historyFallback, FeedSource.HISTORY)

        val sorted = ordered.values.sortedByDescending { it.score }

        // Watched sinks to the bottom (not hidden — cold users still see something).
        val (fresh, rewatch) = sorted.partition { it.video.id !in watchedIds }

        // Window-shuffle so order differs every launch but mix stays balanced.
        val rnd = Random(seed)
        val mixed = windowShuffle(fresh, 6, rnd) + windowShuffle(rewatch, 6, rnd)

        // Diversity cap: max 2 per channel in the first 15 slots, extras sink.
        val head = ArrayList<Scored>()
        val tail = ArrayList<Scored>()
        val perChannel = HashMap<String, Int>()
        for (s in mixed) {
            val key = s.video.channelId.ifBlank { s.video.author }
            val c = perChannel.getOrDefault(key, 0)
            if (head.size < 15 && c >= 2) tail.add(s) else {
                head.add(s)
                perChannel[key] = c + 1
            }
        }
        val final = head + tail
        return final.map { it.video } to final.associate { it.video.id to it.source }
    }

    private fun score(
        v: UiVideo,
        src: FeedSource,
        subIds: Set<String>,
        topIds: Set<String>,
        watched: Set<String>,
    ): Double {
        var s = when (src) {
            FeedSource.SUBS -> 2.0
            FeedSource.QUEUE -> 1.2
            FeedSource.SEARCH -> 0.6
            FeedSource.HISTORY -> 0.1
        }
        if (v.channelId in subIds) s += 2.0
        if (v.channelId in topIds) s += 1.0
        if (v.id in watched) s -= 3.0
        if (v.isLive) s += 0.3
        // Freshness hint from YouTube text ("hour", "day" > "year").
        val pub = v.publishedText.lowercase()
        when {
            "hour" in pub || "minute" in pub || "day" in pub -> s += 0.4
            "week" in pub -> s += 0.2
            "year" in pub -> s -= 0.3
        }
        if (v.title.isBlank()) s -= 5.0
        return s
    }

    private fun windowShuffle(list: List<Scored>, window: Int, rnd: Random): List<Scored> {
        if (list.size <= 1) return list
        val out = ArrayList<Scored>(list.size)
        var i = 0
        while (i < list.size) {
            val end = minOf(i + window, list.size)
            out.addAll(list.subList(i, end).shuffled(rnd))
            i = end
        }
        return out
    }

    /** Round-robin interleave: each source list is newest-first, turns approximate recency mix. */
    fun <T> interleave(lists: List<List<T>>, perListTake: Int = Int.MAX_VALUE): List<T> {
        if (lists.isEmpty()) return emptyList()
        val capped = lists.map { it.take(perListTake) }
        val out = ArrayList<T>(capped.sumOf { it.size })
        var idx = 0
        var added: Boolean
        do {
            added = false
            for (l in capped) {
                if (idx < l.size) {
                    out.add(l[idx])
                    added = true
                }
            }
            idx++
        } while (added)
        return out
    }
}
