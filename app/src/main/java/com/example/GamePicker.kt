package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/** Other games stay collapsed below the current game's box score. */
@Composable
fun CollapsedGameCard(game: Game, onClick: () -> Unit) {
    Card(onClick = onClick, shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = SportsDarkSurface),
        border = BorderStroke(1.dp, ElegantBorderColor),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
            .testTag("select_game_${game.id}")) {
        Column(Modifier.padding(12.dp)) {
            CollapsedTeamRow(game.awayTeam, if (game.status == "SCHEDULED") "—" else game.awayScore.toString())
            Spacer(Modifier.height(6.dp))
            CollapsedTeamRow(game.homeTeam, if (game.status == "SCHEDULED") "—" else game.homeScore.toString())
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${pickerDate(game, compact = true)} · ${pickerStatus(game)}",
                    color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.KeyboardArrowDown, "Show game details", tint = AccentCyan, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun CollapsedTeamRow(team: Team, score: String) {
    Row(Modifier.fillMaxWidth().heightIn(min = 28.dp), verticalAlignment = Alignment.CenterVertically) {
        TeamLogoCanvas(team, 24.dp, tagSuffix = "_collapsed")
        Spacer(Modifier.width(10.dp))
        Text(team.shortName, color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp,
            modifier = Modifier.weight(1f))
        Spacer(Modifier.width(12.dp))
        Text(score, color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
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
