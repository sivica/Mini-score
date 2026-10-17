package com.example

import android.util.AtomicFile
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

data class GameFeed(val defaultGameId: String, val games: List<Game>)

/** Reject a broken publication as a whole, keeping the last working feed on the device. */
object GameFeedCodec {
    const val MAX_BYTES = 2 * 1024 * 1024

    fun decode(json: String): GameFeed {
        require(json.toByteArray(Charsets.UTF_8).size <= MAX_BYTES) { "Feed too large" }
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
                ), portraitUrl = json.imageUrl("portraitUrl"), teamCode = code,
                position = json.optionalText("position")?.also { require(isPlayerPosition(it)) }
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

class GameFeedRepository(
    private val cache: File,
    private val download: () -> String = { downloadFeed() }
) {
    suspend fun cachedFeed(): GameFeed? = withContext(Dispatchers.IO) {
        try {
            val atomic = AtomicFile(cache)
            atomic.openRead().use { input ->
                val bytes = input.readNBytesCompat(GameFeedCodec.MAX_BYTES)
                GameFeedCodec.decode(bytes.toString(Charsets.UTF_8))
            }
        } catch (_: Exception) { null }
    }

    suspend fun refresh(): GameFeed = withContext(Dispatchers.IO) {
        val json = download()
        val feed = GameFeedCodec.decode(json)
        // A storage failure must not discard valid downloaded games from this session.
        val atomic = AtomicFile(cache)
        var output: java.io.FileOutputStream? = null
        try {
            cache.parentFile?.mkdirs()
            output = atomic.startWrite()
            output.write(json.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(output)
        } catch (_: Exception) { atomic.failWrite(output) }
        feed
    }

    companion object {
        const val FEED_URL = BuildConfig.GAME_FEED_URL
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(25, TimeUnit.SECONDS).build()

        private fun downloadFeed(): String {
            val request = Request.Builder().url(FEED_URL).header("Cache-Control", "no-cache").build()
            return client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Feed HTTP ${response.code}" }
                val body = checkNotNull(response.body)
                require(body.contentLength() <= GameFeedCodec.MAX_BYTES) { "Feed too large" }
                body.byteStream().use { it.readNBytesCompat(GameFeedCodec.MAX_BYTES).toString(Charsets.UTF_8) }
            }
        }
    }
}

internal fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
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
