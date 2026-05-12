package com.tencent.kuiklybase

import com.tencent.kuikly.core.base.Attr

/**
 * KuiklyWebView 组件的属性定义
 * 通过 "key" with value 将属性透传到各平台原生 WebView
 */
class KuiklyWebViewAttr : Attr() {

    /**
     * 设置要加载的 URL
     * @param url 网页地址
     */
    fun src(url: String): KuiklyWebViewAttr {
        "src" with url
        return this
    }

    /**
     * 设置直接加载的 HTML 内容
     * @param html HTML 字符串
     */
    fun htmlContent(html: String): KuiklyWebViewAttr {
        "htmlContent" with html
        return this
    }

    /**
     * 设置是否启用 JavaScript（默认 true）
     * @param enabled 是否启用
     */
    fun javaScriptEnabled(enabled: Boolean): KuiklyWebViewAttr {
        "javaScriptEnabled" with if (enabled) "true" else "false"
        return this
    }

    /**
     * 设置自定义 User-Agent
     * @param ua User-Agent 字符串
     */
    fun userAgent(ua: String): KuiklyWebViewAttr {
        "userAgent" with ua
        return this
    }

    /**
     * 设置是否启用 DOM Storage（默认 true）
     * @param enabled 是否启用
     */
    fun domStorageEnabled(enabled: Boolean): KuiklyWebViewAttr {
        "domStorageEnabled" with if (enabled) "true" else "false"
        return this
    }

    /**
     * 设置是否允许内联媒体播放（默认 true）
     * @param allowed 是否允许
     */
    fun allowsInlineMediaPlayback(allowed: Boolean): KuiklyWebViewAttr {
        "allowsInlineMediaPlayback" with if (allowed) "true" else "false"
        return this
    }

    /**
     * 配置需要拦截的 scheme 列表（同步规则下发，原生侧匹配后直接 cancel 加载，
     * 同时通过 [KuiklyWebViewEvent.onShouldOverrideUrlLoading] 上抛 URL）。
     *
     * 适用场景：业务自定义协议（如 `myapp://`、`tdsworkshop://`）希望由原生路由处理，
     * 而不是让 WebView 自己加载。
     *
     * 注意：
     * - http / https / about / file 不会被拦截，始终放行
     * - javascript / data / blob / vbscript 始终被拦截（安全防护，与本配置无关）
     * - 作用范围：**mainFrame 与 iframe 都会拦**（自定义 scheme 不应出现在 iframe 里）
     * - **支持运行时动态更新**：每次重新调用此 attr 都会完全覆盖旧规则
     *
     * @param schemes scheme 名称列表（不带 `://`，不区分大小写），如 `listOf("myapp", "tdsworkshop")`
     */
    fun urlInterceptSchemes(schemes: List<String>): KuiklyWebViewAttr {
        "urlInterceptSchemes" with schemes.joinToString(",")
        return this
    }

    /**
     * 配置需要拦截的 host 列表（同步规则下发，原生侧匹配 mainFrame 导航的 host 后 cancel 加载，
     * 同时通过 [KuiklyWebViewEvent.onShouldOverrideUrlLoading] 上抛 URL）。
     *
     * 支持两种匹配语法：
     * 1. **精确匹配**：`"app.example.com"` → 只匹配 `app.example.com`
     * 2. **通配符匹配**：`"*.example.com"` → 匹配 `example.com` 本身以及任意层级子域
     *    （如 `m.example.com` / `a.b.example.com`）
     *
     * 适用场景：把 `https://app.example.com/order/123` 这种 URL 转交给原生路由打开订单详情页，
     * 而不让 WebView 加载。
     *
     * 注意：
     * - **仅作用于 mainFrame**：iframe 内的同域跳转不会被拦截（避免误伤第三方广告/支付 iframe）
     * - host 比较自动 `trim + lowercase`，且 URL 中的端口会被忽略（`example.com:8080` 只比较 `example.com`）
     * - **支持运行时动态更新**：每次重新调用此 attr 都会完全覆盖旧规则
     *
     * @param hosts host 规则列表，如 `listOf("app.example.com", "*.order.example.com")`
     */
    fun urlInterceptHosts(hosts: List<String>): KuiklyWebViewAttr {
        "urlInterceptHosts" with hosts.joinToString(",")
        return this
    }

    /**
     * 是否对所有 mainFrame 导航都触发 [KuiklyWebViewEvent.onShouldOverrideUrlLoading] 事件
     * （仅感知，不拦截 http/https）。开启后业务可以基于事件感知做埋点；若需要中断加载，
     * 可命令式调用 [KuiklyWebView.stopLoading]，但这属于**事后兜底**，不能回滚已发出的请求
     * （详见 [KuiklyWebView.stopLoading] 的注释）。
     *
     * 默认 false（仅命中 [urlInterceptSchemes] / [urlInterceptHosts] 时才触发事件）。
     *
     * 注意：
     * - 仅作用于 mainFrame，iframe 导航不会上抛（避免噪音）
     * - **支持运行时动态更新**
     */
    fun reportAllNavigation(enabled: Boolean): KuiklyWebViewAttr {
        "reportAllNavigation" with if (enabled) "true" else "false"
        return this
    }

    /**
     * 是否允许组件自动尝试通过系统唤起第三方 App 处理非标准 scheme（如 `weixin://`、
     * `mqqapi://`、`intent://` 等未被 [urlInterceptSchemes] 命中的自定义协议）。
     *
     * - `false`（默认，**推荐**）：未命中规则的非标准 scheme 仅 cancel 加载并上抛事件，
     *   是否唤起外部 App 完全由业务在 [KuiklyWebViewEvent.onShouldOverrideUrlLoading]
     *   里自行调用 RouterModule / IntentModule 决定。更安全、合规。
     * - `true`：保留旧版便利行为——组件**内部**直接 `startActivity(Intent.VIEW)` / iOS
     *   `openURL:`。风险：可能被页面里的恶意链接（如广告里的 `market://`）滥用唤起外部
     *   应用，且用户无法感知。仅当你的 WebView 承载的内容完全可信时再开启。
     *
     * **支持运行时动态更新**。
     */
    fun autoOpenExternalScheme(enabled: Boolean): KuiklyWebViewAttr {
        "autoOpenExternalScheme" with if (enabled) "true" else "false"
        return this
    }
}
