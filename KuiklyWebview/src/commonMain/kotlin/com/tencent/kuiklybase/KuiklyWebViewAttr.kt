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
}
