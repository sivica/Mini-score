package com.example

import androidx.compose.ui.graphics.Color

/**
 * Final snapshot: Philadelphia at Brooklyn, October 8, 2026.
 * Result and quarters: https://www.nba.com/game/phi-vs-bkn-0012600034
 * Player stats: https://basketball.realgm.com/nba/boxscore/2026-10-08/Philadelphia-at-Brooklyn/526643
 * Ratings are unavailable; rebounds/turnovers exclude team-only events.
 */
object PreseasonGameData {
    const val GAME_ID = "0012600034"
    val PHI = Team("PHI", "Philadelphia 76ers", Color(0xFF006BB6), Color(0xFFED174C), "PHI", shortName = "76ers")
    val BKN = Team("BKN", "Brooklyn Nets", Color(0xFF000000), Color(0xFFFFFFFF), "BKN", shortName = "Nets")

    fun createGame(): Game {
        val awayStarters = listOf(
            Player("phi_23", "LeBron James", "23", "", true, true, null, PlayerStats(10, 4, 5, 1, 0, 2, 1, 1, 18, 4)),
            Player("phi_21", "Joel Embiid", "21", "", true, true, null, PlayerStats(6, 3, 3, 1, 1, 8, 1, 5, 23, 42)),
            Player("phi_7", "Jaylen Brown", "7", "", true, true, null, PlayerStats(18, 2, 2, 0, 0, 0, 5, 9, 23, 50)),
            Player("phi_0", "Tyrese Maxey", "0", "", true, true, null, PlayerStats(14, 2, 2, 1, 0, 2, 5, 8, 18, 51)),
            Player("phi_77", "VJ Edgecombe", "77", "", true, true, null, PlayerStats(11, 5, 1, 0, 1, 1, 3, 6, 22, 42))
        )

        val homeStarters = listOf(
            Player("bkn_30", "Julius Randle", "30", "", false, true, null, PlayerStats(14, 6, 2, 2, 0, 1, 4, 11, 21, 37)),
            Player("bkn_13", "Moe Wagner", "13", "", false, true, null, PlayerStats(6, 3, 1, 4, 0, 0, 2, 3, 18, 9)),
            Player("bkn_1", "Keon Ellis", "1", "", false, true, null, PlayerStats(12, 2, 0, 1, 0, 0, 5, 8, 21, 22)),
            Player("bkn_21", "Noah Clowney", "21", "", false, true, null, PlayerStats(11, 1, 0, 1, 1, 0, 3, 4, 19, 48)),
            Player("bkn_8", "Egor Demin", "8", "", false, true, null, PlayerStats(13, 1, 5, 0, 0, 1, 5, 13, 21, 36))
        )

        val awaySubs = listOf(
            Player("phi_1", "Kentavious Caldwell-Pope", "1", "", true, false, null, PlayerStats(2, 1, 1, 0, 1, 0, 1, 3, 12, 26)),
            Player("phi_45", "Dean Wade", "45", "", true, false, null, PlayerStats(7, 4, 0, 0, 0, 2, 2, 2, 15, 44)),
            Player("phi_14", "Jameer Nelson Jr.", "14", "", true, false, null, PlayerStats(6, 1, 1, 1, 0, 2, 2, 3, 7, 15)),
            Player("phi_35", "Dillon Jones", "35", "", true, false, null, PlayerStats(0, 2, 3, 1, 0, 1, 0, 1, 10, 34)),
            Player("phi_8", "Caleb Love", "8", "", true, false, null, PlayerStats(4, 0, 5, 1, 0, 0, 2, 5, 13, 34)),
            Player("phi_12", "Rayan Rupert", "12", "", true, false, null, PlayerStats(5, 3, 0, 0, 0, 1, 2, 4, 15, 19)),
            Player("phi_33", "Jabari Walker", "33", "", true, false, null, PlayerStats(8, 7, 0, 0, 0, 0, 2, 5, 15, 15)),
            Player("phi_11", "Justin Edwards", "11", "", true, false, null, PlayerStats(7, 5, 0, 0, 1, 2, 3, 7, 16, 47)),
            Player("phi_19", "Saint Thomas", "19", "", true, false, null, PlayerStats(4, 1, 1, 0, 0, 0, 2, 3, 4, 45)),
            Player("phi_00", "Labaron Philon Jr.", "00", "", true, false, null, PlayerStats(6, 3, 2, 2, 0, 0, 2, 8, 21, 12))
        )

        val homeSubs = listOf(
            Player("bkn_14", "Terance Mann", "14", "", false, false, null, PlayerStats(0, 2, 1, 1, 0, 0, 0, 4, 12, 0)),
            Player("bkn_16", "Grant Nelson", "16", "", false, false, null, PlayerStats(8, 2, 1, 0, 0, 2, 3, 4, 12, 0)),
            Player("bkn_12", "Ben Humrichous", "12", "", false, false, null, PlayerStats(2, 2, 0, 0, 0, 0, 1, 2, 7, 15)),
            Player("bkn_31", "Chaney Johnson", "31", "", false, false, null, PlayerStats(9, 4, 2, 2, 2, 2, 3, 7, 17, 51)),
            Player("bkn_00", "Josh Minott", "00", "", false, false, null, PlayerStats(4, 3, 1, 0, 0, 2, 1, 5, 17, 52)),
            Player("bkn_2", "Danny Wolf", "2", "", false, false, null, PlayerStats(9, 6, 4, 0, 0, 2, 4, 7, 16, 9)),
            Player("bkn_77", "Ben Saraf", "77", "", false, false, null, PlayerStats(5, 1, 3, 1, 0, 2, 1, 4, 12, 0)),
            Player("bkn_10", "Nolan Traore", "10", "", false, false, null, PlayerStats(12, 1, 2, 1, 0, 2, 4, 8, 15, 55)),
            Player("bkn_34", "Tyler Bilodeau", "34", "", false, false, null, PlayerStats(5, 5, 0, 0, 0, 0, 2, 4, 12, 0)),
            Player("bkn_4", "Drake Powell", "4", "", false, false, null, PlayerStats(4, 0, 1, 1, 0, 0, 1, 4, 14, 26))
        )

        return Game(
            id = GAME_ID,
            awayTeam = PHI,
            homeTeam = BKN,
            awayScore = 108,
            homeScore = 114,
            status = "FINISHED",
            startingAway = awayStarters,
            startingHome = homeStarters,
            substitutesAway = awaySubs,
            substitutesHome = homeSubs,
            contextLabel = "NBA Preseason",
            dateLabel = "Thursday, October 8, 2026",
            gameDate = "2026-10-08",
            venue = "Barclays Center, Brooklyn, NY",
            awayQuarters = listOf(33, 25, 23, 27),
            homeQuarters = listOf(33, 23, 37, 21),
            minutesLeft = 0,
            playLogs = mutableListOf(
                PlayLog(time = "FINAL", description = "Brooklyn wins 114-108. Final preseason box score.", scoreText = "108 - 114")
            )
        )
    }
}
