package com.example.visualization

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.example.PreseasonGameData
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(qualifiers = RobolectricDeviceQualifiers.Pixel8, sdk = [36])
class RendererProviderFallbackTest {
    @get:Rule val compose = createComposeRule()

    @Test(timeout = 60000) fun missingProviderLeavesNativeSourceTableAndCloseUsable() {
        val snapshot = VisualizationContract.snapshotFromGame(PreseasonGameData.createGame())
        var closed = false
        compose.setContent {
            MaterialTheme {
                VisualizationScreen(snapshot, VisualizationSpec("playerStats", snapshot.gameId), {}, { closed = true }, {}, {},
                    createChartView = { throw IllegalStateException("No WebView provider") })
            }
        }
        // Provenance stays present when provider failure swaps the adjacent chart for the table.
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Chart captured:", substring = true))
        compose.waitForIdle()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Chart provider unavailable. Showing the source table."))
        compose.onNodeWithText("Chart provider unavailable. Showing the source table.").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Points source table"))
        compose.onNodeWithText("Points source table").assertExists()
        compose.onNode(hasScrollAction()).performScrollToNode(hasText("Close"))
        compose.onNodeWithText("Close").performClick()
        assertTrue(closed)
    }
}
