# @yuki8273/webview-ohos

基于 [Kuikly](https://github.com/Tencent-TDS/KuiklyUI) 框架的鸿蒙（HarmonyOS）WebView 组件，将系统 Web 组件封装为 Kuikly 渲染视图，提供统一的 DSL 属性设置、事件回调和 JSBridge 通信能力。

## 安装

在鸿蒙宿主工程中安装，执行以下命令：
```bash
ohpm install @yuki8273/webview-ohos
```

## 依赖

| 依赖 | 版本 |
|---|---|
| `@kuikly-open/render` | `>= 2.7.0` |

## 功能特性

- **WebView 渲染**：将鸿蒙 Web 组件封装为 `KRWebView`，通过 Kuikly 框架统一管理
- **属性配置**：支持 URL 加载、HTML 内容加载、JavaScript 开关、DOM Storage、自定义 User-Agent 等
- **事件回调**：页面开始/完成加载、加载错误、标题变更、进度变化、消息接收
- **命令式 API**：loadUrl、loadHtml、evaluateJavaScript、goBack、goForward、reload 等
- **JSBridge 通信**：内置标准 JSBridge 协议，JS 端通过 `window.KuiklyBridge` 与 Native 双向通信

## 导出模块

```typescript
// 主要导出
export { KRWebView } from './src/main/ets/KRWebView';
export { KRWebViewJSBridge, BridgeCallback, NativeMethodHandler } from './src/main/ets/KRWebViewJSBridge';
```

## 使用说明

### 1. 注册视图

在应用初始化时注册 `KRWebView`：

```typescript
import { KRWebView } from '@yuki8273/webview-ohos';
import { KuiklyRenderBaseView } from '@kuikly-open/render';

// 注册 WebView 组件
KuiklyRenderBaseView.registerView(KRWebView.VIEW_NAME, () => new KRWebView());
```

### 2. Kuikly 侧使用

在 Kuikly 页面中通过 DSL 使用组件：

```kotlin
import com.tencent.kuiklybase.KuiklyWebView
import com.tencent.kuiklybase.WebView

WebView {
    attr {
        flex(1f)
        src("https://example.com")       // 加载 URL
        javaScriptEnabled(true)           // 启用 JavaScript
        domStorageEnabled(true)           // 启用 DOM Storage
        userAgent("CustomUA/1.0")         // 自定义 User-Agent
    }
    event {
        onPageStarted { url -> /* 页面开始加载 */ }
        onPageFinished { url -> /* 页面加载完成 */ }
        onReceiveTitle { title -> /* 收到页面标题 */ }
        onProgressChanged { progress -> /* 加载进度 0-100 */ }
        onError { errorCode, description -> /* 加载错误 */ }
        onMessage { message -> /* 收到 JS 消息 */ }
    }
}
```

### 3. 命令式 API

通过 `ViewRef` 获取 WebView 实例后调用：

```kotlin
webViewRef.view?.loadUrl("https://example.com")
webViewRef.view?.loadHtml("<h1>Hello</h1>")
webViewRef.view?.evaluateJavaScript("document.title") { result -> }
webViewRef.view?.goBack()
webViewRef.view?.goForward()
webViewRef.view?.reload()
webViewRef.view?.canGoBack { canGoBack -> }
webViewRef.view?.sendMessageToJS("eventName", """{"key":"value"}""")
```

### 4. JSBridge 通信

JS 端通过 `window.KuiklyBridge` 与 Native 通信：

```javascript
// JS 调用 Native
window.KuiklyBridge.callNative('methodName', { key: 'value' })
  .then(result => console.log(result))
  .catch(err => console.error(err));

// JS 监听 Native 事件
window.KuiklyBridge.registerHandler('eventName', function(params) {
  console.log('收到:', params);
});
```

## API 参考

### 属性 (attr)

| 属性 | 类型 | 默认值 | 说明 |
|---|---|---|---|
| `src` | `string` | `''` | 加载的 URL |
| `htmlContent` | `string` | `''` | 直接加载 HTML 字符串 |
| `javaScriptEnabled` | `boolean` | `true` | 是否启用 JavaScript |
| `domStorageEnabled` | `boolean` | `true` | 是否启用 DOM Storage |
| `userAgent` | `string` | `''` | 自定义 User-Agent |
| `allowsInlineMediaPlayback` | `boolean` | `true` | 是否允许内联媒体播放 |

### 事件 (event)

| 事件 | 回调参数 | 说明 |
|---|---|---|
| `onPageStarted` | `url: string` | 页面开始加载 |
| `onPageFinished` | `url: string` | 页面加载完成 |
| `onError` | `errorCode: number, description: string` | 加载错误 |
| `onReceiveTitle` | `title: string` | 收到页面标题 |
| `onProgressChanged` | `progress: number (0-100)` | 加载进度变化 |
| `onMessage` | `message: string` | 收到 JS 发来的消息 |

### 命令式方法

| 方法 | 说明 |
|---|---|
| `loadUrl(url)` | 加载指定 URL |
| `loadHtml(html)` | 加载 HTML 字符串 |
| `evaluateJavaScript(script, callback)` | 执行 JS 代码 |
| `goBack()` | 后退 |
| `goForward()` | 前进 |
| `reload()` | 重新加载 |
| `canGoBack(callback)` | 查询是否可后退 |
| `canGoForward(callback)` | 查询是否可前进 |

## 适配平台

- HarmonyOS NEXT
- 设备类型：default、tablet

## 许可证

[MIT](./LICENSE)
