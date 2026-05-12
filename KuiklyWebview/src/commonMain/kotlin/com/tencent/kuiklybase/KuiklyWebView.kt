package com.tencent.kuiklybase

import com.tencent.kuikly.core.base.DeclarativeBaseView
import com.tencent.kuikly.core.base.ViewContainer
import com.tencent.kuikly.core.nvi.serialization.json.JSONObject

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
     *
     * 注意：原生侧会把 JS 返回值包装成 `{"result": <value>}` 的 JSONObject 回传，
     * 这是为了规避 Kuikly 桥接对"看起来像 JSON 对象的纯字符串"自动反序列化的问题。
     * 这里负责把 result 字段解包出来再交给业务，对业务调用方完全透明。
     */
    fun evaluateJavaScript(script: String, callback: ((String?) -> Unit)? = null) {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("evaluateJavaScript", script, callback?.let { cb ->
                { result: Any? ->
                    val unwrapped: String? = when (result) {
                        null -> null
                        is JSONObject -> result.optString("result", "")
                        else -> result.toString()
                    }
                    cb(unwrapped)
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
     * 停止当前加载（**事后兜底**，而非同步拦截手段）
     *
     * 本方法主要用于配合 [KuiklyWebViewEvent.onShouldOverrideUrlLoading] 在
     * [KuiklyWebViewAttr.reportAllNavigation] 模式下的**止血**场景：业务通过事件
     * 感知到某次 http(s) 导航需要阻止时，可以调用此方法中断后续加载。
     *
     * **重要限制**：
     * - Kuikly 桥接是异步的：原生侧触发导航 → 上抛事件到 Kotlin → 回传 stopLoading
     *   命令到原生，这整条链路存在桥接延迟。在命令到达前，WebView 已经可能：
     *     * 发起了 HTTP 请求（含 Cookie / Referer）
     *     * 开始解析了响应 HTML
     *     * 触发了 `history` 变化
     * - 调用 stopLoading **只能止血，无法回滚已发出的网络请求 / 已修改的 History**
     *
     * **如果业务需要「真正的同步拦截」**，请优先配置：
     * - [KuiklyWebViewAttr.urlInterceptSchemes]：拦自定义协议
     * - [KuiklyWebViewAttr.urlInterceptHosts]：拦特定域名（支持 `*.example.com` 通配符）
     *
     * 这两个同步规则在原生侧匹配时直接 cancel 加载，完全不会有 HTTP 请求外发。
     */
    fun stopLoading() {
        performTaskWhenRenderViewDidLoad {
            renderView?.callMethod("stopLoading", null)
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
