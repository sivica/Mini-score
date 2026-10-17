package com.example

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import coil.Coil
import java.io.File
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Opt-in live-network smoke test; run on the dedicated emulator, not offline CI. */
@RunWith(AndroidJUnit4::class)
class MediaPreviewSmokeTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun currentGameLoadsActualImagesAndCachedPositions() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("com.ivicastojanoski.miniscore.preview", context.packageName)
        val gameCache = File(context.filesDir, "games-cache.json")
        val positionCache = File(context.filesDir, "player-positions-cache.json")
        var game: Game? = null
        var position: String? = null
        var diagnostic = "Waiting for Railway game cache"
        try {
          compose.waitUntil(45_000) {
            try {
                if (game == null && gameCache.exists()) {
                    val feed = GameFeedCodec.decode(gameCache.readText())
                    game = feed.games.single { it.id == feed.defaultGameId }
                }
                val current = game ?: return@waitUntil false
                val player = current.startingAway.first()
                diagnostic = "Game ${current.id}; waiting for position metadata"
                position = JSONObject(positionCache.readText()).getJSONObject(current.id).getString(player.id)
                val keys = Coil.imageLoader(context).memoryCache?.keys.orEmpty().map { it.key }
                val visibleImages = listOfNotNull(current.awayTeam.logoUrl, current.homeTeam.logoUrl) +
                    (current.startingAway + current.startingHome).mapNotNull { it.portraitUrl }
                diagnostic = "Position=$position; loaded ${visibleImages.count { it in keys }}/${visibleImages.size} images"
                visibleImages.all { it in keys } && isPlayerPosition(position!!)
            } catch (error: Exception) { diagnostic += "; ${error.javaClass.simpleName}: ${error.message}"; false }
          }
        } catch (error: Throwable) {
            val failedScreen = InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot()
            File(context.filesDir, "media-preview-failure.png").outputStream().use {
                failedScreen?.compress(Bitmap.CompressFormat.PNG, 100, it)
            }
            throw AssertionError(diagnostic, error)
        }
        val current = checkNotNull(game)
        val player = current.startingAway.first()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasText("#${player.number} · $position") and
                hasAnyAncestor(hasTestTag("lineup_player_${player.id}")), useUnmergedTree = true)
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNode(hasTestTag("team_logo_${current.awayTeam.code}") and
            hasAnyAncestor(hasTestTag("scoreboard_header")), useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("player_portrait_${player.id}", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("player_minutes_${player.id}", useUnmergedTree = true).assertIsDisplayed()
        compose.waitForIdle()
        compose.mainClock.advanceTimeByFrame()
        compose.waitForIdle()
        val screenshot = checkNotNull(InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot())
        File(context.filesDir, "media-preview-smoke.png").outputStream().use {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it))
        }
    }
}
