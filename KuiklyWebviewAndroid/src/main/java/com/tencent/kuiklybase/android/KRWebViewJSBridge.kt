package com.tencent.kuiklybase.android

import android.os.Handler
import android.os.Looper
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.tencent.kuiklybase.JSBridgeProtocol
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/**
 * Android 端 JSBridge 实现
 * 通过 @JavascriptInterface 注解暴露 postMessage 方法供 JS 调用
 * 管理 Native 方法注册和 callId 到 callback 的映射
 */
class KRWebViewJSBridge(
    private val webView: WebView,
    private val onMessage: (String) -> Unit
) {
    companion object {
        /** 预缓存拼接好的完整 Bridge 脚本，避免每次页面加载时拼接字符串 */
        private val CACHED_FULL_BRIDGE_SCRIPT: String by lazy {
            JSBridgeProtocol.BRIDGE_JS_CODE + JSBridgeProtocol.ANDROID_POST_MESSAGE_JS
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    /** 注册的 Native 方法处理器（线程安全） */
    private val nativeHandlers = ConcurrentHashMap<String, (JSONObject, BridgeCallback) -> Unit>()

    /**
     * JS 端调用此方法发送消息到 Native
     * 通过 @JavascriptInterface 暴露给 JS
     */
    @JavascriptInterface
    fun postMessage(message: String) {
        mainHandler.post {
            handleMessage(message)
        }
    }

    /**
     * 注册 Native 方法供 JS 调用
     * @param name 方法名
     * @param handler 处理函数，接收参数和回调
     */
    fun registerNativeHandler(name: String, handler: (JSONObject, BridgeCallback) -> Unit) {
        nativeHandlers[name] = handler
    }

    /**
     * 注入 JSBridge 脚本到 WebView
     * 应在页面开始加载时调用
     */
    fun injectBridgeScript() {
        // 使用预缓存的完整脚本，避免每次页面加载时拼接字符串
        webView.evaluateJavascript(CACHED_FULL_BRIDGE_SCRIPT, null)
    }

    /**
     * 通过 JSBridge 发送事件到 JS 端
     */
    fun sendEventToJS(method: String, params: String) {
        val script = JSBridgeProtocol.buildEventScript(method, params)
        mainHandler.post {
            webView.evaluateJavascript(script, null)
        }
    }

    private fun handleMessage(message: String) {
        try {
            val json = JSONObject(message)
            val type = json.optString(JSBridgeProtocol.KEY_TYPE)

            when (type) {
                JSBridgeProtocol.TYPE_CALL -> handleNativeCall(json)
                else -> {
                    // 未知类型的消息，通过 onMessage 回调传递给上层
                    onMessage(message)
                }
            }
        } catch (e: Exception) {
            onMessage(message)
        }
    }

    private fun handleNativeCall(json: JSONObject) {
        val callId = json.optString(JSBridgeProtocol.KEY_CALL_ID)
        val method = json.optString(JSBridgeProtocol.KEY_METHOD)
        val params = json.optJSONObject(JSBridgeProtocol.KEY_PARAMS) ?: JSONObject()

        val handler = nativeHandlers[method]
        if (handler != null) {
            val callback = object : BridgeCallback {
                override fun resolve(result: String?) {
                    val script = JSBridgeProtocol.buildResponseScript(callId, result, null)
                    mainHandler.post {
                        webView.evaluateJavascript(script, null)
                    }
                }

                override fun reject(error: String) {
                    val script = JSBridgeProtocol.buildResponseScript(callId, null, error)
                    mainHandler.post {
                        webView.evaluateJavascript(script, null)
                    }
                }
            }
            try {
                handler(params, callback)
            } catch (e: Exception) {
                callback.reject(e.message ?: "Unknown error")
            }
        } else {
            // 未注册的方法，返回错误
            val script = JSBridgeProtocol.buildResponseScript(callId, null, "Method '$method' not registered")
            webView.evaluateJavascript(script, null)
        }
    }
}

/**
 * Bridge 回调接口，用于 Native 方法处理完成后回调 JS Promise
 */
interface BridgeCallback {
    /** 成功回调，resolve Promise */
    fun resolve(result: String? = null)
    /** 失败回调，reject Promise */
    fun reject(error: String)
}
