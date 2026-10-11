package com.example.visualization

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.webkit.*
import java.io.ByteArrayInputStream
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import org.json.JSONObject
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private val BodyText = Color(0xfff5f5f8)
private val SecondaryText = Color(0xffe0e0e8)
private val Canvas = Color(0xff1b1b21)
private val Accent = Color(0xff71d6c4)

/** All filtering, import and source-data authority lives in native code. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VisualizationScreen(
    snapshot: VisualizationSnapshot,
    spec: VisualizationSpec,
    onSpecChange: (VisualizationSpec) -> Unit,
    onClose: () -> Unit,
    onRefresh: () -> Unit,
    onImportSpec: () -> Unit,
    refreshing: Boolean = false,
    error: String? = null,
    modifier: Modifier = Modifier,
    createChartView: (android.content.Context) -> WebView = { WebView(it) },
    promptInput: (@Composable () -> Unit)? = null,
) {
    var showTable by remember { mutableStateOf(false) }
    var failure by remember(snapshot.gameId) { mutableStateOf<String?>(null) }
    var attempt by remember { mutableIntStateOf(0) }
    val rows = remember(snapshot, spec) { VisualizationContract.rows(snapshot, spec) }
    val payload = remember(snapshot, spec) { VisualizationContract.encodeForRenderer(snapshot, spec) }
    val buttonColors = ButtonDefaults.textButtonColors(contentColor = Accent)
    Surface(modifier.fillMaxSize(), color = Canvas, contentColor = BodyText) {
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Text("Game visualizations", fontSize = 18.sp)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onClose, colors = buttonColors, modifier = Modifier.heightIn(min = 48.dp)) { Text("Close", fontSize = 14.sp) }
                    TextButton(onClick = onRefresh, enabled = !refreshing, colors = buttonColors, modifier = Modifier.heightIn(min = 48.dp)) { Text(if (refreshing) "Updating…" else "Refresh", fontSize = 14.sp) }
                    TextButton(onClick = onImportSpec, enabled = !refreshing, colors = buttonColors, modifier = Modifier.heightIn(min = 48.dp)) { Text("Import chart settings", fontSize = 14.sp) }
                }
                Text(snapshot.teams.joinToString(" vs ") { it.code }, fontSize = 14.sp)
                snapshot.competition?.let { Text(it, fontSize = 12.sp, color = SecondaryText) }
                snapshot.date?.let { Text(it, fontSize = 12.sp, color = SecondaryText) }
                Text("Status: ${snapshot.status}", fontSize = 14.sp, color = SecondaryText)
            }
            item {
                OptionPicker("Chart", if (spec.template == "playerStats") "Player statistics" else "Field goals",
                    listOf("playerStats" to "Player statistics", "fieldGoals" to "Field goals")) {
                    onSpecChange(spec.copy(template = it, metric = if (it == "fieldGoals") "points" else spec.metric))
                }
                OptionPicker("Team", if (spec.team == "both") "Both teams" else snapshot.teams.firstOrNull { it.side == spec.team }?.code ?: spec.team,
                    listOf("both" to "Both teams") + snapshot.teams.map { it.side to it.code }) {
                    onSpecChange(spec.copy(team = it, player = "all"))
                }
                val selectablePlayers = snapshot.players.filter { row -> spec.team == "both" || snapshot.teams.any { it.side == spec.team && it.id == row.teamId } }
                OptionPicker("Player", snapshot.players.firstOrNull { it.playerId == spec.player }?.name ?: "All players",
                    listOf("all" to "All players") + selectablePlayers.map { it.playerId to "${it.name} · ${it.teamId}" }) {
                    onSpecChange(spec.copy(player = it))
                }
                if (spec.template == "playerStats") OptionPicker("Statistic", metricLabel(spec.metric),
                    listOf("points", "rebounds", "assists", "steals", "blocks", "turnovers").map { it to metricLabel(it) }) {
                    onSpecChange(spec.copy(metric = it))
                }
                OptionPicker("Sort", sortLabel(spec.sort), listOf("desc", "asc", "name").map { it to sortLabel(it) }) {
                    onSpecChange(spec.copy(sort = it))
                }
            }
            if (promptInput != null) item { promptInput() }
            item {
                val metadata = snapshot.provenance
                Text(buildString {
                    append(if (metadata.fromCache) "Saved game data" else "Fetched game data")
                    append(" · Revision ${metadata.revision ?: "unknown"}")
                    append("\nPublished: ${metadata.publishedAt ?: "unknown"}")
                    append("\nFetched: ${timeLabel(metadata.fetchedAtMillis)}")
                    append("\nChart captured: ${timeLabel(snapshot.capturedAtMillis)}")
                }, color = SecondaryText, fontSize = 12.sp)
                if (error != null) Text(error, fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                if (snapshot.status.equals("scheduled", true)) Text("Game hasn’t started. Statistics may be unavailable.", fontSize = 14.sp)
                if (failure != null) Text("${failure}. Showing the source table.", fontSize = 14.sp, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite })
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (failure == null) TextButton(onClick = { showTable = !showTable }, colors = buttonColors, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text(if (showTable) "Show chart" else "Show accessible source table", fontSize = 14.sp)
                    }
                    if (failure != null) TextButton(onClick = { failure = null; showTable = false; attempt++ }, colors = buttonColors, modifier = Modifier.heightIn(min = 48.dp)) {
                        Text("Retry chart", fontSize = 14.sp)
                    }
                }
            }
            if (!showTable && failure == null && rows.isNotEmpty()) item {
                Text("Visual chart. The source table presents the same filtered values for screen readers.", fontSize = 12.sp, color = SecondaryText)
                key(snapshot.gameId, attempt) {
                    BundledChart(payload, Modifier.fillMaxWidth().height(420.dp), createChartView) { failure = it }
                }
            }
            if (rows.isEmpty()) item { Text("No players match these filters.", fontSize = 14.sp) }
            if (showTable || failure != null) {
                item {
                    Text(if (spec.template == "fieldGoals") "Field goals source table" else "${metricLabel(spec.metric)} source table", fontSize = 18.sp)
                    if (spec.template == "fieldGoals") {
                        val totalAttempts = rows.map { it.fgAttempted }.takeIf { values -> values.all { it != null } }?.sumOf { it ?: 0 }
                        val weighted = VisualizationContract.weightedFgPercent(rows)
                        val percentage = when { totalAttempts == 0 -> "N/A"; weighted == null -> "Unknown"; else -> String.format(Locale.US, "%.1f%%", weighted) }
                        Text("Combined FG%: $percentage (total made ÷ total attempts)", fontSize = 14.sp)
                    }
                }
                items(rows, key = { it.playerId }) { row ->
                    SourceRow(row, spec)
                    HorizontalDivider(color = Color(0xff777780))
                }
            }
        }
    }
}

@Composable
private fun OptionPicker(label: String, selected: String, options: List<Pair<String, String>>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TextButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp), colors = ButtonDefaults.textButtonColors(contentColor = BodyText)) {
            Text("$label: $selected", fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }, modifier = Modifier.heightIn(max = 360.dp)) {
            options.forEach { (value, title) ->
                DropdownMenuItem(text = { Text(title, fontSize = 14.sp) }, onClick = { expanded = false; onSelect(value) }, modifier = Modifier.heightIn(min = 48.dp))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SourceRow(row: VisualizationRow, spec: VisualizationSpec) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("${row.name} · ${row.teamId}", fontSize = 14.sp)
        if (spec.template == "fieldGoals") {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SourceValue("Made", row.fgMade?.toString() ?: "Unknown")
                SourceValue("Attempted", row.fgAttempted?.toString() ?: "Unknown")
                SourceValue("Missed", row.fgMissed?.toString() ?: "Unknown")
                SourceValue("FG%", when { row.fgAttempted == 0 -> "N/A"; row.fgPercent == null -> "Unknown"; else -> String.format(Locale.US, "%.1f%%", row.fgPercent) })
            }
        } else SourceValue(metricLabel(spec.metric), row.metric(spec.metric)?.toString() ?: "Unknown")
    }
}

@Composable
private fun SourceValue(label: String, value: String) {
    Column { Text(label, fontSize = 12.sp, color = SecondaryText); Text(value, fontSize = 14.sp) }
}

private fun metricLabel(metric: String) = metric.replaceFirstChar { it.uppercaseChar() }
private fun sortLabel(sort: String) = when (sort) { "asc" -> "Lowest first"; "name" -> "Player name"; else -> "Highest first" }
private fun timeLabel(value: Long?) = value?.let { DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it)) } ?: "unknown"

@Composable
private fun BundledChart(payload: String, modifier: Modifier, createView: (android.content.Context) -> WebView, onFailure: (String) -> Unit) {
    val context = LocalContext.current
    val scale = LocalDensity.current.fontScale
    val currentFailure by rememberUpdatedState(onFailure)
    val scope = rememberCoroutineScope()
    val initialized = remember(context, createView) {
        ChartController.create(context, scale, createView) { message ->
            // Provider callbacks/update can run while Compose applies changes. Report after that pass.
            scope.launch { yield(); currentFailure(message) }
        }
    }
    val controller = initialized.getOrNull()
    LaunchedEffect(initialized) { if (initialized.isFailure) currentFailure("Chart provider unavailable") }
    DisposableEffect(controller) { onDispose { controller?.close() } }
    if (controller != null) AndroidView(factory = { controller.view }, update = { controller.update(payload, scale) }, modifier = modifier)
}

/** No Javascript interface, navigation, network fallback, persisted renderer state or privileged messages. */
@Suppress("DEPRECATION")
private class ChartController(val view: WebView, val onFailure: (String) -> Unit) {
    companion object {
        fun create(context: android.content.Context, scale: Float, createView: (android.content.Context) -> WebView, onFailure: (String) -> Unit): Result<ChartController> {
            var partial: ChartController? = null
            return RendererInitialization.create({ createView(context) }, { view ->
                ChartController(view, onFailure).also { partial = it; it.start(context, scale) }
            }, { view -> partial?.close() ?: RendererInitialization.cleanup({ (view.parent as? ViewGroup)?.removeView(view) }, { view.destroy() }) })
        }
    }
    private val handler = Handler(Looper.getMainLooper())
    private val token = UUID.randomUUID().toString()
    private var sequence = 0
    private var pageReady = false
    private var pendingPayload: String? = null
    private var deliveredPayload: String? = null
    private var closed = false
    private var timeout: Runnable? = null
    private lateinit var loader: WebViewAssetLoader
    private var listenerAdded = false

    private fun start(context: android.content.Context, scale: Float) {
        loader = WebViewAssetLoader.Builder().addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context)).build()
        view.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
        view.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = false
            databaseEnabled = false
            allowFileAccess = false
            allowContentAccess = false
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            cacheMode = WebSettings.LOAD_NO_CACHE
            textZoom = (scale * 100).toInt()
            mediaPlaybackRequiresUserGesture = true
            blockNetworkLoads = true
        }
        CookieManager.getInstance().apply { setAcceptCookie(false); setAcceptThirdPartyCookies(view, false) }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BASIC_USAGE)) {
            val workers = ServiceWorkerControllerCompat.getInstance()
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_BLOCK_NETWORK_LOADS)) workers.serviceWorkerWebSettings.blockNetworkLoads = true
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_FILE_ACCESS)) workers.serviceWorkerWebSettings.allowFileAccess = false
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_CONTENT_ACCESS)) workers.serviceWorkerWebSettings.allowContentAccess = false
            if (WebViewFeature.isFeatureSupported(WebViewFeature.SERVICE_WORKER_SHOULD_INTERCEPT_REQUEST)) {
                workers.setServiceWorkerClient(object : ServiceWorkerClientCompat() {
                    override fun shouldInterceptRequest(request: WebResourceRequest): WebResourceResponse = denied()
                })
            }
        }
        view.setDownloadListener { _, _, _, _, _ -> }
        view.webChromeClient = object : WebChromeClient() {
            override fun onCreateWindow(v: WebView?, dialog: Boolean, userGesture: Boolean, result: android.os.Message?): Boolean = false
            override fun onPermissionRequest(request: PermissionRequest) = request.deny()
            override fun onGeolocationPermissionsShowPrompt(origin: String?, callback: GeolocationPermissions.Callback?) { callback?.invoke(origin, false, false) }
            override fun onShowFileChooser(v: WebView?, callback: android.webkit.ValueCallback<Array<android.net.Uri>>?, params: FileChooserParams?): Boolean { callback?.onReceiveValue(null); return true }
        }
        view.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(v: WebView?, request: WebResourceRequest): WebResourceResponse =
                if (RendererPolicy.permitsRequest(request.url.toString(), request.method)) loader.shouldInterceptRequest(request.url) ?: denied() else denied()
            override fun shouldOverrideUrlLoading(v: WebView?, request: WebResourceRequest): Boolean = true
            override fun shouldOverrideUrlLoading(v: WebView?, url: String?): Boolean = true
            override fun onPageStarted(v: WebView?, url: String?, favicon: Bitmap?) {
                if (url != RendererPolicy.INDEX) fail("Chart navigation blocked")
            }
            override fun onPageFinished(v: WebView?, url: String?) {
                if (!closed && url == RendererPolicy.INDEX && !pageReady) perform { view.evaluateJavascript("window.bootstrap(${JSONObject.quote(token)})", null) }
            }
            override fun onReceivedSslError(v: WebView?, handler: SslErrorHandler, error: android.net.http.SslError?) { handler.cancel() }
            override fun onReceivedError(v: WebView?, request: WebResourceRequest, error: WebResourceError?) { if (request.isForMainFrame) fail("Chart could not load") }
            override fun onRenderProcessGone(v: WebView?, detail: RenderProcessGoneDetail?): Boolean { fail("Chart renderer stopped"); return true }
        }
        if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE)) {
            WebViewCompat.setWebViewRenderProcessClient(view, object : androidx.webkit.WebViewRenderProcessClient() {
                override fun onRenderProcessUnresponsive(v: WebView, renderer: androidx.webkit.WebViewRenderProcess?) {
                    if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_TERMINATE)) renderer?.terminate()
                    fail("Chart renderer stopped responding")
                }
                override fun onRenderProcessResponsive(v: WebView, renderer: androidx.webkit.WebViewRenderProcess?) = Unit
            })
        }
        if (!WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
            handler.post { fail("Secure chart messaging unavailable") }
        } else {
            WebViewCompat.addWebMessageListener(view, "rendererEvents", setOf(RendererPolicy.ORIGIN)) { _, message, origin, mainFrame, _ ->
                if (!closed && message.type == WebMessageCompat.TYPE_STRING) {
                    when (RendererPolicy.event(message.data, origin.toString(), mainFrame, token, sequence)) {
                        "pageReady" -> if (!pageReady && sequence == 0) { pageReady = true; deliver() }
                        "ready" -> if (pageReady && sequence > 0) cancelTimeout()
                        "error" -> fail("Chart rendering failed")
                    }
                }
            }
            listenerAdded = true
            armTimeout()
            view.loadUrl(RendererPolicy.INDEX)
        }
    }

    fun update(payload: String, scale: Float) {
        if (closed) return
        perform {
            view.settings.textZoom = (scale * 100).toInt()
            pendingPayload = payload
            if (pageReady && payload != deliveredPayload) deliver()
        }
    }

    private fun deliver() {
        val payload = pendingPayload ?: return
        if (closed) return
        sequence++
        deliveredPayload = payload
        armTimeout()
        // Serializer emits bounded, native-validated data; strings never become source text.
        val quoted = RendererPolicy.quotePayload(payload)
        perform { view.evaluateJavascript("window.render(JSON.parse($quoted),${JSONObject.quote(token)},$sequence)", null) }
    }

    private fun armTimeout() {
        cancelTimeout()
        timeout = Runnable { fail("Chart exceeded the two-second wait") }.also { handler.postDelayed(it, 2000) }
    }
    private fun cancelTimeout() { timeout?.let { handler.removeCallbacks(it) }; timeout = null }
    private fun perform(action: () -> Unit) { if (!closed) runCatching(action).onFailure { fail("Chart provider unavailable") } }
    private fun fail(message: String) { if (!closed) { close(); onFailure(message) } }
    fun close() {
        if (closed) return
        closed = true
        cancelTimeout()
        handler.removeCallbacksAndMessages(null)
        RendererInitialization.cleanup(
            { if (listenerAdded) WebViewCompat.removeWebMessageListener(view, "rendererEvents") },
            { view.stopLoading() },
            { (view.parent as? ViewGroup)?.removeView(view) },
            { view.clearHistory() },
            { view.clearCache(true) },
            { view.destroy() },
        )
    }
    private fun denied() = WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", mapOf("Cache-Control" to "no-store"), ByteArrayInputStream(ByteArray(0)))
}
