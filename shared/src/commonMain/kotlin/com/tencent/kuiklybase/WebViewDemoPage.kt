package com.tencent.kuiklybase

import com.tencent.kuikly.core.annotations.Page
import com.tencent.kuikly.core.base.*
import com.tencent.kuikly.core.directives.vif
import com.tencent.kuikly.core.directives.vfor
import com.tencent.kuikly.core.module.RouterModule
import com.tencent.kuikly.core.reactive.handler.*
import com.tencent.kuikly.core.views.*
import com.tencent.kuiklybase.base.BasePager
import com.tencent.kuiklybase.base.BridgeModule
import com.tencent.kuiklybase.KuiklyWebView
import com.tencent.kuiklybase.WebView
import com.tencent.kuikly.core.layout.FlexAlign
import com.tencent.kuikly.core.layout.FlexDirection

/**
 * WebView 功能演示页面
 * 展示 KuiklyWebView 组件的 URL 加载、JS 执行、JSBridge、URL 拦截等功能
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

    /** 是否允许组件自动唤起外部 App（演示运行时切换） */
    private var autoOpenExternal by observable(false)

    /** 最近 5 条 URL 拦截记录（最新的在最前） */
    private var interceptLog by observableList<String>()

    /** WebView 引用 */
    private lateinit var webViewRef: ViewRef<KuiklyWebView>

    private fun bridge(): BridgeModule =
        acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)

    /** 把一条拦截记录追加到日志列表，最多保留 5 条 */
    private fun appendInterceptLog(line: String) {
        interceptLog.add(0, line)
        while (interceptLog.size > 5) {
            interceptLog.removeAt(interceptLog.size - 1)
        }
    }

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

            // ===== URL 拦截功能演示工具条 =====
            // 三个测试按钮：通过注入 JS 模拟前端发起不同类型的导航，验证拦截规则
            View {
                attr {
                    flexDirection(FlexDirection.ROW)
                    alignItems(FlexAlign.CENTER)
                    height(40f)
                    backgroundColor(if (ctx.isNightMode()) Color(0xFF2A2A2AL) else Color(0xFFEFEFF4L))
                    paddingLeft(8f)
                    paddingRight(8f)
                }
                // 触发自定义 scheme（命中 urlInterceptSchemes）
                Text {
                    attr {
                        flex(1f)
                        text("scheme")
                        fontSize(13f)
                        color(Color(0xFF007AFFL))
                        textAlignCenter()
                    }
                    event {
                        click {
                            ctx.webViewRef.view?.evaluateJavaScript(
                                "window.location.href='myapp://order/123';"
                            )
                        }
                    }
                }
                // 触发命中 host 的 https 跳转（命中 urlInterceptHosts）
                Text {
                    attr {
                        flex(1f)
                        text("host")
                        fontSize(13f)
                        color(Color(0xFF007AFFL))
                        textAlignCenter()
                    }
                    event {
                        click {
                            ctx.webViewRef.view?.evaluateJavaScript(
                                "window.location.href='https://demo.example.com/order/42';"
                            )
                        }
                    }
                }
                // 触发 SPA pushState（仅感知不拦截）
                Text {
                    attr {
                        flex(1f)
                        text("pushState")
                        fontSize(13f)
                        color(Color(0xFF007AFFL))
                        textAlignCenter()
                    }
                    event {
                        click {
                            ctx.webViewRef.view?.evaluateJavaScript(
                                "history.pushState({}, '', '/spa/page-' + Date.now());"
                            )
                        }
                    }
                }
                // 切换 autoOpenExternalScheme（运行时动态更新 attr）
                Text {
                    attr {
                        flex(1.2f)
                        text("autoOpen: " + if (ctx.autoOpenExternal) "ON" else "OFF")
                        fontSize(13f)
                        color(if (ctx.autoOpenExternal) Color(0xFFFF9500L) else Color(0xFF8E8E93L))
                        textAlignCenter()
                    }
                    event {
                        click {
                            ctx.autoOpenExternal = !ctx.autoOpenExternal
                            ctx.bridge().toast(
                                "autoOpenExternalScheme = " + ctx.autoOpenExternal
                            )
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
                    // URL 拦截规则示例：
                    // 1. 命中 myapp:// / tdsworkshop:// 自定义协议时，由原生路由处理（不让 WebView 加载）
                    urlInterceptSchemes(listOf("myapp", "tdsworkshop"))
                    // 2. 命中下面 host 的 mainFrame 导航直接拦截，转给原生处理（示例：把 demo.example.com 截走）
                    //    支持 *.example.com 通配符；端口/大小写自动忽略
                    urlInterceptHosts(listOf("demo.example.com", "*.pay.example.com"))
                    // 3. 是否对所有 mainFrame 导航都触发感知事件（false=只在命中规则/SPA 路由时触发）
                    reportAllNavigation(true)
                    // 4. 运行时动态切换：是否允许组件自动唤起外部 App 处理未命中规则的非标准 scheme
                    //    OFF 时由业务在 onShouldOverrideUrlLoading 里自行决定（更安全）
                    autoOpenExternalScheme(ctx.autoOpenExternal)
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
                        ctx.appendInterceptLog("✗ Error $errorCode: $description")
                    }
                    onMessage { message ->
                        ctx.appendInterceptLog("✉ JS->Native: $message")
                    }
                    // ===== URL 拦截分流处理示例 =====
                    // source 取值：navigation / pushState / replaceState / popstate / hashchange
                    onShouldOverrideUrlLoading { url, isMainFrame, source ->
                        // 1) 自定义 scheme：组件已 cancel 加载，业务在这里调起原生路由
                        when {
                            url.startsWith("myapp://") || url.startsWith("tdsworkshop://") -> {
                                ctx.appendInterceptLog("⇢ scheme 路由 $url")
                                ctx.bridge().toast("已被业务接管：$url")
                                // 实际项目里在这里调用 RouterModule / IntentModule 跳转：
                                // ctx.bridge().openPage(url)
                            }

                            // 2) 命中 host 拦截规则：组件已 cancel，业务弹窗确认是否跳转
                            url.contains("demo.example.com") || url.contains(".pay.example.com") -> {
                                ctx.appendInterceptLog("⇢ host 命中 $url")
                                ctx.bridge().showAlert(
                                    title = "拦截到外跳",
                                    message = "目标：$url\n\n要在原生侧打开此订单吗？",
                                    leftBtnTitle = "取消",
                                    rightBtnTitle = "原生打开"
                                ) { /* 业务侧根据点击索引决定后续路由 */ }
                            }

                            // 3) SPA 路由变化：仅感知，可用于埋点 / 同步页面状态
                            source != "navigation" -> {
                                ctx.appendInterceptLog("◇ SPA[$source] $url")
                            }

                            // 4) reportAllNavigation 模式下的所有 http/https 导航（仅感知）
                            else -> {
                                ctx.appendInterceptLog("· nav mainFrame=$isMainFrame $url")
                            }
                        }
                    }
                }
            }

            // ===== 拦截事件日志（最近 5 条）=====
            vif({ ctx.interceptLog.isNotEmpty() }) {
                View {
                    attr {
                        backgroundColor(if (ctx.isNightMode()) Color(0xFF1C1C1EL) else Color(0xFFFAFAFAL))
                        paddingTop(4f)
                        paddingBottom(4f)
                        paddingLeft(12f)
                        paddingRight(12f)
                    }
                    vfor({ ctx.interceptLog }) { line ->
                        Text {
                            attr {
                                text(line)
                                fontSize(11f)
                                color(if (ctx.isNightMode()) Color(0xFFCCCCCCL) else Color(0xFF666666L))
                                lines(1)
                            }
                        }
                    }
                }
            }
        }
    }
}
