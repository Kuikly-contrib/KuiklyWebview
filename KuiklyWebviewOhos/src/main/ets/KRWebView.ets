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

  // 事件回调
  onPageStartedCallback: KuiklyRenderCallback | null = null;
  onPageFinishedCallback: KuiklyRenderCallback | null = null;
  onErrorCallback: KuiklyRenderCallback | null = null;
  onReceiveTitleCallback: KuiklyRenderCallback | null = null;
  onProgressChangedCallback: KuiklyRenderCallback | null = null;
  onMessageCallback: KuiklyRenderCallback | null = null;

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
    }
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
                  callback(result || '');
                }
              })
              .catch((error: Error) => {
                if (callback) {
                  callback('');
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

      case 'canGoBack': {
        if (callback && this.isControllerInitialized) {
          try {
            callback(this.controller.accessBackward() ? 'true' : 'false');
          } catch (e) {
            callback('false');
          }
        }
        return null;
      }

      case 'canGoForward': {
        if (callback && this.isControllerInitialized) {
          try {
            callback(this.controller.accessForward() ? 'true' : 'false');
          } catch (e) {
            callback('false');
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
