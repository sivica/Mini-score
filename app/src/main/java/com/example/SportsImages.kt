package com.example

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.ui.theme.*

@Composable
private fun SportsImage(url: String?, modifier: Modifier, scale: ContentScale,
                        placeholder: @Composable BoxScope.() -> Unit) {
    var loaded by remember(url) { mutableStateOf(false) }
    Box(modifier, contentAlignment = Alignment.Center) {
        if (!loaded) placeholder()
        if (url != null) AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).crossfade(true).build(),
            contentDescription = null, contentScale = scale, modifier = Modifier.fillMaxSize(),
            onSuccess = { loaded = true }, onError = { loaded = false })
    }
}

@Composable
fun TeamIdentity(team: Team, modifier: Modifier = Modifier, logoSize: Dp = 48.dp) {
    Column(modifier.semantics(mergeDescendants = true) {}, horizontalAlignment = Alignment.CenterHorizontally) {
        SportsImage(team.logoUrl, Modifier.size(logoSize).testTag("team_logo_${team.code}"), ContentScale.Fit) {
            Icon(Icons.Default.Star, contentDescription = null, tint = TextSecondary,
                modifier = Modifier.size(logoSize * 0.6f))
        }
        Spacer(Modifier.height(4.dp))
        TeamNameText(team, Modifier.fillMaxWidth())
    }
}

@Composable
fun PlayerPortrait(player: Player, size: Dp, modifier: Modifier = Modifier, tag: String = "player_portrait") {
    SportsImage(player.portraitUrl, modifier.size(size).clip(CircleShape).background(SportsDarkSurface)
        .testTag("${tag}_${player.id}"), ContentScale.Crop) {
        Icon(Icons.Default.Person, contentDescription = null, tint = TextSecondary,
            modifier = Modifier.size(size * 0.65f))
    }
}
