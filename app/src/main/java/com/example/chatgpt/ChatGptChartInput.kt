package com.example.chatgpt

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.visualization.VisualizationSession
import com.example.visualization.VisualizationSessionController

/** Native composer; OAuth and AI content never run inside the chart renderer. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ChatGptChartInput(session: VisualizationSession, controller: VisualizationSessionController) {
    if (!com.example.BuildConfig.CHATGPT_ENABLED) return
    val connection: ChatGptViewModel = viewModel()
    val state by connection.state.collectAsState()
    val context = LocalContext.current
    var prompt by rememberSaveable(session.id) { mutableStateOf("") }
    var accountMenu by remember { mutableStateOf(false) }
    var modelMenu by remember { mutableStateOf(false) }
    val busy = state.working || state.generating
    fun browser(url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addCategory(Intent.CATEGORY_BROWSABLE))
    }
    LaunchedEffect(connection, controller) { connection.bind(controller) }
    if (state.welcome) AlertDialog(onDismissRequest = connection::dismissWelcome,
        title = { Text("You’re using your ChatGPT plan") },
        text = { Text("Eligible requests in Miniscore use your ChatGPT plan allowance. Review limits and disconnect Miniscore in ChatGPT settings.") },
        confirmButton = { TextButton(onClick = connection::dismissWelcome) { Text("Got it") } })
    ElevatedCard(colors = CardDefaults.elevatedCardColors(containerColor = Color(0xff292930), contentColor = Color(0xfff5f5f8))) {
        Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Ask ChatGPT", fontSize = 18.sp)
            Text("Local test feature · Use your own eligible ChatGPT plan", fontSize = 12.sp)
            if (state.accounts.isNotEmpty()) Box {
                TextButton(onClick = { accountMenu = true }, enabled = !busy) {
                    Text(state.accounts.firstOrNull { it.clientId == state.active }?.label ?: "Choose ChatGPT account", fontSize = 14.sp)
                }
                DropdownMenu(expanded = accountMenu, onDismissRequest = { accountMenu = false }) {
                    state.accounts.forEach { account -> DropdownMenuItem(text = { Text(account.label) }, onClick = { accountMenu = false; connection.switchAccount(account.clientId) }) }
                    if (state.accounts.size < 8) DropdownMenuItem(text = { Text("Add ChatGPT account") }, onClick = { accountMenu = false; connection.signIn(openBrowser = ::browser) })
                }
            }
            val selected = state.accounts.firstOrNull { it.clientId == state.active }
            if (selected?.connected != true) Button(onClick = { connection.signIn(selected?.clientId, openBrowser = ::browser) }, enabled = state.ready && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)) {
                Text("Continue with ChatGPT", fontSize = 14.sp)
            } else if (!state.planEnabled) Button(onClick = { connection.signIn(selected.clientId, enablePlan = true, openBrowser = ::browser) }, enabled = !busy) {
                Text("Enable plan usage", fontSize = 14.sp)
            }
            if (state.planEnabled) {
                Text("Using ChatGPT plan", fontSize = 14.sp)
                Box {
                    TextButton(onClick = { modelMenu = true }, enabled = !busy && state.models.isNotEmpty()) {
                        Text("Model: ${state.models.firstOrNull { it.id == state.model }?.name ?: "Choose a model"}", fontSize = 14.sp)
                    }
                    DropdownMenu(expanded = modelMenu, onDismissRequest = { modelMenu = false }) {
                        state.models.forEach { model -> DropdownMenuItem(text = { Text(model.name) }, onClick = { modelMenu = false; connection.chooseModel(model.id) }) }
                    }
                }
                if (state.models.isEmpty()) TextButton(onClick = connection::refreshModels, enabled = !busy) { Text("Load models", fontSize = 14.sp) }
                OutlinedTextField(value = prompt, onValueChange = { if (it.length <= 1000) prompt = it },
                    label = { Text("Describe a chart") }, placeholder = { Text("Compare MEM and CHI rebounds") },
                    supportingText = { Text("Current game only · ${prompt.length}/1000") },
                    enabled = !busy, minLines = 2, maxLines = 4, modifier = Modifier.fillMaxWidth())
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Show points", "Compare rebounds", "Show field goals").forEach { suggestion ->
                        TextButton(onClick = { prompt = suggestion }, enabled = !busy) { Text(suggestion, fontSize = 14.sp) }
                    }
                }
                Text("Sends your prompt, chart settings, team codes and player names to OpenAI.", fontSize = 12.sp)
                Button(onClick = { connection.generate(prompt, session.snapshot, session.spec) { generated -> controller.applyGeneratedSpec(session, generated) } }, enabled = !busy && !session.refreshing && !session.importing && prompt.isNotBlank() && state.model != null) {
                    Text(if (state.generating) "Generating…" else "Generate chart", fontSize = 14.sp)
                }
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (state.generating) TextButton(onClick = connection::cancelGeneration) { Text("Cancel generation", fontSize = 14.sp) }
                if (state.working) TextButton(onClick = connection::cancelSignIn) { Text("Cancel connection", fontSize = 14.sp) }
                TextButton(onClick = { browser(USAGE_URL) }) { Text("Manage usage", fontSize = 14.sp) }
                if (selected?.connected == true) TextButton(onClick = connection::signOut, enabled = !state.working) { Text("Sign out", fontSize = 14.sp) }
            }
            state.message?.let { Text(it, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }) }
        }
    }
}
