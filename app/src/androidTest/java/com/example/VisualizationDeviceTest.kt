package com.example

import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import com.example.visualization.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/** Real packaged WebView tests. Synthetic local data; no publication or backend mutation. */
@RunWith(AndroidJUnit4::class)
class VisualizationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private lateinit var chartSpec: MutableState<VisualizationSpec>

    private fun snapshot(): VisualizationSnapshot {
        val base = PreseasonGameData.createGame()
        val away = base.startingAway.first().copy(name = "<script>literal & safe</script>",
            stats = base.startingAway.first().stats.copy(points = 7, fgMade = 2, fgAttempted = 5))
        val home = base.startingHome.first().copy(name = "Home zero attempts",
            stats = base.startingHome.first().stats.copy(points = 0, fgMade = 0, fgAttempted = 0))
        return VisualizationContract.snapshotFromGame(base.copy(startingAway = listOf(away),
            startingHome = listOf(home), substitutesAway = emptyList(), substitutesHome = emptyList()),
            FeedProvenance(42, "2026-10-11T08:00:00Z", 1000), 2000)
    }

    private fun install(template: String = "fieldGoals", closed: () -> Unit = {}) {
        val data = snapshot()
        chartSpec = mutableStateOf(VisualizationSpec(template, data.gameId))
        compose.setContent {
            VisualizationScreen(data, chartSpec.value, { chartSpec.value = it }, closed, {}, {})
        }
    }

    private fun web(view: View): WebView? {
        if (view is WebView) return view
        if (view is ViewGroup) for (i in 0 until view.childCount) web(view.getChildAt(i))?.let { return it }
        return null
    }

    private fun currentWeb(): WebView? {
        val result = AtomicReference<WebView?>()
        instrumentation.runOnMainSync { result.set(web(compose.activity.window.decorView)) }
        return result.get()
    }

    private fun js(view: WebView, script: String): String {
        val result = AtomicReference<String>()
        val ready = CountDownLatch(1)
        instrumentation.runOnMainSync { view.evaluateJavascript(script) { result.set(it); ready.countDown() } }
        assertTrue("WebView did not respond to test probe", ready.await(3, TimeUnit.SECONDS))
        return result.get()
    }

    private fun readyWeb(): WebView {
        compose.onNodeWithText("Visual chart. The source table presents the same filtered values for screen readers.")
            .performScrollTo()
        // Cold provider initialization may exceed the native watchdog; exercise the supported Retry.
        repeat(3) {
            val until = System.currentTimeMillis() + 4000
            while (System.currentTimeMillis() < until) {
                val found = currentWeb()
                if (found != null && runCatching { js(found, "document.querySelectorAll('.player').length") }.getOrNull() == "2") return found
                Thread.sleep(100)
            }
            compose.onNodeWithText("Retry chart").performScrollTo().performClick()
        }
        error("Packaged chart never rendered")
    }

    @Test fun fieldGoalsRenderLiteralTextAndNativeDerivedValuesWithCspBlocking() {
        install()
        val view = readyWeb()
        val text = js(view, "document.getElementById('chart').textContent")
        assertTrue(text.contains("literal"))
        assertTrue(text.contains("Made 2"))
        assertTrue(text.contains("Attempts 5"))
        assertTrue(text.contains("Missed 3"))
        assertTrue(text.contains("40.0%"))
        assertTrue(text.contains("N/A"))
        assertEquals("0", js(view, "document.querySelectorAll('#chart script').length"))
        js(view, "window.cspBlocked=[];document.addEventListener('securitypolicyviolation',e=>window.cspBlocked.push(e.violatedDirective));fetch('https://example.com/probe').catch(()=>{});void 0")
        Thread.sleep(200)
        assertTrue(js(view, "JSON.stringify(window.cspBlocked)").contains("connect-src"))
        compose.onNodeWithText("Show accessible source table").performScrollTo().performClick()
        compose.onNodeWithText("Field goals source table").assertExists()
        compose.onNodeWithText("<script>literal & safe</script> · PHI").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("40.0%").assertExists()
        compose.onNodeWithText("Combined FG%: 40.0% (total made ÷ total attempts)").assertExists()
    }

    @Test fun rendererTerminationShowsNativeTableAndCloseStillWorks() {
        var closed = false
        install(closed = { closed = true })
        val view = readyWeb()
        assertTrue(WebViewFeature.isFeatureSupported(WebViewFeature.WEB_VIEW_RENDERER_CLIENT_BASIC_USAGE))
        instrumentation.runOnMainSync { assertTrue(WebViewCompat.getWebViewRenderProcess(view)?.terminate() == true) }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Retry chart").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Field goals source table").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        assertTrue(closed)
    }

    @Test fun unavailableProviderFallsBackWithoutLosingNativeClose() {
        var closed = false
        val data = snapshot()
        compose.setContent {
            VisualizationScreen(data, VisualizationSpec("playerStats", data.gameId), {},
                { closed = true }, {}, {}, createChartView = { error("Injected unavailable provider") })
        }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Retry chart").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Points source table").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        assertTrue(closed)
    }

    @Test fun nonresponsiveRenderTimesOutToNativeTable() {
        var closed = false
        install("playerStats", { closed = true })
        val view = readyWeb()
        // Simulate failure inside the renderer without blocking the native UI thread.
        js(view, "window.render=()=>{while(true){}};void 0")
        // Keep the injected view visible: scrolling to a filter could let LazyColumn dispose it.
        assertSame(view, currentWeb())
        instrumentation.runOnMainSync { chartSpec.value = chartSpec.value.copy(metric = "rebounds") }
        compose.waitUntil(5000) { compose.onAllNodesWithText("Retry chart").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Chart exceeded the two-second wait. Showing the source table.").assertExists()
        compose.onNodeWithText("Rebounds source table").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Close").performScrollTo().performClick()
        assertTrue(closed)
    }
}
