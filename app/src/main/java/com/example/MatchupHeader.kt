package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
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
    val largeText = LocalDensity.current.fontScale >= 1.5f
    Column(Modifier.fillMaxWidth().background(SportsHeaderBg).testTag("scoreboard_header")) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp)) {
            val logoSize = if (maxWidth >= 336.dp) 72.dp else 64.dp
            Column {
                if (largeText) GameMetadata(game, Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(logoSize).testTag("scoreboard_logo_${game.awayTeam.code}")) {
                            TeamLogoCanvas(game.awayTeam, logoSize)
                        }
                    }
                    Column(Modifier.weight(2f), horizontalAlignment = Alignment.CenterHorizontally) {
                        if (!largeText) {
                            GameMetadata(game, Modifier.fillMaxWidth())
                            ScorePair(game, Modifier.fillMaxWidth().padding(top = 5.dp))
                        }
                    }
                    Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(logoSize).testTag("scoreboard_logo_${game.homeTeam.code}")) {
                            TeamLogoCanvas(game.homeTeam, logoSize)
                        }
                    }
                }
                // Enlarged text gets the full width for the score rather than being squeezed between logos.
                if (largeText) ScorePair(game, Modifier.fillMaxWidth().padding(top = 6.dp))
                Row(Modifier.fillMaxWidth()) {
                    Text(shortTeamName(game.awayTeam), color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f).alignByBaseline()
                            .testTag("scoreboard_away_name"))
                    TextButton(onClick = { showQuarters = !showQuarters },
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.weight(2f).heightIn(min = 48.dp).alignByBaseline().testTag("quarter_toggle")) {
                        Text(when (game.status) { "FINISHED" -> "Final"; "SCHEDULED" -> "Scheduled"; else -> game.status },
                            color = if (game.status == "LIVE") Color(0xFFFFB4AB) else TextPrimary,
                            fontSize = 12.sp, lineHeight = 16.sp, modifier = Modifier.testTag("game_status"))
                        Spacer(Modifier.width(4.dp))
                        Icon(if (showQuarters) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            if (showQuarters) "Hide game details" else "Show game details", tint = AccentCyan,
                            modifier = Modifier.size(20.dp))
                    }
                    Text(shortTeamName(game.homeTeam), color = TextPrimary, fontSize = 14.sp, lineHeight = 20.sp,
                        textAlign = TextAlign.Center, modifier = Modifier.weight(1f).alignByBaseline()
                            .testTag("scoreboard_home_name"))
                }
            }
        }
        if (showQuarters) game.venue?.let {
            Text(it, color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp))
        }
        if (showQuarters && hasQuarters) {
            val awayName = shortTeamName(game.awayTeam)
            val homeName = shortTeamName(game.homeTeam)
            Column(Modifier.fillMaxWidth().testTag("quarter_scores").padding(horizontal = 12.dp, vertical = 8.dp)) {
                game.awayQuarters.indices.toList().chunked(4).forEach { quarters ->
                    Row(Modifier.fillMaxWidth()) {
                        Spacer(Modifier.weight(2f))
                        quarters.forEach { index ->
                            Text("Q${index + 1}", color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                        }
                    }
                    listOf(awayName to game.awayQuarters, homeName to game.homeQuarters).forEach { (name, scores) ->
                        if (LocalDensity.current.fontScale >= 1.5f) {
                            Text(name, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                        }
                        Row(Modifier.fillMaxWidth()) {
                            if (LocalDensity.current.fontScale < 1.5f) {
                                Text(name, color = TextSecondary, fontSize = 12.sp, lineHeight = 18.sp,
                                    modifier = Modifier.weight(2f))
                            } else {
                                Spacer(Modifier.weight(2f))
                            }
                            quarters.forEach { index ->
                                Text(scores[index].toString(), color = TextSecondary, fontSize = 12.sp,
                                    lineHeight = 18.sp, fontFamily = FontFamily.Monospace,
                                    textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GameMetadata(game: Game, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(game.contextLabel ?: "NBA", color = TextSecondary, fontSize = 12.sp,
            lineHeight = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("game_context"))
        Spacer(Modifier.height(2.dp))
        Text(pickerDate(game, compact = true), color = TextSecondary, fontSize = 12.sp,
            lineHeight = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.testTag("selected_game_date"))
    }
}

@Composable
private fun ScorePair(game: Game, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        Text(if (game.status == "SCHEDULED") "—" else game.awayScore.toString(),
            color = TextPrimary, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End, modifier = Modifier.testTag("team_score_${game.awayTeam.code}"))
        Text("–", color = TextSecondary, fontSize = 16.sp,
            modifier = Modifier.padding(horizontal = 8.dp))
        Text(if (game.status == "SCHEDULED") "—" else game.homeScore.toString(),
            color = TextPrimary, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier.testTag("team_score_${game.homeTeam.code}"))
    }
}

internal fun shortTeamName(team: Team): String = when (team.code) {
    "PHI" -> "76ers"
    "BKN" -> "Nets"
    else -> team.shortName
}

@Composable
fun LineupSectionHeader(game: Game, title: String) {
    Column(Modifier.fillMaxWidth().heightIn(min = 32.dp).background(SportsHeaderBg)
        .testTag("lineup_header_$title").padding(horizontal = 10.dp, vertical = 6.dp)) {
        val largeText = LocalDensity.current.fontScale >= 1.5f
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(shortTeamName(game.awayTeam), color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                modifier = Modifier.weight(1f))
            if (!largeText) Text(title, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp,
                fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
            Text(shortTeamName(game.homeTeam), color = TextSecondary, fontSize = 12.sp, lineHeight = 16.sp,
                textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
        if (largeText) Text(title, color = TextPrimary, fontSize = 12.sp, lineHeight = 16.sp,
            fontWeight = FontWeight.Medium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    }
}
