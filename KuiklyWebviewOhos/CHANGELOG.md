# 更新日志

## [1.0.0] - 2026-04-08

### Added

- KRWebView 组件：将鸿蒙 Web 组件封装为 Kuikly 渲染视图
- 支持属性：src、htmlContent、javaScriptEnabled、domStorageEnabled、userAgent、allowsInlineMediaPlayback
- 支持事件：onPageStarted、onPageFinished、onError、onReceiveTitle、onProgressChanged、onMessage
- 命令式 API：loadUrl、loadHtml、evaluateJavaScript、goBack、goForward、reload、canGoBack、canGoForward
- KRWebViewJSBridge：内置 JSBridge，JS 端通过 window.KuiklyBridge 与 Native 双向通信
