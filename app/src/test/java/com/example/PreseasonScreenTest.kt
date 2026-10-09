package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.foundation.layout.Column
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
        composeTestRule.onNodeWithText("NBA Preseason • October 8, 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("Thursday, October 8, 2026").assertIsDisplayed()
        composeTestRule.onNodeWithText("108 - 114").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_phi_23").assertTextEquals(
            "23", "LeBron James", "PTS", "REB", "AST", "FGM", "FGA", "10", "4", "5", "1", "1"
        )
        composeTestRule.onNodeWithTag("lineup_player_bkn_30").assertTextEquals(
            "30", "Julius Randle", "PTS", "REB", "AST", "FGM", "FGA", "14", "6", "2", "4", "11"
        )
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-game.png")
        composeTestRule.onNodeWithText("LeBron James").performClick()
        composeTestRule.onNodeWithText("Minutes played: 18:04").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Turnovers").performScrollTo().assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-player.png")
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
            "1", "Kentavious Caldwell-Pope", "PTS", "REB", "AST", "FGM", "FGA", "2", "1", "1", "1", "3"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_bkn_14").assertTextEquals(
            "14", "Terance Mann", "PTS", "REB", "AST", "FGM", "FGA", "0", "2", "1", "0", "4"
        ).assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-narrow-stats.png")
    }
}
