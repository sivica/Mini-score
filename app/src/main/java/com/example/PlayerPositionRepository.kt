package com.example

import android.util.AtomicFile
import java.io.File
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

internal fun isPlayerPosition(value: String): Boolean = value.matches(Regex("[A-Z]{1,2}(-[A-Z]{1,2})?"))

/** Optional preview metadata: never changes scores, minutes, rosters or the validated game cache. */
class PlayerPositionRepository(
    private val cache: File,
    private val download: (String) -> String = { downloadSummary(it) }
) {
    private val mutex = Mutex()
    private val positions = linkedMapOf<String, Map<String, String>>()
    private var loaded = false

    suspend fun forGame(gameId: String): Map<String, String> = withContext(Dispatchers.IO) {
        if (!gameId.matches(Regex("espn_[0-9]+"))) return@withContext emptyMap()
        mutex.withLock {
            if (!loaded) {
                try {
                    val root = AtomicFile(cache).openRead().use {
                        JSONObject(it.readNBytesCompat(GameFeedCodec.MAX_BYTES).toString(Charsets.UTF_8))
                    }
                    require(root.length() <= 100)
                    val restored = root.keys().asSequence().associateWith { id ->
                        require(id.matches(Regex("espn_[0-9]+")))
                        val players = root.getJSONObject(id)
                        require(players.length() <= 60)
                        players.keys().asSequence().associateWith { pid ->
                            require(pid.matches(Regex("espn_[0-9]+")))
                            players.getString(pid).also { require(isPlayerPosition(it)) }
                        }
                    }
                    positions.putAll(restored)
                } catch (_: Exception) { /* Missing/broken optional metadata cannot invalidate saved games. */ }
                loaded = true
            }
            positions[gameId]?.let { return@withLock it }
            val result = decode(download(gameId.removePrefix("espn_")), gameId.removePrefix("espn_"))
            positions[gameId] = result
            while (positions.size > 100) positions.remove(positions.keys.first())
            val atomic = AtomicFile(cache)
            var output: java.io.FileOutputStream? = null
            try {
                cache.parentFile?.mkdirs()
                output = atomic.startWrite()
                output.write(JSONObject(positions.mapValues { JSONObject(it.value) }).toString().toByteArray(Charsets.UTF_8))
                atomic.finishWrite(output)
            } catch (_: Exception) { atomic.failWrite(output) }
            result
        }
    }

    companion object {
        private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()

        internal fun decode(source: String, eventId: String): Map<String, String> {
            require(source.toByteArray(Charsets.UTF_8).size <= GameFeedCodec.MAX_BYTES)
            val root = JSONObject(source)
            require(root.getJSONObject("header").getString("id") == eventId) { "Wrong position metadata game" }
            val teams = root.getJSONObject("boxscore").getJSONArray("players")
            require(teams.length() == 2)
            val result = mutableMapOf<String, String>()
            for (t in 0 until teams.length()) {
                val groups = teams.getJSONObject(t).getJSONArray("statistics")
                require(groups.length() in 1..10)
                for (g in 0 until groups.length()) {
                    val players = groups.getJSONObject(g).getJSONArray("athletes")
                    require(players.length() <= 30)
                    for (p in 0 until players.length()) {
                        val athlete = players.getJSONObject(p).getJSONObject("athlete")
                        val id = athlete.getString("id")
                        require(id.matches(Regex("[0-9]+")))
                        val position = athlete.optJSONObject("position")?.optString("abbreviation")
                        if (position != null && isPlayerPosition(position)) result["espn_$id"] = position
                    }
                }
            }
            return result
        }

        private fun downloadSummary(eventId: String): String {
            require(eventId.matches(Regex("[0-9]+")))
            val request = Request.Builder()
                .url("https://site.api.espn.com/apis/site/v2/sports/basketball/nba/summary?event=$eventId")
                .header("User-Agent", "Miniscore-feed/1.0").build()
            return client.newCall(request).execute().use { response ->
                check(response.isSuccessful) { "Position metadata HTTP ${response.code}" }
                checkNotNull(response.body).byteStream().use {
                    it.readNBytesCompat(GameFeedCodec.MAX_BYTES).toString(Charsets.UTF_8)
                }
            }
        }
    }
}

internal fun Game.withPlayerPositions(positions: Map<String, String>): Game {
    fun enrich(players: List<Player>) = players.map { player ->
        player.copy(position = player.position ?: positions[player.id])
    }
    return copy(startingAway = enrich(startingAway), startingHome = enrich(startingHome),
        substitutesAway = enrich(substitutesAway), substitutesHome = enrich(substitutesHome))
}
