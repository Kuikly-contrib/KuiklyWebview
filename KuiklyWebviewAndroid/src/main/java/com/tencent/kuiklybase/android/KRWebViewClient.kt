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

/**
 * 自定义 WebViewClient
 * 拦截页面导航事件，通过 KuiklyRenderCallback 通知 Kotlin 共享层
 */
class KRWebViewClient : WebViewClient() {

    companion object {
        /** 危险 scheme 黑名单（预分配，避免每次 URL 拦截时创建新 Set） */
        private val BLOCKED_SCHEMES = arrayOf("javascript", "data", "blob", "vbscript")
    }

    /** 页面开始加载回调 */
    var onPageStartedCallback: KuiklyRenderCallback? = null

    /** 页面加载完成回调 */
    var onPageFinishedCallback: KuiklyRenderCallback? = null

    /** 页面加载出错回调 */
    var onErrorCallback: KuiklyRenderCallback? = null

    /** JSBridge 引用，用于在页面加载完成后注入 Bridge 脚本 */
    var jsBridge: KRWebViewJSBridge? = null

    /**
     * 拦截 URL 加载，处理非标准 scheme（如 baiduboxapp://, weixin:// 等）
     * 避免 net::ERR_UNKNOWN_URL_SCHEME 错误直接显示在 WebView 中
     */
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
        val url = request?.url?.toString() ?: return false
        val scheme = request.url?.scheme ?: return false

        // http/https 协议由 WebView 正常加载
        if (scheme.equals("http", ignoreCase = true) || scheme.equals("https", ignoreCase = true)) {
            return false
        }

        // 安全防护：禁止 javascript: 和 data: 等危险 scheme
        if (BLOCKED_SCHEMES.any { it.equals(scheme, ignoreCase = true) }) {
            return true
        }

        // 非标准 scheme，先检查是否有应用能处理，再通过系统 Intent 打开
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
