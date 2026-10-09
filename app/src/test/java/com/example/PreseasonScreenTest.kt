package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.foundation.layout.Column
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.example.ui.theme.MyApplicationTheme
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class PreseasonScreenTest {
    @get:Rule val composeTestRule = createComposeRule()

    @Test
    fun opensRecordedGameAndShowsPlayerDetails() {
        val viewModel = GameViewModel()
        composeTestRule.setContent {
            MyApplicationTheme { MainNbaScreen(viewModel = viewModel) }
        }
        composeTestRule.onNodeWithText("NBA Preseason").assertIsDisplayed()
        composeTestRule.onNodeWithTag("selected_game_date", useUnmergedTree = true).assertTextEquals("Thu, Oct 8 · Final").assertIsDisplayed()
        composeTestRule.onNodeWithTag("team_score_PHI").assertTextEquals("108").assertIsDisplayed()
        composeTestRule.onNodeWithTag("team_score_BKN").assertTextEquals("114").assertIsDisplayed()
        composeTestRule.onNodeWithTag("team_logo_PHI").assertIsDisplayed()
        composeTestRule.onNodeWithTag("team_logo_BKN").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_logo").assertIsDisplayed()
        composeTestRule.onNodeWithTag("game_selector").assertIsDisplayed()
        composeTestRule.onNodeWithTag("player_portrait_phi_23", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_phi_23").assertTextEquals(
            "LeBron James", "#23 · PHI", "PTS", "REB", "AST", "FGM", "FGA", "10", "4", "5", "1", "1"
        )
        composeTestRule.onNodeWithTag("lineup_player_bkn_30").assertTextEquals(
            "Julius Randle", "#30 · BKN", "PTS", "REB", "AST", "FGM", "FGA", "14", "6", "2", "4", "11"
        )
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-game.png")
        composeTestRule.onNodeWithText("LeBron James").performClick()
        composeTestRule.onNodeWithText("Minutes played: 18:04").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Turnovers").performScrollTo().assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-player.png")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi", sdk = [36])
    fun selectorSwitchesGameAndScheduledScoresDoNotPretendToBeResults() {
        val model = GameViewModel()
        val scheduled = PreseasonGameData.createGame().copy(id = "scheduled-test", status = "SCHEDULED",
            startingAway = emptyList(), startingHome = emptyList(), substitutesAway = emptyList(), substitutesHome = emptyList(),
            gameDate = "2026-10-10", dateLabel = "Saturday, October 10, 2026", awayQuarters = emptyList(), homeQuarters = emptyList())
        model.applyFeed(GameFeed(PreseasonGameData.GAME_ID, listOf(PreseasonGameData.createGame(), scheduled)))
        composeTestRule.setContent { MyApplicationTheme { MainNbaScreen(model) } }
        composeTestRule.onNodeWithTag("game_selector").performClick()
        composeTestRule.onNodeWithText("Choose game").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thursday, October 8").assertIsDisplayed()
        composeTestRule.onNodeWithTag("select_game_${PreseasonGameData.GAME_ID}").assertIsSelected()
        composeTestRule.onNodeWithTag("select_game_nyk_cle").assertDoesNotExist()
        composeTestRule.onNodeWithTag("select_game_lal_gsw").assertDoesNotExist()
        composeTestRule.onNodeWithTag("select_game_bos_mia").assertDoesNotExist()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/game-selector-sheet.png")
        composeTestRule.onNodeWithTag("select_game_scheduled-test").performClick()
        composeTestRule.onNodeWithTag("game_picker_sheet").assertDoesNotExist()
        composeTestRule.onNodeWithTag("team_score_PHI").assertTextEquals("—")
        composeTestRule.onNodeWithTag("team_score_BKN").assertTextEquals("—")
        composeTestRule.onNodeWithTag("selected_game_date", useUnmergedTree = true).assertTextEquals("Sat, Oct 10 · Scheduled").assertIsDisplayed()
        composeTestRule.onNodeWithText("Player stats will appear when available.").assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/scheduled-game.png")
        composeTestRule.onNodeWithTag("game_selector").performClick()
        composeTestRule.onNodeWithTag("close_game_selector").performClick()
        composeTestRule.onNodeWithTag("game_picker_sheet").assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi", sdk = [36])
    fun benchStatsFitOnNarrowScreens() {
        val game = PreseasonGameData.createGame()
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    SymmetricalLineplayRow(game.substitutesAway.first(), game.substitutesHome.first()) {}
                }
            }
        }
        composeTestRule.onNodeWithTag("lineup_player_phi_1").assertTextEquals(
            "Kentavious Caldwell-Pope", "#1 · PHI", "PTS", "REB", "AST", "FGM", "FGA", "2", "1", "1", "1", "3"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_bkn_14").assertTextEquals(
            "Terance Mann", "#14 · BKN", "PTS", "REB", "AST", "FGM", "FGA", "0", "2", "1", "0", "4"
        ).assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-narrow-stats.png")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi", sdk = [36])
    fun logosStayAboveScoresAndQuarterDetailsRemainAccessible() {
        val game = PreseasonGameData.createGame()
        val resources = RuntimeEnvironment.getApplication().resources
        (game.startingAway + game.startingHome + game.substitutesAway + game.substitutesHome).forEach { player ->
            val resource = GameImageAssets.playerPortrait(player)
            assertNotNull("Bundled portrait for ${player.name}", resource)
            assertNotNull("Decodable portrait for ${player.name}", BitmapFactory.decodeResource(resources, resource!!))
        }
        composeTestRule.setContent {
            MyApplicationTheme {
                Column {
                    ScoreboardHeaderPanel(game)
                    LineupSectionHeader(game, "Starting lineup")
                    SymmetricalLineplayRow(game.startingAway.first(), game.startingHome.first()) {}
                }
            }
        }
        for (team in listOf("PHI", "BKN")) {
            val logo = composeTestRule.onNodeWithTag("team_logo_$team").fetchSemanticsNode().boundsInRoot
            val score = composeTestRule.onNodeWithTag("team_score_$team").fetchSemanticsNode().boundsInRoot
            assertTrue("$team logo is above its score", logo.bottom < score.top)
            assertTrue("$team score fits 320dp", score.left >= 0 && score.right <= 640)
        }
        composeTestRule.onNodeWithTag("lineup_header_Starting lineup").assertHeightIsEqualTo(32.dp)
        composeTestRule.onNodeWithTag("quarter_scores").assertDoesNotExist()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-narrow-matchup.png")
        composeTestRule.onNodeWithTag("quarter_toggle").performClick()
        composeTestRule.onNodeWithTag("quarter_scores").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quarter_toggle").performClick()
        composeTestRule.onNodeWithTag("quarter_scores").assertDoesNotExist()
    }
}
