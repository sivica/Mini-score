package com.example

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GameLayoutTest {
    @Test fun substitutesSortByMinutesThenSecondsAndPreserveTies() {
        val base = PreseasonGameData.createGame().substitutesAway.first()
        val players = listOf(
            base.copy(id = "low", stats = PlayerStats(minutes = 9, seconds = 59)),
            base.copy(id = "tie-a", stats = PlayerStats(minutes = 10, seconds = 1)),
            base.copy(id = "high", stats = PlayerStats(minutes = 10, seconds = 59)),
            base.copy(id = "tie-b", stats = PlayerStats(minutes = 10, seconds = 1))
        )
        assertEquals(listOf("high", "tie-a", "tie-b", "low"), substitutesByMinutes(players).map { it.id })
        assertEquals("low", players.first().id)
    }
    @Test fun searchMatchesWordsWithoutConfusingNetsAndHornets() {
        val nets = PreseasonGameData.createGame()
        val hornets = nets.copy(homeTeam = nets.homeTeam.copy(name = "Charlotte Hornets"))
        assertTrue(matchesGameSearch(nets, "nets"))
        assertTrue(matchesGameSearch(nets, "phil brook"))
        assertTrue(matchesGameSearch(nets, "Oct 8"))
        assertTrue(matchesGameSearch(nets, "  "))
        assertFalse(matchesGameSearch(hornets, "nets"))
    }
}
