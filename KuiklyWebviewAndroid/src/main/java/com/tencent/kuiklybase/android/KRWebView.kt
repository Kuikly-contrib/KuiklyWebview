package com.tencent.kuiklybase.android

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import com.tencent.kuiklybase.JSBridgeProtocol
import com.tencent.kuiklybase.KuiklyWebViewEvent
import com.tencent.kuikly.core.render.android.export.IKuiklyRenderViewExport
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject

/**
 * Android 端 WebView 渲染视图
 * 实现 IKuiklyRenderViewExport 接口，将 android.webkit.WebView 暴露给 Kuikly 框架
 *
 * 通过 Kuikly 的 setProp/call 机制：
 * - setProp: 接收属性设置（src, htmlContent, javaScriptEnabled...）和事件回调绑定
 * - call: 接收命令式方法调用（loadUrl, evaluateJavaScript, goBack...）
 */
@SuppressLint("SetJavaScriptEnabled")
open class KRWebView(context: Context) : FrameLayout(context), IKuiklyRenderViewExport {

    companion object {
        const val VIEW_NAME = "KRWebView"
    }

    /** 内部 WebView 实例 */
    private val webView: WebView = WebView(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
    }

    /** 自定义 WebViewClient */
    private val webViewClient = KRWebViewClient()

    /** 自定义 WebChromeClient */
    private val webChromeClient = KRWebChromeClient()

    /** JSBridge 实例 */
    private val jsBridge: KRWebViewJSBridge

    /** 消息事件回调 */
    private var onMessageCallback: KuiklyRenderCallback? = null

    init {
        addView(webView)

        // 初始化 JSBridge
        jsBridge = KRWebViewJSBridge(webView) { message ->
            // JS 发来的非 Bridge 协议消息，通过 onMessage 事件回调
            onMessageCallback?.invoke(
                JSONObject().apply {
                    put("message", message)
                }
            )
        }

        // 注册内部 SPA 路由 hook handler：JS 端 hook history.pushState 等通过此 method 上抛，
        // 转化为 onShouldOverrideUrlLoading 事件，避免穿透到 onMessage / 业务 handler
        jsBridge.registerNativeHandler(JSBridgeProtocol.INTERNAL_METHOD_NAV_INTERCEPT) { params, callback ->
            val url = params.optString("url", "")
            val source = params.optString("source", "navigation")
            val isMainFrame = params.optBoolean("isMainFrame", true)
            // 记录本次 SPA 路由 URL，供 shouldOverrideUrlLoading 做重复去重
            // （Android WebView 对 hashchange 会同时触发原生 decidePolicy，双发问题见 KRWebViewClient 注释）
            if (url.isNotEmpty()) {
                webViewClient.markSpaRouted(url)
            }
            webViewClient.notifyShouldOverride(url, isMainFrame, source)
            // 立即 resolve，避免 JS 侧的 Promise 走超时
            callback.resolve(null)
        }

        // 设置 WebView 默认配置
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = false // 禁止通过 file:// 访问文件系统，防止数据泄漏
            allowFileAccessFromFileURLs = false
            allowUniversalAccessFromFileURLs = false
            useWideViewPort = true
            loadWithOverviewMode = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE // 仅兼容模式，避免 HTTPS 页面加载 HTTP 资源的中间人攻击
            mediaPlaybackRequiresUserGesture = false
            // 性能优化：启用缓存，非首次加载页面可直接使用缓存资源
            cacheMode = WebSettings.LOAD_DEFAULT
            databaseEnabled = true
        }

        // 启用硬件加速渲染层，提升滚动和动画流畅度
        webView.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, null)

        // 绑定客户端
        webViewClient.jsBridge = jsBridge
        webView.webViewClient = webViewClient
        webView.webChromeClient = webChromeClient

        // 添加 JavascriptInterface
        webView.addJavascriptInterface(jsBridge, JSBridgeProtocol.NATIVE_HANDLER_NAME)
    }

    // ---- IKuiklyRenderViewExport 实现 ----

    override fun setProp(propKey: String, propValue: Any): Boolean {
        return when (propKey) {
            // --- 属性 ---
            "src" -> {
                webView.loadUrl(propValue as String)
                true
            }
            "htmlContent" -> {
                webView.loadDataWithBaseURL(
                    null,
                    propValue as String,
                    "text/html",
                    "UTF-8",
                    null
                )
                true
            }
            "javaScriptEnabled" -> {
                webView.settings.javaScriptEnabled = (propValue as String) != "false"
                true
            }
            "userAgent" -> {
                webView.settings.userAgentString = propValue as String
                true
            }
            "domStorageEnabled" -> {
                webView.settings.domStorageEnabled = (propValue as String) != "false"
                true
            }
            "allowsInlineMediaPlayback" -> {
                webView.settings.mediaPlaybackRequiresUserGesture = (propValue as String) == "false"
                true
            }
            "urlInterceptSchemes" -> {
                webViewClient.interceptSchemes = parseCsvSet(propValue as? String)
                true
            }
            "urlInterceptHosts" -> {
                // 拆分为精确匹配集合与通配符后缀集合；规则会覆盖旧规则，天然支持运行时动态更新
                val (exact, suffix) = parseHostRules(propValue as? String)
                webViewClient.interceptHostsExact = exact
                webViewClient.interceptHostsSuffix = suffix
                true
            }
            "reportAllNavigation" -> {
                webViewClient.reportAllNavigation = (propValue as? String) == "true"
                true
            }
            "autoOpenExternalScheme" -> {
                webViewClient.autoOpenExternalScheme = (propValue as? String) == "true"
                true
            }

            // --- 事件回调 ---
            KuiklyWebViewEvent.EVENT_PAGE_STARTED -> {
                webViewClient.onPageStartedCallback = propValue as KuiklyRenderCallback
                true
            }
            KuiklyWebViewEvent.EVENT_PAGE_FINISHED -> {
                webViewClient.onPageFinishedCallback = propValue as KuiklyRenderCallback
                true
            }
            KuiklyWebViewEvent.EVENT_ERROR -> {
                webViewClient.onErrorCallback = propValue as KuiklyRenderCallback
                true
            }
            KuiklyWebViewEvent.EVENT_RECEIVE_TITLE -> {
                webChromeClient.onReceiveTitleCallback = propValue as KuiklyRenderCallback
                true
            }
            KuiklyWebViewEvent.EVENT_PROGRESS_CHANGED -> {
                webChromeClient.onProgressChangedCallback = propValue as KuiklyRenderCallback
                true
            }
            KuiklyWebViewEvent.EVENT_MESSAGE -> {
                onMessageCallback = propValue as KuiklyRenderCallback
                true
            }
            KuiklyWebViewEvent.EVENT_SHOULD_OVERRIDE_URL_LOADING -> {
                webViewClient.onShouldOverrideUrlLoadingCallback = propValue as KuiklyRenderCallback
                true
            }

            else -> super.setProp(propKey, propValue)
        }
    }

    override fun call(method: String, params: String?, callback: KuiklyRenderCallback?): Any? {
        return when (method) {
            "loadUrl" -> {
                params?.let { webView.loadUrl(it) }
                null
            }
            "loadHtml" -> {
                params?.let {
                    try {
                        val json = JSONObject(it)
                        val html = json.optString("html", "")
                        val baseUrl = json.optString("baseUrl", null)
                        webView.loadDataWithBaseURL(baseUrl, html, "text/html", "UTF-8", null)
                    } catch (e: Exception) {
                        webView.loadDataWithBaseURL(null, it, "text/html", "UTF-8", null)
                    }
                }
                null
            }
            "evaluateJavaScript" -> {
                params?.let { script ->
                    webView.evaluateJavascript(script) { result ->
                        callback?.invoke(result)
                    }
                }
                null
            }
            "goBack" -> {
                if (webView.canGoBack()) webView.goBack()
                null
            }
            "goForward" -> {
                if (webView.canGoForward()) webView.goForward()
                null
            }
            "reload" -> {
                webView.reload()
                null
            }
            "stopLoading" -> {
                webView.stopLoading()
                null
            }
            "canGoBack" -> {
                callback?.invoke(if (webView.canGoBack()) "true" else "false")
                null
            }
            "canGoForward" -> {
                callback?.invoke(if (webView.canGoForward()) "true" else "false")
                null
            }
            else -> super.call(method, params, callback)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 清理回调引用，防止内存泄漏
        onMessageCallback = null
        webViewClient.onPageStartedCallback = null
        webViewClient.onPageFinishedCallback = null
        webViewClient.onErrorCallback = null
        webViewClient.onShouldOverrideUrlLoadingCallback = null
        webViewClient.jsBridge = null
        webChromeClient.onReceiveTitleCallback = null
        webChromeClient.onProgressChangedCallback = null
        // 清理 WebView
        webView.removeJavascriptInterface(JSBridgeProtocol.NATIVE_HANDLER_NAME)
        webView.webViewClient = WebViewClient()
        webView.webChromeClient = null
        webView.stopLoading()
        removeView(webView)
        webView.destroy()
    }

    /**
     * 获取 JSBridge 实例，供外部注册 Native 方法
     */
    fun getJSBridge(): KRWebViewJSBridge = jsBridge

    /**
     * 解析逗号分隔的字符串为小写、不重复的 Set；空串返回空集合。
     */
    private fun parseCsvSet(csv: String?): Set<String> {
        if (csv.isNullOrBlank()) return emptySet()
        return csv.split(',')
            .asSequence()
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .toHashSet()
    }

    /**
     * 将 host 规则 CSV 拆分为 (exact, suffix) 两个集合：
     * - `example.com`      -> 进入 exact
     * - `*.example.com`    -> 进入 suffix，存为 `example.com`（去掉 `*.` 前缀）
     * - 空串 / 非法项会被忽略
     */
    private fun parseHostRules(csv: String?): Pair<Set<String>, Set<String>> {
        if (csv.isNullOrBlank()) return emptySet<String>() to emptySet()
        val exact = HashSet<String>()
        val suffix = HashSet<String>()
        for (raw in csv.split(',')) {
            val trimmed = raw.trim().lowercase()
            if (trimmed.isEmpty()) continue
            if (trimmed.startsWith("*.") && trimmed.length > 2) {
                suffix.add(trimmed.substring(2))
            } else {
                exact.add(trimmed)
            }
        }
        return exact to suffix
    }
}
