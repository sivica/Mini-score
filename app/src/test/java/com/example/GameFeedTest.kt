package com.example

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.json.JSONObject
import org.json.JSONArray
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

    @Test fun pickerShowsTipoffAndGameDateWithoutPhoneTimezoneDrift() {
        val root = JSONObject(fixture())
        root.getJSONArray("games").getJSONObject(0).put("status", "SCHEDULED").put("tipoffUTC", "2026-10-10T00:00:00Z")
        val game = GameFeedCodec.decode(root.toString()).games.single()
        assertEquals("8:00 PM ET", pickerStatus(game))
        assertEquals("Thu, Oct 8", pickerDate(game, compact = true))
        assertEquals("Thursday, October 8", pickerDate(game))
    }

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

    @Test fun invalidIntegersAndQuarterArraysCannotReplaceSavedGames() = runBlocking {
        val valid = fixture()
        val invalid = mutableListOf<Pair<String, String>>()
        for (value in listOf("1.5", "1.0", "1e0", "\"1\"", "true", "null", "4294967297")) {
            invalid += "schemaVersion=$value" to valid.replaceFirst(
                Regex("\"schemaVersion\"\\s*:\\s*1"), "\"schemaVersion\":$value"
            )
        }
        for (side in listOf("away", "home")) {
            for (value in listOf(2.5, "2", true, JSONObject.NULL, -1, 101, 4294967298L)) {
                val root = JSONObject(valid)
                root.getJSONArray("games").getJSONObject(0).getJSONObject(side)
                    .getJSONArray("quarters").put(0, value)
                invalid += "$side quarter=$value" to root.toString()
            }
        }
        // Change both teams so the old parser cannot reject these merely for mismatched period counts.
        for (value in listOf("[]", JSONObject(), 4, true, JSONObject.NULL)) {
            val root = JSONObject(valid)
            val game = root.getJSONArray("games").getJSONObject(0)
            game.getJSONObject("away").put("quarters", value)
            game.getJSONObject("home").put("quarters", value)
            invalid += "quarters container=$value" to root.toString()
        }
        val decimalQuarter = JSONObject(valid)
        decimalQuarter.getJSONArray("games").getJSONObject(0).getJSONObject("away")
            .getJSONArray("quarters").put(0, "DECIMAL_QUARTER")
        invalid += "whole-number decimal quarter" to decimalQuarter.toString().replace("\"DECIMAL_QUARTER\"", "2.0")

        val directory = java.nio.file.Files.createTempDirectory("miniscore-strict-feed-test").toFile()
        try {
            val cache = File(directory, "games.json")
            val expected = GameFeedRepository(cache) { valid }.refresh()
            val saved = cache.readBytes()
            for ((description, publication) in invalid) {
                var rejected = false
                try { GameFeedRepository(cache) { publication }.refresh() }
                catch (_: Exception) { rejected = true }
                assertTrue("Reject $description", rejected)
                assertArrayEquals("Keep exact cache bytes after $description", saved, cache.readBytes())
                assertEquals("Reload saved games after $description", expected, GameFeedRepository(cache).cachedFeed())
            }
        } finally { directory.deleteRecursively() }
    }

    @Test fun omittedAndEmptyQuarterArraysRemainValid() = runBlocking {
        for (omit in listOf(true, false)) {
            val root = JSONObject(fixture())
            val game = root.getJSONArray("games").getJSONObject(0)
            for (side in listOf("away", "home")) {
                if (omit) game.getJSONObject(side).remove("quarters")
                else game.getJSONObject(side).put("quarters", JSONArray())
            }
            val directory = java.nio.file.Files.createTempDirectory("miniscore-empty-quarters-test").toFile()
            try {
                val repository = GameFeedRepository(File(directory, "games.json")) { root.toString() }
                val feed = repository.refresh()
                assertTrue(feed.games.single().awayQuarters.isEmpty())
                assertTrue(feed.games.single().homeQuarters.isEmpty())
                assertEquals(feed, repository.cachedFeed())
            } finally { directory.deleteRecursively() }
        }
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
