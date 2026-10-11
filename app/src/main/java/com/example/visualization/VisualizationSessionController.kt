package com.example.visualization

import com.example.FeedProvenance
import com.example.Game
import com.example.GameFeed
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

data class VisualizationSession(
    val id: Long,
    val snapshot: VisualizationSnapshot,
    val spec: VisualizationSpec,
    val refreshing: Boolean = false,
    val importing: Boolean = false,
    val settingsRevision: Long = 0,
    val error: String? = null
)

/** An in-memory session. No saved snapshots are written or reopened after process death. */
class VisualizationSessionController(
    private val scope: CoroutineScope,
    private val loadLatestFeed: suspend () -> GameFeed,
    private val onAcceptedFeed: (GameFeed) -> Unit = {},
    private val now: () -> Long = System::currentTimeMillis
) {
    private val _session = MutableStateFlow<VisualizationSession?>(null)
    val session: StateFlow<VisualizationSession?> = _session.asStateFlow()
    private var nextId = 0L
    private var importSequence = 0L
    private var refreshJob: Job? = null
    private var importJob: Job? = null

    fun open(game: Game, provenance: FeedProvenance): Boolean {
        // Construct and validate before replacing a valid session.
        val snapshot = VisualizationContract.snapshotFromGame(game, provenance, now())
        val spec = VisualizationSpec(template = "playerStats", gameId = game.id)
        VisualizationContract.validateSpec(spec, snapshot)
        close()
        _session.value = VisualizationSession(++nextId, snapshot, spec)
        return true
    }

    fun close() {
        _session.value = null
        importSequence++
        refreshJob?.cancel()
        importJob?.cancel()
        refreshJob = null
        importJob = null
    }

    fun changeSpec(spec: VisualizationSpec) {
        val current = _session.value ?: return
        try {
            VisualizationContract.validateSpec(spec, current.snapshot)
            _session.value = current.copy(spec = spec, settingsRevision = current.settingsRevision + 1, error = null)
        } catch (_: Exception) {
            _session.value = current.copy(error = "That chart is not available for this game. Your previous chart is unchanged.")
        }
    }

    /** An AI request cannot overwrite a refreshed, closed, imported or manually edited chart. */
    fun applyGeneratedSpec(expected: VisualizationSession, spec: VisualizationSpec): Boolean {
        val current = _session.value ?: return false
        if (current.id != expected.id || current.settingsRevision != expected.settingsRevision || current.snapshot != expected.snapshot || current.spec != expected.spec ||
            current.refreshing || current.importing) return false
        return try {
            VisualizationContract.validateSpec(spec, current.snapshot)
            _session.value = current.copy(spec = spec, settingsRevision = current.settingsRevision + 1, error = null)
            true
        } catch (_: Exception) { false }
    }

    /** Import is bounded while reading; arbitrary HTML, scripts and URLs are never accepted. */
    fun importSpec(openStream: () -> InputStream?) {
        val current = _session.value ?: return
        val sessionId = current.id
        val request = ++importSequence
        importJob?.cancel()
        _session.value = current.copy(importing = true, error = null)
        importJob = scope.launch {
            try {
                val raw = withContext(Dispatchers.IO) {
                    val context = currentCoroutineContext()
                    requireNotNull(openStream()).use { readBoundedSpec(it) { context.ensureActive() } }
                }
                // Validate against the current captured data, including an explicit refresh that
                // finished while the document was being read.
                val latest = _session.value?.takeIf { it.id == sessionId && importSequence == request }
                    ?: return@launch
                val spec = withContext(Dispatchers.Default) {
                    VisualizationContract.decodeSpec(raw, latest.snapshot)
                }
                val final = _session.value?.takeIf { it.id == sessionId && importSequence == request }
                    ?: return@launch
                VisualizationContract.validateSpec(spec, final.snapshot)
                _session.value = final.copy(spec = spec, settingsRevision = final.settingsRevision + 1, importing = false, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _session.value?.takeIf { it.id == sessionId && importSequence == request }?.let {
                    _session.value = it.copy(importing = false,
                        error = "Couldn’t import that chart. Choose a valid chart file up to 16 KB. Your previous chart is unchanged.")
                }
            }
        }
    }

    fun refresh() {
        val initial = _session.value ?: return
        if (initial.refreshing) return
        val sessionId = initial.id
        _session.value = initial.copy(refreshing = true, error = null)
        refreshJob = scope.launch {
            try {
                val feed = loadLatestFeed()
                val current = _session.value?.takeIf { it.id == sessionId } ?: return@launch
                val revision = current.snapshot.provenance.revision
                require(revision == null || (feed.provenance.revision != null && feed.provenance.revision >= revision)) {
                    "Older publication"
                }
                val game = feed.games.firstOrNull { it.id == current.snapshot.gameId }
                if (game == null) {
                    _session.value = current.copy(refreshing = false,
                        error = "This game is no longer in the latest update. Showing your saved chart.")
                    return@launch
                }
                val snapshot = VisualizationContract.snapshotFromGame(game, feed.provenance, now())
                VisualizationContract.validateSpec(current.spec, snapshot)
                onAcceptedFeed(feed)
                _session.value = current.copy(snapshot = snapshot, settingsRevision = current.settingsRevision + 1, refreshing = false, error = null)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _session.value?.takeIf { it.id == sessionId }?.let {
                    _session.value = it.copy(refreshing = false,
                        error = "Couldn’t update this chart. Showing your saved chart and stats.")
                }
            }
        }
    }
}

internal const val MAX_SPEC_BYTES = 16 * 1024

internal fun readBoundedSpec(input: InputStream, checkActive: () -> Unit = {}): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(4096)
    while (true) {
        checkActive()
        val count = input.read(buffer, 0, minOf(buffer.size, MAX_SPEC_BYTES - output.size() + 1))
        if (count < 0) break
        if (count == 0) {
            val single = input.read()
            if (single < 0) break
            require(output.size() < MAX_SPEC_BYTES) { "Chart file too large" }
            output.write(single)
            continue
        }
        require(output.size() + count <= MAX_SPEC_BYTES) { "Chart file too large" }
        output.write(buffer, 0, count)
    }
    return Charsets.UTF_8.newDecoder()
        .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT)
        .decode(ByteBuffer.wrap(output.toByteArray())).toString()
}
