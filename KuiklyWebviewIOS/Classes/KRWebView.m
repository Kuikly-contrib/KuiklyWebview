#import "KRWebView.h"
#import "KRWebViewJSBridge.h"
#import "KRComponentDefine.h"

static NSString *const kNativeHandlerName = @"KuiklyNativeHandler";
static NSString *const kInternalMethodNavIntercept = @"__kuiklyNavIntercept";

/**
 * 共享 WKProcessPool 单例
 * 多个 KRWebView 实例共享同一 Web 进程池，减少进程数量和内存开销
 * 同时实现实例间 Cookie/SessionStorage 共享
 */
static WKProcessPool *_sharedProcessPool = nil;
static dispatch_once_t _processPoolOnceToken;

@interface KRWebView ()

@property (nonatomic, strong, readwrite) WKWebView *webView;
@property (nonatomic, strong, readwrite) KRWebViewJSBridge *jsBridge;

// KVO 注册状态标记，避免 dealloc 中使用 @try/@catch（异常处理有栈展开开销）
@property (nonatomic, assign) BOOL isObservingProgress;
@property (nonatomic, assign) BOOL isObservingTitle;

// 事件回调
@property (nonatomic, copy, nullable) KuiklyRenderCallback css_onPageStarted;
@property (nonatomic, copy, nullable) KuiklyRenderCallback css_onPageFinished;
@property (nonatomic, copy, nullable) KuiklyRenderCallback css_onError;
@property (nonatomic, copy, nullable) KuiklyRenderCallback css_onReceiveTitle;
@property (nonatomic, copy, nullable) KuiklyRenderCallback css_onProgressChanged;
@property (nonatomic, copy, nullable) KuiklyRenderCallback css_onMessage;
@property (nonatomic, copy, nullable) KuiklyRenderCallback css_onShouldOverrideUrlLoading;

// 属性
@property (nonatomic, copy, nullable) NSString *css_src;
@property (nonatomic, copy, nullable) NSString *css_htmlContent;

// URL 拦截规则（同步决策）
@property (nonatomic, strong) NSSet<NSString *> *interceptSchemes;
// host 规则拆分：精确匹配 + 通配符后缀（已去掉前缀 `*.`）
@property (nonatomic, strong) NSSet<NSString *> *interceptHostsExact;
@property (nonatomic, strong) NSArray<NSString *> *interceptHostsSuffix;
@property (nonatomic, assign) BOOL reportAllNavigation;
// 是否允许组件自动 openURL 唤起外部 App（默认 NO）
@property (nonatomic, assign) BOOL autoOpenExternalScheme;

@end

@implementation KRWebView

@synthesize hr_rootView;

- (instancetype)init {
    self = [super init];
    if (self) {
        _interceptSchemes = [NSSet set];
        _interceptHostsExact = [NSSet set];
        _interceptHostsSuffix = @[];
        [self setupWebView];
    }
    return self;
}

- (void)dealloc {
    [self.jsBridge dispose];
    [self.webView.configuration.userContentController removeScriptMessageHandlerForName:kNativeHandlerName];
    // 使用布尔标记安全移除 KVO，避免 @try/@catch 的栈展开开销
    if (self.isObservingProgress) {
        [self.webView removeObserver:self forKeyPath:@"estimatedProgress"];
        self.isObservingProgress = NO;
    }
    if (self.isObservingTitle) {
        [self.webView removeObserver:self forKeyPath:@"title"];
        self.isObservingTitle = NO;
    }
    self.webView.navigationDelegate = nil;
    self.webView.UIDelegate = nil;
}

#pragma mark - Setup

- (void)setupWebView {
    WKWebViewConfiguration *config = [[WKWebViewConfiguration alloc] init];
    config.allowsInlineMediaPlayback = YES;
    config.mediaTypesRequiringUserActionForPlayback = WKAudiovisualMediaTypeNone;
    
    // 共享 WKProcessPool：减少进程数量和内存开销，实现 Cookie/SessionStorage 共享
    dispatch_once(&_processPoolOnceToken, ^{
        _sharedProcessPool = [[WKProcessPool alloc] init];
    });
    config.processPool = _sharedProcessPool;

    self.webView = [[WKWebView alloc] initWithFrame:self.bounds configuration:config];
    self.webView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    self.webView.navigationDelegate = self;
    self.webView.UIDelegate = self;
    [self addSubview:self.webView];

    // 初始化 JSBridge
    __weak typeof(self) weakSelf = self;
    self.jsBridge = [[KRWebViewJSBridge alloc] initWithWebView:self.webView
                                                     onMessage:^(NSString *message) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (strongSelf.css_onMessage) {
            strongSelf.css_onMessage(@{@"message": message ?: @""});
        }
    }];

    // 注册内部 SPA 路由 hook handler：JS 端 hook history.pushState 等通过此 method 上抛，
    // 转化为 onShouldOverrideUrlLoading 事件，避免穿透到 onMessage / 业务 handler
    [self.jsBridge registerNativeHandler:kInternalMethodNavIntercept
                                 handler:^(NSDictionary *params, KRBridgeResponseCallback callback) {
        __strong typeof(weakSelf) strongSelf = weakSelf;
        if (!strongSelf) {
            if (callback) callback(nil, nil);
            return;
        }
        NSString *url = params[@"url"] ?: @"";
        NSString *source = params[@"source"] ?: @"navigation";
        BOOL isMainFrame = params[@"isMainFrame"] ? [params[@"isMainFrame"] boolValue] : YES;
        [strongSelf notifyShouldOverrideUrlLoading:url isMainFrame:isMainFrame source:source];
        if (callback) callback(nil, nil);
    }];

    // 注册 ScriptMessageHandler
    [self.webView.configuration.userContentController addScriptMessageHandler:self.jsBridge
                                                                         name:kNativeHandlerName];

    // KVO 监听进度和标题（记录注册状态，dealloc 时安全移除）
    [self.webView addObserver:self forKeyPath:@"estimatedProgress" options:NSKeyValueObservingOptionNew context:nil];
    self.isObservingProgress = YES;
    [self.webView addObserver:self forKeyPath:@"title" options:NSKeyValueObservingOptionNew context:nil];
    self.isObservingTitle = YES;
}

#pragma mark - KuiklyRenderViewExportProtocol

- (void)hrv_setPropWithKey:(NSString *)propKey propValue:(id)propValue {
    // 设置通用样式（布局、背景色等）
    KUIKLY_SET_CSS_COMMON_PROP;
    // 自定义属性和事件通过 setCss_xxx: 方法自动分发（运行时匹配）
}

- (void)hrv_callWithMethod:(NSString *)method
                    params:(NSString *)params
                  callback:(KuiklyRenderCallback)callback {
    KUIKLY_CALL_CSS_METHOD;
}

#pragma mark - CSS Properties (由 KUIKLY_SET_CSS_COMMON_PROP 运行时分发)

- (void)setCss_src:(NSString *)css_src {
    if ([_css_src isEqualToString:css_src]) return;
    _css_src = css_src;
    if (css_src.length > 0) {
        NSURL *url = [NSURL URLWithString:css_src];
        if (url) {
            [self.webView loadRequest:[NSURLRequest requestWithURL:url]];
        }
    }
}

- (void)setCss_htmlContent:(NSString *)css_htmlContent {
    if ([_css_htmlContent isEqualToString:css_htmlContent]) return;
    _css_htmlContent = css_htmlContent;
    if (css_htmlContent.length > 0) {
        [self.webView loadHTMLString:css_htmlContent baseURL:nil];
    }
}

- (void)setCss_javaScriptEnabled:(NSString *)enabled {
    // WKWebView 默认启用 JS，iOS 14+ 无法禁用
    // 此属性保留以保持跨端 API 一致性
}

- (void)setCss_userAgent:(NSString *)userAgent {
    if (userAgent.length > 0) {
        self.webView.customUserAgent = userAgent;
    }
}

- (void)setCss_domStorageEnabled:(NSString *)enabled {
    // WKWebView 默认启用 DOM Storage，无需额外设置
}

- (void)setCss_allowsInlineMediaPlayback:(NSString *)allowed {
    // 已在初始化时配置，运行时不可更改
}

- (void)setCss_urlInterceptSchemes:(NSString *)csv {
    self.interceptSchemes = [self parseCsvSet:csv];
}

- (void)setCss_urlInterceptHosts:(NSString *)csv {
    // 拆分为精确匹配 + 通配符后缀两部分；每次 setter 调用都会完全覆盖旧规则，天然支持运行时动态更新
    NSMutableSet<NSString *> *exact = [NSMutableSet set];
    NSMutableArray<NSString *> *suffix = [NSMutableArray array];
    if (csv.length > 0) {
        NSArray<NSString *> *parts = [csv componentsSeparatedByString:@","];
        for (NSString *raw in parts) {
            NSString *trimmed = [[raw stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]] lowercaseString];
            if (trimmed.length == 0) continue;
            if ([trimmed hasPrefix:@"*."] && trimmed.length > 2) {
                [suffix addObject:[trimmed substringFromIndex:2]];
            } else {
                [exact addObject:trimmed];
            }
        }
    }
    self.interceptHostsExact = [exact copy];
    self.interceptHostsSuffix = [suffix copy];
}

- (void)setCss_reportAllNavigation:(NSString *)flag {
    self.reportAllNavigation = [flag isEqualToString:@"true"];
}

- (void)setCss_autoOpenExternalScheme:(NSString *)flag {
    self.autoOpenExternalScheme = [flag isEqualToString:@"true"];
}

#pragma mark - CSS Methods (由 KUIKLY_CALL_CSS_METHOD 运行时分发)

- (void)css_loadUrl:(NSDictionary *)args {
    NSString *params = args[KRC_PARAM_KEY];
    if (params.length > 0) {
        NSURL *url = [NSURL URLWithString:params];
        if (url) {
            [self.webView loadRequest:[NSURLRequest requestWithURL:url]];
        }
    }
}

- (void)css_loadHtml:(NSDictionary *)args {
    NSString *params = args[KRC_PARAM_KEY];
    if (params.length > 0) {
        NSError *error = nil;
        NSDictionary *json = [NSJSONSerialization JSONObjectWithData:[params dataUsingEncoding:NSUTF8StringEncoding]
                                                             options:0
                                                               error:&error];
        if (json) {
            NSString *html = json[@"html"] ?: @"";
            NSString *baseUrlStr = json[@"baseUrl"];
            NSURL *baseUrl = baseUrlStr.length > 0 ? [NSURL URLWithString:baseUrlStr] : nil;
            [self.webView loadHTMLString:html baseURL:baseUrl];
        } else {
            [self.webView loadHTMLString:params baseURL:nil];
        }
    }
}

- (void)css_evaluateJavaScript:(NSDictionary *)args {
    NSString *script = args[KRC_PARAM_KEY];
    KuiklyRenderCallback callback = args[KRC_CALLBACK_KEY];

    if (script.length > 0) {
        [self.webView evaluateJavaScript:script completionHandler:^(id result, NSError *error) {
            if (callback) {
                NSString *resultStr = nil;
                if (result) {
                    if ([result isKindOfClass:[NSString class]]) {
                        resultStr = result;
                    } else {
                        resultStr = [NSString stringWithFormat:@"%@", result];
                    }
                }
                callback(resultStr ?: @"");
            }
        }];
    }
}

- (void)css_goBack:(NSDictionary *)args {
    if (self.webView.canGoBack) {
        [self.webView goBack];
    }
}

- (void)css_goForward:(NSDictionary *)args {
    if (self.webView.canGoForward) {
        [self.webView goForward];
    }
}

- (void)css_reload:(NSDictionary *)args {
    [self.webView reload];
}

- (void)css_stopLoading:(NSDictionary *)args {
    [self.webView stopLoading];
}

- (void)css_canGoBack:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KRC_CALLBACK_KEY];
    if (callback) {
        callback(self.webView.canGoBack ? @"true" : @"false");
    }
}

- (void)css_canGoForward:(NSDictionary *)args {
    KuiklyRenderCallback callback = args[KRC_CALLBACK_KEY];
    if (callback) {
        callback(self.webView.canGoForward ? @"true" : @"false");
    }
}

#pragma mark - WKNavigationDelegate

/**
 * 拦截 URL 加载
 * 决策顺序：
 * 1. javascript / data / blob / vbscript：始终 cancel（安全防护）
 * 2. 命中 interceptSchemes：cancel + 上抛事件
 * 3. 标准 scheme（http/https/about/file）：默认 allow；命中 interceptHosts 则 cancel + 事件；
 *    reportAllNavigation 开启时不拦但上抛事件
 * 4. 其他自定义 scheme：上抛事件 + 尝试 system openURL，cancel
 */
- (void)webView:(WKWebView *)webView decidePolicyForNavigationAction:(WKNavigationAction *)navigationAction decisionHandler:(void (^)(WKNavigationActionPolicy))decisionHandler {
    NSURL *url = navigationAction.request.URL;
    NSString *scheme = url.scheme.lowercaseString ?: @"";
    BOOL isMainFrame = navigationAction.targetFrame ? navigationAction.targetFrame.isMainFrame : YES;
    NSString *urlStr = url.absoluteString ?: @"";

    // 安全防护：始终拦截
    if ([scheme isEqualToString:@"javascript"] ||
        [scheme isEqualToString:@"data"] ||
        [scheme isEqualToString:@"blob"] ||
        [scheme isEqualToString:@"vbscript"]) {
        decisionHandler(WKNavigationActionPolicyCancel);
        return;
    }

    // 命中自定义 scheme 黑名单：cancel + 上抛事件
    if (scheme.length > 0 && [self.interceptSchemes containsObject:scheme]) {
        [self notifyShouldOverrideUrlLoading:urlStr isMainFrame:isMainFrame source:@"navigation"];
        decisionHandler(WKNavigationActionPolicyCancel);
        return;
    }

    BOOL isStandard = [scheme isEqualToString:@"http"] ||
                      [scheme isEqualToString:@"https"] ||
                      [scheme isEqualToString:@"about"] ||
                      [scheme isEqualToString:@"file"];

    if (isStandard) {
        // 命中 host 黑名单（支持精确 + 通配符子域）：cancel + 上抛
        if (isMainFrame && (self.interceptHostsExact.count > 0 || self.interceptHostsSuffix.count > 0)) {
            NSString *host = url.host.lowercaseString;
            if (host.length > 0 && [self matchHost:host]) {
                [self notifyShouldOverrideUrlLoading:urlStr isMainFrame:isMainFrame source:@"navigation"];
                decisionHandler(WKNavigationActionPolicyCancel);
                return;
            }
        }
        // 仅感知不拦截
        if (self.reportAllNavigation && isMainFrame) {
            [self notifyShouldOverrideUrlLoading:urlStr isMainFrame:isMainFrame source:@"navigation"];
        }
        decisionHandler(WKNavigationActionPolicyAllow);
        return;
    }
    
    // 非标准 scheme（且未命中 interceptSchemes）：上抛事件 + cancel；
    // 仅当业务显式开启 autoOpenExternalScheme 时，组件才额外尝试通过系统 openURL 打开
    [self notifyShouldOverrideUrlLoading:urlStr isMainFrame:isMainFrame source:@"navigation"];
    if (self.autoOpenExternalScheme) {
        @try {
            if ([[UIApplication sharedApplication] canOpenURL:url]) {
                [[UIApplication sharedApplication] openURL:url options:@{} completionHandler:nil];
            }
        } @catch (NSException *exception) {
            // 静默忽略
        }
    }
    decisionHandler(WKNavigationActionPolicyCancel);
}

- (void)webView:(WKWebView *)webView didStartProvisionalNavigation:(WKNavigation *)navigation {
    if (self.css_onPageStarted) {
        self.css_onPageStarted(@{@"url": webView.URL.absoluteString ?: @""});
    }
}

/**
 * 302 重定向二次拦截：
 *
 * WKWebView 的 `decidePolicyForNavigationAction:` 在 302 跳转后并不会带上最新 URL 再触发一次，
 * 导致「原始 URL 未命中规则、但跳转后的 Location 命中」这种场景漏拦。
 * 这里在收到响应时再做一次 host 匹配：命中则 cancel，并补发 onShouldOverrideUrlLoading 事件。
 *
 * 只做 host 匹配（mainFrame + 标准 scheme）；scheme 类规则不在这里处理，
 * 因为 302 到非标准 scheme 的链路在 decidePolicyForNavigationAction 已经处理过。
 */
- (void)webView:(WKWebView *)webView
decidePolicyForNavigationResponse:(WKNavigationResponse *)navigationResponse
decisionHandler:(void (^)(WKNavigationResponsePolicy))decisionHandler {
    NSURL *url = navigationResponse.response.URL;
    NSString *scheme = url.scheme.lowercaseString ?: @"";
    BOOL isMainFrame = navigationResponse.isForMainFrame;
    BOOL isStandard = [scheme isEqualToString:@"http"] ||
                      [scheme isEqualToString:@"https"] ||
                      [scheme isEqualToString:@"about"] ||
                      [scheme isEqualToString:@"file"];
    if (isMainFrame && isStandard &&
        (self.interceptHostsExact.count > 0 || self.interceptHostsSuffix.count > 0)) {
        NSString *host = url.host.lowercaseString;
        if (host.length > 0 && [self matchHost:host]) {
            NSString *urlStr = url.absoluteString ?: @"";
            [self notifyShouldOverrideUrlLoading:urlStr isMainFrame:isMainFrame source:@"navigation"];
            decisionHandler(WKNavigationResponsePolicyCancel);
            return;
        }
    }
    decisionHandler(WKNavigationResponsePolicyAllow);
}

- (void)webView:(WKWebView *)webView didFinishNavigation:(WKNavigation *)navigation {
    // 页面加载完成后注入 JSBridge 脚本
    [self.jsBridge injectBridgeScript];

    if (self.css_onPageFinished) {
        self.css_onPageFinished(@{@"url": webView.URL.absoluteString ?: @""});
    }
}

- (void)webView:(WKWebView *)webView didFailProvisionalNavigation:(WKNavigation *)navigation
      withError:(NSError *)error {
    if (self.css_onError) {
        self.css_onError(@{
            @"errorCode": @(error.code),
            @"description": error.localizedDescription ?: @"Unknown error"
        });
    }
}

- (void)webView:(WKWebView *)webView didFailNavigation:(WKNavigation *)navigation
      withError:(NSError *)error {
    if (self.css_onError) {
        self.css_onError(@{
            @"errorCode": @(error.code),
            @"description": error.localizedDescription ?: @"Unknown error"
        });
    }
}

#pragma mark - WKUIDelegate

- (void)webView:(WKWebView *)webView
runJavaScriptAlertPanelWithMessage:(NSString *)message
initiatedByFrame:(WKFrameInfo *)frame
completionHandler:(void (^)(void))completionHandler {
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:nil
                                                                  message:message
                                                           preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        completionHandler();
    }]];

    UIViewController *vc = [self findViewController];
    if (vc) {
        [vc presentViewController:alert animated:YES completion:nil];
    } else {
        completionHandler();
    }
}

- (void)webView:(WKWebView *)webView
runJavaScriptConfirmPanelWithMessage:(NSString *)message
initiatedByFrame:(WKFrameInfo *)frame
completionHandler:(void (^)(BOOL))completionHandler {
    UIAlertController *alert = [UIAlertController alertControllerWithTitle:nil
                                                                  message:message
                                                           preferredStyle:UIAlertControllerStyleAlert];
    [alert addAction:[UIAlertAction actionWithTitle:@"Cancel" style:UIAlertActionStyleCancel handler:^(UIAlertAction *action) {
        completionHandler(NO);
    }]];
    [alert addAction:[UIAlertAction actionWithTitle:@"OK" style:UIAlertActionStyleDefault handler:^(UIAlertAction *action) {
        completionHandler(YES);
    }]];

    UIViewController *vc = [self findViewController];
    if (vc) {
        [vc presentViewController:alert animated:YES completion:nil];
    } else {
        completionHandler(NO);
    }
}

#pragma mark - KVO

- (void)observeValueForKeyPath:(NSString *)keyPath
                      ofObject:(id)object
                        change:(NSDictionary<NSKeyValueChangeKey,id> *)change
                       context:(void *)context {
    if ([keyPath isEqualToString:@"estimatedProgress"]) {
        if (self.css_onProgressChanged) {
            int progress = (int)(self.webView.estimatedProgress * 100);
            self.css_onProgressChanged(@{@"progress": @(progress)});
        }
    } else if ([keyPath isEqualToString:@"title"]) {
        if (self.css_onReceiveTitle) {
            self.css_onReceiveTitle(@{@"title": self.webView.title ?: @""});
        }
    }
}

#pragma mark - Helper

- (UIViewController *)findViewController {
    UIResponder *responder = self;
    while (responder) {
        if ([responder isKindOfClass:[UIViewController class]]) {
            return (UIViewController *)responder;
        }
        responder = [responder nextResponder];
    }
    return nil;
}

/**
 * 触发 onShouldOverrideUrlLoading 事件
 */
- (void)notifyShouldOverrideUrlLoading:(NSString *)url
                            isMainFrame:(BOOL)isMainFrame
                                 source:(NSString *)source {
    if (self.css_onShouldOverrideUrlLoading) {
        self.css_onShouldOverrideUrlLoading(@{
            @"url": url ?: @"",
            @"isMainFrame": @(isMainFrame),
            @"source": source ?: @"navigation"
        });
    }
}

/**
 * 解析逗号分隔的 CSV 字符串为小写、不重复的 NSSet
 */
- (NSSet<NSString *> *)parseCsvSet:(NSString *)csv {
    if (csv.length == 0) return [NSSet set];
    NSArray<NSString *> *parts = [csv componentsSeparatedByString:@","];
    NSMutableSet<NSString *> *result = [NSMutableSet setWithCapacity:parts.count];
    for (NSString *raw in parts) {
        NSString *trimmed = [[raw stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceCharacterSet]] lowercaseString];
        if (trimmed.length > 0) {
            [result addObject:trimmed];
        }
    }
    return [result copy];
}

/**
 * host 匹配：
 * 1. 精确匹配 (interceptHostsExact)：host == rule
 * 2. 通配符匹配 (interceptHostsSuffix)：rule = `*.foo.com` 可匹配 `foo.com` 自身与任意子域
 */
- (BOOL)matchHost:(NSString *)host {
    if ([self.interceptHostsExact containsObject:host]) return YES;
    if (self.interceptHostsSuffix.count == 0) return NO;
    NSUInteger hostLen = host.length;
    for (NSString *suffix in self.interceptHostsSuffix) {
        // 自身匹配：`*.foo.com` 也能匹配 `foo.com`
        if ([host isEqualToString:suffix]) return YES;
        NSUInteger suffixLen = suffix.length;
        if (hostLen > suffixLen + 1 &&
            [host hasSuffix:suffix] &&
            [host characterAtIndex:(hostLen - suffixLen - 1)] == '.') {
            return YES;
        }
    }
    return NO;
}

@end
