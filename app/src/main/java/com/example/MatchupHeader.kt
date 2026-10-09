package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun ScoreboardHeaderPanel(game: Game) {
    var showQuarters by remember(game.id) { mutableStateOf(false) }
    val hasQuarters = game.awayQuarters.isNotEmpty() && game.awayQuarters.size == game.homeQuarters.size
    Column(
        modifier = Modifier.fillMaxWidth().background(SportsHeaderBg).padding(top = 6.dp, bottom = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(game.contextLabel ?: "NBA", color = AccentCyan, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(6.dp))
            Text(if (game.status == "FINISHED") "FINAL" else game.status,
                color = if (game.status == "LIVE") Color(0xFFFFB4AB) else TextSecondary,
                fontSize = 12.sp, lineHeight = 16.sp)
            if (hasQuarters) {
                IconButton(onClick = { showQuarters = !showQuarters },
                    modifier = Modifier.size(48.dp).testTag("quarter_toggle")) {
                    Icon(if (showQuarters) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                        contentDescription = if (showQuarters) "Hide quarter scores" else "Show quarter scores",
                        tint = AccentCyan, modifier = Modifier.size(18.dp))
                }
            }
        }
        Text(pickerDate(game, compact = true), color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
            modifier = Modifier.testTag("selected_game_date").padding(horizontal = 12.dp, vertical = 2.dp))
        game.venue?.let {
            Text(it, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.padding(horizontal = 12.dp))
        }
        BoxWithConstraints(Modifier.fillMaxWidth()) {
          if (LocalDensity.current.fontScale >= 1.5f && maxWidth < 360.dp) {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp)) {
                Column(Modifier.weight(1f)) {
                    TeamLogoCanvas(game.awayTeam, 32.dp)
                    Text(shortTeamName(game.awayTeam), color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    TeamLogoCanvas(game.homeTeam, 32.dp)
                    Text(shortTeamName(game.homeTeam), color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp,
                        textAlign = TextAlign.End, modifier = Modifier.padding(top = 4.dp))
                }
            }
          } else {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp, start = 16.dp, end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                TeamLogoCanvas(game.awayTeam, 32.dp)
                Spacer(Modifier.width(7.dp))
                Text(shortTeamName(game.awayTeam), color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp,
                    modifier = Modifier.weight(1f))
            }
            Spacer(Modifier.width(16.dp))
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.End) {
                Text(shortTeamName(game.homeTeam), color = TextPrimary, fontSize = 14.sp, lineHeight = 18.sp,
                    modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                Spacer(Modifier.width(7.dp))
                TeamLogoCanvas(game.homeTeam, 32.dp)
            }
            }
          }
        }
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(if (game.status == "SCHEDULED") "—" else game.awayScore.toString(),
                color = TextPrimary, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.End,
                modifier = Modifier.weight(1f).testTag("team_score_${game.awayTeam.code}"))
            Text("–", color = TextSecondary, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.width(24.dp))
            Text(if (game.status == "SCHEDULED") "—" else game.homeScore.toString(),
                color = TextPrimary, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f).testTag("team_score_${game.homeTeam.code}"))
        }
        if (showQuarters && hasQuarters) {
            Text(
                text = "     " + game.awayQuarters.indices.joinToString("") { "Q${it + 1}".padStart(4) } + "\n" +
                    game.awayTeam.code.padEnd(5) + game.awayQuarters.joinToString("") { it.toString().padStart(4) } + "\n" +
                    game.homeTeam.code.padEnd(5) + game.homeQuarters.joinToString("") { it.toString().padStart(4) },
                color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center, modifier = Modifier.testTag("quarter_scores").padding(top = 8.dp)
            )
        }
    }
}

private fun shortTeamName(team: Team): String = when (team.code) {
    "PHI" -> "76ers"
    "BKN" -> "Nets"
    else -> team.shortName
}

@Composable
fun LineupSectionHeader(game: Game, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp).background(SportsHeaderBg)
            .testTag("lineup_header_$title").padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(game.awayTeam.code, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.weight(1f))
        Text(title, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium)
        Text(game.homeTeam.code, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f))
    }
}
