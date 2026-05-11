package com.tencent.kuiklybase

import com.tencent.kuikly.core.base.event.Event
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

/**
 * KuiklyWebView 组件的事件定义
 * 原生侧通过 KuiklyRenderCallback 触发这些事件
 */
class KuiklyWebViewEvent : Event() {

    /**
     * 页面开始加载时触发
     * @param handler 回调，参数为加载的 URL
     */
    fun onPageStarted(handler: (url: String) -> Unit) {
        register(EVENT_PAGE_STARTED) {
            val params = it as? JSONObject ?: JSONObject()
            handler(params.optString("url", ""))
        }
    }

    /**
     * 页面加载完成时触发
     * @param handler 回调，参数为加载完成的 URL
     */
    fun onPageFinished(handler: (url: String) -> Unit) {
        register(EVENT_PAGE_FINISHED) {
            val params = it as? JSONObject ?: JSONObject()
            handler(params.optString("url", ""))
        }
    }

    /**
     * 页面加载出错时触发
     * @param handler 回调，参数为错误码和错误描述
     */
    fun onError(handler: (errorCode: Int, description: String) -> Unit) {
        register(EVENT_ERROR) {
            val params = it as? JSONObject ?: JSONObject()
            handler(
                params.optInt("errorCode", -1),
                params.optString("description", "Unknown error")
            )
        }
    }

    /**
     * 收到页面标题时触发
     * @param handler 回调，参数为页面标题
     */
    fun onReceiveTitle(handler: (title: String) -> Unit) {
        register(EVENT_RECEIVE_TITLE) {
            val params = it as? JSONObject ?: JSONObject()
            handler(params.optString("title", ""))
        }
    }

    /**
     * URL 加载拦截感知事件（异步通知，非同步拦截）
     *
     * 注意：Kuikly 的事件回调是异步的，因此此事件**不能**像原生
     * `WebViewClient.shouldOverrideUrlLoading` 那样通过返回值阻止加载。
     * 它的语义是「即将发生 / 已经发生导航的感知」，由原生侧基于 attr 中下发的
     * 同步规则（[KuiklyWebViewAttr.urlInterceptSchemes] 等）决定是否真正拦截。
     *
     * 触发时机：
     * - 原生 mainFrame 导航（http/https/自定义 scheme），此时 `source = "navigation"`
     * - SPA 内部路由变化：`source = "pushState" / "replaceState" / "popstate" / "hashchange"`
     *
     * @param handler 回调参数：
     *   - url: 即将加载或已加载的 URL
     *   - isMainFrame: 是否为主框架（iframe 内的导航为 false）
     *   - source: 触发来源，便于业务区分
     */
    fun onShouldOverrideUrlLoading(handler: (url: String, isMainFrame: Boolean, source: String) -> Unit) {
        register(EVENT_SHOULD_OVERRIDE_URL_LOADING) {
            val params = it as? JSONObject ?: JSONObject()
            handler(
                params.optString("url", ""),
                params.optBoolean("isMainFrame", true),
                params.optString("source", "navigation")
            )
        }
    }

    /**
     * 页面加载进度变化时触发
     * @param handler 回调，参数为进度值（0-100）
     */
    fun onProgressChanged(handler: (progress: Int) -> Unit) {
        register(EVENT_PROGRESS_CHANGED) {
            val params = it as? JSONObject ?: JSONObject()
            handler(params.optInt("progress", 0))
        }
    }

    /**
     * 从 JS 收到消息时触发（JSBridge 通道）
     * @param handler 回调，参数为 JS 发送的消息（JSON 字符串）
     */
    fun onMessage(handler: (message: String) -> Unit) {
        register(EVENT_MESSAGE) {
            val params = it as? JSONObject ?: JSONObject()
            handler(params.optString("message", ""))
        }
    }

    companion object {
        const val EVENT_PAGE_STARTED = "onPageStarted"
        const val EVENT_PAGE_FINISHED = "onPageFinished"
        const val EVENT_ERROR = "onError"
        const val EVENT_RECEIVE_TITLE = "onReceiveTitle"
        const val EVENT_PROGRESS_CHANGED = "onProgressChanged"
        const val EVENT_MESSAGE = "onMessage"
        const val EVENT_SHOULD_OVERRIDE_URL_LOADING = "onShouldOverrideUrlLoading"
    }
}
