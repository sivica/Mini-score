package com.example

import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.foundation.layout.Column
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import java.util.concurrent.atomic.AtomicInteger
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
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
        composeTestRule.onNodeWithTag("selected_game_date").assertTextEquals("Thu, Oct 8").assertIsDisplayed()
        composeTestRule.onNodeWithTag("team_score_PHI").assertTextEquals("108").assertIsDisplayed()
        composeTestRule.onNodeWithTag("team_score_BKN").assertTextEquals("114").assertIsDisplayed()
        composeTestRule.onNodeWithTag("team_logo_PHI").assertIsDisplayed()
        composeTestRule.onNodeWithTag("team_logo_BKN").assertIsDisplayed()
        composeTestRule.onNodeWithTag("app_logo").assertIsDisplayed()
        composeTestRule.onNodeWithText("Miniscore").assertIsNotDisplayed()
        composeTestRule.onNodeWithContentDescription("Refresh games").assertDoesNotExist()
        val logo = composeTestRule.onNodeWithTag("app_logo").fetchSemanticsNode().boundsInRoot
        val bar = composeTestRule.onNodeWithTag("main_app_bar").fetchSemanticsNode().boundsInRoot
        assertTrue("Logo centered in main app bar", kotlin.math.abs(logo.center.x - bar.center.x) < 1f)
        composeTestRule.onNodeWithTag("game_selector").assertDoesNotExist()
        for (label in listOf("Games", "Players", "Standings", "Settings")) {
            composeTestRule.onNodeWithText(label).assertDoesNotExist()
        }
        composeTestRule.onNodeWithTag("player_portrait_phi_23", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_phi_23").assertTextEquals(
            "LeBron James", "#23", "18:04", "PTS", "REB", "AST", "FGM", "FGA", "10", "4", "5", "1", "1"
        )
        composeTestRule.onNodeWithTag("lineup_player_bkn_30").assertTextEquals(
            "Julius Randle", "21:37", "#30", "PTS", "REB", "AST", "FGM", "FGA", "14", "6", "2", "4", "11"
        )
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-game.png")
        composeTestRule.onNodeWithText("LeBron James").performClick()
        composeTestRule.onNodeWithText("Minutes played: 18:04").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Turnovers").performScrollTo().assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-player.png")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi", sdk = [36])
    fun drawerSearchSelectsGamesAndClosesAfterSelection() {
        val model = GameViewModel()
        val scheduled = PreseasonGameData.createGame().copy(id = "scheduled-test", status = "SCHEDULED",
            startingAway = emptyList(), startingHome = emptyList(), substitutesAway = emptyList(), substitutesHome = emptyList(),
            gameDate = "2026-10-10", dateLabel = "Saturday, October 10, 2026", awayQuarters = emptyList(), homeQuarters = emptyList(),
            tipoffUtc = "2026-10-10T00:00:00Z")
        model.applyFeed(GameFeed(PreseasonGameData.GAME_ID, listOf(PreseasonGameData.createGame(), scheduled)))
        val fontScale = mutableStateOf(1f)
        var focusManager: FocusManager? = null
        composeTestRule.setContent {
            val density = LocalDensity.current
            focusManager = LocalFocusManager.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale.value)) {
                MyApplicationTheme { MainNbaScreen(model) }
            }
        }
        composeTestRule.onNodeWithTag("game_selector").assertDoesNotExist()
        composeTestRule.onNodeWithTag("select_game_${PreseasonGameData.GAME_ID}").assertIsNotDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_phi_23").assertIsDisplayed()
        composeTestRule.onNodeWithTag("select_game_scheduled-test").assertIsNotDisplayed()
        composeTestRule.onNodeWithTag("open_games_drawer").performClick()
        composeTestRule.onNodeWithTag("games_drawer").assertIsDisplayed()
        composeTestRule.onNodeWithText("Miniscore").assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/games-drawer-matches.png")
        composeTestRule.onNodeWithTag("game_search").performTextReplacement("no-such-team")
        composeTestRule.onNodeWithText("No games found").assertIsDisplayed()
        composeTestRule.onNodeWithTag("game_search").performTextReplacement("Sat")
        composeTestRule.onNodeWithTag("select_game_scheduled-test").assertIsDisplayed()
        composeTestRule.onNode(hasContentDescription("Show game details") and
            hasAnyAncestor(hasTestTag("drawer_game_list")), useUnmergedTree = true).assertDoesNotExist()
        val awayLogo = composeTestRule.onNodeWithTag("team_logo_PHI_drawer_scheduled-test", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val homeLogo = composeTestRule.onNodeWithTag("team_logo_BKN_drawer_scheduled-test", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val drawerScore = composeTestRule.onNodeWithTag("drawer_score_scheduled-test", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val awayName = composeTestRule.onNodeWithTag("drawer_away_name_scheduled-test", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        val homeName = composeTestRule.onNodeWithTag("drawer_home_name_scheduled-test", useUnmergedTree = true)
            .fetchSemanticsNode().boundsInRoot
        assertTrue("Drawer score stays between opposing logos", awayLogo.right <= drawerScore.left && drawerScore.right <= homeLogo.left)
        assertTrue("Drawer names sit below logos", awayName.top >= awayLogo.bottom && homeName.top >= homeLogo.bottom)
        composeTestRule.onNodeWithTag("select_game_${PreseasonGameData.GAME_ID}").assertDoesNotExist()
        composeTestRule.onNodeWithText("Other games").assertDoesNotExist()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/games-drawer.png")
        composeTestRule.onNodeWithTag("game_search").performTextReplacement("Thu")
        composeTestRule.runOnIdle { focusManager?.clearFocus(); fontScale.value = 2f }
        composeTestRule.onNodeWithTag("drawer_score_${PreseasonGameData.GAME_ID}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_away_name_${PreseasonGameData.GAME_ID}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onNodeWithTag("drawer_home_name_${PreseasonGameData.GAME_ID}", useUnmergedTree = true).assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/games-drawer-200-percent.png")
        fontScale.value = 1f
        composeTestRule.onNodeWithTag("game_search").performTextReplacement("Sat")
        composeTestRule.onNodeWithTag("select_game_scheduled-test").performClick()
        composeTestRule.onNodeWithTag("games_drawer").assertIsNotDisplayed()
        composeTestRule.onNodeWithTag("team_score_PHI").assertTextEquals("—")
        composeTestRule.onNodeWithTag("team_score_BKN").assertTextEquals("—")
        composeTestRule.onNodeWithTag("selected_game_date").assertTextEquals("Sat, Oct 10").assertIsDisplayed()
        composeTestRule.onNodeWithText("Game hasn’t started").assertIsDisplayed()
        composeTestRule.onNodeWithText("Tipoff: 8:00 PM ET").assertIsDisplayed()
        composeTestRule.onNodeWithText("Player stats will appear when available.").assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/scheduled-game.png")
        composeTestRule.onNodeWithTag("open_games_drawer").performClick()
        composeTestRule.onNodeWithTag("game_search").performTextReplacement("Thu")
        composeTestRule.onNodeWithTag("select_game_${PreseasonGameData.GAME_ID}").performClick()
        composeTestRule.onNodeWithTag("team_score_PHI").assertTextEquals("108").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_phi_23").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi", sdk = [36])
    fun twoHundredPercentFontsStackPlayersAndKeepAllFiveStats() {
        val game = PreseasonGameData.createGame()
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, 2f)) {
                MyApplicationTheme {
                    SymmetricalLineplayRow(game.substitutesAway.first(), game.substitutesHome.first()) {}
                }
            }
        }
        composeTestRule.onNodeWithTag("stacked_player_pair").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_phi_1").assertIsDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_bkn_14").assertIsDisplayed()
        val left = composeTestRule.onNodeWithTag("lineup_player_phi_1").fetchSemanticsNode().boundsInRoot
        val right = composeTestRule.onNodeWithTag("lineup_player_bkn_14").fetchSemanticsNode().boundsInRoot
        assertTrue("Large-font player cards stack without overlap", left.bottom <= right.top)
        composeTestRule.onNodeWithTag("lineup_player_phi_1").assertTextEquals(
            "Kentavious Caldwell-Pope", "#1", "12:26", "PTS", "REB", "AST", "FGM", "FGA", "2", "1", "1", "1", "3"
        )
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-200-percent.png")
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
            "Kentavious Caldwell-Pope", "#1", "12:26", "PTS", "REB", "AST", "FGM", "FGA", "2", "1", "1", "1", "3"
        ).assertIsDisplayed()
        composeTestRule.onNodeWithTag("lineup_player_bkn_14").assertTextEquals(
            "Terance Mann", "12:00", "#14", "PTS", "REB", "AST", "FGM", "FGA", "0", "2", "1", "0", "4"
        ).assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-narrow-stats.png")
    }

    @Test
    @Config(qualifiers = "w320dp-h640dp-xhdpi", sdk = [36])
    fun compactScoreboardAndMirroredMinutesStayAligned() {
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
            assertTrue("$team logo shares the score row", logo.top < score.bottom && score.top < logo.bottom)
            assertTrue("$team score fits 320dp", score.left >= 0 && score.right <= 640)
            val status = composeTestRule.onNodeWithTag("game_status", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
            assertTrue("Status and details control are below the score", status.top >= score.bottom)
        }
        val leftMinutes = composeTestRule.onNodeWithTag("player_minutes_phi_23", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        val rightMinutes = composeTestRule.onNodeWithTag("player_minutes_bkn_30", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Mirrored minutes share a baseline", kotlin.math.abs(leftMinutes.top - rightMinutes.top) < 1f)
        val leftName = composeTestRule.onNodeWithTag("player_name_phi_23", useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
        assertTrue("Name stays above minutes", leftName.bottom <= leftMinutes.top)
        composeTestRule.onNodeWithTag("lineup_header_Starting lineup").assertHeightIsEqualTo(32.dp)
        composeTestRule.onNodeWithTag("quarter_scores").assertDoesNotExist()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-narrow-matchup.png")
        composeTestRule.onNodeWithTag("quarter_toggle").performClick()
        composeTestRule.onNodeWithTag("quarter_scores").assertIsDisplayed()
        composeTestRule.onNodeWithTag("quarter_toggle").performClick()
        composeTestRule.onNodeWithTag("quarter_scores").assertDoesNotExist()
    }
    @Test
    fun pullingDownRequestsRefreshWithoutChangingChosenGame() {
        val calls = AtomicInteger()
        val model = GameViewModel()
        composeTestRule.setContent {
            MyApplicationTheme { MainNbaScreen(model, onRefresh = { calls.incrementAndGet() }) }
        }
        composeTestRule.onNodeWithTag("game_list").performTouchInput { swipeDown() }
        composeTestRule.waitUntil(5_000) { calls.get() == 1 }
        composeTestRule.onNodeWithTag("team_score_PHI").assertTextEquals("108").assertIsDisplayed()
        assertTrue("Pull refresh preserves selected game", model.selectedGameId.value == PreseasonGameData.GAME_ID)
    }
}
