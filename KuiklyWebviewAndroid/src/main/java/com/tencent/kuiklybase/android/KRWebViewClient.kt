package com.tencent.kuiklybase.android

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import com.tencent.kuiklybase.KuiklyWebViewEvent
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject
import java.util.Locale

/**
 * 自定义 WebViewClient
 * 拦截页面导航事件，通过 KuiklyRenderCallback 通知 Kotlin 共享层
 *
 * URL 拦截策略（同步决策，与 OHOS / iOS 行为对齐）：
 * 1. `javascript` / `data` / `blob` / `vbscript`：**始终拦截**（安全防护）
 * 2. 命中 [interceptSchemes]：cancel + 上抛事件
 * 3. 标准 scheme（`http` / `https` / `about` / `file`）：
 *    - mainFrame 命中 [interceptHostsExact] / [interceptHostsSuffix]：cancel + 上抛事件
 *    - 未命中：放行；若开启 [reportAllNavigation] 且 mainFrame，则附带上抛事件（仅感知）
 * 4. 其他自定义 scheme（如 `weixin://`、`myapp://`）：cancel + 上抛事件；
 *    仅当显式开启 [autoOpenExternalScheme] 时，组件才会**额外**尝试用系统 Intent 唤起外部 App
 *    （默认关闭，避免 WebView 被嵌入第三方页面时擅自唤起应用的安全/合规风险）
 */
class KRWebViewClient : WebViewClient() {

    companion object {
        /** 危险 scheme 黑名单（预分配，避免每次 URL 拦截时创建新 Set） */
        private val BLOCKED_SCHEMES = arrayOf("javascript", "data", "blob", "vbscript")

        /** 标准 scheme（默认放行不拦） */
        private val STANDARD_SCHEMES = arrayOf("http", "https", "about", "file")
    }

    /** 页面开始加载回调 */
    var onPageStartedCallback: KuiklyRenderCallback? = null

    /** 页面加载完成回调 */
    var onPageFinishedCallback: KuiklyRenderCallback? = null

    /** 页面加载出错回调 */
    var onErrorCallback: KuiklyRenderCallback? = null

    /** URL 拦截事件回调（onShouldOverrideUrlLoading） */
    var onShouldOverrideUrlLoadingCallback: KuiklyRenderCallback? = null

    /** JSBridge 引用，用于在页面加载完成后注入 Bridge 脚本 */
    var jsBridge: KRWebViewJSBridge? = null

    /** 需要拦截的 scheme 列表（小写、不含 ://） */
    var interceptSchemes: Set<String> = emptySet()

    /**
     * 需要拦截的 host 列表——精确匹配部分（小写、不含端口）。
     * 例如配置 `example.com` 只会匹配 `example.com`，不会匹配 `m.example.com`。
     */
    var interceptHostsExact: Set<String> = emptySet()

    /**
     * 需要拦截的 host 列表——通配符匹配部分（小写、不含端口、不含前缀 `*.`）。
     * 例如配置 `*.example.com` 会匹配 `example.com` 本身以及任意子域（`a.example.com`、`a.b.example.com`）。
     */
    var interceptHostsSuffix: Set<String> = emptySet()

    /**
     * 是否对所有 mainFrame 导航都触发事件（仅感知，不拦截 http/https）。
     * 默认 false：只在命中规则或非标准 scheme 时触发。
     */
    var reportAllNavigation: Boolean = false

    /**
     * 是否允许组件自动用系统 Intent 唤起外部 App 处理未命中规则的非标准 scheme。
     * 默认 false（安全优先）。详见 KuiklyWebViewAttr.autoOpenExternalScheme 的说明。
     */
    var autoOpenExternalScheme: Boolean = false

    /**
     * SPA hash 变更去重：记录上一次上抛的 URL，
     * 如果 JS hook 刚刚通过 `__kuiklyNavIntercept` 上抛过同一个 URL，
     * 紧接着 WebView 原生 [shouldOverrideUrlLoading] 又上抛一次 hash 跳转，则吞掉后一次，
     * 避免业务收到重复事件。iOS 的 WKWebView 不会对 hashchange 走 decidePolicy，无此问题。
     */
    @Volatile
    private var lastSpaRoutedUrl: String? = null

    /**
     * 告知拦截器：刚刚通过 SPA hook 上抛了一个 url，紧接着的原生导航若 url 相同则不再重复上抛。
     * 由 JSBridge 的 `__kuiklyNavIntercept` handler 调用。
     */
    fun markSpaRouted(url: String) {
        lastSpaRoutedUrl = url
    }

    /**
     * 拦截 URL 加载
     *
     * 对 302 重定向的行为：Android WebView 在 302 跳转后会**再次**触发本方法
     * （request.isRedirect == true，API 24+），因此跳转后的新 URL 会重新走一次
     * 下面的规则匹配，命中的 host 仍能被正确拦截。
     */
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val scheme = request.url?.scheme?.lowercase(Locale.ROOT) ?: return false
        val isMainFrame = request.isForMainFrame

        // 安全防护：禁止 javascript: 和 data: 等危险 scheme（始终拦，无论规则）
        if (BLOCKED_SCHEMES.contains(scheme)) {
            return true
        }

        // 命中 scheme 黑名单：cancel 加载并上抛事件，业务自行决定如何处理
        if (interceptSchemes.contains(scheme)) {
            notifyShouldOverride(url, isMainFrame, "navigation")
            return true
        }

        // 标准 scheme：原则上放行，但若 host 命中则 cancel
        if (STANDARD_SCHEMES.contains(scheme)) {
            // mainFrame 上命中 host 规则 → 同步拦截
            if (isMainFrame &&
                (interceptHostsExact.isNotEmpty() || interceptHostsSuffix.isNotEmpty())
            ) {
                val host = request.url?.host?.lowercase(Locale.ROOT)
                if (host != null && matchHost(host)) {
                    notifyShouldOverride(url, isMainFrame, "navigation")
                    return true
                }
            }
            // 未命中规则：放行；若开启 reportAllNavigation 则附带感知事件
            if (reportAllNavigation && isMainFrame) {
                // 如果是 SPA hook 刚上抛过的同一个 URL，说明这是 hashchange/pushState 带出的
                // 重复原生事件，吞掉避免双发
                if (consumeSpaRoutedIfMatches(url)) {
                    return false
                }
                notifyShouldOverride(url, isMainFrame, "navigation")
            }
            return false
        }

        // 非标准 scheme（且未命中 interceptSchemes）：上抛事件 + cancel，由业务决定是否唤起外部 App。
        // 仅当业务显式开启 autoOpenExternalScheme 时，组件才额外尝试用系统 Intent 打开。
        notifyShouldOverride(url, isMainFrame, "navigation")
        if (autoOpenExternalScheme) {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                if (intent.resolveActivity(view?.context?.packageManager ?: return true) != null) {
                    view.context.startActivity(intent)
                }
            } catch (e: Exception) {
                // 没有应用能处理该 scheme，静默忽略
            }
        }
        return true
    }

    /**
     * 通知上层 onShouldOverrideUrlLoading 事件（供 SPA hook handler 也复用）
     */
    fun notifyShouldOverride(url: String, isMainFrame: Boolean, source: String) {
        onShouldOverrideUrlLoadingCallback?.invoke(
            JSONObject().apply {
                put("url", url)
                put("isMainFrame", isMainFrame)
                put("source", source)
            }
        )
    }

    /**
     * 若 [url] 与上次 SPA hook 标记的相同，则消费标记并返回 true（表示应吞掉这次事件）。
     */
    private fun consumeSpaRoutedIfMatches(url: String): Boolean {
        val last = lastSpaRoutedUrl ?: return false
        if (last == url) {
            lastSpaRoutedUrl = null
            return true
        }
        return false
    }

    /**
     * 判断 host 是否命中拦截规则。
     *
     * 匹配优先级：
     * 1. 精确匹配：host == rule
     * 2. 通配符匹配：rule = `*.foo.com` 可匹配 `foo.com` 本身及任意子域（`a.foo.com`、`a.b.foo.com`）
     *
     * @param host 请求 URL 的 host（小写、不含端口）
     */
    private fun matchHost(host: String): Boolean {
        if (interceptHostsExact.contains(host)) return true
        if (interceptHostsSuffix.isEmpty()) return false
        for (suffix in interceptHostsSuffix) {
            // 自身匹配：`*.foo.com` 也能匹配 `foo.com`
            if (host == suffix) return true
            // 子域匹配：以 `.foo.com` 结尾
            if (host.length > suffix.length &&
                host.endsWith(suffix) &&
                host[host.length - suffix.length - 1] == '.'
            ) {
                return true
            }
        }
        return false
    }

    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
        super.onPageStarted(view, url, favicon)
        onPageStartedCallback?.invoke(
            JSONObject().apply {
                put("url", url ?: "")
            }
        )
    }

    override fun onPageFinished(view: WebView?, url: String?) {
        super.onPageFinished(view, url)
        // 页面加载完成后注入 JSBridge 脚本
        jsBridge?.injectBridgeScript()
        onPageFinishedCallback?.invoke(
            JSONObject().apply {
                put("url", url ?: "")
            }
        )
    }

    override fun onReceivedError(
        view: WebView?,
        request: WebResourceRequest?,
        error: WebResourceError?
    ) {
        super.onReceivedError(view, request, error)
        // 仅处理主框架的错误
        if (request?.isForMainFrame == true) {
            onErrorCallback?.invoke(
                JSONObject().apply {
                    put("errorCode", error?.errorCode ?: -1)
                    put("description", error?.description?.toString() ?: "Unknown error")
                }
            )
        }
    }
}
