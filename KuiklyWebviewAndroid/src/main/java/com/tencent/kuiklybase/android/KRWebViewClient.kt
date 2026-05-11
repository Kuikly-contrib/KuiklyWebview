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
 * 1. javascript / data / blob / vbscript：始终拦截（安全防护）
 * 2. http / https / about / file：标准 scheme 默认放行；如果命中 [interceptHosts] 则
 *    cancel 加载并上抛 `onShouldOverrideUrlLoading` 事件；如果开启 [reportAllNavigation]
 *    则放行的同时也上抛事件供业务感知
 * 3. 其他自定义 scheme（如 weixin://、myapp://）：
 *    - 命中 [interceptSchemes]：cancel + 上抛事件，由业务方决定如何处理
 *    - 未命中：保持现有行为，尝试通过系统 Intent 打开对应应用
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

    /** 是否存在任何 host 拦截规则（判空优化，避免每次导航都调用 isEmpty 两次） */
    private val hasAnyHostRule: Boolean
        get() = interceptHostsExact.isNotEmpty() || interceptHostsSuffix.isNotEmpty()

    /**
     * 是否对所有 mainFrame 导航都触发事件（仅感知，不拦截 http/https）。
     * 默认 false：只在命中规则或非标准 scheme 时触发。
     */
    var reportAllNavigation: Boolean = false

    /**
     * 拦截 URL 加载，处理非标准 scheme（如 baiduboxapp://, weixin:// 等）
     * 避免 net::ERR_UNKNOWN_URL_SCHEME 错误直接显示在 WebView 中
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
            if (isMainFrame && hasAnyHostRule) {
                val host = request.url?.host?.lowercase(Locale.ROOT)
                if (host != null && matchHost(host)) {
                    notifyShouldOverride(url, isMainFrame, "navigation")
                    return true
                }
            }
            // 仅感知不拦截
            if (reportAllNavigation && isMainFrame) {
                notifyShouldOverride(url, isMainFrame, "navigation")
            }
            return false
        }

        // 非标准 scheme（且未命中 interceptSchemes）：保持原有行为，尝试通过系统 Intent 打开
        // 同时上抛事件让业务感知（业务可在事件回调里做埋点）
        notifyShouldOverride(url, isMainFrame, "navigation")
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            if (intent.resolveActivity(view?.context?.packageManager ?: return true) != null) {
                view.context.startActivity(intent)
            }
        } catch (e: Exception) {
            // 没有应用能处理该 scheme，静默忽略
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
