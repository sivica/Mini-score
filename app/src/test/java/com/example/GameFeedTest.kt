package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class GameFeedTest {
    private fun fixture() = File("../games.json").readText()

    @Test fun publishedFeedMatchesRecordedStats() {
        val feed = GameFeedCodec.decode(fixture())
        val actual = feed.games.single()
        val expected = PreseasonGameData.createGame()
        assertEquals(expected.id, feed.defaultGameId)
        assertEquals(expected.dateLabel, actual.dateLabel)
        assertEquals(expected.awayScore, actual.awayScore)
        assertEquals(expected.homeScore, actual.homeScore)
        assertEquals(expected.awayQuarters, actual.awayQuarters)
        assertEquals(expected.homeQuarters, actual.homeQuarters)
        val players = actual.startingAway + actual.startingHome + actual.substitutesAway + actual.substitutesHome
        val original = expected.startingAway + expected.startingHome + expected.substitutesAway + expected.substitutesHome
        assertEquals(original.map { it.stats }, players.map { it.stats })
        assertTrue(players.all { it.portraitUrl!!.startsWith("https://") })
        assertFalse(actual.isDemo)
    }

    @Test fun changingDefaultUpdatesDateAndMatchWithoutAppUpdate() {
        val root = JSONObject(fixture())
        val next = JSONObject(root.getJSONArray("games").getJSONObject(0).toString())
        next.put("id", "next-game").put("date", "2026-10-10").put("status", "SCHEDULED")
        next.getJSONObject("away").put("players", org.json.JSONArray()).put("quarters", org.json.JSONArray()).put("score", 0)
        next.getJSONObject("home").put("players", org.json.JSONArray()).put("quarters", org.json.JSONArray()).put("score", 0)
        root.getJSONArray("games").put(next)
        root.put("defaultGameId", "next-game")
        val model = GameViewModel()
        model.applyFeed(GameFeedCodec.decode(fixture()))
        model.applyFeed(GameFeedCodec.decode(root.toString()))
        assertEquals("next-game", model.selectedGameId.value)
        val game = model.games.single { it.id == "next-game" }
        assertEquals("Saturday, October 10, 2026", game.dateLabel)
        assertEquals("SCHEDULED", game.status)
        assertEquals(2, model.games.count { !it.isDemo })
        model.toggleSimulation(game.id)
        assertFalse(game.isSimulating)
        model.selectGame(PreseasonGameData.GAME_ID)
        model.applyFeed(GameFeedCodec.decode(root.toString()))
        assertEquals("Browsing a different game survives refresh", PreseasonGameData.GAME_ID, model.selectedGameId.value)
    }

    @Test fun invalidPublicationsAreRejected() {
        fun rejects(change: (JSONObject) -> Unit) {
            val root = JSONObject(fixture()).also(change)
            assertThrows(Exception::class.java) { GameFeedCodec.decode(root.toString()) }
        }
        rejects { it.put("schemaVersion", 2) }
        rejects { it.put("defaultGameId", "missing") }
        rejects { it.getJSONArray("games").getJSONObject(0).put("date", "2026-02-30") }
        rejects { it.getJSONArray("games").put(it.getJSONArray("games").getJSONObject(0)) }
        rejects { it.getJSONArray("games").getJSONObject(0).getJSONObject("away").put("score", -1) }
        rejects { it.getJSONArray("games").getJSONObject(0).getJSONObject("away").put("score", 2.5) }
        rejects { it.getJSONArray("games").getJSONObject(0).getJSONObject("away").getJSONArray("players").getJSONObject(0).put("portraitUrl", "http://example.com/photo.png") }
        rejects { it.getJSONArray("games").getJSONObject(0).getJSONObject("away").getJSONArray("players").getJSONObject(0).getJSONObject("stats").put("fgMade", 99) }
    }

    @Test fun failedRefreshPreservesLastValidatedCache() = runBlocking {
        val directory = java.nio.file.Files.createTempDirectory("miniscore-feed-test").toFile()
        try {
            val cache = File(directory, "games.json")
            val repository = GameFeedRepository(cache) { fixture() }
            repository.refresh()
            assertEquals(PreseasonGameData.GAME_ID, repository.cachedFeed()!!.defaultGameId)
            val saved = cache.readText()
            try { GameFeedRepository(cache) { "broken JSON" }.refresh(); fail("Invalid feed accepted") } catch (_: Exception) { }
            assertEquals(saved, cache.readText())
            try { GameFeedRepository(cache) { throw java.io.IOException("Offline") }.refresh(); fail("Offline succeeded") } catch (_: java.io.IOException) { }
            assertEquals(saved, cache.readText())
            cache.writeText("corrupt")
            assertNull(repository.cachedFeed())
        } finally { directory.deleteRecursively() }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test fun offlineStartupLoadsCacheAndReportsRefreshFailure() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val directory = java.nio.file.Files.createTempDirectory("miniscore-offline-test").toFile()
        try {
            val cache = File(directory, "games.json")
            val root = JSONObject(fixture()).apply {
                put("defaultGameId", "cached-game")
                getJSONArray("games").getJSONObject(0).put("id", "cached-game").put("date", "2026-10-10")
            }
            GameFeedRepository(cache) { root.toString() }.refresh()
            val model = GameViewModel()
            model.startFeed(GameFeedRepository(cache) { throw java.io.IOException("Offline") })
            model.isRefreshing.first { !it }
            assertEquals("cached-game", model.selectedGameId.value)
            assertEquals("Saturday, October 10, 2026", model.games.single { it.id == "cached-game" }.dateLabel)
            assertNotNull(model.feedMessage.value)
        } finally { directory.deleteRecursively(); Dispatchers.resetMain() }
    }
}
