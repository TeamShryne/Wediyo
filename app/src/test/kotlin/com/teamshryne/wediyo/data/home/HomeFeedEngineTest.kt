package com.teamshryne.wediyo.data.home

import com.teamshryne.wediyo.data.model.UiVideo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure-JVM regression tests for the home feed ranking.
 * Run in CI via `./gradlew testDebugUnitTest` (android.yml).
 */
class HomeFeedEngineTest {

    private fun vid(
        id: String,
        channel: String = "c-$id",
        author: String = "author",
        published: String = "2 days ago"
    ) = UiVideo(
        id = id,
        title = "title $id",
        author = author,
        channelId = channel,
        thumbnailUrl = "",
        thumbnailsJson = "[]",
        avatarUrl = "",
        avatarsJson = "[]",
        viewCountText = "1K views",
        publishedText = published,
        durationText = "10:00",
        isLive = false,
        badges = emptyList(),
        description = ""
    )

    @Test
    fun emptyInputs_emptyFeed() {
        val (videos, sources) = HomeFeedEngine.rankAndMix()
        assertTrue(videos.isEmpty())
        assertTrue(sources.isEmpty())
    }

    @Test
    fun dedupe_subsWinsOverQueue() {
        val subs = listOf(vid("v1", channel = "sub1"))
        val queue = listOf(vid("v1", channel = "sub1"), vid("v2", channel = "q1"))
        val (videos, sources) = HomeFeedEngine.rankAndMix(
            subs = subs, queue = queue, subIds = setOf("sub1"), seed = 1L
        )
        assertEquals(listOf("v1", "v2").sorted(), videos.map { it.id }.sorted())
        assertEquals(FeedSource.SUBS, sources["v1"])
    }

    @Test
    fun watchedSinksBelowFresh() {
        val queue = listOf(vid("watched"), vid("fresh"))
        val (videos, _) = HomeFeedEngine.rankAndMix(
            queue = queue, watchedIds = setOf("watched"), seed = 42L
        )
        assertEquals("fresh", videos.first().id)
        assertEquals("watched", videos.last().id)
    }

    @Test
    fun subbedChannelRanksFirst() {
        val subs = listOf(vid("s1", channel = "sub1"))
        val search = listOf(vid("x1", channel = "other"))
        val (videos, _) = HomeFeedEngine.rankAndMix(
            subs = subs, search = search, subIds = setOf("sub1"), seed = 7L
        )
        assertEquals("s1", videos.first().id)
    }

    @Test
    fun sameSeed_isDeterministic() {
        val queue = (1..12).map { vid("v$it", channel = "c$it") }
        val first = HomeFeedEngine.rankAndMix(queue = queue, seed = 123L).first.map { it.id }
        val second = HomeFeedEngine.rankAndMix(queue = queue, seed = 123L).first.map { it.id }
        assertEquals(first, second)
    }

    @Test
    fun diversityCap_limitsSingleChannelUpFront() {
        val same = (1..6).map { vid("s$it", channel = "spam") }
        val other = (1..6).map { vid("o$it", channel = "c$it") }
        val (videos, _) = HomeFeedEngine.rankAndMix(
            subs = same, queue = other, seed = 9L
        )
        assertTrue(videos.take(15).count { it.channelId == "spam" } <= 2)
        // Nothing dropped entirely — extras sink to the tail.
        assertEquals(12, videos.size)
    }

    @Test
    fun interleave_isRoundRobin() {
        val out = HomeFeedEngine.interleave(
            listOf(listOf(1, 2, 3), listOf(10, 20))
        )
        assertEquals(listOf(1, 10, 2, 20, 3), out)
    }
}
