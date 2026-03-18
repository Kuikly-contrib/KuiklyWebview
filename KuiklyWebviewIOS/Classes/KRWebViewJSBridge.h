#import <Foundation/Foundation.h>
#import <WebKit/WebKit.h>

NS_ASSUME_NONNULL_BEGIN

/**
 * Bridge 回调 Block
 * @param result 成功时的结果（JSON 字符串），失败时为 nil
 * @param error 失败时的错误信息，成功时为 nil
 */
typedef void(^KRBridgeResponseCallback)(NSString * _Nullable result, NSString * _Nullable error);

/**
 * Native 方法处理 Block
 * @param params 调用参数 (NSDictionary)
 * @param callback 回调函数
 */
typedef void(^KRNativeMethodHandler)(NSDictionary *params, KRBridgeResponseCallback callback);

/**
 * KRWebView JSBridge iOS 实现
 * 遵循 WKScriptMessageHandler 协议处理 JS 消息
 * 管理 Native 方法注册和 callId 到 callback 映射
 */
@interface KRWebViewJSBridge : NSObject <WKScriptMessageHandler>

/**
 * 初始化
 * @param webView 关联的 WKWebView
 * @param onMessage 通用消息回调（非 Bridge 协议消息）
 */
- (instancetype)initWithWebView:(WKWebView *)webView
                      onMessage:(void (^)(NSString *message))onMessage;

/**
 * 注册 Native 方法供 JS 调用
 * @param name 方法名
 * @param handler 处理 Block
 */
- (void)registerNativeHandler:(NSString *)name handler:(KRNativeMethodHandler)handler;

/**
 * 注入 JSBridge 脚本到 WebView
 */
- (void)injectBridgeScript;

/**
 * 发送事件到 JS 端
 * @param method 事件方法名
 * @param params JSON 参数字符串
 */
- (void)sendEventToJS:(NSString *)method params:(NSString *)params;

/**
 * 清理资源
 */
- (void)dispose;

@end

NS_ASSUME_NONNULL_END
