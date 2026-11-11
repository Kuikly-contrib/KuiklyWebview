package com.tencent.kuiklybase

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.tencent.kuikly.compose.ComposeContainer
import com.tencent.kuikly.compose.extension.MakeKuiklyComposeNode
import com.tencent.kuikly.compose.foundation.background
import com.tencent.kuikly.compose.foundation.clickable
import com.tencent.kuikly.compose.foundation.layout.Arrangement
import com.tencent.kuikly.compose.foundation.layout.Box
import com.tencent.kuikly.compose.foundation.layout.Column
import com.tencent.kuikly.compose.foundation.layout.Row
import com.tencent.kuikly.compose.foundation.layout.Spacer
import com.tencent.kuikly.compose.foundation.layout.fillMaxSize
import com.tencent.kuikly.compose.foundation.layout.fillMaxWidth
import com.tencent.kuikly.compose.foundation.layout.height
import com.tencent.kuikly.compose.foundation.layout.padding
import com.tencent.kuikly.compose.foundation.shape.RoundedCornerShape
import com.tencent.kuikly.compose.foundation.layout.width
import com.tencent.kuikly.compose.material3.Button
import com.tencent.kuikly.compose.material3.Text
import com.tencent.kuikly.compose.setContent
import com.tencent.kuikly.compose.ui.Alignment
import com.tencent.kuikly.compose.ui.Modifier
import com.tencent.kuikly.compose.ui.unit.Dp
import com.tencent.kuikly.compose.ui.graphics.Color
import com.tencent.kuikly.compose.ui.unit.dp
import com.tencent.kuikly.compose.ui.unit.sp
import com.tencent.kuikly.core.annotations.Page

/**
 * Reproduces a click on a Compose toolbar that overlaps a native WebView.
 *
 * The HTML below the toolbar reports every link click through the WebView JSBridge.
 * A single tap is a passthrough when both counters increase from the same tap.
 */
@Page("router", supportInLocal = true)
internal class WebViewTouchPassthroughDemoPage : ComposeContainer() {

    override fun willInit() {
        super.willInit()
        setContent {
            TouchPassthroughDemo()
        }
    }
}

private enum class DemoMode {
    FIXED,
    REGIONS,
    BASELINE,
    PADDING,
}

@Composable
private fun TouchPassthroughDemo() {
    var mode by remember { mutableStateOf(DemoMode.FIXED) }
    var toolbarClicks by remember { mutableIntStateOf(0) }
    var webClicks by remember { mutableIntStateOf(0) }
    var lastWebClick by remember { mutableStateOf("none") }
    var pageTitle by remember { mutableStateOf("loading") }
    var webUrl by remember { mutableStateOf(BAIDU_URL) }
    var logs by remember { mutableStateOf(listOf<String>()) }

    Box(modifier = Modifier.fillMaxSize().background(Color(0xFFF4F5F7))) {
        Column(modifier = Modifier.fillMaxSize()) {
            DemoHeader()
            ModeSelector(mode) { mode = it }
            CounterPanel(
                toolbarClicks = toolbarClicks,
                webClicks = webClicks,
                lastWebClick = lastWebClick,
                pageTitle = pageTitle,
                webUrl = webUrl,
                logs = logs,
                onReset = {
                    toolbarClicks = 0
                    webClicks = 0
                    lastWebClick = "none"
                    logs = emptyList()
                },
            )

            Box(modifier = Modifier.fillMaxSize()) {
                KWebView(
                    modifier = Modifier.fillMaxSize(),
                    bottomInset = if (mode == DemoMode.PADDING) 96.dp else 0.dp,
                    shields = when (mode) {
                        DemoMode.FIXED -> listOf(
                            TouchShieldRect(
                                height = 96f,
                                align = TouchShieldAlign.BOTTOM_STRETCH,
                            ),
                        )
                        DemoMode.REGIONS -> listOf(
                            TouchShieldRect(
                                height = 96f,
                                align = TouchShieldAlign.BOTTOM_STRETCH,
                            ),
                            TouchShieldRect(
                                height = 48f,
                                align = TouchShieldAlign.TOP_STRETCH,
                            ),
                            TouchShieldRect(
                                width = 72f,
                                align = TouchShieldAlign.LEFT_STRETCH,
                            ),
                        )
                        else -> emptyList()
                    },
                    onWebClick = { message ->
                        webClicks++
                        lastWebClick = message
                        logs = (listOf("web: $message") + logs).take(6)
                    },
                    onPageFinished = { url, title ->
                        webUrl = url
                        pageTitle = title
                        logs = (listOf("page: $title") + logs).take(6)
                    },
                    onPageStarted = { url ->
                        webUrl = url
                        logs = (listOf("start: $url") + logs).take(6)
                    },
                )

                Toolbar(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    onToolbarClick = {
                        toolbarClicks++
                        logs = (listOf("toolbar: ${toolbarClicks}") + logs).take(6)
                    },
                )
            }
        }
    }
}

@Composable
private fun DemoHeader() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF18212F))
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            text = "WebView / Compose touch passthrough",
            fontSize = 18.sp,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "www.baidu.com / tap toolbar; compare counters and page state",
            fontSize = 12.sp,
            color = Color(0xFFB9C4D4),
        )
    }
}

@Composable
private fun ModeSelector(mode: DemoMode, onModeChange: (DemoMode) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ModeButton("Fixed", mode == DemoMode.FIXED) { onModeChange(DemoMode.FIXED) }
        ModeButton("Regions", mode == DemoMode.REGIONS) { onModeChange(DemoMode.REGIONS) }
        ModeButton("Baseline", mode == DemoMode.BASELINE) { onModeChange(DemoMode.BASELINE) }
        ModeButton("Padding", mode == DemoMode.PADDING) { onModeChange(DemoMode.PADDING) }
    }
}

@Composable
private fun ModeButton(text: String, selected: Boolean, onClick: () -> Unit) {
    Button(onClick = onClick) {
        Text(
            text = if (selected) "$text*" else text,
            fontSize = 11.sp,
            color = if (selected) Color(0xFF006BFF) else Color(0xFF334155),
        )
    }
}

@Composable
private fun CounterPanel(
    toolbarClicks: Int,
    webClicks: Int,
    lastWebClick: String,
    pageTitle: String,
    webUrl: String,
    logs: List<String>,
    onReset: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE8EEF7))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Toolbar: $toolbarClicks    Web link / tap: $webClicks",
                fontSize = 14.sp,
                color = Color(0xFF172033),
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = onReset) {
                Text("Reset", fontSize = 10.sp)
            }
        }
        Text(
            text = "Last web message: $lastWebClick",
            fontSize = 11.sp,
            color = Color(0xFF52637A),
        )
        Text(
            text = "Title: $pageTitle",
            fontSize = 10.sp,
            color = Color(0xFF52637A),
        )
        Text(
            text = "URL: $webUrl",
            fontSize = 10.sp,
            color = Color(0xFF52637A),
        )
        logs.forEach { line ->
            Text(text = line, fontSize = 10.sp, color = Color(0xFF52637A))
        }
    }
}

@Composable
private fun KWebView(
    modifier: Modifier,
    bottomInset: Dp,
    shields: List<TouchShieldRect>,
    onWebClick: (String) -> Unit,
    onPageFinished: (String, String) -> Unit,
    onPageStarted: (String) -> Unit,
) {
    MakeKuiklyComposeNode<KuiklyWebView>(
        factory = { KuiklyWebView() },
        modifier = modifier.padding(bottom = bottomInset),
        viewInit = {
            attr {
                src(BAIDU_URL)
                javaScriptEnabled(true)
                domStorageEnabled(true)
                touchShields(shields)
            }
            event {
                onMessage(onWebClick)
                onPageStarted { url -> onPageStarted(url) }
                onPageFinished { url -> onPageFinished(url, "loaded") }
                onReceiveTitle { title -> onPageFinished(BAIDU_URL, title) }
            }
        },
        viewUpdate = { view ->
            view.getViewAttr().touchShields(shields)
        },
    )
}

@Composable
private fun Toolbar(
    modifier: Modifier,
    onToolbarClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(96.dp)
            .background(Color(0xEE111827), shape = RoundedCornerShape(8.dp))
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ToolbarButton("Avatar", Color(0xFF111827), onToolbarClick)
        ToolbarButton("Voice / text", Color(0xFF334155), onToolbarClick)
        ToolbarButton("Ask AI", Color(0xFF2563EB), onToolbarClick)
    }
}

@Composable
private fun ToolbarButton(text: String, color: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(56.dp)
            .background(color, shape = RoundedCornerShape(28.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, fontSize = 12.sp, color = Color.White)
    }
}

private const val BAIDU_URL = "https://www.baidu.com/"
