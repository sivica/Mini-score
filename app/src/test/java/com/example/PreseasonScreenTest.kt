package com.example

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
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
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-game.png")
        composeTestRule.onNodeWithText("LeBron James").performClick()
        composeTestRule.onNodeWithText("Minutes played: 18:04").performScrollTo().assertIsDisplayed()
        composeTestRule.onNodeWithText("Turnovers").performScrollTo().assertIsDisplayed()
        composeTestRule.onRoot().captureRoboImage(filePath = "build/screenshots/preseason-player.png")
    }
}
