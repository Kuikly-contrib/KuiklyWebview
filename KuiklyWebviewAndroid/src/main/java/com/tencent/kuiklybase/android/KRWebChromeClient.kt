package com.tencent.kuiklybase.android

import android.webkit.WebChromeClient
import android.webkit.WebView
import com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback
import org.json.JSONObject

/**
 * 自定义 WebChromeClient
 * 处理页面标题变化、加载进度等回调
 */
class KRWebChromeClient : WebChromeClient() {

    /** 收到页面标题回调 */
    var onReceiveTitleCallback: KuiklyRenderCallback? = null

    /** 加载进度变化回调 */
    var onProgressChangedCallback: KuiklyRenderCallback? = null

    /** 复用的 JSONObject，避免高频回调中反复创建（onProgressChanged 每秒可能触发数十次） */
    private val reusableProgressJson = JSONObject()
    private val reusableTitleJson = JSONObject()

    override fun onReceivedTitle(view: WebView?, title: String?) {
        super.onReceivedTitle(view, title)
        onReceiveTitleCallback?.let { callback ->
            reusableTitleJson.put("title", title ?: "")
            callback.invoke(reusableTitleJson)
        }
    }

    override fun onProgressChanged(view: WebView?, newProgress: Int) {
        super.onProgressChanged(view, newProgress)
        onProgressChangedCallback?.let { callback ->
            reusableProgressJson.put("progress", newProgress)
            callback.invoke(reusableProgressJson)
        }
    }
}
