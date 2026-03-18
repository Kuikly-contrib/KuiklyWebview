package com.tencent.kuiklybase

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.*
import com.tencent.kuiklybase.base.BasePager
import com.tencent.kuiklybase.KuiklyWebView
import com.tencent.kuiklybase.WebView
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexDirection

/**
 * WebView 功能演示页面
 * 展示 KuiklyWebView 组件的 URL 加载、JS 执行、JSBridge 通信等功能
 */
@Page("router", supportInLocal = true)
internal class WebViewDemoPage : BasePager() {

    /** 当前加载的 URL */
    private var currentUrl by observable("https://tds.qq.com/")

    /** 页面标题 */
    private var pageTitle by observable("WebView Demo")

    /** 加载进度 */
    private var progress by observable(0)

    /** 是否加载中 */
    private var isLoading by observable(false)

    /** JS 执行结果 */
    private var jsResult by observable("")

    /** WebView 引用 */
    private lateinit var webViewRef: ViewRef<KuiklyWebView>

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            // 顶部标题栏
            View {
                attr {
                    flexDirection(FlexDirection.ROW)
                    alignItems(FlexAlign.CENTER)
                    height(44f)
                    backgroundColor(if (ctx.isNightMode()) Color(0xFF333333L) else Color(0xFFF5F5F5L))
                    paddingLeft(12f)
                    paddingRight(12f)
                }

                // 返回按钮
                Text {
                    attr {
                        text("‹ 返回")
                        fontSize(16f)
                        color(if (ctx.isNightMode()) Color(0xFFFFFFFFL) else Color(0xFF007AFFL))
                    }
                    event {
                        click {
                            if (ctx::webViewRef.isInitialized) {
                                ctx.webViewRef.view?.canGoBack { canGoBack ->
                                    if (canGoBack) {
                                        ctx.webViewRef.view?.goBack()
                                    } else {
                                        ctx.acquireModule<RouterModule>(RouterModule.MODULE_NAME).closePage()
                                    }
                                }
                            }
                        }
                    }
                }

                // 标题
                Text {
                    attr {
                        flex(1f)
                        text(ctx.pageTitle)
                        fontSize(16f)
                        color(if (ctx.isNightMode()) Color(0xFFFFFFFFL) else Color(0xFF333333L))
                        textAlignCenter()
                        lines(1)
                    }
                }

                // 刷新按钮
                Text {
                    attr {
                        text("↻ 刷新")
                        fontSize(16f)
                        color(if (ctx.isNightMode()) Color(0xFFFFFFFFL) else Color(0xFF007AFFL))
                    }
                    event {
                        click {
                            if (ctx::webViewRef.isInitialized) {
                                ctx.webViewRef.view?.reload()
                            }
                        }
                    }
                }
            }

            // 进度条（加载中时显示）
            vif({ ctx.isLoading }) {
                View {
                    attr {
                        height(2f)
                        backgroundColor(Color(0xFFE0E0E0L))
                    }
                    View {
                        attr {
                            height(2f)
                            width(ctx.pageData.pageViewWidth * ctx.progress / 100f)
                            backgroundColor(Color(0xFF007AFFL))
                        }
                    }
                }
            }

            // WebView 组件
            WebView {
                ref {
                    ctx.webViewRef = it
                }
                attr {
                    flex(1f)
                    src(ctx.currentUrl)
                    javaScriptEnabled(true)
                    domStorageEnabled(true)
                }
                event {
                    onPageStarted { url ->
                        ctx.isLoading = true
                    }
                    onPageFinished { url ->
                        ctx.isLoading = false
                    }
                    onReceiveTitle { title ->
                        ctx.pageTitle = title
                    }
                    onProgressChanged { newProgress ->
                        ctx.progress = newProgress
                    }
                    onError { errorCode, description ->
                        ctx.jsResult = "Error: $errorCode - $description"
                    }
                    onMessage { message ->
                        ctx.jsResult = "Message from JS: $message"
                    }
                }
            }

            // 底部信息/调试栏
            vif({ ctx.jsResult.isNotEmpty() }) {
                View {
                    attr {
                        height(32f)
                        backgroundColor(if (ctx.isNightMode()) Color(0xFF222222L) else Color(0xFFFAFAFAL))
                        paddingLeft(12f)
                        paddingRight(12f)
                        alignItems(FlexAlign.CENTER)
                        flexDirection(FlexDirection.ROW)
                    }
                    Text {
                        attr {
                            flex(1f)
                            text(ctx.jsResult)
                            fontSize(12f)
                            color(Color(0xFF999999L))
                            lines(1)
                        }
                    }
                }
            }
        }
    }
}
