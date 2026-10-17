package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.*
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Compact game choices inside the searchable drawer. */
@Composable
fun CollapsedGameCard(game: Game, selected: Boolean = false, onClick: () -> Unit) {
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (selected) ElegantSelectedBg else SportsDarkSurface),
        border = BorderStroke(1.dp, ElegantBorderColor),
        modifier = Modifier.fillMaxWidth().padding(horizontal = if (largeText) 12.dp else 16.dp, vertical = 4.dp)
            .testTag("select_game_${game.id}").semantics { this.selected = selected }) {
        Column(Modifier.fillMaxWidth().padding(horizontal = if (largeText) 8.dp else 12.dp, vertical = 12.dp)) {
            if (largeText) DrawerScore(game, 14, Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TeamIdentity(game.awayTeam, Modifier.fillMaxWidth().testTag("drawer_team_${game.awayTeam.code}_${game.id}"), 32.dp)
                }
                if (!largeText) {
                    DrawerScore(game, 18, Modifier.weight(1.25f))
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    TeamIdentity(game.homeTeam, Modifier.fillMaxWidth().testTag("drawer_team_${game.homeTeam.code}_${game.id}"), 32.dp)
                }
            }
            Text(pickerStatus(game), color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                    .testTag("drawer_status_${game.id}"))
        }
    }
}

@Composable
private fun DrawerScore(game: Game, fontSize: Int, modifier: Modifier) {
    Row(modifier.testTag("drawer_score_${game.id}"), horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically) {
        Text(if (game.status == "SCHEDULED") "—" else game.awayScore.toString(),
            color = TextPrimary, fontSize = fontSize.sp, fontWeight = FontWeight.Medium)
        Text("–", color = TextSecondary, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Text(if (game.status == "SCHEDULED") "—" else game.homeScore.toString(),
            color = TextPrimary, fontSize = fontSize.sp, fontWeight = FontWeight.Medium)
    }
}

internal fun pickerDate(game: Game, compact: Boolean = false): String {
    val date = game.gameDate ?: return game.dateLabel ?: "Date unavailable"
    return try {
        val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC"); isLenient = false
        }
        SimpleDateFormat(if (compact) "EEE, MMM d" else "EEEE, MMMM d", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(requireNotNull(parser.parse(date)))
    } catch (_: Exception) { game.dateLabel ?: date }
}

internal fun pickerStatus(game: Game): String = when (game.status) {
    "FINISHED" -> "Final"
    "LIVE" -> "Live"
    "SCHEDULED" -> try {
        val date = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC"); isLenient = false
        }.parse(requireNotNull(game.tipoffUtc))
        SimpleDateFormat("h:mm a 'ET'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("America/New_York")
        }.format(requireNotNull(date))
    } catch (_: Exception) { "Scheduled" }
    else -> game.status.lowercase().replaceFirstChar { it.uppercase() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesDrawer(games: List<Game>, selectedId: String, onClose: () -> Unit, onSelect: (Game) -> Unit) {
    val uriHandler = LocalUriHandler.current
    var query by rememberSaveable { mutableStateOf("") }
    val visible = games.filter { matchesGameSearch(it, query) }
    val width = (LocalConfiguration.current.screenWidthDp - 44).coerceAtMost(320).dp
    ModalDrawerSheet(modifier = Modifier.width(width).testTag("games_drawer"),
        drawerContainerColor = SportsDarkBackground, windowInsets = WindowInsets(0, 0, 0, 0)) {
        Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MiniscoreLogo(32.dp)
                Text("Miniscore", color = TextPrimary, fontSize = 18.sp, lineHeight = 24.sp,
                    fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f).padding(horizontal = 8.dp))
                IconButton(onClick = onClose, modifier = Modifier.size(48.dp)) {
                    Icon(Icons.Default.Close, "Close games drawer", tint = AccentCyan)
                }
            }
            OutlinedTextField(query, onValueChange = { query = it }, label = { Text("Search games") },
                singleLine = true, modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)
                    .testTag("game_search"),
                colors = OutlinedTextFieldDefaults.colors(focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary, focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = TextSecondary, focusedLabelColor = AccentCyan,
                    unfocusedLabelColor = TextSecondary, cursorColor = AccentCyan))
            LazyColumn(Modifier.weight(1f).testTag("drawer_game_list"), contentPadding = PaddingValues(bottom = 16.dp)) {
                if (visible.isEmpty()) item {
                    Text("No games found", color = TextSecondary, fontSize = 14.sp,
                        modifier = Modifier.padding(16.dp).semantics { liveRegion = LiveRegionMode.Polite })
                }
                visible.groupBy { it.gameDate ?: it.dateLabel }.forEach { (_, datedGames) ->
                    item {
                        Text(pickerDate(datedGames.first()), color = TextSecondary, fontSize = 12.sp,
                            lineHeight = 16.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    items(datedGames, key = { it.id }) { game ->
                        CollapsedGameCard(game, selected = game.id == selectedId) { onSelect(game) }
                    }
                }
            }
            TextButton(onClick = { uriHandler.openUri("https://sivica.github.io/Mini-score/privacy/") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("privacy_policy")) {
                Text("Privacy policy", color = AccentCyan, fontSize = 14.sp)
            }
        }
    }
}

internal fun matchesGameSearch(game: Game, query: String): Boolean {
    val terms = query.lowercase(Locale.ROOT).trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    val words = "${game.awayTeam.name} ${game.homeTeam.name} ${game.gameDate} ${pickerDate(game)}"
        .lowercase(Locale.ROOT).split(Regex("[^\\p{L}\\p{N}]+"))
    return terms.all { term -> words.any { it.startsWith(term) } }
}
