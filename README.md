# KuiklyWebview

KuiklyWebview 是基于 [Kuikly](https://github.com/Tencent-TDS/KuiklyUI) 框架的跨端 WebView 组件，支持 **Android、iOS、鸿蒙（OHOS）** 三端，提供统一的 DSL API 和 JSBridge 通信能力。




## Android 接入

### 1. 配置仓库

在项目根目录 `settings.gradle.kts` 或 `build.gradle.kts` 中添加 Maven 仓库：

```kotlin
repositories {
    maven { url = uri("https://mirrors.tencent.com/nexus/repository/maven-tencent") }
}
```

### 2. 添加依赖

```kotlin
// KMP 跨端组件（Kotlin 侧 DSL）
implementation("com.tencent.kuiklybase:KuiklyWebview:1.0.0-2.0.21")

// Android 原生实现（必须同时引入）
implementation("com.tencent.kuiklybase:KuiklyWebview-android:1.0.0-2.0.21")
```

### 3. 注册原生视图

在 `Application.onCreate()` 或 Kuikly 初始化时注册 WebView：

```kotlin
// Kotlin
KuiklyRenderCore.registerView("KRWebView") { KRWebView(context) }
```


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

### `event { }` 事件

| 方法 | 回调参数 | 说明 |
|---|---|---|
| `onPageStarted { url }` | `url: String` | 页面开始加载 |
| `onPageFinished { url }` | `url: String` | 页面加载完成 |
| `onError { errorCode, description }` | `Int, String` | 页面加载出错 |
| `onReceiveTitle { title }` | `title: String` | 收到页面标题 |
| `onProgressChanged { progress }` | `progress: Int`（0-100） | 加载进度变化 |
| `onMessage { message }` | `message: String` | 收到 JS 发来的消息 |

### 命令式方法

| 方法 | 说明 |
|---|---|
| `loadUrl(url)` | 加载指定 URL |
| `loadHtml(html, baseUrl)` | 加载 HTML 字符串 |
| `evaluateJavaScript(script, callback)` | 执行 JS 代码 |
| `goBack()` | 后退 |
| `goForward()` | 前进 |
| `reload()` | 重新加载 |
| `canGoBack(callback)` | 查询是否可后退 |
| `canGoForward(callback)` | 查询是否可前进 |
| `sendMessageToJS(method, params)` | 向 JS 发送消息（JSBridge） |
