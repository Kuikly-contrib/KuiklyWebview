#import "KRWebViewJSBridge.h"

// JSBridge 协议常量 - 与 Kotlin 共享层 JSBridgeProtocol 一致
static NSString *const kBridgeName = @"KuiklyBridge";
static NSString *const kNativeHandlerName = @"KuiklyNativeHandler";
static NSString *const kKeyCallId = @"callId";
static NSString *const kKeyMethod = @"method";
static NSString *const kKeyParams = @"params";
static NSString *const kKeyType = @"type";
static NSString *const kTypeCall = @"call";

@interface KRWebViewJSBridge ()

@property (nonatomic, weak) WKWebView *webView;
@property (nonatomic, copy) void (^onMessage)(NSString *message);
@property (nonatomic, strong) NSMutableDictionary<NSString *, KRNativeMethodHandler> *nativeHandlers;

@end

// 预缓存拼接好的完整 Bridge 脚本，避免每次 injectBridgeScript 时拼接字符串
static NSString *_cachedFullBridgeScript = nil;

@implementation KRWebViewJSBridge

+ (NSString *)cachedFullBridgeScript {
    static dispatch_once_t onceToken;
    dispatch_once(&onceToken, ^{
        // 与 commonMain JSBridgeProtocol.BRIDGE_JS_CODE 保持一致
        // 含 KuiklyBridge 桥接定义 + SPA 路由 hook（pushState/replaceState/popstate/hashchange 上抛 __kuiklyNavIntercept）
        NSString *bridgeJS = @"(function(){if(window.KuiklyBridge)return;var _callId=0;var _callbacks={};var _handlers={};var CALLBACK_TIMEOUT=30000;function cleanupCallback(id){if(_callbacks[id]){_callbacks[id].reject(new Error('Bridge call timeout'));delete _callbacks[id];}}window.KuiklyBridge={callNative:function(method,params){return new Promise(function(resolve,reject){var id='cb_'+(_callId++);_callbacks[id]={resolve:resolve,reject:reject};var timer=setTimeout(function(){cleanupCallback(id);},CALLBACK_TIMEOUT);_callbacks[id]._timer=timer;var message=JSON.stringify({type:'call',callId:id,method:method,params:params||{}});window.KuiklyBridge._postMessage(message);});},_onNativeResponse:function(callId,result,error){var cb=_callbacks[callId];if(cb){if(cb._timer)clearTimeout(cb._timer);if(error){cb.reject(new Error(error));}else{cb.resolve(result);}delete _callbacks[callId];}},_onNativeEvent:function(data){var eventName=data.method||'message';var handler=_handlers[eventName];if(handler){handler(data.params);}},registerHandler:function(name,handler){_handlers[name]=handler;},_postMessage:function(message){}};function _reportNav(url,source){try{var msg=JSON.stringify({type:'call',callId:'',method:'__kuiklyNavIntercept',params:{url:url,source:source,isMainFrame:true}});window.KuiklyBridge._postMessage(msg);}catch(e){}}try{var _origPush=history.pushState;history.pushState=function(){var ret=_origPush.apply(this,arguments);_reportNav(location.href,'pushState');return ret;};var _origReplace=history.replaceState;history.replaceState=function(){var ret=_origReplace.apply(this,arguments);_reportNav(location.href,'replaceState');return ret;};window.addEventListener('hashchange',function(){_reportNav(location.href,'hashchange');});window.addEventListener('popstate',function(){_reportNav(location.href,'popstate');});}catch(e){}})();";
        NSString *iosPostMessageJS = @"(function(){window.KuiklyBridge._postMessage=function(message){window.webkit.messageHandlers.KuiklyNativeHandler.postMessage(message);};})();";
        _cachedFullBridgeScript = [NSString stringWithFormat:@"%@%@", bridgeJS, iosPostMessageJS];
    });
    return _cachedFullBridgeScript;
}

- (instancetype)initWithWebView:(WKWebView *)webView
                      onMessage:(void (^)(NSString *))onMessage {
    self = [super init];
    if (self) {
        _webView = webView;
        _onMessage = [onMessage copy];
        _nativeHandlers = [NSMutableDictionary dictionary];
    }
    return self;
}

#pragma mark - Public

- (void)registerNativeHandler:(NSString *)name handler:(KRNativeMethodHandler)handler {
    self.nativeHandlers[name] = [handler copy];
}

- (void)injectBridgeScript {
    // 使用预缓存的完整脚本，避免每次页面加载时拼接字符串
    [self.webView evaluateJavaScript:[KRWebViewJSBridge cachedFullBridgeScript] completionHandler:nil];
}

- (void)sendEventToJS:(NSString *)method params:(NSString *)params {
    NSString *escapedMethod = [self escapeJSString:method];
    NSString *script = [NSString stringWithFormat:
                        @"window.KuiklyBridge._onNativeEvent({method:'%@',params:%@});",
                        escapedMethod, params];
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.webView evaluateJavaScript:script completionHandler:nil];
    });
}

- (void)dispose {
    [self.nativeHandlers removeAllObjects];
    self.onMessage = nil;
}

#pragma mark - WKScriptMessageHandler

- (void)userContentController:(WKUserContentController *)userContentController
      didReceiveScriptMessage:(WKScriptMessage *)message {
    if (![message.name isEqualToString:kNativeHandlerName]) {
        return;
    }

    NSString *messageBody = nil;
    if ([message.body isKindOfClass:[NSString class]]) {
        messageBody = message.body;
    } else {
        return;
    }

    [self handleMessage:messageBody];
}

#pragma mark - Private

- (void)handleMessage:(NSString *)message {
    NSError *error = nil;
    NSData *data = [message dataUsingEncoding:NSUTF8StringEncoding];
    NSDictionary *json = [NSJSONSerialization JSONObjectWithData:data options:0 error:&error];

    if (error || !json) {
        if (self.onMessage) {
            self.onMessage(message);
        }
        return;
    }

    NSString *type = json[kKeyType];
    if ([type isEqualToString:kTypeCall]) {
        [self handleNativeCall:json];
    } else {
        if (self.onMessage) {
            self.onMessage(message);
        }
    }
}

- (void)handleNativeCall:(NSDictionary *)json {
    NSString *callId = json[kKeyCallId] ?: @"";
    NSString *method = json[kKeyMethod] ?: @"";
    NSDictionary *params = json[kKeyParams] ?: @{};

    KRNativeMethodHandler handler = self.nativeHandlers[method];
    if (handler) {
        __weak typeof(self) weakSelf = self;
        KRBridgeResponseCallback callback = ^(NSString *result, NSString *error) {
            __strong typeof(weakSelf) strongSelf = weakSelf;
            if (!strongSelf) return;

            NSString *script = [strongSelf buildResponseScript:callId result:result error:error];
            dispatch_async(dispatch_get_main_queue(), ^{
                [strongSelf.webView evaluateJavaScript:script completionHandler:nil];
            });
        };

        @try {
            handler(params, callback);
        } @catch (NSException *exception) {
            NSString *script = [self buildResponseScript:callId result:nil error:exception.reason];
            [self.webView evaluateJavaScript:script completionHandler:nil];
        }
    } else {
        NSString *errorMsg = [NSString stringWithFormat:@"Method '%@' not registered", method];
        NSString *script = [self buildResponseScript:callId result:nil error:errorMsg];
        [self.webView evaluateJavaScript:script completionHandler:nil];
    }
}

- (NSString *)buildResponseScript:(NSString *)callId
                           result:(NSString *)result
                            error:(NSString *)error {
    NSString *resultStr = result ?: @"null";
    NSString *errorStr = error ? [NSString stringWithFormat:@"'%@'", [self escapeJSString:error]] : @"null";
    return [NSString stringWithFormat:
            @"window.KuiklyBridge._onNativeResponse('%@',%@,%@);",
            [self escapeJSString:callId], resultStr, errorStr];
}

/**
 * 单次遍历转义 JS 字符串（O(n) 复杂度）
 * 替代 10 次 stringByReplacingOccurrencesOfString 链式调用，避免产生 10 个中间 NSString 对象
 */
- (NSString *)escapeJSString:(NSString *)str {
    NSUInteger len = str.length;
    if (len == 0) return str;
    
    NSMutableString *result = [NSMutableString stringWithCapacity:len + (len >> 3) + 16];
    unichar buffer[1024];
    NSUInteger offset = 0;
    
    while (offset < len) {
        NSUInteger chunkLen = MIN(sizeof(buffer) / sizeof(unichar), len - offset);
        [str getCharacters:buffer range:NSMakeRange(offset, chunkLen)];
        
        for (NSUInteger i = 0; i < chunkLen; i++) {
            unichar c = buffer[i];
            switch (c) {
                case '\\': [result appendString:@"\\\\"]; break;
                case '\'': [result appendString:@"\\'"]; break;
                case '"':  [result appendString:@"\\\""]; break;
                case '\n': [result appendString:@"\\n"]; break;
                case '\r': [result appendString:@"\\r"]; break;
                case '\t': [result appendString:@"\\t"]; break;
                case 0x2028: [result appendString:@"\\u2028"]; break;
                case 0x2029: [result appendString:@"\\u2029"]; break;
                case 0x0000: [result appendString:@"\\0"]; break;
                case '<': {
                    // 检测 </script>（不区分大小写）
                    NSUInteger absPos = offset + i;
                    if (absPos + 8 < len) {
                        NSRange range = NSMakeRange(absPos, 9);
                        NSString *substr = [str substringWithRange:range];
                        if ([substr caseInsensitiveCompare:@"</script>"] == NSOrderedSame) {
                            [result appendString:@"<\\/script>"];
                            i += 8; // 跳过 "/script>" 共 8 个字符
                            break;
                        }
                    }
                    [result appendFormat:@"%C", c];
                    break;
                }
                default:
                    [result appendFormat:@"%C", c];
                    break;
            }
        }
        offset += chunkLen;
    }
    return [result copy];
}

@end
