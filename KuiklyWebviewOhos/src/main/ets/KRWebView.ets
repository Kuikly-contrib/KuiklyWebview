import { KRAny, KuiklyRenderBaseView, KuiklyRenderCallback } from '@kuikly-open/render';
import { ComponentContent } from '@kit.ArkUI';
import { webview } from '@kit.ArkWeb';
import { KRWebViewJSBridge, JSProxyObject } from './KRWebViewJSBridge';

/**
 * javaScriptProxy 配置接口
 */
interface JavaScriptProxyConfig {
  object: JSProxyObject;
  name: string;
  methodList: Array<string>;
  controller: webview.WebviewController;
}

// 默认的空 proxy 对象
const defaultProxyObject: JSProxyObject = {
  postMessage: (_msg: string) => {}
};

/**
 * KRWebView - OHOS 端 WebView 渲染组件
 * 继承 KuiklyRenderBaseView，将鸿蒙 Web 组件暴露给 Kuikly 框架
 */
@Observed
export class KRWebView extends KuiklyRenderBaseView {
  static readonly VIEW_NAME = 'KRWebView';

  // Web 组件控制器
  controller: webview.WebviewController = new webview.WebviewController();

  // JSBridge 实例
  jsBridge: KRWebViewJSBridge | null = null;

  // 属性
  cssSrc: string = '';
  cssHtmlContent: string = '';
  cssJavaScriptEnabled: boolean = true;
  cssUserAgent: string = '';
  cssDomStorageEnabled: boolean = true;

  // URL 拦截规则
  cssUrlInterceptSchemes: Set<string> = new Set();
  // host 规则拆分：精确匹配 + 通配符后缀（已去掉前缀 `*.`）
  cssUrlInterceptHostsExact: Set<string> = new Set();
  cssUrlInterceptHostsSuffix: Array<string> = [];
  cssReportAllNavigation: boolean = false;
  // 是否允许组件对未命中规则的非标准 scheme 自动 cancel（默认即 cancel，OHOS 不额外唤起）；
  // 该字段目前仅用于对齐 API（OHOS Web 组件本身不具备直接唤起第三方应用的能力），
  // 行为差异详见 README。
  cssAutoOpenExternalScheme: boolean = false;

  // 事件回调
  onPageStartedCallback: KuiklyRenderCallback | null = null;
  onPageFinishedCallback: KuiklyRenderCallback | null = null;
  onErrorCallback: KuiklyRenderCallback | null = null;
  onReceiveTitleCallback: KuiklyRenderCallback | null = null;
  onProgressChangedCallback: KuiklyRenderCallback | null = null;
  onMessageCallback: KuiklyRenderCallback | null = null;
  onShouldOverrideUrlLoadingCallback: KuiklyRenderCallback | null = null;

  // 是否已初始化 controller
  private isControllerInitialized: boolean = false;

  // 缓存的 javaScriptProxy 配置（避免重复创建）
  cachedProxyConfig: JavaScriptProxyConfig | null = null;

  // javaScriptProxy 暴露给 JS 的方法列表
  static readonly PROXY_METHOD_LIST: Array<string> = ['postMessage'];

  initJSBridge(): void {
    if (!this.jsBridge) {
      this.jsBridge = new KRWebViewJSBridge(this.controller, (message: string) => {
        if (this.onMessageCallback) {
          this.onMessageCallback({ 'message': message });
        }
      });
      // 注册内部 SPA 路由 hook handler：JS 端 hook history.pushState 等通过此 method 上抛，
      // 转化为 onShouldOverrideUrlLoading 事件，避免穿透到 onMessage / 业务 handler
      this.jsBridge.registerNativeHandler('__kuiklyNavIntercept', (params, callback) => {
        const url = (params['url'] as string) || '';
        const source = (params['source'] as string) || 'navigation';
        const isMainFrame = (params['isMainFrame'] as boolean) ?? true;
        this.notifyShouldOverride(url, isMainFrame, source);
        callback.resolve(null);
      });
    }
  }

  /**
   * 触发 onShouldOverrideUrlLoading 事件（OHOS 内部 helper）
   */
  notifyShouldOverride(url: string, isMainFrame: boolean, source: string): void {
    if (this.onShouldOverrideUrlLoadingCallback) {
      this.onShouldOverrideUrlLoadingCallback({
        'url': url,
        'isMainFrame': isMainFrame,
        'source': source
      });
    }
  }

  /**
   * 解析逗号分隔的字符串为小写 Set
   */
  private parseCsvSet(csv: string): Set<string> {
    const result = new Set<string>();
    if (!csv || csv.length === 0) return result;
    const parts = csv.split(',');
    for (const raw of parts) {
      const trimmed = raw.trim().toLowerCase();
      if (trimmed.length > 0) {
        result.add(trimmed);
      }
    }
    return result;
  }

  /**
   * 解析 host 规则 CSV，分别填充到传入的精确集合与通配后缀数组中。
   * - `example.com`     -> exactOut
   * - `*.example.com`   -> suffixOut，存为 `example.com`（去掉 `*.`）
   *
   * 用 out 参数而不是返回元组 / 对象，是为了规避 ArkTS 不支持解构声明的限制。
   */
  private parseHostRules(csv: string, exactOut: Set<string>, suffixOut: Array<string>): void {
    exactOut.clear();
    suffixOut.length = 0;
    if (!csv || csv.length === 0) return;
    const parts = csv.split(',');
    for (const raw of parts) {
      const trimmed = raw.trim().toLowerCase();
      if (trimmed.length === 0) continue;
      if (trimmed.startsWith('*.') && trimmed.length > 2) {
        suffixOut.push(trimmed.substring(2));
      } else {
        exactOut.add(trimmed);
      }
    }
  }

  /**
   * host 匹配：
   * 1. 精确匹配：host == rule
   * 2. 通配符匹配：rule = `*.foo.com` 可匹配 `foo.com` 自身与任意子域
   */
  matchHost(host: string): boolean {
    if (this.cssUrlInterceptHostsExact.has(host)) return true;
    if (this.cssUrlInterceptHostsSuffix.length === 0) return false;
    const hostLen = host.length;
    for (const suffix of this.cssUrlInterceptHostsSuffix) {
      if (host === suffix) return true;
      const suffixLen = suffix.length;
      if (hostLen > suffixLen + 1 &&
        host.endsWith(suffix) &&
        host.charAt(hostLen - suffixLen - 1) === '.') {
        return true;
      }
    }
    return false;
  }

  /**
   * 从 URL 字符串里解析出 (scheme, host)，均返回小写、去端口；
   * host 部分会剥离 `user:pass@` 这样的 userinfo，避免把凭据误当作 host。
   *
   * 示例：
   * - `https://u:p@m.example.com:8080/a` → `["https", "m.example.com"]`
   * - `weixin://...`                      → `["weixin", ""]`
   * - `mailto:foo@bar.com`                → `["mailto", ""]`（无 `://`）
   */
  parseSchemeAndHost(url: string): [string, string] {
    const schemeEnd = url.indexOf(':');
    const scheme = schemeEnd > 0 ? url.substring(0, schemeEnd).toLowerCase() : '';
    let host = '';
    const hostStart = url.indexOf('://');
    if (hostStart > 0) {
      let authority = url.substring(hostStart + 3);
      const authorityEnd = authority.search(/[\/\?#]/);
      if (authorityEnd >= 0) {
        authority = authority.substring(0, authorityEnd);
      }
      // 剥离 userinfo：`user:pass@host:port` → `host:port`
      const atIdx = authority.lastIndexOf('@');
      if (atIdx >= 0) {
        authority = authority.substring(atIdx + 1);
      }
      // 去端口
      const portIdx = authority.indexOf(':');
      host = (portIdx > 0 ? authority.substring(0, portIdx) : authority).toLowerCase();
    }
    return [scheme, host];
  }

  createArkUIView(): ComponentContent<KuiklyRenderBaseView> {
    const uiContext = this.getUIContext() as UIContext;
    return new ComponentContent<KuiklyRenderBaseView>(
      uiContext,
      wrapBuilder<[KuiklyRenderBaseView]>(createKRWebView),
      this
    );
  }

  setProp(propKey: string, propValue: KRAny | KuiklyRenderCallback): boolean {
    switch (propKey) {
      case 'src':
        this.cssSrc = propValue as string;
        if (this.isControllerInitialized && this.cssSrc.length > 0) {
          try {
            this.controller.loadUrl(this.cssSrc);
          } catch (e) {
            console.error('KRWebView: Failed to load URL', JSON.stringify(e));
          }
        }
        return true;

      case 'htmlContent':
        this.cssHtmlContent = propValue as string;
        if (this.isControllerInitialized && this.cssHtmlContent.length > 0) {
          try {
            this.controller.loadData(this.cssHtmlContent, 'text/html', 'UTF-8');
          } catch (e) {
            console.error('KRWebView: Failed to load HTML', JSON.stringify(e));
          }
        }
        return true;

      case 'javaScriptEnabled':
        this.cssJavaScriptEnabled = (propValue as string) !== 'false';
        return true;

      case 'userAgent':
        this.cssUserAgent = propValue as string;
        if (this.isControllerInitialized && this.cssUserAgent.length > 0) {
          try {
            this.controller.setCustomUserAgent(this.cssUserAgent);
          } catch (e) {
            console.error('KRWebView: Failed to set user agent', JSON.stringify(e));
          }
        }
        return true;

      case 'domStorageEnabled':
        this.cssDomStorageEnabled = (propValue as string) !== 'false';
        return true;

      case 'allowsInlineMediaPlayback':
        // OHOS Web 组件默认支持内联播放
        return true;

      case 'urlInterceptSchemes':
        this.cssUrlInterceptSchemes = this.parseCsvSet(propValue as string);
        return true;

      case 'urlInterceptHosts':
        // 每次 setProp 都会用新规则覆盖旧规则，天然支持运行时动态更新
        this.parseHostRules(
          propValue as string,
          this.cssUrlInterceptHostsExact,
          this.cssUrlInterceptHostsSuffix
        );
        return true;

      case 'reportAllNavigation':
        this.cssReportAllNavigation = (propValue as string) === 'true';
        return true;

      case 'autoOpenExternalScheme':
        this.cssAutoOpenExternalScheme = (propValue as string) === 'true';
        return true;

      // 事件回调
      case 'onPageStarted':
        this.onPageStartedCallback = propValue as KuiklyRenderCallback;
        return true;

      case 'onPageFinished':
        this.onPageFinishedCallback = propValue as KuiklyRenderCallback;
        return true;

      case 'onError':
        this.onErrorCallback = propValue as KuiklyRenderCallback;
        return true;

      case 'onReceiveTitle':
        this.onReceiveTitleCallback = propValue as KuiklyRenderCallback;
        return true;

      case 'onProgressChanged':
        this.onProgressChangedCallback = propValue as KuiklyRenderCallback;
        return true;

      case 'onMessage':
        this.onMessageCallback = propValue as KuiklyRenderCallback;
        return true;

      case 'onShouldOverrideUrlLoading':
        this.onShouldOverrideUrlLoadingCallback = propValue as KuiklyRenderCallback;
        return true;

      default:
        return super.setProp(propKey, propValue);
    }
  }

  call(method: string, params: KRAny, callback: KuiklyRenderCallback | null): KRAny {
    switch (method) {
      case 'loadUrl': {
        const url = params as string;
        if (url && this.isControllerInitialized) {
          try {
            this.controller.loadUrl(url);
          } catch (e) {
            console.error('KRWebView: Failed to load URL', JSON.stringify(e));
          }
        }
        return null;
      }

      case 'loadHtml': {
        const paramsStr = params as string;
        if (paramsStr && this.isControllerInitialized) {
          try {
            const json: Record<string, string> = JSON.parse(paramsStr) as Record<string, string>;
            const html = json['html'] || '';
            this.controller.loadData(html, 'text/html', 'UTF-8');
          } catch (e) {
            try {
              this.controller.loadData(paramsStr, 'text/html', 'UTF-8');
            } catch (e2) {
              console.error('KRWebView: Failed to load HTML', JSON.stringify(e2));
            }
          }
        }
        return null;
      }

      case 'evaluateJavaScript': {
        const script = params as string;
        if (script && this.isControllerInitialized) {
          try {
            this.controller.runJavaScript(script)
              .then((result) => {
                if (callback) {
                  // 用对象包一层，避免 Kuikly 桥接对"看起来像 JSON 对象的纯字符串"
                  // 自动尝试反序列化导致 JSONException。对端在 KuiklyWebView.evaluateJavaScript
                  // 里会取出 result 字段后再回调业务。
                  callback({ result: result || '' });
                }
              })
              .catch((error: Error) => {
                if (callback) {
                  callback({ result: '' });
                }
              });
          } catch (e) {
            console.error('KRWebView: Failed to evaluate JS', JSON.stringify(e));
          }
        }
        return null;
      }

      case 'goBack': {
        if (this.isControllerInitialized) {
          try {
            if (this.controller.accessBackward()) {
              this.controller.backward();
            }
          } catch (e) {
            console.error('KRWebView: Failed to go back', JSON.stringify(e));
          }
        }
        return null;
      }

      case 'goForward': {
        if (this.isControllerInitialized) {
          try {
            if (this.controller.accessForward()) {
              this.controller.forward();
            }
          } catch (e) {
            console.error('KRWebView: Failed to go forward', JSON.stringify(e));
          }
        }
        return null;
      }

      case 'reload': {
        if (this.isControllerInitialized) {
          try {
            this.controller.refresh();
          } catch (e) {
            console.error('KRWebView: Failed to reload', JSON.stringify(e));
          }
        }
        return null;
      }

      case 'stopLoading': {
        if (this.isControllerInitialized) {
          try {
            this.controller.stop();
          } catch (e) {
            console.error('KRWebView: Failed to stop loading', JSON.stringify(e));
          }
        }
        return null;
      }

      case 'canGoBack': {
        if (callback && this.isControllerInitialized) {
          try {
            callback({ result: this.controller.accessBackward() });
          } catch (e) {
            callback({ result: false });
          }
        }
        return null;
      }

      case 'canGoForward': {
        if (callback && this.isControllerInitialized) {
          try {
            callback({ result: this.controller.accessForward() });
          } catch (e) {
            callback({ result: false });
          }
        }
        return null;
      }

      default: {
        return null;
      }
    }
  }

  /**
   * 组件销毁时清理资源
   */
  dispose(): void {
    this.jsBridge?.dispose();
    this.jsBridge = null;
    this.cachedProxyConfig = null as JavaScriptProxyConfig | null;
    this.onPageStartedCallback = null;
    this.onPageFinishedCallback = null;
    this.onErrorCallback = null;
    this.onReceiveTitleCallback = null;
    this.onProgressChangedCallback = null;
    this.onMessageCallback = null;
    this.onShouldOverrideUrlLoadingCallback = null;
  }

  /**
   * 获取 javaScriptProxy 配置对象（缓存，避免重复创建）
   */
  getJavaScriptProxyConfig(): JavaScriptProxyConfig {
    if (this.cachedProxyConfig === null) {
      this.cachedProxyConfig = {
        object: this.jsBridge?.getJavaScriptProxyObject() ?? defaultProxyObject,
        name: 'KuiklyNativeHandler',
        methodList: KRWebView.PROXY_METHOD_LIST,
        controller: this.controller
      };
    }
    return this.cachedProxyConfig;
  }

  /**
   * Web 组件 onControllerAttached 回调中调用
   * 标记 controller 已就绪，处理延迟的 URL/HTML 加载
   */
  onWebControllerReady(): void {
    this.isControllerInitialized = true;
    this.initJSBridge();

    // 处理在 controller 就绪前设置的属性
    if (this.cssUserAgent.length > 0) {
      try {
        this.controller.setCustomUserAgent(this.cssUserAgent);
      } catch (e) {
        console.error('KRWebView: Failed to set user agent', JSON.stringify(e));
      }
    }
    if (this.cssSrc.length > 0) {
      try {
        this.controller.loadUrl(this.cssSrc);
      } catch (e) {
        console.error('KRWebView: Failed to load URL', JSON.stringify(e));
      }
    } else if (this.cssHtmlContent.length > 0) {
      try {
        this.controller.loadData(this.cssHtmlContent, 'text/html', 'UTF-8');
      } catch (e) {
        console.error('KRWebView: Failed to load HTML', JSON.stringify(e));
      }
    }
  }
}

@Builder
function createKRWebView(view: KuiklyRenderBaseView) {
  KRWebViewComponent({ renderView: view as KRWebView })
}

@Component
struct KRWebViewComponent {
  @ObjectLink renderView: KRWebView;

  build() {
    Web({
      src: '',
      controller: this.renderView.controller
    })
      .javaScriptAccess(this.renderView.cssJavaScriptEnabled)
      .domStorageAccess(this.renderView.cssDomStorageEnabled)
      .javaScriptProxy(this.renderView.getJavaScriptProxyConfig())
      .onControllerAttached(() => {
        this.renderView.onWebControllerReady();
      })
      .onLoadIntercept((event) => {
        // 同步拦截：基于 attr 下发的规则匹配，命中则 cancel 加载并上抛事件
        // 不命中或事件回调缺失则放行
        const req = event?.data;
        if (!req) return false;
        let url = '';
        try {
          url = req.getRequestUrl() ?? '';
        } catch (e) {
          return false;
        }
        const isMainFrame = req.isMainFrame ? req.isMainFrame() : true;
        if (!url || url.length === 0) return false;

        const parsed = this.renderView.parseSchemeAndHost(url);
        const scheme = parsed[0];
        const host = parsed[1];

        const schemeHit = scheme.length > 0 && this.renderView.cssUrlInterceptSchemes.has(scheme);
        const hostHit = isMainFrame && host.length > 0 && this.renderView.matchHost(host);
        if (schemeHit || hostHit) {
          this.renderView.notifyShouldOverride(url, isMainFrame, 'navigation');
          return true;
        }

        // 标准 scheme 放行；按需上抛感知事件
        const isStandard = scheme === 'http' || scheme === 'https' || scheme === 'about' || scheme === 'file';
        if (isStandard) {
          if (this.renderView.cssReportAllNavigation && isMainFrame) {
            this.renderView.notifyShouldOverride(url, isMainFrame, 'navigation');
          }
          return false;
        }

        // 非标准 scheme 且未命中规则：上抛事件 + cancel。OHOS Web 组件本身不具备唤起第三方应用
        // 的能力，业务需在 onShouldOverrideUrlLoading 里自行调用原生桥实现唤起。
        this.renderView.notifyShouldOverride(url, isMainFrame, 'navigation');
        return true;
      })
      .onPageBegin((event) => {
        if (this.renderView.onPageStartedCallback) {
          this.renderView.onPageStartedCallback({ 'url': event?.url ?? '' });
        }
      })
      .onPageEnd((event) => {
        // 页面加载完成后注入 JSBridge 脚本
        this.renderView.jsBridge?.injectBridgeScript();
        if (this.renderView.onPageFinishedCallback) {
          this.renderView.onPageFinishedCallback({ 'url': event?.url ?? '' });
        }
      })
      .onErrorReceive((event) => {
        if (this.renderView.onErrorCallback) {
          const error = event?.error;
          this.renderView.onErrorCallback({
            'errorCode': error?.getErrorCode() ?? -1,
            'description': error?.getErrorInfo() ?? 'Unknown error'
          });
        }
      })
      .onProgressChange((event) => {
        if (this.renderView.onProgressChangedCallback) {
          this.renderView.onProgressChangedCallback({ 'progress': event?.newProgress ?? 0 });
        }
      })
      .onTitleReceive((event) => {
        if (this.renderView.onReceiveTitleCallback) {
          this.renderView.onReceiveTitleCallback({ 'title': event?.title ?? '' });
        }
      })
      .width('100%')
      .height('100%')
  }
}
