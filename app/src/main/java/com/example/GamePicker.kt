package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamePicker(games: List<Game>, selected: Game, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)
        .heightIn(min = 58.dp).background(SportsDarkSurface, RoundedCornerShape(14.dp))
        .border(1.dp, ElegantBorderColor, RoundedCornerShape(14.dp))
        .testTag("game_selector").clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.width(42.dp).height(24.dp)) {
            TeamLogoCanvas(selected.awayTeam, 24.dp, Modifier.align(Alignment.CenterStart), tagSuffix = "_picker")
            TeamLogoCanvas(selected.homeTeam, 24.dp, Modifier.align(Alignment.CenterEnd), tagSuffix = "_picker")
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Text("${selected.awayTeam.shortName} at ${selected.homeTeam.shortName}",
                color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${pickerDate(selected, compact = true)} · ${pickerStatus(selected)}",
                color = TextSecondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("selected_game_date").padding(top = 3.dp))
        }
        Icon(Icons.Default.KeyboardArrowDown, "Choose game", tint = AccentCyan, modifier = Modifier.size(24.dp))
    }

    if (expanded) {
        ModalBottomSheet(onDismissRequest = { expanded = false }, sheetState = sheetState,
            containerColor = SportsHeaderBg, contentColor = TextPrimary,
            modifier = Modifier.testTag("game_picker_sheet")) {
            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Choose game", fontSize = 17.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                IconButton(onClick = { expanded = false }, modifier = Modifier.testTag("close_game_selector")) {
                    Icon(Icons.Default.Close, "Close game selector", tint = TextSecondary, modifier = Modifier.size(20.dp))
                }
            }
            // Long feeds scroll within the sheet rather than covering the status bar.
            LazyColumn(Modifier.fillMaxWidth(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)) {
                games.groupBy { it.gameDate ?: it.dateLabel }.forEach { (_, datedGames) ->
                    item {
                        Text(pickerDate(datedGames.first()), color = TextSecondary, fontSize = 11.sp,
                            modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))
                    }
                    items(datedGames, key = { it.id }) { game ->
                        GamePickerCard(game, game.id == selected.id) { onSelect(game.id); expanded = false }
                    }
                }
            }
        }
    }
}

@Composable
private fun GamePickerCard(game: Game, isSelected: Boolean, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = if (isSelected) ElegantSelectedBg else SportsDarkSurface),
        border = BorderStroke(1.dp, if (isSelected) AccentCyan else ElegantBorderColor),
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).testTag("select_game_${game.id}")
            .semantics { selected = isSelected }) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 9.dp)) {
            PickerTeamRow(game.awayTeam, if (game.status == "SCHEDULED") "—" else game.awayScore.toString())
            Spacer(Modifier.height(3.dp))
            PickerTeamRow(game.homeTeam, if (game.status == "SCHEDULED") "—" else game.homeScore.toString())
            Row(Modifier.fillMaxWidth().padding(start = 32.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(pickerStatus(game), color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
                if (isSelected) {
                    Icon(Icons.Default.Check, null, tint = AccentCyan, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Selected", color = AccentCyan, fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun PickerTeamRow(team: Team, score: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 26.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamLogoCanvas(team, 22.dp)
        Spacer(Modifier.width(10.dp))
        Text(team.shortName, color = TextPrimary, fontSize = 12.sp, maxLines = 1,
            overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Spacer(Modifier.width(8.dp))
        Text(score, color = TextPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
