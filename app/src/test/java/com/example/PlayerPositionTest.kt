package com.example

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class PlayerPositionTest {
    private fun summary(): JSONObject {
        val root = JSONObject(File("../scripts/tests/fixtures/boxscore.json").readText())
        root.getJSONObject("boxscore").getJSONArray("players").getJSONObject(0)
            .getJSONArray("statistics").getJSONObject(0).getJSONArray("athletes").getJSONObject(0)
            .getJSONObject("athlete").put("position", JSONObject().put("abbreviation", "SF"))
        return root
    }

    @Test fun metadataIsCachedByGameAndRestoredOffline() = runBlocking {
        val dir = createTempDirectory("positions-").toFile()
        try {
            val cache = File(dir, "positions.json")
            var requests = 0
            val repository = PlayerPositionRepository(cache) { requests++; summary().toString() }
            val first = repository.forGame("espn_401898395")
            assertTrue(first.values.contains("SF"))
            assertEquals(first, repository.forGame("espn_401898395"))
            assertEquals(1, requests)
            val offline = PlayerPositionRepository(cache) { error("Offline") }
            assertEquals(first, offline.forGame("espn_401898395"))
        } finally { dir.deleteRecursively() }
    }

    @Test fun rejectedMetadataPreservesExistingCacheAndUnknownIdsMakeNoRequest() = runBlocking {
        val dir = createTempDirectory("positions-").toFile()
        try {
            val cache = File(dir, "positions.json")
            PlayerPositionRepository(cache) { summary().toString() }.forGame("espn_401898395")
            val original = cache.readBytes()
            var requests = 0
            val repository = PlayerPositionRepository(cache) { requests++; summary().toString() }
            assertTrue(repository.forGame("legacy-id").isEmpty())
            assertEquals(0, requests)
            try { repository.forGame("espn_999"); fail("Wrong summary should be rejected") }
            catch (_: IllegalArgumentException) { }
            assertArrayEquals(original, cache.readBytes())
        } finally { dir.deleteRecursively() }
    }

    @Test fun positionsFollowPlayerIdsAndCannotReplaceStatsOrFeedPositions() {
        val game = PreseasonGameData.createGame()
        val player = game.startingAway.first()
        val enriched = game.withPlayerPositions(mapOf(player.id to "SF", "not-in-game" to "C"))
        assertEquals("SF", enriched.startingAway.first().position)
        assertEquals(game.awayScore, enriched.awayScore)
        assertEquals(game.startingAway.map { it.stats }, enriched.startingAway.map { it.stats })
        assertEquals(game.startingAway.map { it.id }, enriched.startingAway.map { it.id })
        assertEquals("PG", enriched.copy(startingAway = listOf(player.copy(position = "PG")))
            .withPlayerPositions(mapOf(player.id to "C")).startingAway.first().position)
    }

    @Test fun invalidOrMissingProviderPositionsAreNotInvented() {
        val root = summary()
        val athlete = root.getJSONObject("boxscore").getJSONArray("players").getJSONObject(0)
            .getJSONArray("statistics").getJSONObject(0).getJSONArray("athletes").getJSONObject(0)
            .getJSONObject("athlete")
        val id = "espn_" + athlete.getString("id")
        athlete.put("position", JSONObject().put("abbreviation", "Not known"))
        assertNull(PlayerPositionRepository.decode(root.toString(), "401898395")[id])
        for (position in listOf("PG", "SG", "SF", "PF", "C", "G", "F", "G-F", "F-C")) assertTrue(isPlayerPosition(position))
        for (position in listOf("", "forward", "123", "PG\n", "<b>C</b>")) assertFalse(isPlayerPosition(position))
    }
}
