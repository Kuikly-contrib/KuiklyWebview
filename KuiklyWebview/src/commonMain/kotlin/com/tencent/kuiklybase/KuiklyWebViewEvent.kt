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
    }
}
