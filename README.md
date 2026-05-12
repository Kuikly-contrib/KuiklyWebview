# KuiklyWebview

KuiklyWebview 是基于 [Kuikly](https://github.com/Tencent-TDS/KuiklyUI) 框架的跨端 WebView 组件，支持 **Android、iOS、鸿蒙（OHOS）** 三端，提供统一的 DSL API 和 JSBridge 通信能力。




## 跨端层接入（KMP）

在 KMP 共享模块的 `build.gradle.kts` 中添加 Maven 仓库和依赖：

```kotlin
repositories {
    maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent") }
}

dependencies {
    implementation("com.tencent.kuiklybase:KuiklyWebview:1.0.1-2.0.21")
}
```

在 KMP 共享模块的 `build.ohos.gradle.kts` 中添加鸿蒙构建 Maven 仓库和依赖：
```kotlin
repositories {
    maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent") }
}

dependencies {
    implementation("com.tencent.kuiklybase:KuiklyWebview:1.0.1-2.0.21-KBA-010")
}
```


---

## Android 接入

### 1. 添加依赖

在 Android 宿主工程中引入原生实现：

```kotlin
implementation("com.tencent.kuiklybase:kuikly-webview-android:1.0.1-2.0.21")
```

### 2. 注册原生视图

在 `Application.onCreate()` 或 Kuikly 初始化时注册 WebView：

```kotlin
KuiklyRenderCore.registerView("KRWebView") { KRWebView(context) }
```

---

## iOS 接入

### 1. 添加 CocoaPods 依赖

在 `Podfile` 中添加：

```ruby
# 方式一：从 GitHub 引入（推荐）
pod 'KuiklyWebviewIOS', :git => 'https://github.com/Kuikly-contrib/KuiklyWebview.git', :tag => '1.0.0'
```

然后执行：

```bash
pod install
```

### 2. 注册原生视图

iOS 端的 `KRWebView` 遵循 `KuiklyRenderViewExportProtocol` 协议，**通过运行时自动发现，无需手动注册**。

只需确保 `KuiklyWebviewIOS` Pod 被正确引入即可。

---

## 鸿蒙（OHOS）接入

### 1. 安装依赖

```bash
ohpm install @yuki8273/webview-ohos
```

### 2. 注册原生视图

在应用初始化时注册 `KRWebView`：

```typescript
import { KRWebView } from '@yuki8273/webview-ohos';
import { KuiklyRenderBaseView } from '@kuikly-open/render';

KuiklyRenderBaseView.registerView(KRWebView.VIEW_NAME, () => new KRWebView());
```

---

## Kuikly 侧使用

在 Kuikly 页面中通过 `WebView { }` DSL 使用组件：

```kotlin
import com.tencent.kuiklybase.KuiklyWebView
import com.tencent.kuiklybase.WebView

@Page("myPage")
class MyPage : BasePager() {

    private var pageTitle by observable("加载中...")
    private var progress by observable(0)
    private var isLoading by observable(false)
    private lateinit var webViewRef: ViewRef<KuiklyWebView>

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            // 进度条
            vif({ ctx.isLoading }) {
                View {
                    attr {
                        height(2f)
                        width(ctx.pageData.pageViewWidth * ctx.progress / 100f)
                        backgroundColor(Color(0xFF007AFFL))
                    }
                }
            }

            // WebView 组件
            WebView {
                ref { ctx.webViewRef = it }
                attr {
                    flex(1f)
                    src("https://example.com")       // 加载 URL
                    javaScriptEnabled(true)           // 启用 JS（默认 true）
                    domStorageEnabled(true)           // 启用 DOM Storage
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
                        // 处理加载错误
                    }
                    onMessage { message ->
                        // 收到 JS 发来的消息（JSBridge）
                    }
                }
            }
        }
    }
}
```

### 命令式 API

通过 `ViewRef` 获取 WebView 实例后，可调用以下方法：

```kotlin
// 加载 URL
webViewRef.view?.loadUrl("https://example.com")

// 加载 HTML 字符串
webViewRef.view?.loadHtml("<h1>Hello</h1>", baseUrl = "https://example.com")

// 执行 JS
webViewRef.view?.evaluateJavaScript("document.title") { result ->
    println("JS 返回: $result")
}

// 导航
webViewRef.view?.goBack()
webViewRef.view?.goForward()
webViewRef.view?.reload()

// 查询导航状态
webViewRef.view?.canGoBack { canGoBack ->
    if (canGoBack) webViewRef.view?.goBack()
}

// 向 JS 发送消息（JSBridge）
webViewRef.view?.sendMessageToJS("onDataReady", """{"key":"value"}""")
```

---

## JSBridge 通信

KuiklyWebview 内置了一套标准 JSBridge 协议，JS 端通过 `window.KuiklyBridge` 与 Native 通信。

### JS 端调用 Native

```javascript
// JS 调用 Native 方法（返回 Promise）
window.KuiklyBridge.callNative('getUserInfo', { userId: '123' })
  .then(result => {
    console.log('Native 返回:', result)
  })
  .catch(err => {
    console.error('调用失败:', err)
  })
```

### JS 端监听 Native 事件

```javascript
// 注册事件处理器
window.KuiklyBridge.registerHandler('onDataReady', function(params) {
  console.log('收到 Native 消息:', params)
})
```

### Native 端向 JS 发送消息

```kotlin
// Kuikly 侧
webViewRef.view?.sendMessageToJS("onDataReady", """{"key":"value"}""")
```

### 消息格式

| 字段 | 说明 |
|---|---|
| `type` | 消息类型：`call`（JS→Native）/ `response`（Native→JS）/ `event`（Native推送） |
| `callId` | 调用 ID，用于匹配回调 |
| `method` | 方法名 |
| `params` | 参数（JSON 对象） |
| `result` | 返回结果 |
| `error` | 错误信息 |

---

## API 参考

### `attr { }` 属性

| 方法 | 类型 | 说明 |
|---|---|---|
| `src(url)` | `String` | 设置加载的 URL |
| `htmlContent(html)` | `String` | 直接加载 HTML 字符串 |
| `javaScriptEnabled(enabled)` | `Boolean` | 是否启用 JavaScript，默认 `true` |
| `domStorageEnabled(enabled)` | `Boolean` | 是否启用 DOM Storage，默认 `true` |
| `userAgent(ua)` | `String` | 自定义 User-Agent |
| `allowsInlineMediaPlayback(allowed)` | `Boolean` | 是否允许内联媒体播放，默认 `true` |
| `urlInterceptSchemes(schemes)` | `List<String>` | 需要由原生拦截的 scheme 列表（命中即 cancel + 上抛事件），mainFrame 与 iframe 都生效 |
| `urlInterceptHosts(hosts)` | `List<String>` | 需要拦截的 host 列表，**支持 `*.example.com` 通配符**（仅作用于 mainFrame，对 302 跳转后的目标 host 也生效） |
| `reportAllNavigation(enabled)` | `Boolean` | 是否对所有 mainFrame 导航都触发事件（仅感知，不拦截 http/https），默认 `false` |
| `autoOpenExternalScheme(enabled)` | `Boolean` | 是否允许组件自动唤起外部 App 处理未命中规则的非标准 scheme（如 `weixin://`），**默认 `false`**。默认行为：cancel + 上抛事件，由业务自行决定是否唤起 |

### `event { }` 事件

| 方法 | 回调参数 | 说明 |
|---|---|---|
| `onPageStarted { url }` | `url: String` | 页面开始加载 |
| `onPageFinished { url }` | `url: String` | 页面加载完成 |
| `onError { errorCode, description }` | `Int, String` | 页面加载出错 |
| `onReceiveTitle { title }` | `title: String` | 收到页面标题 |
| `onProgressChanged { progress }` | `progress: Int`（0-100） | 加载进度变化 |
| `onMessage { message }` | `message: String` | 收到 JS 发来的消息 |
| `onShouldOverrideUrlLoading { url, isMainFrame, source }` | `String, Boolean, String` | URL 拦截感知事件，详见下方 [URL 拦截](#url-拦截) |

### 命令式方法

| 方法 | 说明 |
|---|---|
| `loadUrl(url)` | 加载指定 URL |
| `loadHtml(html, baseUrl)` | 加载 HTML 字符串 |
| `evaluateJavaScript(script, callback)` | 执行 JS 代码 |
| `goBack()` | 后退 |
| `goForward()` | 前进 |
| `reload()` | 重新加载 |
| `stopLoading()` | 停止当前加载（**事后兜底**，不能回滚已发出的请求；真正的同步拦截请用 `urlInterceptSchemes` / `urlInterceptHosts`） |
| `canGoBack(callback)` | 查询是否可后退 |
| `canGoForward(callback)` | 查询是否可前进 |
| `sendMessageToJS(method, params)` | 向 JS 发送消息（JSBridge） |

---

## URL 拦截

KuiklyWebview 提供两层 URL 拦截能力，覆盖原生导航和 SPA 路由变化两类场景。

### 设计要点

由于 Kuikly 桥接是**单向异步**的，无法像原生 `WebViewClient.shouldOverrideUrlLoading` 那样通过返回值同步阻断加载。本组件的拦截能力分为两层：

- **同步规则层**：`urlInterceptSchemes` / `urlInterceptHosts` 下发到原生层，命中即 cancel 加载，**零请求外发**。这是真正意义上的"拦截"。host 规则在 iOS 端会在响应阶段（`decidePolicyForNavigationResponse`）做一次二次校验，覆盖 302 重定向后的目标 host；Android 端 WebView 自身会对 302 后的新 URL 重新触发 `shouldOverrideUrlLoading`，无需额外处理。
- **异步感知层**：`onShouldOverrideUrlLoading` 事件，用于业务监听。事件来源包括原生导航、SPA 路由变化（组件内置 `history.pushState` / `replaceState` / `popstate` / `hashchange` 的 JS hook，Android 上对 hashchange 已做去重，避免与原生事件双发）。

> `stopLoading()` 不属于上述任何一层，它是**事后兜底**：由于桥接异步，无法阻止已发出的 HTTP 请求、也无法回滚已修改的 History。要做到真正的同步拦截，请使用同步规则层。

### 非标准 scheme 默认行为

未命中 `urlInterceptSchemes` 的非标准 scheme（如 `weixin://`、`mqqapi://`、`intent://` 等）默认行为是 **cancel 加载 + 上抛事件**，由业务在 `onShouldOverrideUrlLoading` 里自行决定是否唤起外部 App。这样可以避免 WebView 嵌入第三方页面（广告、UGC 内容）时，被恶意链接擅自唤起应用。

如需保留组件自动唤起的便利行为（**仅在 WebView 承载内容完全可信时**）可显式开启：

```kotlin
attr {
    autoOpenExternalScheme(true)   // 默认 false
}
```

### host 匹配语法

`urlInterceptHosts` 的每一条规则支持两种语法：

| 语法 | 说明 | 示例命中 | 示例不命中 |
|---|---|---|---|
| `example.com` | **精确匹配**：host 必须完全等于规则 | `example.com` | `m.example.com` / `foo.example.com` |
| `*.example.com` | **通配符匹配**：匹配 `example.com` 自身 + 任意层级子域 | `example.com` / `m.example.com` / `a.b.example.com` | `myexample.com` / `example.com.cn` |

匹配时自动忽略端口与大小写。

### 运行时动态更新

`urlInterceptSchemes` / `urlInterceptHosts` / `reportAllNavigation` 三个 attr 都支持**运行时动态更新**：
每次变更都会完全覆盖旧规则，已经加载的页面继续保留，但后续的导航立即按新规则执行。适合风控下发、A/B 实验等场景。

```kotlin
// 在页面打开后，运行时切换规则
private var interceptHosts by observable(listOf("order.example.com"))

// ...
WebView {
    attr {
        src("https://example.com")
        urlInterceptHosts(ctx.interceptHosts)  // 依赖响应式状态，变更即生效
    }
}

// 用户点击某个按钮后，追加规则
fun onToggleStrictMode() {
    interceptHosts = listOf("order.example.com", "*.pay.example.com")
}
```

### Frame 作用范围

| 规则 | mainFrame | iframe |
|---|---|---|
| `urlInterceptSchemes` | ✅ 拦截 + 事件 | ✅ 拦截 + 事件（自定义 scheme 不应出现在 iframe，防御性拦截） |
| `urlInterceptHosts` | ✅ 拦截 + 事件 | ❌ 不处理（避免误伤第三方广告 / 支付 iframe） |
| `reportAllNavigation` | ✅ 上抛事件（不拦） | ❌ 不上抛（降低噪音） |
| 安全黑名单（javascript/data/blob/vbscript） | ✅ 始终拦截 | ✅ 始终拦截 |

### 使用示例

```kotlin
WebView {
    attr {
        src("https://app.example.com/page")
        // 自定义 scheme 由原生路由处理（同步拦截）
        urlInterceptSchemes(listOf("myapp", "tdsworkshop"))
        // 业务域名跳转走原生（同步拦截，仅 mainFrame）
        // 支持通配符：`*.order.example.com` 匹配订单相关所有子域
        urlInterceptHosts(listOf("order.example.com", "*.pay.example.com"))
        // 想监听所有 mainFrame 导航做埋点（不拦截 http/https）
        reportAllNavigation(true)
    }
    event {
        onShouldOverrideUrlLoading { url, isMainFrame, source ->
            when {
                url.startsWith("myapp://") -> {
                    // 业务自定义协议，走原生路由
                    routerOpen(url)
                }
                url.contains("/order/") -> {
                    // 命中 host 拦截规则后已 cancel，无需 stopLoading
                    routerOpenOrderDetail(url)
                }
                source == "pushState" || source == "hashchange" -> {
                    // SPA 内部路由变化（埋点 / 同步页面状态）
                    reportRoute(url)
                }
            }
        }
    }
}
```

### 事件触发场景

| 触发源 | `source` 值 | 是否同步拦截 |
|---|---|---|
| 原生导航命中 `urlInterceptSchemes` | `"navigation"` | ✅ cancel |
| 原生导航命中 `urlInterceptHosts`（含通配符） | `"navigation"` | ✅ cancel |
| 非标准 scheme（且未命中规则） | `"navigation"` | ✅ cancel + 尝试 system openURL |
| http/https 且开启 `reportAllNavigation` | `"navigation"` | ❌ 仅感知 |
| `history.pushState()` | `"pushState"` | ❌ 仅感知 |
| `history.replaceState()` | `"replaceState"` | ❌ 仅感知 |
| `popstate` 事件 | `"popstate"` | ❌ 仅感知 |
| `hashchange` 事件 | `"hashchange"` | ❌ 仅感知 |

### 不能拦截的场景

由于桥接异步特性 / 平台 API 限制，以下场景**无法在 Kotlin 侧拦下**，业务方需谨慎：

- 未在 `urlInterceptHosts` 中配置的服务器 302 重定向（已配置的 host 在三端都会被同步拦截，包括 iOS 通过 `decidePolicyForNavigationResponse` 二次校验）
- `<form method="POST">` 提交跳转（payload 已发出）
- 首次 `src()` 加载的 URL（属于业务自己设置的 URL，不应被拦）

对这些场景，`stopLoading()` 只能止血，无法回滚已发生的 HTTP 请求。

### 端到端示例

下面给出一个**可直接运行的完整页面**，演示了 URL 拦截在真实业务里如何分流处理：

- 自定义 scheme（`myapp://`、`tdsworkshop://`）→ 走原生路由
- 命中业务 host（`*.pay.example.com` 等）→ 弹原生确认框，再走原生
- SPA 路由变化 → 仅埋点
- 运行时切换 `autoOpenExternalScheme`，演示动态更新 attr

```kotlin
@Page("WebViewDemo")
internal class WebViewDemoPage : BasePager() {

    private var pageTitle by observable("加载中...")
    private var progress by observable(0)
    private var isLoading by observable(false)
    private var autoOpenExternal by observable(false)
    private var interceptLog by observableList<String>()
    private lateinit var webViewRef: ViewRef<KuiklyWebView>

    private fun bridge() = acquireModule<BridgeModule>(BridgeModule.MODULE_NAME)

    private fun appendLog(line: String) {
        interceptLog.add(0, line)
        while (interceptLog.size > 5) interceptLog.removeAt(interceptLog.size - 1)
    }

    override fun body(): ViewBuilder {
        val ctx = this
        return {
            // —— 顶部测试工具条：注入 JS 模拟前端发起不同导航 ——
            View {
                attr { flexDirectionRow(); height(40f) }
                Text {
                    attr { flex(1f); text("scheme"); textAlignCenter() }
                    event { click {
                        ctx.webViewRef.view?.evaluateJavaScript(
                            "window.location.href='myapp://order/123';"
                        )
                    } }
                }
                Text {
                    attr { flex(1f); text("host"); textAlignCenter() }
                    event { click {
                        ctx.webViewRef.view?.evaluateJavaScript(
                            "window.location.href='https://demo.example.com/order/42';"
                        )
                    } }
                }
                Text {
                    attr { flex(1f); text("pushState"); textAlignCenter() }
                    event { click {
                        ctx.webViewRef.view?.evaluateJavaScript(
                            "history.pushState({}, '', '/spa/page-' + Date.now());"
                        )
                    } }
                }
                Text {
                    attr { flex(1.2f); text("autoOpen: " + if (ctx.autoOpenExternal) "ON" else "OFF") }
                    event { click { ctx.autoOpenExternal = !ctx.autoOpenExternal } }
                }
            }

            // —— WebView 主体 ——
            WebView {
                ref { ctx.webViewRef = it }
                attr {
                    flex(1f)
                    src("https://app.example.com/")
                    javaScriptEnabled(true)
                    domStorageEnabled(true)

                    // ① 自定义 scheme：命中即原生 cancel，零请求外发
                    urlInterceptSchemes(listOf("myapp", "tdsworkshop"))

                    // ② 业务域名：mainFrame 命中即 cancel，支持 *. 通配
                    urlInterceptHosts(listOf("demo.example.com", "*.pay.example.com"))

                    // ③ 想感知所有 mainFrame 导航做埋点（不拦截 http/https）
                    reportAllNavigation(true)

                    // ④ 运行时切换：是否允许组件自动唤起外部 App
                    autoOpenExternalScheme(ctx.autoOpenExternal)
                }
                event {
                    onPageStarted { ctx.isLoading = true }
                    onPageFinished { ctx.isLoading = false }
                    onReceiveTitle { ctx.pageTitle = it }
                    onProgressChanged { ctx.progress = it }

                    // —— 业务侧分流处理 ——
                    onShouldOverrideUrlLoading { url, isMainFrame, source ->
                        when {
                            // 1) 自定义 scheme → 原生路由
                            url.startsWith("myapp://") || url.startsWith("tdsworkshop://") -> {
                                ctx.appendLog("⇢ scheme $url")
                                ctx.bridge().openPage(url)        // 实际走业务路由
                            }

                            // 2) 命中 host → 弹原生确认框
                            url.contains("demo.example.com") || url.contains(".pay.example.com") -> {
                                ctx.appendLog("⇢ host $url")
                                ctx.bridge().showAlert(
                                    title = "拦截到外跳",
                                    message = "目标：$url\n要在原生侧打开吗？",
                                    leftBtnTitle = "取消",
                                    rightBtnTitle = "原生打开"
                                ) { idx ->
                                    if (idx == 1) ctx.bridge().openPage(url)
                                }
                            }

                            // 3) SPA 路由变化 → 仅埋点
                            source != "navigation" -> ctx.appendLog("◇ SPA[$source] $url")

                            // 4) reportAllNavigation 模式下的常规导航
                            else -> ctx.appendLog("· nav $url")
                        }
                    }
                }
            }

            // —— 拦截日志 ——
            vfor({ ctx.interceptLog }) { line ->
                Text { attr { text(line); fontSize(11f) } }
            }
        }
    }
}
```

完整版（含返回/刷新/进度条/夜间模式）见
[`WebViewDemoPage.kt`](shared/src/commonMain/kotlin/com/tencent/kuiklybase/WebViewDemoPage.kt)，可直接在 demo 工程里运行。




