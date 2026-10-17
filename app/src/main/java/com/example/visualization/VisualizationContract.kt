package com.example.visualization

import com.example.FeedProvenance
import com.example.Game
import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

/** Only native, validated data enters the bundled renderer. Null means unavailable, never zero. */
data class VisualizationSpec(
    val template: String, val gameId: String, val team: String = "both", val player: String = "all",
    val metric: String = "points", val sort: String = "desc", val title: String? = null
)
data class VisualizationTeam(val id: String, val side: String, val code: String, val name: String, val score: Int?)
data class VisualizationRow(
    val playerId: String, val teamId: String, val name: String,
    val points: Int?, val rebounds: Int?, val assists: Int?, val steals: Int?, val blocks: Int?, val turnovers: Int?,
    val fgMade: Int?, val fgAttempted: Int?, val minutes: Int?, val seconds: Int?
) {
    val fgMissed: Int? get() = if (fgMade != null && fgAttempted != null) fgAttempted - fgMade else null
    val fgPercent: Double? get() = if (fgMade != null && fgAttempted != null && fgAttempted > 0) fgMade * 100.0 / fgAttempted else null
    fun metric(name: String): Int? = when (name) {
        "points" -> points; "rebounds" -> rebounds; "assists" -> assists
        "steals" -> steals; "blocks" -> blocks; "turnovers" -> turnovers
        else -> error("Unsupported metric")
    }
}
class VisualizationSnapshot internal constructor(
    val gameId: String, teams: List<VisualizationTeam>, players: List<VisualizationRow>,
    val provenance: FeedProvenance, val capturedAtMillis: Long, val status: String,
    val date: String?, val competition: String?
) {
    val teams: List<VisualizationTeam> = Collections.unmodifiableList(ArrayList(teams))
    val players: List<VisualizationRow> = Collections.unmodifiableList(ArrayList(players))
}
object VisualizationContract {
    const val MAX_SPEC_BYTES = 16 * 1024
    const val MAX_PAYLOAD_BYTES = 256 * 1024
    val metrics: List<String> = Collections.unmodifiableList(listOf("points", "rebounds", "assists", "steals", "blocks", "turnovers"))
    val templates: List<String> = Collections.unmodifiableList(listOf("playerStats", "fieldGoals"))
    private val specFields = setOf("schemaVersion", "template", "gameId", "team", "player", "metric", "sort", "title")
    private val rowFields = setOf("playerId", "teamId", "name", "points", "rebounds", "assists", "steals", "blocks", "turnovers", "fgMade", "fgAttempted", "minutes", "seconds")

    fun snapshotFromGame(game: Game, provenance: FeedProvenance = FeedProvenance(), capturedAtMillis: Long = System.currentTimeMillis()): VisualizationSnapshot {
        require(game.status in setOf("FINISHED", "LIVE")) { "Player statistics are not available for this game yet" }
        val teams = listOf(VisualizationTeam(game.awayTeam.code, "away", game.awayTeam.code, game.awayTeam.name, game.awayScore), VisualizationTeam(game.homeTeam.code, "home", game.homeTeam.code, game.homeTeam.name, game.homeScore))
        fun players(values: List<com.example.Player>, team: String) = values.map { player ->
            val stat = player.stats
            VisualizationRow(player.id, team, player.name, stat.points, stat.rebounds, stat.assists, stat.steals, stat.blocks, stat.turnovers, stat.fgMade, stat.fgAttempted, stat.minutes, stat.seconds)
        }
        return VisualizationSnapshot(game.id, teams, players(game.startingAway + game.substitutesAway, game.awayTeam.code) + players(game.startingHome + game.substitutesHome, game.homeTeam.code), provenance, capturedAtMillis, game.status, game.gameDate, game.contextLabel).also(::validateSnapshot)
    }

    fun decodeSpec(raw: String, snapshot: VisualizationSnapshot): VisualizationSpec {
        val obj = objectValue(StrictJson.parse(raw, MAX_SPEC_BYTES))
        require(obj.keys.all { it in specFields }) { "Unknown specification field" }
        require(integer(obj["schemaVersion"], "schemaVersion", 1) == 1) { "Unsupported schema version" }
        val template = text(obj["template"], "template")
        require(template != "fieldGoals" || !obj.containsKey("metric")) { "Field-goal specifications must omit metric" }
        fun default(key: String, value: String) = if (obj.containsKey(key)) text(obj[key], key) else value
        val spec = VisualizationSpec(template, identity(obj["gameId"], "gameId"), default("team", "both"), default("player", "all"), default("metric", "points"), default("sort", "desc"), if (obj.containsKey("title")) text(obj["title"], "title", 120) else null)
        validateSpec(spec, snapshot)
        return spec
    }

    fun validateSpec(spec: VisualizationSpec, snapshot: VisualizationSnapshot) {
        validateSnapshot(snapshot)
        require(spec.template in templates) { "Unsupported template" }
        require(spec.gameId == snapshot.gameId) { "Specification belongs to a different game" }
        require(spec.team in listOf("both", "home", "away")) { "Unsupported team filter" }
        require(spec.metric in metrics && (spec.template != "fieldGoals" || spec.metric == "points")) { "Unsupported metric" }
        require(spec.sort in listOf("asc", "desc", "name")) { "Unsupported sort" }
        spec.title?.let { text(it, "title", 120) }; if (spec.player != "all") identity(spec.player, "player ID")
        if (spec.player != "all") {
            val player = snapshot.players.singleOrNull { it.playerId == spec.player } ?: error("Unknown player")
            require(spec.team == "both" || snapshot.teams.single { it.id == player.teamId }.side == spec.team) { "Player belongs to a different team" }
        }
    }

    fun rows(snapshot: VisualizationSnapshot, spec: VisualizationSpec): List<VisualizationRow> {
        validateSpec(spec, snapshot)
        val teamIds = snapshot.teams.filter { spec.team == "both" || it.side == spec.team }.map { it.id }
        val filtered = snapshot.players.filter { it.teamId in teamIds && (spec.player == "all" || it.playerId == spec.player) }
        val ordered = if (spec.sort == "name") filtered.sortedWith(compareBy<VisualizationRow> { it.name.lowercase(java.util.Locale.ROOT) }.thenBy { it.playerId }) else filtered.sortedWith(Comparator { a, b ->
            val av = if (spec.template == "fieldGoals") a.fgAttempted else a.metric(spec.metric)
            val bv = if (spec.template == "fieldGoals") b.fgAttempted else b.metric(spec.metric)
            val number = when { av == null && bv == null -> 0; av == null -> 1; bv == null -> -1; spec.sort == "asc" -> av.compareTo(bv); else -> bv.compareTo(av) }
            if (number != 0) number else a.name.compareTo(b.name, ignoreCase = true).let { if (it == 0) a.playerId.compareTo(b.playerId) else it }
        })
        return Collections.unmodifiableList(ordered)
    }

    /** FG percentage is weighted by attempts; any unavailable input makes the aggregate unavailable. */
    fun weightedFgPercent(rows: List<VisualizationRow>): Double? {
        if (rows.any { it.fgMade == null || it.fgAttempted == null }) return null
        val attempts = rows.sumOf { it.fgAttempted ?: 0 }
        return if (attempts == 0) null else rows.sumOf { it.fgMade ?: 0 } * 100.0 / attempts
    }

    /** Versioned payload import is a validation boundary; production sessions only capture live native games. */
    fun decodePayload(raw: String): VisualizationSnapshot {
        val obj = objectValue(StrictJson.parse(raw, MAX_PAYLOAD_BYTES))
        requireKeys(obj, setOf("schemaVersion", "gameId", "teams", "players", "provenance", "capturedAtMillis", "status", "date", "competition"))
        require(integer(obj["schemaVersion"], "schemaVersion", 1) == 1)
        val teams = arrayValue(obj["teams"]).map { value ->
            val team = objectValue(value); requireKeys(team, setOf("id", "side", "code", "name", "score"))
            VisualizationTeam(identity(team["id"], "team ID"), text(team["side"], "side"), text(team["code"], "code"), text(team["name"], "team name", 160), nullableInteger(team["score"], "score", 10000))
        }
        val players = arrayValue(obj["players"]).map { value ->
            val row = objectValue(value); requireKeys(row, rowFields)
            fun stat(key: String, max: Int = 10000) = nullableInteger(row[key], key, max)
            VisualizationRow(identity(row["playerId"], "player ID"), identity(row["teamId"], "team ID"), text(row["name"], "name", 160), stat("points"), stat("rebounds"), stat("assists"), stat("steals"), stat("blocks"), stat("turnovers"), stat("fgMade"), stat("fgAttempted"), stat("minutes", 100), stat("seconds", 59))
        }
        val meta = objectValue(obj["provenance"]); requireKeys(meta, setOf("revision", "publishedAt", "fetchedAtMillis", "fromCache"))
        require(meta["fromCache"] is Boolean)
        val provenance = FeedProvenance(nullableLong(meta["revision"], "revision"), optionalText(meta["publishedAt"], "publication time", 64), nullableLong(meta["fetchedAtMillis"], "fetch time"), meta["fromCache"] as Boolean)
        return VisualizationSnapshot(identity(obj["gameId"], "game ID"), teams, players, provenance, integerLong(obj["capturedAtMillis"], "capture time"), text(obj["status"], "status"), optionalText(obj["date"], "date"), optionalText(obj["competition"], "competition")).also(::validateSnapshot)
    }

    fun encodeForRenderer(snapshot: VisualizationSnapshot, spec: VisualizationSpec): String {
        val visible = rows(snapshot, spec)
        val json = snapshotJson(snapshot, visible).put("spec", specJson(spec)).put("weightedFgPercent", weightedFgPercent(visible) ?: JSONObject.NULL)
        val players = json.getJSONArray("players")
        visible.forEachIndexed { i, row -> players.getJSONObject(i).put("fgMissed", row.fgMissed ?: JSONObject.NULL).put("fgPercent", row.fgPercent ?: JSONObject.NULL) }
        val encoded = json.toString()
        require(encoded.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) { "Renderer payload exceeds limit" }
        return encoded
    }
    fun encodePayload(snapshot: VisualizationSnapshot): String { validateSnapshot(snapshot); return snapshotJson(snapshot, snapshot.players).toString().also { require(it.toByteArray(Charsets.UTF_8).size <= MAX_PAYLOAD_BYTES) } }
    fun encodeSpec(spec: VisualizationSpec): String = specJson(spec).toString()
    private fun specJson(spec: VisualizationSpec) = JSONObject().put("schemaVersion", 1).put("template", spec.template).put("gameId", spec.gameId).put("team", spec.team).put("player", spec.player).put("sort", spec.sort).also { if (spec.template == "playerStats") it.put("metric", spec.metric); if (spec.title != null) it.put("title", spec.title) }
    private fun snapshotJson(snapshot: VisualizationSnapshot, rows: List<VisualizationRow>) = JSONObject().put("schemaVersion", 1).put("gameId", snapshot.gameId).put("status", snapshot.status).put("date", snapshot.date ?: JSONObject.NULL).put("competition", snapshot.competition ?: JSONObject.NULL).put("capturedAtMillis", snapshot.capturedAtMillis)
        .put("teams", JSONArray(snapshot.teams.map { JSONObject().put("id", it.id).put("side", it.side).put("code", it.code).put("name", it.name).put("score", it.score ?: JSONObject.NULL) }))
        .put("players", JSONArray(rows.map { row -> JSONObject().put("playerId", row.playerId).put("teamId", row.teamId).put("name", row.name).also { obj -> metrics.forEach { obj.put(it, row.metric(it) ?: JSONObject.NULL) } }.put("fgMade", row.fgMade ?: JSONObject.NULL).put("fgAttempted", row.fgAttempted ?: JSONObject.NULL).put("minutes", row.minutes ?: JSONObject.NULL).put("seconds", row.seconds ?: JSONObject.NULL) }))
        .put("provenance", JSONObject().put("revision", snapshot.provenance.revision ?: JSONObject.NULL).put("publishedAt", snapshot.provenance.publishedAt ?: JSONObject.NULL).put("fetchedAtMillis", snapshot.provenance.fetchedAtMillis ?: JSONObject.NULL).put("fromCache", snapshot.provenance.fromCache))

    private fun validateSnapshot(snapshot: VisualizationSnapshot) {
        identity(snapshot.gameId, "game ID"); require(snapshot.status in setOf("FINISHED", "LIVE")) { "Unsupported game status" }
        require(snapshot.capturedAtMillis >= 0 && (snapshot.provenance.revision ?: 0) >= 0 && (snapshot.provenance.fetchedAtMillis ?: 0) >= 0)
        snapshot.date?.let { require(StrictJson.validDate(it)) }; snapshot.competition?.let { text(it, "competition") }
        snapshot.provenance.publishedAt?.let { require(it.length <= 64 && StrictJson.validPublicationTime(it)) }
        require(snapshot.teams.size == 2 && snapshot.teams.map { it.id }.distinct().size == 2 && snapshot.teams.map { it.code }.distinct().size == 2 && snapshot.teams.map { it.side }.toSet() == setOf("away", "home")) { "Expected two distinct teams" }
        snapshot.teams.forEach { identity(it.id, "team ID"); text(it.name, "team name", 160); require(it.code.matches(Regex("[A-Z]{2,4}"))); it.score?.let { score -> require(score in 0..10000) } }
        require(snapshot.players.size in 1..80 && snapshot.players.map { it.playerId }.distinct().size == snapshot.players.size) { "Invalid player roster" }
        require(snapshot.teams.all { team -> snapshot.players.count { it.teamId == team.id } <= 40 }) { "Too many players per team" }
        snapshot.players.forEach { row ->
            identity(row.playerId, "player ID"); identity(row.teamId, "team ID"); text(row.name, "player name", 160); require(snapshot.teams.any { it.id == row.teamId }) { "Unknown team binding" }
            metrics.forEach { require(row.metric(it) == null || row.metric(it)!! in 0..10000) }
            require(row.fgMade == null || row.fgMade in 0..10000); require(row.fgAttempted == null || row.fgAttempted in 0..10000)
            require(row.fgMade == null || row.fgAttempted == null || row.fgMade <= row.fgAttempted) { "FGM exceeds FGA" }
            require(row.minutes == null || row.minutes in 0..100); require(row.seconds == null || row.seconds in 0..59)
        }
    }
    private fun requireKeys(obj: Map<String, Any?>, keys: Set<String>) { require(obj.keys == keys) { "Missing or unknown payload fields" } }
    @Suppress("UNCHECKED_CAST") private fun objectValue(value: Any?): Map<String, Any?> = value as? Map<String, Any?> ?: error("Expected JSON object")
    private fun arrayValue(value: Any?): List<*> = value as? List<*> ?: error("Expected JSON array")
    private fun identity(value: Any?, field: String): String = text(value, field, 128).also { require(it != "all") { "Reserved identity" } }
    private fun text(value: Any?, field: String, max: Int = 256): String { require(value is String && value.isNotBlank() && value.length <= max && StrictJson.safeName(value)) { "Invalid $field" }; return value }
    private fun optionalText(value: Any?, field: String, max: Int = 256): String? = if (value == null) null else text(value, field, max)
    private fun integerLong(value: Any?, field: String): Long { require(value is Long && value >= 0) { "$field must be a nonnegative JSON integer" }; return value }
    private fun nullableLong(value: Any?, field: String): Long? = if (value == null) null else integerLong(value, field)
    private fun integer(value: Any?, field: String, max: Int): Int = integerLong(value, field).also { require(it <= max) { "$field exceeds limit" } }.toInt()
    private fun nullableInteger(value: Any?, field: String, max: Int): Int? = if (value == null) null else integer(value, field, max)
}
