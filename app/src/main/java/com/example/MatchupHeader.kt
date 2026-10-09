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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun ScoreboardHeaderPanel(game: Game) {
    var showQuarters by remember(game.id) { mutableStateOf(false) }
    val hasQuarters = game.awayQuarters.isNotEmpty() && game.awayQuarters.size == game.homeQuarters.size
    Column(
        modifier = Modifier.fillMaxWidth().background(SportsHeaderBg).padding(top = 14.dp, bottom = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(horizontal = 12.dp)) {
            Text(game.contextLabel ?: "NBA", color = AccentCyan, fontSize = 11.sp)
            Text(if (game.status == "FINISHED") "FINAL" else game.status,
                color = if (game.status.contains("LIVE")) RatingRed else TextSecondary,
                fontSize = 11.sp, fontWeight = FontWeight.Medium)
        }
        if (game.venue != null || hasQuarters) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 12.dp)) {
                game.venue?.let { Text(it, color = TextSecondary, fontSize = 11.sp) }
                if (hasQuarters) {
                    IconButton(onClick = { showQuarters = !showQuarters },
                        modifier = Modifier.size(32.dp).testTag("quarter_toggle")) {
                        Icon(if (showQuarters) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = if (showQuarters) "Hide quarter scores" else "Show quarter scores",
                            tint = AccentCyan, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }
        Row(modifier = Modifier.fillMaxWidth().padding(top = 10.dp, start = 12.dp, end = 12.dp)) {
            ScoreboardTeamColumn(game.awayTeam, game.awayScore, Modifier.weight(1f))
            ScoreboardTeamColumn(game.homeTeam, game.homeScore, Modifier.weight(1f))
        }
        if (showQuarters && hasQuarters) {
            Text(
                text = "     " + game.awayQuarters.indices.joinToString("") { "Q${it + 1}".padStart(4) } + "\n" +
                    game.awayTeam.code.padEnd(5) + game.awayQuarters.joinToString("") { it.toString().padStart(4) } + "\n" +
                    game.homeTeam.code.padEnd(5) + game.homeQuarters.joinToString("") { it.toString().padStart(4) },
                color = TextSecondary, fontSize = 11.sp, fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center, modifier = Modifier.testTag("quarter_scores").padding(top = 8.dp)
            )
        }
    }
}

@Composable
private fun ScoreboardTeamColumn(team: Team, score: Int, modifier: Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        TeamLogoCanvas(team, size = 56.dp)
        Spacer(Modifier.height(7.dp))
        Text(team.name, color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().heightIn(min = 16.dp))
        Spacer(Modifier.height(5.dp))
        Text(score.toString(), color = TextPrimary, fontSize = 32.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("team_score_${team.code}"))
    }
}

@Composable
fun LineupSectionHeader(game: Game, title: String) {
    Row(
        modifier = Modifier.fillMaxWidth().height(32.dp).background(SportsHeaderBg)
            .testTag("lineup_header_$title").padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(game.awayTeam.code, color = TextSecondary, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text(title, color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Medium)
        Text(game.homeTeam.code, color = TextSecondary, fontSize = 11.sp, textAlign = TextAlign.End,
            modifier = Modifier.weight(1f))
    }
}
