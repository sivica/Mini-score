package com.example

import org.junit.Assert.*
import org.junit.Test

class PreseasonGameDataTest {
    @Test
    fun finalBoxScoreReconcilesWithPlayersAndQuarters() {
        val game = PreseasonGameData.createGame()
        val away = game.startingAway + game.substitutesAway
        val home = game.startingHome + game.substitutesHome
        assertEquals(108, game.awayScore)
        assertEquals(114, game.homeScore)
        assertEquals(game.awayScore, away.sumOf { it.stats.points })
        assertEquals(game.homeScore, home.sumOf { it.stats.points })
        assertEquals(game.awayScore, game.awayQuarters.sum())
        assertEquals(game.homeScore, game.homeQuarters.sum())
        assertEquals(43, away.sumOf { it.stats.rebounds })
        assertEquals(39, home.sumOf { it.stats.rebounds })
        assertEquals(26, away.sumOf { it.stats.assists })
        assertEquals(23, home.sumOf { it.stats.assists })
        assertEquals(33, away.sumOf { it.stats.fgMade })
        assertEquals(70, away.sumOf { it.stats.fgAttempted })
        assertEquals(39, home.sumOf { it.stats.fgMade })
        assertEquals(88, home.sumOf { it.stats.fgAttempted })
        for (players in listOf(away, home)) {
            assertEquals(15, players.size)
            assertEquals(5, players.count { it.isStarting })
            assertEquals(240 * 60, players.sumOf { it.stats.minutes * 60 + it.stats.seconds })
            assertTrue(players.all { it.rating == null })
        }
        assertTrue(away.all { it.isLeftTeam })
        assertTrue(home.none { it.isLeftTeam })
    }

    @Test
    fun recordedGameOpensByDefaultAndCannotBeSimulated() {
        val viewModel = GameViewModel()
        assertEquals(PreseasonGameData.GAME_ID, viewModel.selectedGameId.value)
        assertEquals(PreseasonGameData.GAME_ID, viewModel.games.first().id)
        assertEquals(1, viewModel.games.size)
        val game = viewModel.games.single { it.id == PreseasonGameData.GAME_ID }
        assertEquals("FINISHED", game.status)
        assertFalse(game.isSimulating)
        assertEquals(108, game.awayScore)
        assertEquals(114, game.homeScore)
    }

    @Test
    fun missingRatingStillShowsRecordedPerformanceAndExactMinutes() {
        val lebron = PreseasonGameData.createGame().startingAway.first()
        assertEquals("18:04", lebron.stats.minutesDisplay)
        assertEquals("10 points, 4 rebounds and 5 assists in 18:04. Player rating unavailable.", getAnalystVerdict(lebron))
    }
}
