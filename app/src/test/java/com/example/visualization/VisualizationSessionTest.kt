package com.example.visualization

import com.example.FeedProvenance
import com.example.Game
import com.example.GameFeed
import com.example.PreseasonGameData
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withContext
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.InputStream

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisualizationSessionTest {
    private fun game() = PreseasonGameData.createGame()
    private fun provenance(revision: Long? = 4L) = FeedProvenance(
        revision = revision, publishedAt = "2026-10-11T06:00:00Z", fetchedAtMillis = 100L)
    private fun feed(game: Game = game(), revision: Long? = 5L) =
        GameFeed(game.id, listOf(game), provenance(revision))

    @Test fun capturedSnapshotDoesNotFollowMutableGameObjects() = runTest {
        val game = game()
        val controller = VisualizationSessionController(this, { feed() }, now = { 200L })
        controller.open(game, provenance())
        val before = controller.session.value!!.snapshot
        val points = before.players.first().points
        game.startingAway.first().stats.points += 50
        assertEquals(before, controller.session.value!!.snapshot)
        assertEquals(points, controller.session.value!!.snapshot.players.first().points)
        assertNotEquals(game.startingAway.first().stats.points, before.players.first().points)
        assertEquals(200L, before.capturedAtMillis)
        assertEquals(4L, before.provenance.revision)
        assertEquals(100L, before.provenance.fetchedAtMillis)
    }

    @Test fun refreshKeepsCapturedGameWhenPublishedDefaultChanges() = runTest(UnconfinedTestDispatcher()) {
        val captured = game()
        val another = game().copy(id = "another-game")
        var accepted: GameFeed? = null
        val controller = VisualizationSessionController(this,
            { GameFeed(another.id, listOf(another, captured), provenance(5)) }, { accepted = it })
        controller.open(captured, provenance())
        controller.changeSpec(controller.session.value!!.spec.copy(template = "fieldGoals"))
        controller.refresh()
        val session = controller.session.value!!
        assertFalse(session.refreshing)
        assertEquals(captured.id, session.snapshot.gameId)
        assertEquals("fieldGoals", session.spec.template)
        assertEquals(5L, session.snapshot.provenance.revision)
        assertEquals(another.id, accepted!!.defaultGameId)
    }

    @Test fun missingGameLeavesTheLastChartAndTableAvailable() = runTest(UnconfinedTestDispatcher()) {
        var accepted = false
        val controller = VisualizationSessionController(this,
            { feed(game().copy(id = "another-game")) }, { accepted = true })
        controller.open(game(), provenance())
        val before = controller.session.value!!
        controller.refresh()
        assertEquals(before.snapshot, controller.session.value!!.snapshot)
        assertEquals(before.spec, controller.session.value!!.spec)
        assertNotNull(controller.session.value!!.error)
        assertFalse(controller.session.value!!.refreshing)
        assertFalse(accepted)
    }

    @Test fun olderAndUnknownRevisionsCannotReplaceCapturedPublication() = runTest(UnconfinedTestDispatcher()) {
        for (revision in listOf(3L, null)) {
            val controller = VisualizationSessionController(this, { feed(revision = revision) })
            controller.open(game(), provenance())
            val before = controller.session.value!!
            controller.refresh()
            assertEquals(before.snapshot, controller.session.value!!.snapshot)
            assertEquals(before.spec, controller.session.value!!.spec)
            assertNotNull(controller.session.value!!.error)
        }
    }

    @Test fun offlineRefreshKeepsThePreviousSnapshotAndSpec() = runTest(UnconfinedTestDispatcher()) {
        val controller = VisualizationSessionController(this, { throw java.io.IOException("Offline") })
        controller.open(game(), provenance())
        val before = controller.session.value!!
        controller.refresh()
        assertEquals(before.snapshot, controller.session.value!!.snapshot)
        assertEquals(before.spec, controller.session.value!!.spec)
        assertFalse(controller.session.value!!.refreshing)
        assertNotNull(controller.session.value!!.error)
    }

    @Test fun refreshedDataCannotInvalidateTheChosenPlayerAndReplaceThePreviousSnapshot() = runTest(UnconfinedTestDispatcher()) {
        val original = game()
        val removedId = original.startingAway.first().id
        val changed = original.copy(startingAway = original.startingAway.filterNot { it.id == removedId })
        val controller = VisualizationSessionController(this, { feed(changed) })
        controller.open(original, provenance())
        controller.changeSpec(controller.session.value!!.spec.copy(player = removedId))
        val before = controller.session.value!!
        controller.refresh()
        assertEquals(before.snapshot, controller.session.value!!.snapshot)
        assertEquals(before.spec, controller.session.value!!.spec)
        assertNotNull(controller.session.value!!.error)
    }

    @Test fun filtersChangedDuringRefreshAreKeptAndRepeatedRefreshIsIgnored() = runTest(UnconfinedTestDispatcher()) {
        val result = CompletableDeferred<GameFeed>()
        var loads = 0
        val controller = VisualizationSessionController(this, { loads++; result.await() })
        controller.open(game(), provenance())
        controller.refresh()
        controller.refresh()
        val edited = controller.session.value!!.spec.copy(team = "home", sort = "name", metric = "rebounds")
        controller.changeSpec(edited)
        result.complete(feed())
        assertEquals(1, loads)
        assertEquals(edited, controller.session.value!!.spec)
        assertEquals(5L, controller.session.value!!.snapshot.provenance.revision)
    }

    @Test fun lateUncooperativeRefreshCannotReopenClosedSessionOrChangeNewGame() = runTest(UnconfinedTestDispatcher()) {
        val result = CompletableDeferred<GameFeed>()
        var accepted = false
        val controller = VisualizationSessionController(this,
            { withContext(NonCancellable) { result.await() } }, { accepted = true })
        controller.open(game(), provenance())
        controller.refresh()
        assertTrue(controller.session.value!!.refreshing)
        controller.close()
        assertNull(controller.session.value)
        val next = game().copy(id = "next-game")
        controller.open(next, provenance(8))
        val nextSession = controller.session.value
        result.complete(feed())
        assertEquals(nextSession, controller.session.value)
        assertFalse(accepted)
    }

    @Test fun closingCancelsCooperativeRefresh() = runTest(UnconfinedTestDispatcher()) {
        val started = CompletableDeferred<Unit>()
        val cancelled = CompletableDeferred<Unit>()
        val wait = CompletableDeferred<GameFeed>()
        val controller = VisualizationSessionController(this, {
            started.complete(Unit)
            try { wait.await() } finally { cancelled.complete(Unit) }
        })
        controller.open(game(), provenance())
        controller.refresh()
        started.await()
        controller.close()
        cancelled.await()
        assertNull(controller.session.value)
    }

    @Test fun invalidImportRetainsThePreviousSpecAndCapturedData() = runTest(UnconfinedTestDispatcher()) {
        val controller = VisualizationSessionController(this, { feed() })
        controller.open(game(), provenance())
        val before = controller.session.value!!
        var streamClosed = false
        controller.importSpec { object : ByteArrayInputStream("<script>alert(1)</script>".toByteArray()) {
            override fun close() { streamClosed = true; super.close() }
        } }
        val after = controller.session.first { it != null && !it.importing }!!
        assertEquals(before.spec, after.spec)
        assertEquals(before.snapshot, after.snapshot)
        assertNotNull(after.error)
        assertTrue(streamClosed)
    }

    @Test fun scheduledGameCannotReplaceValidSession() = runTest {
        val controller = VisualizationSessionController(this, { feed() })
        controller.open(game(), provenance())
        val before = controller.session.value
        assertThrows(IllegalArgumentException::class.java) {
            controller.open(game().copy(status = "SCHEDULED"), provenance())
        }
        assertEquals(before, controller.session.value)
    }

    @Test fun aNewControllerDoesNotReopenPersistedSnapshots() = runTest {
        val first = VisualizationSessionController(this, { feed() })
        first.open(game(), provenance())
        assertNotNull(first.session.value)
        val recreatedProcess = VisualizationSessionController(this, { feed() })
        assertNull(recreatedProcess.session.value)
    }

    @Test fun documentReaderRejectsOverLimitWhileReadingAndMalformedUtf8() {
        var bytesRead = 0
        val infinite = object : InputStream() {
            override fun read(): Int { bytesRead++; return 32 }
            override fun read(bytes: ByteArray, offset: Int, length: Int): Int {
                bytes.fill(32, offset, offset + length); bytesRead += length; return length
            }
        }
        assertThrows(IllegalArgumentException::class.java) { readBoundedSpec(infinite) }
        assertEquals(MAX_SPEC_BYTES + 1, bytesRead)
        assertEquals(MAX_SPEC_BYTES, readBoundedSpec(ByteArrayInputStream(ByteArray(MAX_SPEC_BYTES) { 32 })).length)
        assertThrows(java.nio.charset.CharacterCodingException::class.java) {
            readBoundedSpec(ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)))
        }
    }
}
