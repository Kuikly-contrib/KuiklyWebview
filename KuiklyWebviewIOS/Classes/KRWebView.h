#import <UIKit/UIKit.h>
#import <WebKit/WebKit.h>
#import <OpenKuiklyIOSRender/KuiklyRenderViewExportProtocol.h>

@class KRWebViewJSBridge;

NS_ASSUME_NONNULL_BEGIN

/**
 * KRWebView - iOS 端 WebView 渲染组件
 * 遵循 KuiklyRenderViewExportProtocol 协议，将 WKWebView 暴露给 Kuikly 框架
 *
 * 类名 "KRWebView" 与 Kotlin 侧 viewName() 返回值一致
 * iOS 端通过运行时自动发现，无需手动注册
 */
@interface KRWebView : UIView <KuiklyRenderViewExportProtocol, WKNavigationDelegate, WKUIDelegate>

/** 内部 WKWebView 实例 */
@property (nonatomic, strong, readonly) WKWebView *webView;

/** JSBridge 实例 */
@property (nonatomic, strong, readonly) KRWebViewJSBridge *jsBridge;

@end

NS_ASSUME_NONNULL_END
