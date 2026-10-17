package com.example.visualization

import com.example.FeedProvenance
import com.example.GameFeed
import com.example.GameFeedCodec
import com.example.GameFeedRepository
import com.example.FeedDownload
import com.example.GameViewModel
import com.example.PreseasonGameData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicReference

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisualizationIntegrationTest {
    @Test fun explicitChartRefreshAndCloseKeepCapturedGameWhenPublicationDefaultChanges() = runBlocking {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        val directory = java.nio.file.Files.createTempDirectory("miniscore-chart-selection").toFile()
        try {
            val initialJson = File("../games.json").readText()
            val root = JSONObject(initialJson)
            val originalId = root.getString("defaultGameId")
            val next = JSONObject(root.getJSONArray("games").getJSONObject(0).toString()).put("id", "new-default")
            root.getJSONArray("games").put(next)
            root.put("defaultGameId", "new-default")
            val changedJson = root.toString()
            val publication = AtomicReference(FeedDownload(initialJson, revision = 2))
            val repository = GameFeedRepository.withDownload(File(directory, "games.json"), { publication.get() })
            val model = GameViewModel()
            model.startFeed(repository)
            withTimeout(10_000) { model.isRefreshing.first { !it } }
            model.openVisualization()
            assertEquals(originalId, model.visualizations.session.value!!.snapshot.gameId)
            publication.set(FeedDownload(changedJson, revision = 3))
            model.visualizations.refresh()
            withTimeout(10_000) { model.visualizations.session.first { it != null && !it.refreshing } }
            assertNull(model.visualizations.session.value!!.error)
            assertEquals(3L, model.visualizations.session.value!!.snapshot.provenance.revision)
            assertEquals(originalId, model.selectedGameId.value)
            model.visualizations.close()
            assertEquals("Closing returns to the captured game's scoreboard", originalId, model.selectedGameId.value)

            val ordinary = GameViewModel()
            ordinary.applyFeed(GameFeedCodec.decode(initialJson))
            ordinary.applyFeed(GameFeedCodec.decode(changedJson))
            assertEquals("Ordinary scoreboard refresh still follows a changed default", "new-default", ordinary.selectedGameId.value)
        } finally { directory.deleteRecursively(); Dispatchers.resetMain() }
    }

    @Test fun feedUpdatesAndChangedDefaultsDoNotReplaceAnOpenChart() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        try {
            val model = GameViewModel()
            val original = PreseasonGameData.createGame()
            model.applyFeed(GameFeed(original.id, listOf(original), FeedProvenance(revision = 2)))
            model.openVisualization()
            val captured = model.visualizations.session.value!!
            val other = original.copy(id = "other-game")
            model.applyFeed(GameFeed(other.id, listOf(other), FeedProvenance(revision = 3)))
            assertEquals(other.id, model.selectedGameId.value)
            assertEquals(captured, model.visualizations.session.value)
            model.selectGame(other.id)
            assertNull(model.visualizations.session.value)
        } finally { Dispatchers.resetMain() }
    }

    @Test fun feedCannotMoveBackwardsOnceRevisionIsKnown() {
        val model = GameViewModel()
        val original = PreseasonGameData.createGame()
        model.applyFeed(GameFeed(original.id, listOf(original), FeedProvenance(revision = 8)))
        for (revision in listOf(7L, null)) {
            val oldGame = original.copy(id = "old-game")
            model.applyFeed(GameFeed(oldGame.id, listOf(oldGame), FeedProvenance(revision = revision)))
            assertEquals(original.id, model.selectedGameId.value)
            assertEquals(listOf(original), model.games)
        }
    }

    @Test fun unavailableGameShowsAnOutcomeAndKeepsTheScoreboard() {
        val model = GameViewModel()
        val scheduled = PreseasonGameData.createGame().copy(status = "SCHEDULED")
        model.applyFeed(GameFeed(scheduled.id, listOf(scheduled)))
        model.openVisualization()
        assertNull(model.visualizations.session.value)
        assertNotNull(model.visualizationMessage.value)
        assertEquals(scheduled, model.games.single())
    }
}
