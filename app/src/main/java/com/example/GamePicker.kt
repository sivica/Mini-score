package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun GamePicker(games: List<Game>, selected: Game, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(Modifier.fillMaxWidth().heightIn(min = 48.dp)
            .background(SportsDarkSurface, RoundedCornerShape(12.dp))
            .border(1.dp, ElegantBorderColor, RoundedCornerShape(12.dp))
            .testTag("game_selector").clickable { expanded = true }.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("${selected.awayTeam.shortName} at ${selected.homeTeam.shortName}",
                color = TextPrimary, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f))
            Spacer(Modifier.width(8.dp))
            Text(if (selected.isDemo) "Demo" else if (selected.status == "FINISHED") "Final" else selected.status.lowercase().replaceFirstChar { it.uppercase() },
                color = AccentCyan, fontSize = 11.sp)
            Icon(Icons.Default.KeyboardArrowDown, "Select game", tint = TextSecondary, modifier = Modifier.size(24.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            games.forEach { game ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text("${game.awayTeam.name} at ${game.homeTeam.name}", fontSize = 13.sp)
                            Text(if (game.isDemo) "Demo game" else "${game.dateLabel} · ${game.status}", fontSize = 11.sp)
                        }
                    }, modifier = Modifier.testTag("select_game_${game.id}"),
                    onClick = { onSelect(game.id); expanded = false }
                )
            }
        }
    }
}
