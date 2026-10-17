package com.example.visualization

import com.example.FeedProvenance
import com.example.PreseasonGameData
import org.json.JSONObject
import org.json.JSONArray
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class VisualizationContractTest {
    private fun snapshot() = VisualizationContract.snapshotFromGame(PreseasonGameData.createGame(), FeedProvenance(7, "2026-10-11T08:00:00Z", 1000), 1100)
    private fun rawSpec(template: String = "playerStats") = """{"schemaVersion":1,"template":"$template","gameId":"0012600034"}"""
    private fun reject(raw: String) = assertThrows(Exception::class.java) { VisualizationContract.decodeSpec(raw, snapshot()) }
    @Test fun strictSpecRequiresVersionIdentityEnumsAndCanonicalFields() {
        val valid = VisualizationContract.decodeSpec(rawSpec(), snapshot())
        assertEquals("points", valid.metric)
        assertEquals("fieldGoals", VisualizationContract.decodeSpec(rawSpec("fieldGoals"), snapshot()).template)
        for (raw in listOf(
            rawSpec().replace("\"schemaVersion\":1,", ""), rawSpec().replace(":1,", ":1.0,"), rawSpec().replace(":1,", ":true,"), rawSpec().replace(":1,", ":\"1\","), rawSpec().replace(":1,", ":2,"),
            rawSpec().replace("0012600034", "other"), rawSpec("html"), rawSpec().replace("}", ",\"schemaVersion\":1}"), rawSpec().replace("}", ",\"unknown\":1}"), rawSpec().replace("}", ",\"__proto__\":{}}"),
            rawSpec().replace("}", ",\"title\":\"constructor\"}"), rawSpec().replace("}", ",\"team\":\"PHI\"}"), rawSpec().replace("}", ",\"sort\":\"random\"}"), rawSpec().replace("}", ",\"metric\":\"fgMade\"}"),
            rawSpec("fieldGoals").replace("}", ",\"metric\":\"points\"}"), rawSpec().replace("}", ",\"title\":\"${"x".repeat(121)}\"}"), rawSpec().replace("}", ",\"player\":\"bkn_30\",\"team\":\"away\"}"),
            "// comment\n" + rawSpec(), rawSpec().dropLast(1) + ",}", rawSpec() + " trailing", "[".repeat(9) + "0" + "]".repeat(9), " ".repeat(VisualizationContract.MAX_SPEC_BYTES) + rawSpec()
        )) reject(raw)
    }
    @Test fun snapshotsCopyMutableSourceAndExposeReadOnlyCollections() {
        val game = PreseasonGameData.createGame()
        val original = VisualizationContract.snapshotFromGame(game)
        val points = original.players.first().points
        game.startingAway.first().stats.points = 199
        assertEquals(points, original.players.first().points)
        assertThrows(UnsupportedOperationException::class.java) { (original.players as MutableList).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (original.teams as MutableList).clear() }
    }
    @Test fun payloadRoundTripRetainsUnknownZeroAndDerivedWeightedValues() {
        val raw = JSONObject(VisualizationContract.encodePayload(snapshot()))
        val players = raw.getJSONArray("players")
        players.getJSONObject(0).put("points", JSONObject.NULL).put("fgMade", 1).put("fgAttempted", 1)
        players.getJSONObject(1).put("points", 0).put("fgMade", 1).put("fgAttempted", 9)
        players.getJSONObject(2).put("fgMade", 0).put("fgAttempted", 0)
        val decoded = VisualizationContract.decodePayload(raw.toString())
        assertNull(decoded.players[0].points)
        assertEquals(0, decoded.players[1].points)
        assertEquals(8, decoded.players[1].fgMissed)
        assertNull(decoded.players[2].fgPercent)
        assertEquals(20.0, VisualizationContract.weightedFgPercent(decoded.players.take(2))!!, 0.00001)
        assertEquals(1100L, decoded.capturedAtMillis)
        assertEquals(1000L, decoded.provenance.fetchedAtMillis)
        assertEquals("2026-10-11T08:00:00Z", decoded.provenance.publishedAt)
        assertEquals(7L, decoded.provenance.revision)
    }
    @Test fun malformedCountsContainersAndBindingsAreRejected() {
        val valid = VisualizationContract.encodePayload(snapshot())
        fun rejects(change: (JSONObject) -> Unit) { val root = JSONObject(valid).also(change); assertThrows(Exception::class.java) { VisualizationContract.decodePayload(root.toString()) } }
        for (value in listOf("2", true, -1, 10001, 4294967297L)) rejects { it.getJSONArray("players").getJSONObject(0).put("points", value) }
        for (value in listOf("[]", JSONObject(), true, JSONObject.NULL)) rejects { it.put("players", value) }
        rejects { it.getJSONArray("players").getJSONObject(0).put("teamId", "other") }
        rejects { it.getJSONArray("players").getJSONObject(1).put("playerId", it.getJSONArray("players").getJSONObject(0).getString("playerId")) }
        rejects { it.getJSONArray("players").getJSONObject(0).put("fgMade", 100).put("fgAttempted", 1) }
        rejects { it.getJSONArray("players").getJSONObject(0).remove("points") }
        rejects { it.put("schemaVersion", 2) }
        rejects { it.put("date", "2026-02-30") }
        rejects { it.getJSONObject("provenance").put("publishedAt", "2026-10-11T25:00:00Z") }
        rejects { it.getJSONArray("players").getJSONObject(0).put("playerId", "all") }
        rejects { it.getJSONArray("players").getJSONObject(0).put("name", "x".repeat(161)) }
        rejects { it.getJSONArray("players").getJSONObject(0).put("playerId", "x".repeat(129)) }
        assertThrows(Exception::class.java) { VisualizationContract.decodePayload(valid.replaceFirst("\"points\":10", "\"points\":10.0")) }
        assertThrows(Exception::class.java) { VisualizationContract.decodePayload(" ".repeat(VisualizationContract.MAX_PAYLOAD_BYTES) + valid) }
    }
    @Test fun maximumRostersAndMarksAreBoundedPerTeamAndOverall() {
        val root = JSONObject(VisualizationContract.encodePayload(snapshot()))
        val old = root.getJSONArray("players")
        val away = JSONObject(old.getJSONObject(0).toString())
        val home = JSONObject(old.getJSONObject(15).toString())
        val players = JSONArray()
        for (i in 0 until 40) { players.put(JSONObject(away.toString()).put("playerId", "away-$i")); players.put(JSONObject(home.toString()).put("playerId", "home-$i")) }
        root.put("players", players)
        val max = VisualizationContract.decodePayload(root.toString())
        assertEquals(80, max.players.size)
        assertEquals(80, VisualizationContract.rows(max, VisualizationSpec("fieldGoals", max.gameId)).size)
        players.put(JSONObject(away.toString()).put("playerId", "away-40"))
        assertThrows(Exception::class.java) { VisualizationContract.decodePayload(root.toString()) }
        players.remove(80); players.getJSONObject(79).put("teamId", away.getString("teamId"))
        assertThrows(Exception::class.java) { VisualizationContract.decodePayload(root.toString()) }
    }
    @Test fun nativeFiltersSortUnavailableLastAndLabelsStayData() {
        val raw = JSONObject(VisualizationContract.encodePayload(snapshot()))
        raw.getJSONArray("players").getJSONObject(0).put("points", JSONObject.NULL).put("name", "<img src=x onerror=alert(1)>")
        val value = VisualizationContract.decodePayload(raw.toString())
        val spec = VisualizationSpec("playerStats", value.gameId, "away")
        val rows = VisualizationContract.rows(value, spec)
        assertEquals(15, rows.size)
        assertNull(rows.last().points)
        assertTrue(rows.all { it.teamId == "PHI" })
        val encoded = JSONObject(VisualizationContract.encodeForRenderer(value, spec))
        assertEquals("<img src=x onerror=alert(1)>", encoded.getJSONArray("players").getJSONObject(14).getString("name"))
        val derived = encoded.getJSONArray("players").getJSONObject(0)
        val first = rows[0]
        assertEquals(first.fgMissed, derived.getInt("fgMissed"))
        assertEquals(first.fgPercent!!, derived.getDouble("fgPercent"), 0.00001)
        assertEquals(1, VisualizationContract.rows(value, spec.copy(player = "phi_23")).size)
        assertTrue(VisualizationContract.rows(value, spec.copy(sort = "asc")).dropLast(1).zipWithNext().all { (a,b) -> a.points!! <= b.points!! })
    }
    @Test fun unavailableStatusesRejectWithoutInventingStats() {
        val game = PreseasonGameData.createGame()
        for (status in listOf("SCHEDULED", "POSTPONED", "CANCELLED", "unsupported")) { game.status = status; assertThrows(Exception::class.java) { VisualizationContract.snapshotFromGame(game) } }
        game.status = "LIVE"
        assertEquals("LIVE", VisualizationContract.snapshotFromGame(game).status)
    }

    @Test fun parserAcceptsStrictGrammarOnlyAndMeasuresContainerDepth() {
        assertNotNull(StrictJson.parse("[".repeat(8) + "0" + "]".repeat(8), 100, 8))
        assertThrows(Exception::class.java) { StrictJson.parse("[".repeat(9) + "0" + "]".repeat(9), 100, 8) }
        for (raw in listOf("{\"a\":1,\"a\":2}", "{\"__proto__\":1}", "{\"\\u0063onstructor\":1}", "[01]", "[+1]", "[NaN]", "[1,]", "[1e999]", "[\"\\uD800\"]", "{a:1}")) {
            assertThrows(raw, Exception::class.java) { StrictJson.parse(raw, 100) }
        }
    }
}
