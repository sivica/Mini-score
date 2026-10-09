package com.example

import android.os.Bundle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ui.theme.*
import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import coil.compose.AsyncImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
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
    var minutes: Int = 18,
    var seconds: Int = 0
) {
    val fgPercentage: Int get() = if (fgAttempted > 0) (fgMade * 100) / fgAttempted else 0
    val minutesDisplay: String get() = "$minutes:${seconds.toString().padStart(2, '0')}"
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
    val keyPlays: List<String> = emptyList(),
    val portraitUrl: String? = null,
    val teamCode: String? = null
)

data class Team(
    val code: String, // NYK, CLE, LAL, GSW, etc
    val name: String,
    val primaryColor: Color,
    val secondaryColor: Color,
    val logoText: String,
    val shortName: String = name,
    val logoUrl: String? = null
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
    var secondsLeft: Int = 0,
    val contextLabel: String? = null,
    val dateLabel: String? = null,
    val venue: String? = null,
    val awayQuarters: List<Int> = emptyList(),
    val homeQuarters: List<Int> = emptyList(),
    val isDemo: Boolean = false,
    val gameDate: String? = null,
    val tipoffUtc: String? = null
)

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
            val systemInstruction = "You are an NBA sports analyst. Write two concise paragraphs based only on the supplied game statistics. Summarize the result, then highlight players' contributions. Do not invent plays, ratings, awards, or tactical details absent from the data. Ratings marked N/A are unavailable, not evidence of poor performance. Avoid bullet points, titles, or code blocks."
            
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
    val games: List<Game> get() = _games.values.sortedWith(compareByDescending<Game> { it.gameDate }.thenBy { if (it.id == feedDefaultId) 0 else 1 }.thenBy { it.id })
    private var feedRepository: GameFeedRepository? = null
    private var feedDefaultId: String? = null
    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing = _isRefreshing.asStateFlow()
    private val _feedMessage = MutableStateFlow<String?>(null)
    val feedMessage = _feedMessage.asStateFlow()

    fun startFeed(repository: GameFeedRepository) {
        if (feedRepository != null) return
        feedRepository = repository
        refreshGames(loadCache = true)
    }

    fun refreshGames(loadCache: Boolean = false) {
        val repository = feedRepository ?: return
        if (_isRefreshing.value) return
        _isRefreshing.value = true
        viewModelScope.launch {
            try {
                if (loadCache) repository.cachedFeed()?.let { applyFeed(it) }
                applyFeed(repository.refresh())
                _feedMessage.value = null
            } catch (cancelled: kotlinx.coroutines.CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                _feedMessage.value = "Couldn’t refresh. Showing saved games."
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    internal fun applyFeed(feed: GameFeed) {
        val oldSelection = _selectedGameId.value
        val followDefault = feedDefaultId == null && oldSelection == PreseasonGameData.GAME_ID || oldSelection == feedDefaultId
        _games.clear()
        feed.games.forEach { _games[it.id] = it }
        if (followDefault || oldSelection !in _games) _selectedGameId.value = feed.defaultGameId
        feedDefaultId = feed.defaultGameId
        _selectedPlayer.value = null
        _aiAnalysisState.value = AiState.Idle
    }

    private val _selectedGameId = MutableStateFlow(PreseasonGameData.GAME_ID)
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
        val preseasonGame = PreseasonGameData.createGame()
        _games[preseasonGame.id] = preseasonGame

    }

    fun selectGame(id: String) {
        if (id !in _games) return
        _selectedGameId.value = id
        _selectedPlayer.value = null
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

    private fun generateLocalAnalysis(game: Game): String =
        "${game.awayTeam.name} ${game.awayScore} – ${game.homeScore} ${game.homeTeam.name}. " +
            "${game.dateLabel ?: ""}. ${game.status}. " +
            "Player totals are from the selected game’s saved box score."
}

// ==========================================
// 6. VECTOR CUSTOM GRAPHICS FOR TEAM LOGOS
// ==========================================

@Composable
fun TeamLogoCanvas(team: Team, size: Dp, modifier: Modifier = Modifier, tagSuffix: String = "") {
    GameImageAssets.teamLogo(team)?.let { logo ->
        Image(
            painter = painterResource(logo),
            contentDescription = "${team.name} logo",
            contentScale = ContentScale.Fit,
            modifier = modifier.size(size).clip(CircleShape).background(Color.White)
                .padding(size * 0.13f).testTag("team_logo_${team.code}$tagSuffix")
        )
        return
    }
    if (team.logoUrl != null) {
        var imageLoaded by remember(team.logoUrl) { mutableStateOf(false) }
        Box(modifier.size(size).clip(CircleShape).background(Color.White).testTag("team_logo_${team.code}$tagSuffix"),
            contentAlignment = Alignment.Center) {
            if (!imageLoaded) Text(team.code, color = Color.Black, fontSize = (size.value * 0.24f).sp)
            AsyncImage(model = team.logoUrl, contentDescription = "${team.name} logo",
                onSuccess = { imageLoaded = true }, onError = { imageLoaded = false },
                contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(size * 0.1f))
        }
        return
    }
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
                "PHI", "BKN" -> {
                    drawCircle(color = team.primaryColor, radius = radius, center = center)
                    drawCircle(color = team.secondaryColor, radius = radius, center = center, style = Stroke(width = 4f))
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNbaScreen(viewModel: GameViewModel) {
    val games = viewModel.games
    val selectedGameId by viewModel.selectedGameId.collectAsState()
    val selectedGame = games.find { it.id == selectedGameId } ?: games.firstOrNull() ?: return
    val selectedPlayer by viewModel.selectedPlayer.collectAsState()
    val aiState by viewModel.aiAnalysisState.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()
    val feedMessage by viewModel.feedMessage.collectAsState()

    val listState = rememberLazyListState()
    LaunchedEffect(selectedGame.id) { listState.scrollToItem(0) }

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
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(11.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(13.dp))
                            .background(ElegantAccent),
                        contentAlignment = Alignment.Center
                    ) {
                        Image(painterResource(R.drawable.miniscore_mark), contentDescription = "Miniscore logo",
                            modifier = Modifier.fillMaxSize().testTag("app_logo"))
                    }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = "Miniscore",
                            color = ElegantTextPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = (-0.5).sp,
                            fontFamily = FontFamily.SansSerif
                        )

                    }
                }
                IconButton(onClick = { viewModel.refreshGames() }, enabled = !isRefreshing,
                    modifier = Modifier.size(48.dp)) {
                    if (isRefreshing) CircularProgressIndicator(Modifier.size(20.dp), color = AccentCyan, strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Refresh games", tint = TextPrimary, modifier = Modifier.size(22.dp))
                }
            }
            feedMessage?.let {
                Text(it, color = TextSecondary, fontSize = 11.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }

            Divider(color = ElegantBorderColor.copy(alpha = 0.3f), thickness = 1.dp)

            // C. DETAIL VIEW (SCROLLABLE LINEUPS & STATS MOCKING FLASHSCORE LOGO/SYMMETRY ACCURATELY)
            PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refreshGames() },
                modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.testTag("game_list")
                        .fillMaxSize()
                        .fillMaxWidth()
                        .background(SportsDarkBackground)
                ) {
                    // 1. Core Header (Scoreboard like screenshot)
                    item {
                        ScoreboardHeaderPanel(game = selectedGame)
                    }

                    // 4. HEADER: STARTING LINEUPS
                    item {
                        if (selectedGame.startingAway.isNotEmpty() || selectedGame.startingHome.isNotEmpty()) {
                            LineupSectionHeader(selectedGame, "Starting lineup")
                        } else if (selectedGame.substitutesAway.isEmpty() && selectedGame.substitutesHome.isEmpty()) {
                            GameEmptyState(selectedGame)
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
                        if (selectedGame.substitutesAway.isNotEmpty() || selectedGame.substitutesHome.isNotEmpty()) {
                            LineupSectionHeader(selectedGame, "Substitutes")
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

                    val otherGames = games.filterNot { it.id == selectedGame.id }
                    if (otherGames.isNotEmpty()) {
                        item(key = "other_games_heading") {
                            Text("Other games", color = TextPrimary, fontSize = 16.sp, lineHeight = 22.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
                                    .testTag("other_games_heading"))
                        }
                        items(otherGames, key = { "collapsed_${it.id}" }) { game ->
                            CollapsedGameCard(game) { viewModel.selectGame(game.id) }
                        }
                    }
                    item { MockAdvertisementBanner() }

                    // Bottom padding
                    item {
                        Spacer(modifier = Modifier.height(24.dp))
                    }
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
fun SymmetricalLineplayRow(
    leftPlayer: Player?,
    rightPlayer: Player?,
    onPlayerClick: (Player) -> Unit
) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    if (largeText) {
        Column(Modifier.fillMaxWidth().background(SportsDarkBackground).padding(horizontal = 16.dp)
            .testTag("stacked_player_pair")) {
            leftPlayer?.let { PlayerLineupCell(it, Modifier.fillMaxWidth().padding(vertical = 10.dp), onPlayerClick) }
            rightPlayer?.let { PlayerLineupCell(it, Modifier.fillMaxWidth().padding(vertical = 10.dp), onPlayerClick) }
        }
    } else {
        Row(Modifier.fillMaxWidth().background(SportsDarkBackground).padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            if (leftPlayer != null) PlayerLineupCell(leftPlayer, Modifier.weight(1f).padding(end = 5.dp), onPlayerClick)
            else Spacer(Modifier.weight(1f))
            Box(Modifier.width(1.dp).height(108.dp).background(ElegantBorderColor))
            if (rightPlayer != null) PlayerLineupCell(rightPlayer, Modifier.weight(1f).padding(start = 5.dp), onPlayerClick)
            else Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
fun PlayerLineupCell(player: Player, modifier: Modifier = Modifier, onPlayerClick: (Player) -> Unit) {
    Column(
        modifier = modifier
            .clickable(onClickLabel = "Open player details") { onPlayerClick(player) }
            .testTag("lineup_player_${player.id}")
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(7.dp)
        ) {
            PlayerPortrait(player, size = 38.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = player.name,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (player.isTopPerformer) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "#${player.number} · ${player.teamCode ?: player.id.substringBefore('_').uppercase()}" +
                        if (player.flag.isNotEmpty()) " ${player.flag}" else "",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                    modifier = Modifier.padding(top = 2.dp)
                )
                player.rating?.let { rating ->
                    Text(
                        text = String.format("%.1f", rating), color = Color.Black, fontSize = 12.sp,
                        modifier = Modifier.clip(RoundedCornerShape(3.dp))
                            .background(if (rating >= 7.0) RatingGreen else if (rating >= 6.0) RatingOrange else RatingRed)
                            .padding(horizontal = 3.dp)
                    )
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
            listOf("PTS", "REB", "AST", "FGM", "FGA").forEach { label ->
                Text(
                    text = label,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 2.dp)) {
            listOf(player.stats.points, player.stats.rebounds, player.stats.assists, player.stats.fgMade, player.stats.fgAttempted).forEach { value ->
                Text(
                    text = value.toString(),
                    color = AccentCyan,
                    fontSize = 14.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun PlayerPortrait(player: Player, size: Dp, modifier: Modifier = Modifier) {
    val portrait = GameImageAssets.playerPortrait(player)
    val portraitModifier = modifier.size(size).clip(CircleShape).background(ElegantBorderColor)
        .testTag("player_portrait_${player.id}")
    if (portrait != null) {
        Image(painterResource(portrait), contentDescription = "${player.name} photo",
            contentScale = ContentScale.Crop, modifier = portraitModifier)
    } else {
        var imageLoaded by remember(player.portraitUrl) { mutableStateOf(false) }
        Box(portraitModifier, contentAlignment = Alignment.Center) {
            if (!imageLoaded) Text(player.name.split(' ').take(2).mapNotNull { it.firstOrNull() }.joinToString(""),
                color = TextPrimary, fontSize = (size.value * 0.3f).sp)
            if (player.portraitUrl != null) {
                AsyncImage(model = player.portraitUrl, contentDescription = "${player.name} photo",
                    onSuccess = { imageLoaded = true }, onError = { imageLoaded = false },
                    contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
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
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = ElegantHeaderBg,
        scrimColor = Color.Black.copy(alpha = 0.7f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = ElegantBorderColor) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
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
                    player.rating == null -> ElegantBorderColor
                    (player.rating ?: 0.0) >= 7.0 -> RatingGreen
                    (player.rating ?: 0.0) >= 6.0 -> RatingOrange
                    else -> RatingRed
                }
                if (GameImageAssets.playerPortrait(player) != null || player.portraitUrl != null) {
                    PlayerPortrait(player, size = 54.dp)
                } else Box(
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
            TelemetryBar(label = "Turnovers", current = player.stats.turnovers, max = 10)
            TelemetryBar(label = "FG % / Accuracy", current = player.stats.fgPercentage, max = 100, isPercentage = true, displayValue = "${player.stats.fgMade}/${player.stats.fgAttempted}")
            Text(text = "Minutes played: ${player.stats.minutesDisplay}", color = TextSecondary, fontSize = 12.sp)

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
        if (player.stats.minutes > 0 || player.stats.seconds > 0) {
            return "${player.stats.points} points, ${player.stats.rebounds} rebounds and ${player.stats.assists} assists in ${player.stats.minutesDisplay}. Player rating unavailable."
        }
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
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 16.dp)
        .border(1.dp, ElegantBorderColor, RoundedCornerShape(8.dp)).padding(12.dp)
        .testTag("promotion"), verticalAlignment = Alignment.CenterVertically) {
        Text("Sponsored", color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.weight(1f))
        Text("TEMU", color = TextSecondary, fontSize = 14.sp, lineHeight = 18.sp)
    }
}

@Composable
fun GameEmptyState(game: Game) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 24.dp)
        .testTag("game_empty_state"), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (game.status == "SCHEDULED") "Game hasn’t started" else "Player stats unavailable",
            color = TextPrimary, fontSize = 18.sp, lineHeight = 24.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center)
        if (game.status == "SCHEDULED") {
            val time = pickerStatus(game)
            Text(if (time == "Scheduled") "Tipoff time to be confirmed" else "Tipoff: $time",
                color = AccentCyan, fontSize = 14.sp, lineHeight = 20.sp,
                textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
        }
        Text("Player stats will appear when available.", color = TextSecondary, fontSize = 12.sp,
            lineHeight = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 8.dp))
    }
}
