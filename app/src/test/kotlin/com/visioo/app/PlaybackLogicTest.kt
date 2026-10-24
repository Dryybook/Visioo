package com.visioo.app

import com.visioo.app.data.Episode
import com.visioo.app.data.Season
import com.visioo.app.feature.player.FallbackPolicy
import com.visioo.app.feature.player.FallbackPolicy.Action
import com.visioo.app.feature.player.ResumePolicy
import com.visioo.app.feature.player.TrackLabels
import com.visioo.app.feature.player.formatTime
import com.visioo.app.feature.player.nextEpisodeId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlaybackLogicTest {
    @Test fun retriesOnceThenMovesToTheNextSourceThenGivesUp() {
        val p = FallbackPolicy(2)
        assertEquals(Action.RetrySame, p.onError(authOrExpired = false))
        assertEquals(Action.Next(1), p.onError(false))
        assertEquals(Action.RetrySame, p.onError(false))
        assertEquals(Action.GiveUp, p.onError(false))
    }

    @Test fun anExpiredOrForbiddenSourceTriggersOneRefreshOnly() {
        val p = FallbackPolicy(1)
        assertEquals(Action.Refresh, p.onError(authOrExpired = true))
        // A second auth error does not refresh again: it falls back to the normal retry then give up.
        assertEquals(Action.RetrySame, p.onError(true))
        assertEquals(Action.GiveUp, p.onError(true))
    }

    @Test fun afterARefreshTheNewPolicyNeverRefreshesAgain() {
        val next = FallbackPolicy(2).onRefreshed(2)
        assertEquals(Action.RetrySame, next.onError(true))
        assertEquals(Action.Next(1), next.onError(true))
    }

    @Test fun neverLoopsForever() {
        val p = FallbackPolicy(3)
        var steps = 0
        while (p.onError(false) != Action.GiveUp) {
            steps++
            check(steps < 20) { "fallback did not terminate" }
        }
        assertEquals(5, steps)
    }

    @Test fun resumeRules() {
        assertEquals(0L, ResumePolicy.startPosition(90_000, 6_000_000))
        assertEquals(0L, ResumePolicy.startPosition(120_000 - 1, 6_000_000))
        assertEquals(600_000L, ResumePolicy.startPosition(600_000, 6_000_000))
        assertEquals(0L, ResumePolicy.startPosition(5_800_000, 6_000_000))
        assertEquals(600_000L, ResumePolicy.startPosition(600_000, 0))
    }

    @Test fun completedAbove95Percent() {
        assertEquals(true, ResumePolicy.isCompleted(951, 1000))
        assertEquals(false, ResumePolicy.isCompleted(950, 1000))
        assertEquals(false, ResumePolicy.isCompleted(10, 0))
    }

    private fun ep(id: String, n: Int) = Episode(id, n, "E$n", null, null, null)
    private val seasons = listOf(
        Season("s2", 2, null, listOf(ep("c", 1))),
        Season("s1", 1, null, listOf(ep("b", 2), ep("a", 1))),
    )

    @Test fun nextEpisodeFollowsSeasonOrderAndRollsOver() {
        assertEquals("b", nextEpisodeId(seasons, "a"))
        assertEquals("c", nextEpisodeId(seasons, "b"))
        assertNull(nextEpisodeId(seasons, "c"))
        assertNull(nextEpisodeId(seasons, "unknown"))
    }

    @Test fun audioLabelsAreHumanFriendly() {
        assertEquals("Français (VF)", TrackLabels.audio("fr-FR", null))
        assertEquals("English (VO)", TrackLabels.audio("en", "Stereo"))
        assertEquals("Commentary", TrackLabels.audio("und", "Commentary"))
        assertEquals("Audio", TrackLabels.audio(null, null))
        assertEquals("Français", TrackLabels.subtitle("fr", "Français"))
    }

    @Test fun formatsTime() {
        assertEquals("0:05", formatTime(5_000))
        assertEquals("28:15", formatTime(28 * 60_000L + 15_000))
        assertEquals("1:02:03", formatTime(3_723_000))
        assertEquals("0:00", formatTime(-5))
    }
}
