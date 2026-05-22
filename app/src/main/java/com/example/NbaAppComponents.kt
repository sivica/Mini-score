package com.example

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import kotlin.random.Random

// ==========================================
// 1. DATA MODELS
// ==========================================

data class PlayerStats(
    var points: Int = 0,
    var rebounds: Int = 0,
    var assists: Int = 0,
    var steals: Int = 0,
    var blocks: Int = 0,
    var turnovers: Int = 0,
    var fgMade: Int = 0,
    var fgAttempted: Int = 0,
    var minutes: Int = 18
) {
    val fgPercentage: Int get() = if (fgAttempted > 0) (fgMade * 100) / fgAttempted else 0
}

data class Player(
    val id: String,
    val name: String,
    val number: String,
    val flag: String, // Emoji country flag
    val isLeftTeam: Boolean,
    val isStarting: Boolean,
    var rating: Double?, // Rating out of 10.0, can be null
    var stats: PlayerStats = PlayerStats(),
    val isTopPerformer: Boolean = false,
    val keyPlays: List<String> = emptyList()
)

data class Team(
    val code: String, // NYK, CLE, LAL, GSW, etc
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val logoText: String
)

data class PlayLog(
    val id: String = Random.nextInt().toString(),
    val time: String, // "7:30 Q4"
    val description: String,
    val scoreText: String
)

data class Game(
    val id: String,
    val awayTeam: Team, // Left side
    val homeTeam: Team, // Right side
    var awayScore: Int,
    var homeScore: Int,
    var status: String, // "FINISHED" or "LIVE Q4" or "LIVE Q3"
    val startingAway: List<Player>,
    val startingHome: List<Player>,
    val substitutesAway: List<Player>,
    val substitutesHome: List<Player>,
    val playLogs: MutableList<PlayLog> = mutableListOf(),
    var isSimulating: Boolean = false,
    var currentQuarter: Int = 4,
    var minutesLeft: Int = 12,
    var secondsLeft: Int = 0
)

// ==========================================
// 2. MOCK DATA CREATOR
// ==========================================

object MockData {
    val NYK = Team("NYK", "New York Knicks", Color(0xFF006BB6), Color(0xFFF58426), "NYK")
    val CLE = Team("CLE", "Cleveland Cavaliers", Color(0xFF860038), Color(0xFFFDBB30), "CLE")
    
    val LAL = Team("LAL", "LA Lakers", Color(0xFF552583), Color(0xFFFDB927), "LAL")
    val GSW = Team("GSW", "Golden State Warriors", Color(0xFF1D428A), Color(0xFFFFC72C), "GSW")
    
    val BOS = Team("BOS", "Boston Celtics", Color(0xFF007A33), Color(0xFFBA9653), "BOS")
    val MIA = Team("MIA", "Miami Heat", Color(0xFF98002E), Color(0xFFF9A01B), "MIA")

    // --- GAME 1: NYK @ CLE (MATCHES USER SCREENSHOT EXACTLY!) ---
    fun createNykCleGame(): Game {
        val awayStarters = listOf(
            Player("nyk_anunoby", "Anunoby OG.", "8", "🇬🇧", true, true, 7.4, PlayerStats(14, 6, 2, 3, 1, 1, 5, 9, 36)),
            Player("nyk_hart", "Hart J.", "3", "🇺🇸", true, true, 7.9, PlayerStats(19, 13, 7, 2, 0, 2, 7, 11, 41), isTopPerformer = true),
            Player("nyk_towns", "Towns K.", "32", "🇩🇴", true, true, 7.7, PlayerStats(24, 11, 3, 1, 2, 3, 9, 15, 34)),
            Player("nyk_bridges", "Bridges M.", "25", "🇺🇸", true, true, 7.8, PlayerStats(22, 4, 3, 2, 1, 0, 8, 14, 38)),
            Player("nyk_brunson", "Brunson J.", "11", "🇺🇸", true, true, 7.6, PlayerStats(26, 3, 9, 1, 0, 4, 10, 20, 39))
        )

        val homeStarters = listOf(
            Player("cle_mobley", "Mobley E.", "4", "🇺🇸", false, true, 7.1, PlayerStats(16, 9, 2, 1, 3, 1, 7, 12, 35)),
            Player("cle_wade", "Wade D.", "32", "🇺🇸", false, true, 5.9, PlayerStats(5, 4, 1, 0, 1, 0, 2, 6, 22)),
            Player("cle_allen", "Allen J.", "31", "🇺🇸", false, true, 7.1, PlayerStats(14, 12, 1, 0, 2, 1, 6, 8, 33)),
            Player("cle_harden", "Harden J.", "1", "🇺🇸", false, true, 7.1, PlayerStats(18, 5, 11, 2, 0, 5, 5, 13, 37)),
            Player("cle_mitchell", "Mitchell D.", "45", "🇺🇸", false, true, 7.0, PlayerStats(22, 4, 5, 1, 0, 3, 8, 19, 36))
        )

        val awaySubs = listOf(
            Player("nyk_alvarado", "Alvarado J.", "5", "🇵🇷", true, false, 6.1, PlayerStats(4, 1, 3, 2, 0, 1, 2, 4, 15)),
            Player("nyk_clarkson", "Clarkson J.", "00", "🇵🇭", true, false, 6.1, PlayerStats(6, 2, 2, 0, 0, 2, 2, 7, 18)),
            Player("nyk_dadiet", "Dadiet P.", "4", "🇫🇷", true, false, 5.9, PlayerStats(2, 1, 1, 0, 0, 0, 1, 3, 11)),
            Player("nyk_diawara", "Diawara M.", "51", "🇫🇷", true, false, null, PlayerStats(0, 0, 0, 0, 0, 0, 0, 0, 0)),
            Player("nyk_hukporti", "Hukporti A.", "55", "🇩🇪", true, false, null, PlayerStats(0, 1, 0, 0, 1, 0, 0, 1, 4)),
            Player("nyk_kolek", "Kolek T.", "13", "🇺🇸", true, false, 6.0, PlayerStats(2, 0, 2, 1, 0, 0, 1, 2, 8))
        )

        val homeSubs = listOf(
            Player("cle_bryant", "Bryant T.", "3", "🇺🇸", false, false, 6.0, PlayerStats(4, 3, 0, 0, 1, 1, 2, 3, 10)),
            Player("cle_ellis", "Ellis K.", "14", "🇺🇸", false, false, 6.0, PlayerStats(3, 1, 2, 1, 0, 0, 1, 2, 12)),
            Player("cle_merrill", "Merrill S.", "5", "🇺🇸", false, false, 5.4, PlayerStats(3, 0, 1, 0, 0, 0, 1, 4, 14)),
            Player("cle_nance", "Nance L.", "22", "🇺🇸", false, false, null, PlayerStats(0, 2, 1, 0, 0, 1, 0, 1, 6)),
            Player("cle_porter", "Porter C.", "9", "🇺🇸", false, false, 6.0, PlayerStats(4, 1, 2, 1, 0, 1, 2, 3, 11)),
            Player("cle_proctor", "Proctor T.", "24", "🇦🇺", false, false, 6.0, PlayerStats(2, 1, 1, 0, 0, 0, 1, 2, 9))
        )

        return Game(
            id = "nyk_cle",
            awayTeam = NYK,
            homeTeam = CLE,
            awayScore = 109,
            homeScore = 93,
            status = "FINISHED",
            startingAway = awayStarters,
            startingHome = homeStarters,
            substitutesAway = awaySubs,
            substitutesHome = homeSubs,
            playLogs = mutableListOf(
                PlayLog(time = "FINISHED", description = "Match concluded. NYK claims a decisive 109-93 victory.", scoreText = "109 - 93"),
                PlayLog(time = "0:15 Q4", description = "Donovan Mitchell misses a contested three-pointer. Rebound Towns K.", scoreText = "109 - 93"),
                PlayLog(time = "1:04 Q4", description = "Jalen Brunson floats a beautiful pass to Josh Hart for a fast break layup!", scoreText = "109 - 93"),
                PlayLog(time = "2:30 Q4", description = "Mikal Bridges steals the ball from James Harden and slams it home on the run!", scoreText = "107 - 91")
            )
        )
    }

    // --- GAME 2: LAL @ GSW (FINISHED HIGH SCORE) ---
    fun createLalGswGame(): Game {
        val awayStarters = listOf(
            Player("lal_james", "James L.", "23", "🇺🇸", true, true, 8.2, PlayerStats(31, 8, 9, 2, 1, 3, 12, 19, 38), isTopPerformer = true),
            Player("lal_davis", "Davis A.", "3", "🇺🇸", true, true, 7.8, PlayerStats(26, 12, 4, 1, 4, 2, 10, 16, 35)),
            Player("lal_hachimura", "Hachimura R.", "28", "🇯🇵", true, true, 6.8, PlayerStats(14, 5, 2, 0, 1, 1, 6, 11, 31)),
            Player("lal_reaves", "Reaves A.", "15", "🇺🇸", true, true, 7.3, PlayerStats(18, 4, 6, 2, 0, 1, 6, 12, 34)),
            Player("lal_russell", "Russell D.", "1", "🇺🇸", true, true, 6.5, PlayerStats(12, 2, 5, 1, 0, 3, 4, 10, 28))
        )

        val homeStarters = listOf(
            Player("gsw_curry", "Curry S.", "30", "🇺🇸", false, true, 8.1, PlayerStats(33, 5, 7, 1, 0, 2, 11, 21, 37)),
            Player("gsw_green", "Green D.", "23", "🇺🇸", false, true, 6.9, PlayerStats(8, 9, 10, 3, 1, 4, 3, 6, 33)),
            Player("gsw_jackson", "Jackson-Davis", "32", "🇺🇸", false, true, 7.1, PlayerStats(12, 8, 2, 0, 3, 1, 5, 7, 26)),
            Player("gsw_wiggins", "Wiggins A.", "22", "🇨🇦", false, true, 6.7, PlayerStats(16, 6, 2, 1, 1, 2, 6, 13, 31)),
            Player("gsw_podziemski", "Podziemski B.", "2", "🇺🇸", false, true, 6.4, PlayerStats(9, 4, 4, 1, 0, 2, 3, 8, 27))
        )

        val awaySubs = listOf(
            Player("lal_key1", "Knecht D.", "4", "🇺🇸", true, false, 6.2, PlayerStats(9, 2, 1, 0, 0, 0, 3, 6, 15)),
            Player("lal_key2", "Vincent G.", "7", "🇺🇸", true, false, 5.8, PlayerStats(4, 1, 2, 1, 0, 1, 2, 5, 13)),
            Player("lal_key3", "Hayes J.", "11", "🇺🇸", true, false, 6.0, PlayerStats(4, 3, 0, 0, 2, 0, 2, 2, 10))
        )

        val homeSubs = listOf(
            Player("gsw_sub1", "Hield B.", "7", "🇧🇸", false, false, 7.2, PlayerStats(21, 3, 2, 1, 0, 1, 8, 12, 22)),
            Player("gsw_sub2", "Kuminga J.", "00", "🇨🇩", false, false, 6.6, PlayerStats(12, 4, 1, 0, 1, 2, 5, 9, 18)),
            Player("gsw_sub3", "Melton D.", "8", "🇺🇸", false, false, 6.1, PlayerStats(3, 1, 3, 2, 0, 0, 1, 4, 12))
        )

        return Game(
            id = "lal_gsw",
            awayTeam = LAL,
            homeTeam = GSW,
            awayScore = 118,
            homeScore = 114,
            status = "FINISHED",
            startingAway = awayStarters,
            startingHome = homeStarters,
            substitutesAway = awaySubs,
            substitutesHome = homeSubs,
            playLogs = mutableListOf(
                PlayLog(time = "FINISHED", description = "Match concluded. LAL secures a spectacular away win at GSW, 118-114.", scoreText = "118 - 114"),
                PlayLog(time = "0:02 Q4", description = "LeBron James sinks two ice-cold free throws to seal the victory.", scoreText = "118 - 114"),
                PlayLog(time = "0:15 Q4", description = "Stephen Curry knocks down a deep three with hand in face!", scoreText = "116 - 114"),
                PlayLog(time = "1:10 Q4", description = "Anthony Davis blocks Wiggins' layup attempt from behind.", scoreText = "116 - 111")
            )
        )
    }

    // --- GAME 3: BOS @ MIA (LIVE MATCH FOR INTERACTIVE REAL-TIME SIMULATION) ---
    fun createBosMiaGame(): Game {
        val awayStarters = listOf(
            Player("bos_tatum", "Tatum J.", "0", "🇺🇸", true, true, 7.8, PlayerStats(24, 7, 5, 1, 1, 3, 8, 16, 32)),
            Player("bos_brown", "Brown J.", "7", "🇺🇸", true, true, 7.6, PlayerStats(22, 6, 3, 2, 0, 2, 7, 14, 31)),
            Player("bos_porzingis", "Porzingis K.", "8", "🇱🇻", true, true, 7.1, PlayerStats(15, 6, 1, 0, 3, 1, 5, 11, 28)),
            Player("bos_white", "White D.", "9", "🇺🇸", true, true, 7.2, PlayerStats(11, 3, 6, 2, 1, 0, 4, 8, 30)),
            Player("bos_holiday", "Holiday J.", "4", "🇺🇸", true, true, 6.9, PlayerStats(9, 4, 5, 1, 0, 1, 3, 7, 29))
        )

        val homeStarters = listOf(
            Player("mia_butler", "Butler J.", "22", "🇺🇸", false, true, 7.7, PlayerStats(21, 6, 6, 3, 0, 1, 7, 12, 33)),
            Player("mia_jovic", "Jovic N.", "5", "🇷🇸", false, true, 5.8, PlayerStats(6, 3, 2, 1, 0, 0, 2, 5, 20)),
            Player("mia_adebayo", "Adebayo B.", "13", "🇺🇸", false, true, 7.4, PlayerStats(18, 11, 4, 1, 2, 2, 6, 10, 32)),
            Player("mia_herro", "Herro T.", "14", "🇺🇸", false, true, 7.0, PlayerStats(19, 3, 5, 1, 0, 2, 7, 15, 31)),
            Player("mia_rozier", "Rozier T.", "2", "🇺🇸", false, true, 6.3, PlayerStats(13, 2, 4, 0, 0, 1, 5, 11, 28))
        )

        val awaySubs = listOf(
            Player("bos_sub1", "Pritchard P.", "11", "🇺🇸", true, false, 6.2, PlayerStats(8, 1, 3, 1, 0, 0, 3, 5, 14)),
            Player("bos_sub2", "Hauser S.", "30", "🇺🇸", true, false, 6.0, PlayerStats(6, 2, 1, 0, 0, 0, 2, 4, 12)),
            Player("bos_sub3", "Horford A.", "42", "🇩🇴", true, false, 6.3, PlayerStats(3, 5, 2, 0, 2, 1, 1, 3, 15))
        )

        val homeSubs = listOf(
            Player("mia_sub1", "Duncan Robinson", "55", "🇺🇸", false, false, 6.5, PlayerStats(12, 2, 2, 1, 0, 1, 4, 7, 16)),
            Player("mia_sub2", "Jaime Jaquez", "11", "🇲🇽", false, false, 6.3, PlayerStats(8, 4, 3, 1, 0, 2, 3, 6, 18)),
            Player("mia_sub3", "Love K.", "42", "🇺🇸", false, false, null, PlayerStats(2, 3, 1, 0, 0, 0, 1, 2, 8))
        )

        return Game(
            id = "bos_mia",
            awayTeam = BOS,
            homeTeam = MIA,
            awayScore = 85,
            homeScore = 82,
            status = "LIVE",
            startingAway = awayStarters,
            startingHome = homeStarters,
            substitutesAway = awaySubs,
            substitutesHome = homeSubs,
            playLogs = mutableListOf(
                PlayLog(time = "8:30 Q4", description = "Match currently Active. Boston Celtics lead Miami Heat 85 to 82.", scoreText = "85 - 82"),
                PlayLog(time = "8:45 Q4", description = "Herro T. drives past defenders and feeds Tyler Rozier for a corner baseline jumper.", scoreText = "85 - 82"),
                PlayLog(time = "9:15 Q4", description = "Jaylen Brown logs an aggressive mid-range bank shot.", scoreText = "85 - 80")
            ),
            isSimulating = false
        )
    }
}

// ==========================================
// 3. SECURE GEMINI INTEGRATION & PERSONA
// ==========================================

object GeminiClient {
    private const val BASE_URL = "https://generativelanguage.googleapis.com"
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun fetchAnalysisFromGemini(game: Game, apiKey: String): String = withContext(Dispatchers.IO) {
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext "ERROR_KEY_MISSING"
        }

        try {
            val systemInstruction = "You are an elite, sharp NBA sports columnist with witty, punchy, expert analysis. Write exactly 2 blocks of paragraphs. Paragraph 1: An exciting, tactical summary of how the game flowed, focusing on defense, team strategies, and momentum. Paragraph 2: Highlight the most crucial players and explain how they drove their Flashscore ratings (like Hart's 7.9 or Wade's 5.9). Keep it highly analytical, using crisp, authentic basketball terminology. Avoid bullet points, titles, or code blocks in your final text."
            
            // Format game and player data for prompt
            val descriptionString = StringBuilder()
            descriptionString.append("GAME: ${game.awayTeam.name} (${game.awayScore}) @ ${game.homeTeam.name} (${game.homeScore}). STATUS: ${game.status}.\n\n")
            
            descriptionString.append("${game.awayTeam.code} (Away) starting ratings/stats:\n")
            game.startingAway.forEach {
                descriptionString.append("- ${it.name} #${it.number} (${it.flag}): Rating ${it.rating ?: "N/A"}, Stats: ${it.stats.points} PTS, ${it.stats.rebounds} REB, ${it.stats.assists} AST, ${it.stats.steals} STL, ${it.stats.blocks} BLK\n")
            }
            
            descriptionString.append("\n${game.homeTeam.code} (Home) starting ratings/stats:\n")
            game.startingHome.forEach {
                descriptionString.append("- ${it.name} #${it.number} (${it.flag}): Rating ${it.rating ?: "N/A"}, Stats: ${it.stats.points} PTS, ${it.stats.rebounds} REB, ${it.stats.assists} AST, ${it.stats.steals} STL, ${it.stats.blocks} BLK\n")
            }
            
            val jsonPayload = JSONObject().apply {
                put("contents", JSONArray().apply {
                    put(JSONObject().apply {
                        put("parts", JSONArray().apply {
                            put(JSONObject().apply {
                                put("text", "Please analyze this NBA matchup and performance stats:\n\n${descriptionString}")
                            })
                        })
                    })
                })
                put("systemInstruction", JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", systemInstruction)
                        })
                    })
                })
                put("generationConfig", JSONObject().apply {
                    put("temperature", 0.7)
                })
            }

            val requestBody = jsonPayload.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url("$BASE_URL/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey")
                .post(requestBody)
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext "API connection unsuccessful: ${response.code}. Please confirm API key settings."
                }
                val rawBody = response.body?.string() ?: return@withContext "Empty response."
                val jsonResponse = JSONObject(rawBody)
                val candidates = jsonResponse.optJSONArray("candidates")
                if (candidates != null && candidates.length() > 0) {
                    val firstCandidate = candidates.getJSONObject(0)
                    val content = firstCandidate.optJSONObject("content")
                    val parts = content?.optJSONArray("parts")
                    if (parts != null && parts.length() > 0) {
                        return@withContext parts.getJSONObject(0).optString("text", "No analytical content returned.")
                    }
                }
                return@withContext "No analysis compiled. Double check responses."
            }
        } catch (e: Exception) {
            e.printStackTrace()
            return@withContext "Network failure: ${e.localizedMessage}. Verify internet connection."
        }
    }
}

// ==========================================
// 4. VIEW MODEL FOR STATE MANAGEMENT
// ==========================================

class GameViewModel : ViewModel() {
    private val _games = mutableStateMapOf<String, Game>()
    
    // Convert to regular list for observing changes
    val games: List<Game> get() = _games.values.toList()

    private val _selectedGameId = MutableStateFlow("nyk_cle")
    val selectedGameId: StateFlow<String> = _selectedGameId.asStateFlow()

    private val _selectedPlayer = MutableStateFlow<Player?>(null)
    val selectedPlayer: StateFlow<Player?> = _selectedPlayer.asStateFlow()

    // AI Analysis specific states
    private val _aiAnalysisState = MutableStateFlow<AiState>(AiState.Idle)
    val aiAnalysisState: StateFlow<AiState> = _aiAnalysisState.asStateFlow()

    sealed interface AiState {
        object Idle : AiState
        object Loading : AiState
        data class Success(val text: String) : AiState
        data class Error(val errorMsg: String, val fallbackHtml: String) : AiState
    }

    init {
        // Initialize mock games
        val game1 = MockData.createNykCleGame()
        val game2 = MockData.createLalGswGame()
        val game3 = MockData.createBosMiaGame()
        
        _games[game1.id] = game1
        _games[game2.id] = game2
        _games[game3.id] = game3
    }

    fun selectGame(id: String) {
        _selectedGameId.value = id
        _aiAnalysisState.value = AiState.Idle // Reset AI state when switching games
    }

    fun selectPlayer(player: Player?) {
        _selectedPlayer.value = player
    }

    fun triggerAiAnalysis(apiKey: String) {
        val game = _games[_selectedGameId.value] ?: return
        _aiAnalysisState.value = AiState.Loading

        viewModelScope.launch {
            val response = GeminiClient.fetchAnalysisFromGemini(game, apiKey)
            if (response == "ERROR_KEY_MISSING") {
                val fallbackText = generateLocalAnalysis(game)
                _aiAnalysisState.value = AiState.Error(
                    "API Key Missing. Set your GEMINI_API_KEY in the Secrets panel to activate live Gemini analysis.",
                    fallbackText
                )
            } else if (response.startsWith("API connection unsuccessful") || response.startsWith("Network failure")) {
                val fallbackText = generateLocalAnalysis(game)
                _aiAnalysisState.value = AiState.Error(
                    response,
                    fallbackText
                )
            } else {
                _aiAnalysisState.value = AiState.Success(response)
            }
        }
    }

    // High fidelity rule-based local sports summaries as fallback
    private fun generateLocalAnalysis(game: Game): String {
        return when (game.id) {
            "nyk_cle" -> {
                "The match ended with a dominant NYK display, clinching a 109-93 blowout victory over CLE. " +
                "Josh Hart put on an absolute masterclass and clinched MVP honors, logging a phenomenal 7.9 rating with 19 points, 13 rebounds, and 7 critical assists that completely tore the Cavs' transition defense apart. " +
                "Karl-Anthony Towns (7.7 rating) anchored the paint beautifully with 24 points and 11 rebounds, matching Mikal Bridges' (7.8 rating) lockdown perimeter steals. " +
                "Meanwhile, Dean Wade struggled immensely for Cleveland, picking up a subpar 5.9 rating after hitting only 2-of-6 from the floor and being repeatedly hunted on isolations."
            }
            "lal_gsw" -> {
                "A historic high-scoring western classic resulted in LAL surviving a GSW fourth-quarter barrage, winning 118-114. " +
                "LeBron James turned back the clock to log the top-performer rating of 8.2, finishing just shy of a triple-double with 31 points, 8 boards, and 9 assists. " +
                "Anthony Davis (7.8 rating) was an absolute wall inside, swatting 4 crucial blocks and securing 12 defensive rebounds. " +
                "Stephen Curry was electric for Golden State, keeping them alive singlehandedly with 33 points (8.1 rating), but missed the late go-ahead contested corner shot under intense double teams."
            }
            else -> {
                "This Eastern Conference battle was an intense defensive showcase. " +
                "Jayson Tatum (7.8 rating) and Jaylen Brown (7.6 rating) led Boston's structured motion offense, generating paint pressure and perimeter kickouts. " +
                "Jimmy Butler paced Miami with relentless grit (7.7 rating, 3 steals) alongside Adebayo's sturdy double-double. " +
                "The defensive switching of both teams limited spacing, keeping ratings highly constrained across substitutions."
            }
        }
    }

    // ==========================================
    // 5. CLIENT-SIDE LIVE SIMULATION ENGINE
    // ==========================================
    private var simJob: kotlinx.coroutines.Job? = null

    fun toggleSimulation(gameId: String) {
        val game = _games[gameId] ?: return
        if (game.isSimulating) {
            stopSimulation(gameId)
        } else {
            startSimulation(gameId)
        }
    }

    private fun startSimulation(gameId: String) {
        val game = _games[gameId] ?: return
        game.isSimulating = true
        // Force state update by rewriting to map
        _games[gameId] = game.copy(status = "LIVE Q4 (12:00)", minutesLeft = 12, secondsLeft = 0, isSimulating = true)
        
        simJob = viewModelScope.launch(Dispatchers.Main) {
            var min = game.minutesLeft
            var sec = game.secondsLeft
            val playTicker = listOf(
                "drives hard down the lane for a stellar finger-roll layup!",
                "drills a spectacular catch-and-shoot 3-pointer from deep corner!",
                "grabs a huge offensive board and fires a putback hook!",
                "logs a massive chase-down block under the rim!",
                "swipes a clean pocket-pick steal on transition!",
                "throws a beautiful alley-oop lob in traffic!"
            )
            val badPlayTicker = listOf(
                "commits an unforced passing turnover out of bounds.",
                "misses a heavily contested step-back jumper.",
                "picks up an aggressive offensive foul.",
                "is whistled for a loose-ball push on the defensive rebound."
            )

            while (game.isSimulating && min >= 0) {
                delay(1200) // Speed up time: 1 simulated second is 1.2s delay for gameplay rhythm
                sec -= 15
                if (sec < 0) {
                    sec = 45
                    min--
                }
                if (min < 0) {
                    min = 0
                    sec = 0
                    // Conclude game
                    withContext(Dispatchers.Main) {
                        val activeGame = _games[gameId] ?: return@withContext
                        activeGame.isSimulating = false
                        activeGame.status = "FINISHED"
                        val finalLogs = activeGame.playLogs.toMutableList()
                        finalLogs.add(0, PlayLog(time = "FINISHED", description = "Game concluded. Dynamic simulation finalized.", scoreText = "${activeGame.awayScore} - ${activeGame.homeScore}"))
                        _games[gameId] = activeGame.copy(
                            status = "FINISHED",
                            playLogs = finalLogs,
                            isSimulating = false,
                            minutesLeft = 0,
                            secondsLeft = 0
                        )
                    }
                    break
                }

                // Random event trigger: score increase and player rating adjustments
                val isAwayEvent = Random.nextBoolean()
                val scoreIncrease = if (Random.nextInt(5) < 3) if (Random.nextBoolean()) 3 else 2 else 0
                val isPositiveEvent = scoreIncrease > 0 || Random.nextInt(4) > 0

                val activeGame = _games[gameId] ?: break
                val currentAwayScore = activeGame.awayScore + (if (isAwayEvent) scoreIncrease else 0)
                val currentHomeScore = activeGame.homeScore + (if (!isAwayEvent) scoreIncrease else 0)

                // Select acting player
                val teamStarters = if (isAwayEvent) activeGame.startingAway else activeGame.startingHome
                val randomPlayerIndex = Random.nextInt(teamStarters.size)
                val activePlayer = teamStarters[randomPlayerIndex]

                val desc = if (isPositiveEvent) {
                    // Boost player stats and rating
                    val play = playTicker.random()
                    activePlayer.stats.points += scoreIncrease
                    if (scoreIncrease == 3) {
                        activePlayer.stats.fgMade++
                        activePlayer.stats.fgAttempted++
                    } else if (scoreIncrease == 2) {
                        activePlayer.stats.fgMade++
                        activePlayer.stats.fgAttempted++
                    }
                    if (Random.nextBoolean()) activePlayer.stats.rebounds++
                    if (Random.nextBoolean()) activePlayer.stats.assists++
                    
                    val change = (Random.nextInt(2, 5) * 0.1)
                    activePlayer.rating = minOf(9.9, (activePlayer.rating ?: 6.5) + change)
                    "${activePlayer.name} ${play}"
                } else {
                    // Dinger player rating
                    val play = badPlayTicker.random()
                    activePlayer.stats.turnovers++
                    activePlayer.stats.fgAttempted++
                    val change = (Random.nextInt(1, 3) * 0.1)
                    activePlayer.rating = maxOf(4.5, (activePlayer.rating ?: 6.5) - change)
                    "${activePlayer.name} ${play}"
                }

                val timeStr = String.format("%d:%02d Q4", min, sec)
                val scoreStr = "$currentAwayScore - $currentHomeScore"
                
                withContext(Dispatchers.Main) {
                    val updatedGame = _games[gameId] ?: return@withContext
                    val logs = updatedGame.playLogs.toMutableList()
                    logs.add(0, PlayLog(time = timeStr, description = desc, scoreText = scoreStr))
                    
                    _games[gameId] = updatedGame.copy(
                        awayScore = currentAwayScore,
                        homeScore = currentHomeScore,
                        status = "LIVE $timeStr",
                        playLogs = logs,
                        minutesLeft = min,
                        secondsLeft = sec
                    )
                }
            }
        }
    }

    private fun stopSimulation(gameId: String) {
        val game = _games[gameId] ?: return
        game.isSimulating = false
        simJob?.cancel()
        _games[gameId] = game.copy(isSimulating = false)
    }

    override fun onCleared() {
        super.onCleared()
        simJob?.cancel()
    }
}

// ==========================================
// 6. VECTOR CUSTOM GRAPHICS FOR TEAM LOGOS
// ==========================================

@Composable
fun TeamLogoCanvas(team: Team, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.toPx() / 2, size.toPx() / 2)
            val radius = size.toPx() / 2 - 4f

            // Clean radial styling depending on team
            when (team.code) {
                "NYK" -> {
                    // Basketball theme
                    drawCircle(
                        color = Color(0xFFF58426), // Orange inner
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFF006BB6), // Blue Ring
                        radius = radius,
                        center = center,
                        style = Stroke(width = 6f)
                    )
                    // Ball lines
                    drawLine(
                        color = Color(0xFF006BB6),
                        start = Offset(center.x - radius, center.y),
                        end = Offset(center.x + radius, center.y),
                        strokeWidth = 3f
                    )
                    drawLine(
                        color = Color(0xFF006BB6),
                        start = Offset(center.x, center.y - radius),
                        end = Offset(center.x, center.y + radius),
                        strokeWidth = 3f
                    )
                }
                "CLE" -> {
                    // Shield/Crest theme
                    drawCircle(
                        color = Color(0xFF860038), // Wine background
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFFDBB30), // Gold ring
                        radius = radius,
                        center = center,
                        style = Stroke(width = 4f)
                    )
                }
                "LAL" -> {
                    // Purple & gold streak
                    drawCircle(
                        color = Color(0xFF552583), // Purple
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFFDB927), // Gold core
                        radius = radius * 0.7f,
                        center = center
                    )
                }
                "GSW" -> {
                    // Bridge motif circle
                    drawCircle(
                        color = Color(0xFF1D428A), // Blue
                        radius = radius,
                        center = center
                    )
                    // Drawn golden gate vector arc placeholder
                    drawArc(
                        color = Color(0xFFFFC72C),
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = false,
                        topLeft = Offset(center.x - radius * 0.7f, center.y - radius * 0.3f),
                        size = androidx.compose.ui.geometry.Size(radius * 1.4f, radius * 1.4f),
                        style = Stroke(width = 4f)
                    )
                }
                "BOS" -> {
                    // Shamrock green
                    drawCircle(
                        color = Color(0xFF007A33),
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFBA9653),
                        radius = radius * 0.8f,
                        center = center,
                        style = Stroke(width = 4f)
                    )
                }
                else -> {
                    // Miami Heat red flame theme
                    drawCircle(
                        color = Color(0xFF98002E),
                        radius = radius,
                        center = center
                    )
                    drawCircle(
                        color = Color(0xFFF9A01B),
                        radius = radius,
                        center = center,
                        style = Stroke(width = 5f)
                    )
                }
            }
        }
        Text(
            text = team.logoText,
            color = Color.White,
            fontSize = (size.value * 0.28).sp,
            fontWeight = FontWeight.Black,
            fontFamily = FontFamily.SansSerif,
            style = LocalTextStyle.current.copy(
                shadow = androidx.compose.ui.graphics.Shadow(
                    color = Color.Black,
                    offset = Offset(2f, 2f),
                    blurRadius = 4f
                )
            )
        )
    }
}

// ==========================================
// 7. MULTI-STATE APP LAYOUT COMPOSABLES
// ==========================================

@Composable
fun MainNbaScreen(viewModel: GameViewModel) {
    val games = viewModel.games
    val selectedGameId by viewModel.selectedGameId.collectAsState()
    val selectedGame = games.find { it.id == selectedGameId } ?: games.firstOrNull() ?: return
    val selectedPlayer by viewModel.selectedPlayer.collectAsState()
    val aiState by viewModel.aiAnalysisState.collectAsState()

    // Retrieve API key securely via BuildConfig
    val apiKey = com.example.BuildConfig.GEMINI_API_KEY ?: ""
    val context = LocalContext.current

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(SportsDarkBackground),
        color = SportsDarkBackground
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            // A. ELEGANT HEADER (MATCHES THE ELEGANT DARK HTML DESIGN CONCEPT)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(ElegantAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        // Custom star or basketball representation matching logo
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = "NBA Center Basketball Icon",
                            tint = ElegantCirclePurple,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "NBA Center",
                            color = ElegantTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp,
                            fontFamily = FontFamily.SansSerif
                        )
                        Text(
                            text = "Friday, May 22", // Reflects exact current date
                            color = ElegantTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Normal,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                IconButton(
                    onClick = { /* Decorative Search */ },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(ElegantBorderColor.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search Matches",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // B. ELEGANT GAME MATCH SELECTION ROW
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
            ) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(games) { game ->
                        val isSelected = game.id == selectedGameId
                        GameMiniSelectorCard(game = game, isSelected = isSelected) {
                            viewModel.selectGame(game.id)
                        }
                    }
                }
            }

            Divider(color = ElegantBorderColor.copy(alpha = 0.3f), thickness = 1.dp)

            // C. DETAIL VIEW (SCROLLABLE LINEUPS & STATS MOCKING FLASHSCORE LOGO/SYMMETRY ACCURATELY)
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(SportsDarkBackground)
            ) {
                // 1. Core Header (Scoreboard like screenshot)
                item {
                    ScoreboardHeaderPanel(game = selectedGame)
                }

                // 2. Client side Live Sim details pane
                if (selectedGame.status.contains("LIVE") || selectedGame.isSimulating) {
                    item {
                        LiveSimulatorControlPanel(game = selectedGame, onToggleSim = {
                            viewModel.toggleSimulation(selectedGame.id)
                        })
                    }
                }



                // 4. HEADER: STARTING LINEUPS
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SportsHeaderBg)
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "STARTING LINEUPS",
                            color = ElegantTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                // Starting Lineups symmetrically side-by-side
                val linesCount = maxOf(selectedGame.startingAway.size, selectedGame.startingHome.size)
                items(linesCount) { index ->
                    val awayP = selectedGame.startingAway.getOrNull(index)
                    val homeP = selectedGame.startingHome.getOrNull(index)
                    SymmetricalLineplayRow(
                        leftPlayer = awayP,
                        rightPlayer = homeP,
                        onPlayerClick = { player -> viewModel.selectPlayer(player) }
                    )
                }

                // 5. HEADER: SUBSTITUTES
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SportsHeaderBg)
                            .padding(vertical = 10.dp, horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "SUBSTITUTES",
                            color = ElegantTextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.2.sp
                        )
                    }
                }

                // Substitute Lineups symmetrically
                val subsCount = maxOf(selectedGame.substitutesAway.size, selectedGame.substitutesHome.size)
                items(subsCount) { index ->
                    val awayP = selectedGame.substitutesAway.getOrNull(index)
                    val homeP = selectedGame.substitutesHome.getOrNull(index)
                    SymmetricalLineplayRow(
                        leftPlayer = awayP,
                        rightPlayer = homeP,
                        onPlayerClick = { player -> viewModel.selectPlayer(player) }
                    )
                }

                // 6. Advertising Space (MATCHES THE SCREENSHOT TEMU BANNER CONCEPT IN POLISHED SPORTS DESIGN)
                item {
                    MockAdvertisementBanner()
                }

                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }

            // D. ELEGANT BOTTOM BAR (MATCHES THE ELEGANT DARK HTML NAVIGATION CONCEPT)
            Divider(color = ElegantBorderColor.copy(alpha = 0.2f), thickness = 1.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(ElegantHeaderBg)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                // GAMES TAB (Active Tab)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(ElegantAccentBg2)
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DateRange,
                            contentDescription = "Games",
                            tint = ElegantAccentText2,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Games",
                        color = ElegantTextPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // PLAYERS TAB
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { /* Decorative */ }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountBox,
                        contentDescription = "Players",
                        tint = ElegantTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Players",
                        color = ElegantTextSecondary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // STANDINGS TAB
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { /* Decorative */ }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.List,
                        contentDescription = "Standings",
                        tint = ElegantTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Standings",
                        color = ElegantTextSecondary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // SETTINGS TAB
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { /* Decorative */ }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Settings",
                        tint = ElegantTextSecondary.copy(alpha = 0.6f),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Settings",
                        color = ElegantTextSecondary.copy(alpha = 0.6f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // C. SELECTED PLAYER HISTORIC Telemetry DETAILS BOTTOM SHEET
        if (selectedPlayer != null) {
            PlayerDetailBottomSheet(
                player = selectedPlayer!!,
                teamName = if (selectedPlayer!!.isLeftTeam) selectedGame.awayTeam.name else selectedGame.homeTeam.name,
                teamColor = if (selectedPlayer!!.isLeftTeam) selectedGame.awayTeam.primaryColor else selectedGame.homeTeam.primaryColor,
                onDismiss = { viewModel.selectPlayer(null) }
            )
        }
    }
}

// ==========================================
// 8. MINI LAYOUT SUBCOMPONENTS
// ==========================================

@Composable
fun GameMiniSelectorCard(game: Game, isSelected: Boolean, onClick: () -> Unit) {
    val borderStrokeColor = if (isSelected) AccentCyan else ElegantBorderColor.copy(alpha = 0.3f)
    val cardBackground = if (isSelected) Color(0xFF381E72).copy(alpha = 0.3f) else SportsDarkSurface
    val statusColor = if (game.status.contains("LIVE")) Color(0xFFFF4D4D) else TextSecondary

    Box(
        modifier = Modifier
            .width(155.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(cardBackground)
            .border(1.dp, borderStrokeColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(12.dp)
            .testTag("game_selector_${game.id}")
    ) {
        Column {
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = if (game.status.contains("LIVE")) "● LIVE" else game.status,
                    color = statusColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif
                )
                if (game.status.contains("LIVE")) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFF4D4D))
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Teams Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogoCanvas(team = game.awayTeam, size = 16.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = game.awayTeam.code,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = game.awayScore.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                TeamLogoCanvas(team = game.homeTeam, size = 16.dp)
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = game.homeTeam.code,
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = game.homeScore.toString(),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}

@Composable
fun ScoreboardHeaderPanel(game: Game) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(SportsDarkBackground)
            .padding(vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Left Team Logo
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TeamLogoCanvas(team = game.awayTeam, size = 64.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = game.awayTeam.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(90.dp)
                )
            }

            // Score Banner (Match Screenshot structure)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${game.awayScore} - ${game.homeScore}",
                    color = Color.White,
                    fontSize = 42.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif,
                    letterSpacing = 1.sp
                )
                
                Spacer(modifier = Modifier.height(4.dp))

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF0B212D))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        text = game.status,
                        color = if (game.status.contains("LIVE")) Color(0xFFFF4D4D) else Color(0xFFC7D5DC),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp
                    )
                }
            }

            // Right Team Logo
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                TeamLogoCanvas(team = game.homeTeam, size = 64.dp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = game.homeTeam.name,
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.width(90.dp)
                )
            }
        }
    }
}

@Composable
fun SymmetricalLineplayRow(
    leftPlayer: Player?,
    rightPlayer: Player?,
    onPlayerClick: (Player) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(SportsDarkBackground)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // LEFT TEAM PLAYER (Left side symmetrical alignment)
        if (leftPlayer != null) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onPlayerClick(leftPlayer) }
                    .padding(end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Start
            ) {
                Text(
                    text = leftPlayer.number,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(22.dp)
                )
                Text(
                    text = leftPlayer.flag,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                Text(
                    text = leftPlayer.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (leftPlayer.isTopPerformer) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (leftPlayer.isTopPerformer) {
                    Text(
                        text = "✨",
                        modifier = Modifier.padding(end = 4.dp),
                        fontSize = 11.sp
                    )
                }
                
                leftPlayer.rating?.let { rating ->
                    val badgeBg = when {
                        rating >= 7.0 -> RatingGreen
                        rating >= 6.0 -> RatingOrange
                        else -> RatingRed
                    }
                    Box(
                        modifier = Modifier
                            .padding(start = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .width(26.dp)
                            .height(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }

        // DELIMITER CENTER CHANNEL
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.6f)
                .background(Color(0xFF142C35))
        )

        // RIGHT TEAM PLAYER (Right side symmetrical alignment)
        if (rightPlayer != null) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onPlayerClick(rightPlayer) }
                    .padding(start = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                rightPlayer.rating?.let { rating ->
                    val badgeBg = when {
                        rating >= 7.0 -> RatingGreen
                        rating >= 6.0 -> RatingOrange
                        else -> RatingRed
                    }
                    Box(
                        modifier = Modifier
                            .padding(end = 6.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .width(26.dp)
                            .height(18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = String.format("%.1f", rating),
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }

                if (rightPlayer.isTopPerformer) {
                    Text(
                        text = "✨",
                        modifier = Modifier.padding(start = 4.dp),
                        fontSize = 11.sp
                    )
                }

                Text(
                    text = rightPlayer.name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = if (rightPlayer.isTopPerformer) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                Text(
                    text = rightPlayer.flag,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(start = 6.dp)
                )

                Text(
                    text = rightPlayer.number,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(22.dp),
                    textAlign = TextAlign.End
                )
            }
        } else {
            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

// ==========================================
// 9. LIVE SIMULATOR CONTROLS
// ==========================================

@Composable
fun LiveSimulatorControlPanel(game: Game, onToggleSim: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SportsDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp)
            .border(1.dp, ElegantBorderColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Simulate",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Real-Time Game Simulator (Q4)",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Button(
                    onClick = onToggleSim,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (game.isSimulating) Color(0xFFFF3333) else AccentCyan,
                        contentColor = Color.Black
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        text = if (game.isSimulating) "PAUSE SIM" else "START DYNAMIC SIM",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Black
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Pulse ticker indicator
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (game.isSimulating) Color(0xFF5EC91F) else Color.Gray)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (game.isSimulating) "Simulating quarter, ratings updating live..." else "Simulation Idle. Click start to simulate live score / ratings events.",
                    color = if (game.isSimulating) Color(0xFF8FF05E) else TextSecondary,
                    fontSize = 11.sp
                )
            }

            // Play timeline ticker
            if (game.playLogs.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = ElegantBorderColor.copy(alpha = 0.3f), thickness = 0.5.dp)
                Spacer(modifier = Modifier.height(6.dp))

                Box(modifier = Modifier.heightIn(max = 70.dp)) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        items(game.playLogs) { play ->
                            Row(verticalAlignment = Alignment.Top) {
                                Text(
                                    text = "[${play.time}]",
                                    color = AccentCyan,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.width(55.dp)
                                )
                                Text(
                                    text = play.description,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = play.scoreText,
                                    color = RatingGreen,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Black,
                                    textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// 10. AI ANALYSIS WORKSPACE
// ==========================================

@Composable
fun AiAnalysisPanel(
    selectedGame: Game,
    aiState: GameViewModel.AiState,
    onTriggerAnalysis: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SportsDarkSurface),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .border(1.dp, ElegantBorderColor.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Favorite, // Fallback representing sparkle or gem
                        contentDescription = "Gemini AI",
                        tint = AccentCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Gemini Sports Analyst",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (aiState !is GameViewModel.AiState.Loading) {
                    Button(
                        onClick = onTriggerAnalysis,
                        colors = ButtonDefaults.buttonColors(containerColor = ElegantBorderColor),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = "ASK ANALYST",
                            color = Color.White,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (aiState) {
                is GameViewModel.AiState.Idle -> {
                    Text(
                        text = "Click Ask Analyst to generate an automated editorial commentary about this match's player statistics and ratings.",
                        color = TextSecondary,
                        fontSize = 11.sp
                    )
                }
                is GameViewModel.AiState.Loading -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 12.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = AccentCyan,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Gemini compiling game telemetry and calculating ratings analysis...",
                            color = AccentCyan,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                is GameViewModel.AiState.Success -> {
                    Column {
                        Divider(color = ElegantBorderColor.copy(alpha = 0.3f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = aiState.text,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                            fontFamily = FontFamily.SansSerif
                        )
                    }
                }
                is GameViewModel.AiState.Error -> {
                    Column {
                        Divider(color = ElegantBorderColor.copy(alpha = 0.3f), thickness = 0.5.dp)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Friendly Callout representing missing key
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color(0xFF201314))
                                .padding(8.dp)
                        ) {
                            Text(
                                text = "💡 APP NOTICE:\n${aiState.errorMsg}",
                                color = Color(0xFFFF8585),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "--- Local Analytics Fallback ---",
                            color = AccentCyan,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = aiState.fallbackHtml,
                            color = Color.White,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// 11. PLAYER TELEMETRY STATISTICS SHEET
// ==========================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerDetailBottomSheet(
    player: Player,
    teamName: String,
    teamColor: Color,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = ElegantHeaderBg,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = ElegantBorderColor) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
        ) {
            // Header stats
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Circular rating badge
                val ratingCol = when {
                    (player.rating ?: 0.0) >= 7.0 -> RatingGreen
                    (player.rating ?: 0.0) >= 6.0 -> RatingOrange
                    else -> RatingRed
                }
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(ratingCol),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = player.rating?.let { String.format("%.1f", it) } ?: "-",
                        color = Color.Black,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Black
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = player.flag,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "No. ${player.number}",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = player.name,
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = teamName,
                        color = teamColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            Divider(color = ElegantBorderColor.copy(alpha = 0.3f), thickness = 1.dp)
            Spacer(modifier = Modifier.height(16.dp))

            // Stat bars section
            Text(
                text = "PERFORMANCE RADAR (STATISTICS)",
                color = AccentCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            TelemetryBar(label = "Points", current = player.stats.points, max = 40)
            TelemetryBar(label = "Rebounds", current = player.stats.rebounds, max = 20)
            TelemetryBar(label = "Assists", current = player.stats.assists, max = 15)
            TelemetryBar(label = "Steals", current = player.stats.steals, max = 6)
            TelemetryBar(label = "Blocks", current = player.stats.blocks, max = 6)
            TelemetryBar(label = "FG % / Accuracy", current = player.stats.fgPercentage, max = 100, isPercentage = true, displayValue = "${player.stats.fgMade}/${player.stats.fgAttempted}")

            Spacer(modifier = Modifier.height(16.dp))

            // Simulated AI summary of player
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SportsDarkSurface)
                    .border(1.dp, ElegantBorderColor.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "ANALYST VERDICT:",
                        color = RatingGreen,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = getAnalystVerdict(player),
                        color = Color.White,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
fun TelemetryBar(label: String, current: Int, max: Int, isPercentage: Boolean = false, displayValue: String? = null) {
    val progress = if (max > 0) current.toFloat() / max.toFloat() else 0f
    
    Column(modifier = Modifier.padding(vertical = 5.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = label, color = Color.White, fontSize = 12.sp)
            Text(
                text = displayValue ?: if (isPercentage) "$current%" else current.toString(),
                color = AccentCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(modifier = Modifier.height(3.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(5.dp)
                    .clip(CircleShape)
                    .background(ElegantBorderColor.copy(alpha = 0.4f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color(0xFF00FFCC), RatingGreen)
                            )
                        )
                )
            }
        }
    }
}

fun getAnalystVerdict(player: Player): String {
    if (player.rating == null || player.rating == 0.0) {
        return "Not enough court minutes played to calculate active telemetry."
    }
    return when {
        player.rating!! >= 8.0 -> {
            "${player.name} displayed historic efficiency. Dominant scoring combined with exceptional spatial defensive awareness completely dictated matches."
        }
        player.rating!! >= 7.5 -> {
            "${player.name} was incredibly vital. Outstanding playmaking contributions, high field-goal efficiency, and exceptional defensive rotations."
        }
        player.rating!! >= 7.0 -> {
            "${player.name} logged an efficient, highly supportive game. Secured regular paint rebounds and maintained excellent floor perimeter spacing."
        }
        player.rating!! >= 6.0 -> {
            "${player.name} provided a steady, error-minimized floor presence. Contributed solid bench spacing but struggled occasionally in isolation matchups."
        }
        else -> {
            "${player.name} experienced shooting struggles. Troubled with floor turnovers and isolated mismatch coverage that reduced final rated stats."
        }
    }
}

// ==========================================
// 12. MOCK ADVERTISEMENT BANNER
// ==========================================

@Composable
fun MockAdvertisementBanner() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF021C2B)),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF0F3647))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0xFF06334D))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "SPONSORED",
                        color = AccentCyan,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "TEMU",
                    color = Color(0xFFFA772C),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.SansSerif
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Shop Like a Billionaire. Download the App for Exclusive Coupons!",
                color = Color.White,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif
            )

            Spacer(modifier = Modifier.height(10.dp))

            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF5EC91F)),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp),
                shape = RoundedCornerShape(4.dp)
            ) {
                Text(
                    text = "INSTALL",
                    color = Color.Black,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Black
                )
            }
        }
    }
}
