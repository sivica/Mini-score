package com.example

import android.util.AtomicFile
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import java.io.IOException
import kotlin.coroutines.coroutineContext
import com.example.visualization.StrictJson
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class FeedProvenance(
    val revision: Long? = null,
    val publishedAt: String? = null,
    val fetchedAtMillis: Long? = null,
    val fromCache: Boolean = false
)
data class FeedDownload(val json: String, val revision: Long? = null, val publishedAt: String? = null)
data class GameFeed(val defaultGameId: String, val games: List<Game>, val provenance: FeedProvenance = FeedProvenance())

/** Reject a broken publication as a whole, keeping the last working feed on the device. */
object GameFeedCodec {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun decode(json: String): GameFeed {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Feed too large" }
        StrictJson.parse(json, MAX_BYTES, 16)
        val root = JSONObject(json)
        require(root.stat("schemaVersion", 1) == 1) { "Unsupported feed version" }
        val entries = root.getJSONArray("games")
        require(entries.length() in 1..100) { "Expected 1–100 games" }
        val games = (0 until entries.length()).map { game(entries.getJSONObject(it)) }
        require(games.map { it.id }.distinct().size == games.size) { "Duplicate game IDs" }
        require(games.none { it.id in listOf("nyk_cle", "lal_gsw", "bos_mia") }) { "Reserved demo ID" }
        val defaultId = root.text("defaultGameId")
        require(games.any { it.id == defaultId }) { "Missing default game" }
        return GameFeed(defaultId, games)
    }

    private fun game(json: JSONObject): Game {
        val date = json.text("date")
        require(date.matches(Regex("\\d{4}-\\d{2}-\\d{2}"))) { "Date must be YYYY-MM-DD" }
        val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            isLenient = false
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val parsed = requireNotNull(formatter.parse(date))
        val label = SimpleDateFormat("EEEE, MMMM d, yyyy", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(parsed)
        val away = json.getJSONObject("away")
        val home = json.getJSONObject("home")
        val awayTeam = team(away)
        val homeTeam = team(home)
        require(awayTeam.code != homeTeam.code) { "Teams must differ" }
        val awayPlayers = players(away, awayTeam.code, true)
        val homePlayers = players(home, homeTeam.code, false)
        require((awayPlayers + homePlayers).map { it.id }.distinct().size == awayPlayers.size + homePlayers.size) {
            "Duplicate player IDs"
        }
        val awayQuarters = quarters(away)
        val homeQuarters = quarters(home)
        require(awayQuarters.size == homeQuarters.size) { "Mismatched quarters" }
        val status = json.text("status")
        require(status in listOf("SCHEDULED", "FINISHED", "LIVE", "POSTPONED", "CANCELLED")) { "Unknown status" }
        return Game(
            id = json.text("id"), awayTeam = awayTeam, homeTeam = homeTeam,
            awayScore = away.stat("score", 500), homeScore = home.stat("score", 500), status = status,
            startingAway = awayPlayers.filter { it.isStarting }, startingHome = homePlayers.filter { it.isStarting },
            substitutesAway = awayPlayers.filterNot { it.isStarting }, substitutesHome = homePlayers.filterNot { it.isStarting },
            contextLabel = json.text("competition"), dateLabel = label, gameDate = date,
            venue = json.optionalText("venue"), awayQuarters = awayQuarters, homeQuarters = homeQuarters,
            tipoffUtc = json.optionalText("tipoffUTC"),
            minutesLeft = 0
        )
    }

    private fun team(json: JSONObject): Team {
        val code = json.text("code")
        require(code.matches(Regex("[A-Z]{2,4}"))) { "Invalid team code" }
        fun color(key: String, fallback: String): Color {
            val hex = json.optString(key, fallback)
            require(hex.matches(Regex("#[0-9a-fA-F]{6}"))) { "Color must be #RRGGBB" }
            return Color(0xFF000000 or hex.drop(1).toLong(16))
        }
        val name = json.text("name")
        return Team(code, name, color("primaryColor", "#777777"), color("secondaryColor", "#FFFFFF"), code,
            json.optionalText("shortName") ?: name, json.imageUrl("logoUrl"))
    }

    private fun players(team: JSONObject, code: String, left: Boolean): List<Player> {
        val array = team.getJSONArray("players")
        require(array.length() <= 30) { "Too many players" }
        return (0 until array.length()).map { index ->
            val json = array.getJSONObject(index)
            val stats = json.getJSONObject("stats")
            val fgMade = stats.stat("fgMade", 100)
            val fgAttempted = stats.stat("fgAttempted", 100)
            require(fgMade <= fgAttempted) { "FGM exceeds FGA" }
            Player(
                id = json.text("id"), name = json.text("name"), number = json.text("number"), flag = "",
                isLeftTeam = left, isStarting = json.getBoolean("starter"), rating = null,
                stats = PlayerStats(
                    points = stats.stat("points", 200), rebounds = stats.stat("rebounds", 100),
                    assists = stats.stat("assists", 100), steals = stats.stat("steals", 100),
                    blocks = stats.stat("blocks", 100), turnovers = stats.stat("turnovers", 100),
                    fgMade = fgMade, fgAttempted = fgAttempted,
                    minutes = stats.stat("minutes", 100), seconds = stats.stat("seconds", 59)
                ), portraitUrl = json.imageUrl("portraitUrl"), teamCode = code
            )
        }.also { require(it.count { p -> p.isStarting } <= 5) { "More than five starters" } }
    }

    private fun quarters(json: JSONObject): List<Int> {
        if (!json.has("quarters")) return emptyList()
        val array = json.getJSONArray("quarters")
        require(array.length() <= 15) { "Too many periods" }
        return (0 until array.length()).map { strictInteger(array.get(it), "quarters[$it]", 100) }
    }

    private fun JSONObject.stat(key: String, max: Int): Int = strictInteger(get(key), key, max)

    private fun strictInteger(raw: Any, field: String, max: Int): Int {
        require(raw is Int || raw is Long) { "$field must be a JSON integer" }
        val value = (raw as Number).toLong()
        require(value in 0..max.toLong()) { "Invalid $field" }
        return value.toInt()
    }

    private fun JSONObject.text(key: String): String = getString(key).trim().also {
        require(it.isNotEmpty() && it.length <= 200) { "Missing or long $key" }
    }
    private fun JSONObject.optionalText(key: String): String? =
        if (!has(key) || isNull(key)) null else text(key)

    private fun JSONObject.imageUrl(key: String): String? = optionalText(key)?.also {
        val url = it.toHttpUrlOrNull()
        require(url != null && url.isHttps && url.username.isEmpty() && url.password.isEmpty()) { "Image URL must be HTTPS" }
    }
}

class GameFeedRepository private constructor(
    private val cache: File,
    private val transport: suspend () -> FeedDownload,
    private val clock: () -> Long
) {
    constructor(cache: File) : this(cache, { downloadFeed() }, System::currentTimeMillis)
    /** Retained for callers/tests that supply a plain JSON publication without HTTP metadata. */
    constructor(cache: File, download: () -> String) : this(cache, { FeedDownload(download()) }, System::currentTimeMillis)
    private val lock = synchronized(locks) { locks.getOrPut(cache.canonicalPath) { Any() } }
    private var acceptedRevision: Long? = null

    suspend fun cachedFeed(): GameFeed? = withContext(Dispatchers.IO) {
        synchronized(lock) { readCache()?.let { it.copy(provenance = it.provenance.copy(fromCache = true)) } }
    }

    private fun readCache(): GameFeed? = try {
        val raw = AtomicFile(cache).openRead().use { it.readNBytesCompat(GameFeedCodec.MAX_BYTES + 8192).toString(Charsets.UTF_8) }
        StrictJson.parse(raw, GameFeedCodec.MAX_BYTES + 8192, 17)
        val root = JSONObject(raw)
        if (!root.has("cacheSchemaVersion")) GameFeedCodec.decode(raw)
        else {
            require(root.get("cacheSchemaVersion") == 1)
            require(root.keys().asSequence().toSet() == setOf("cacheSchemaVersion", "feed", "provenance"))
            val meta = root.getJSONObject("provenance")
            require(meta.keys().asSequence().toSet() == setOf("revision", "publishedAt", "fetchedAtMillis"))
            fun nullableLong(key: String): Long? = if (meta.isNull(key)) null else {
                val value = meta.get(key); require(value is Int || value is Long); (value as Number).toLong().also { require(it >= 0) }
            }
            val provenance = FeedProvenance(nullableLong("revision"), if (meta.isNull("publishedAt")) null else meta.getString("publishedAt"), nullableLong("fetchedAtMillis"))
            validateProvenance(provenance)
            GameFeedCodec.decode(root.getJSONObject("feed").toString()).copy(provenance = provenance)
        }
    } catch (_: Exception) { null }


    suspend fun refresh(): GameFeed = withContext(Dispatchers.IO) {
        coroutineContext.ensureActive()
        val downloaded = transport()
        coroutineContext.ensureActive()
        val provenance = FeedProvenance(downloaded.revision, downloaded.publishedAt, clock())
        validateProvenance(provenance)
        val feed = GameFeedCodec.decode(downloaded.json).copy(provenance = provenance)
        synchronized(lock) {
            coroutineContext.ensureActive()
            val currentRevision = listOfNotNull(acceptedRevision, readCache()?.provenance?.revision).maxOrNull()
            require(currentRevision == null || (provenance.revision != null && provenance.revision >= currentRevision)) { "Publication is older than the accepted feed" }
            // The feed and its metadata commit together; rejected feeds never touch the atomic cache.
            val envelope = JSONObject().put("cacheSchemaVersion", 1).put("feed", JSONObject(downloaded.json))
                .put("provenance", JSONObject().put("revision", provenance.revision ?: JSONObject.NULL)
                    .put("publishedAt", provenance.publishedAt ?: JSONObject.NULL)
                    .put("fetchedAtMillis", provenance.fetchedAtMillis ?: JSONObject.NULL)).toString()
            val atomic = AtomicFile(cache)
            var output: java.io.FileOutputStream? = null
            try {
                cache.parentFile?.mkdirs()
                output = atomic.startWrite()
                output.write(envelope.toByteArray(Charsets.UTF_8))
                coroutineContext.ensureActive()
                atomic.finishWrite(output)
            } catch (cancelled: kotlinx.coroutines.CancellationException) { atomic.failWrite(output); throw cancelled }
            catch (_: Exception) { atomic.failWrite(output) }
            acceptedRevision = provenance.revision ?: acceptedRevision
            feed
        }
    }

    companion object {
        const val FEED_URL = BuildConfig.GAME_FEED_URL
        private val locks = mutableMapOf<String, Any>()
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()
        fun withDownload(cache: File, download: () -> FeedDownload, clock: () -> Long = System::currentTimeMillis) = GameFeedRepository(cache, { download() }, clock)

        private fun validateProvenance(value: FeedProvenance) {
            require(value.revision == null || value.revision >= 0) { "Invalid revision" }
            require(value.fetchedAtMillis == null || value.fetchedAtMillis >= 0) { "Invalid fetch time" }
            value.publishedAt?.let { require(it.length <= 64 && StrictJson.validPublicationTime(it)) { "Invalid publication time" } }
        }
        private suspend fun downloadFeed(): FeedDownload = suspendCancellableCoroutine { continuation ->
            val request = Request.Builder().url(FEED_URL).header("Cache-Control", "no-cache").build()
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : Callback {
                override fun onFailure(call: Call, error: IOException) {
                    if (continuation.isActive) continuation.resumeWithException(error)
                }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        val value = decodeResponse(response)
                        if (continuation.isActive) continuation.resume(value)
                    } catch (error: Exception) {
                        if (continuation.isActive) continuation.resumeWithException(error)
                    }
                }
            })
        }

        private fun decodeResponse(response: Response): FeedDownload {
            return response.use {
                check(response.isSuccessful) { "Feed HTTP ${response.code}" }
                val body = checkNotNull(response.body)
                require(body.contentLength() <= GameFeedCodec.MAX_BYTES) { "Feed too large" }
                val revision = response.header("X-Miniscore-Revision")?.let {
                    require(it.matches(Regex("0|[1-9][0-9]*"))) { "Invalid revision header" }
                    it.toLongOrNull() ?: error("Revision overflow")
                }
                val raw = body.byteStream().use { it.readNBytesCompat(GameFeedCodec.MAX_BYTES).toString(Charsets.UTF_8) }
                FeedDownload(raw, revision, response.header("X-Miniscore-Updated-At"))
            }
        }
    }
}

private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    while (true) {
        val size = read(buffer)
        if (size < 0) break
        require(output.size() + size <= limit) { "Feed too large" }
        output.write(buffer, 0, size)
    }
    return output.toByteArray()
}
