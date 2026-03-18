package com.tencent.kuiklybase

import com.tencent.kuikly.core.base.DeclarativeBaseView
import com.tencent.kuikly.core.base.ViewContainer

/**
 * KuiklyWebView 跨端 WebView 组件
 *
 * 使用 Kuikly 的 DeclarativeBaseView 模式，将各平台原生 WebView 暴露为统一的跨端组件。
 * 原生端需注册名为 "KRWebView" 的渲染视图实现。
 *
 * 使用示例：
 * ```kotlin
 * WebView {
 *     attr {
 *         src("https://example.com")
 *         javaScriptEnabled(true)
 *         domStorageEnabled(true)
 *     }
 *     event {
 *         onPageFinished { url ->
 *             // 页面加载完成
 *         }
 *         onMessage { message ->
 *             // 收到 JS 消息
 *         }
 *     }
 * }
 * ```
 */
class KuiklyWebView : DeclarativeBaseView<KuiklyWebViewAttr, KuiklyWebViewEvent>() {

    override fun createAttr(): KuiklyWebViewAttr {
        return KuiklyWebViewAttr()
    }

    override fun createEvent(): KuiklyWebViewEvent {
        return KuiklyWebViewEvent()
    }

    override fun viewName(): String {
        return VIEW_NAME
    }

    // ---- 命令式方法 ----

    /**
     * 加载指定 URL
     * @param url 要加载的网页地址
     */
    fun loadUrl(url: String) {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("loadUrl", url)
        }
    }

    /**
     * 加载 HTML 字符串
     * @param html HTML 内容
     * @param baseUrl 基础 URL（用于解析相对路径）
     */
    fun loadHtml(html: String, baseUrl: String = "") {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("loadHtml", "{\"html\":\"${JSBridgeProtocol.escapeJsString(html)}\",\"baseUrl\":\"${JSBridgeProtocol.escapeJsString(baseUrl)}\"}")
        }
    }

    /**
     * 执行 JavaScript 代码
     * @param script JS 代码
     * @param callback 执行结果回调（可选）
     */
    fun evaluateJavaScript(script: String, callback: ((String?) -> Unit)? = null) {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("evaluateJavaScript", script, callback?.let { cb ->
                { result: Any? ->
                    cb(result?.toString())
                }
            })
        }
    }

    /**
     * 后退
     */
    fun goBack() {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("goBack", null)
        }
    }

    /**
     * 前进
     */
    fun goForward() {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("goForward", null)
        }
    }

    /**
     * 重新加载
     */
    fun reload() {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("reload", null)
        }
    }

    /**
     * 查询是否可以后退
     * @param callback 结果回调，"true" 或 "false"
     */
    fun canGoBack(callback: (Boolean) -> Unit) {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("canGoBack", null) { result: Any? ->
                callback(result?.toString() == "true")
            }
        }
    }

    /**
     * 查询是否可以前进
     * @param callback 结果回调，"true" 或 "false"
     */
    fun canGoForward(callback: (Boolean) -> Unit) {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("canGoForward", null) { result: Any? ->
                callback(result?.toString() == "true")
            }
        }
    }

    /**
     * 通过 JSBridge 发送消息到 JS 端
     * @param method 方法名
     * @param params JSON 参数字符串
     */
    fun sendMessageToJS(method: String, params: String) {
        val script = JSBridgeProtocol.buildEventScript(method, params)
        evaluateJavaScript(script)
    }

    companion object {
        /** 组件对应的原生视图名称，各端注册时使用此名称 */
        const val VIEW_NAME = "KRWebView"
    }
}

/**
 * KuiklyWebView DSL 扩展函数
 * 在 ViewContainer 中使用 WebView { ... } 构建 WebView 组件
 */
fun ViewContainer<*, *>.WebView(init: KuiklyWebView.() -> Unit) {
    addChild(KuiklyWebView(), init)
}
