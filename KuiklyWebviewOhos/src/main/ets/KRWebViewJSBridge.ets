import { webview } from '@kit.ArkWeb';

/**
 * 预缓存的完整 JSBridge 注入脚本（BRIDGE_JS_CODE + OHOS_POST_MESSAGE_JS 合并）
 * 避免每次注入时拼接字符串
 */
const CACHED_BRIDGE_SCRIPT: string =
  `(function() {
    if (window.KuiklyBridge) return;

    var _callId = 0;
    var _callbacks = {};
    var _handlers = {};
    var CALLBACK_TIMEOUT = 30000;

    function cleanupCallback(id) {
        if (_callbacks[id]) {
            _callbacks[id].reject(new Error('Bridge call timeout'));
            delete _callbacks[id];
        }
    }

    window.KuiklyBridge = {
        callNative: function(method, params) {
            return new Promise(function(resolve, reject) {
                var id = 'cb_' + (_callId++);
                _callbacks[id] = { resolve: resolve, reject: reject };
                var timer = setTimeout(function() { cleanupCallback(id); }, CALLBACK_TIMEOUT);
                _callbacks[id]._timer = timer;
                var message = JSON.stringify({
                    type: 'call',
                    callId: id,
                    method: method,
                    params: params || {}
                });
                window.KuiklyBridge._postMessage(message);
            });
        },

        _onNativeResponse: function(callId, result, error) {
            var cb = _callbacks[callId];
            if (cb) {
                if (cb._timer) clearTimeout(cb._timer);
                if (error) {
                    cb.reject(new Error(error));
                } else {
                    cb.resolve(result);
                }
                delete _callbacks[callId];
            }
        },

        _onNativeEvent: function(data) {
            var eventName = data.method || 'message';
            var handler = _handlers[eventName];
            if (handler) {
                handler(data.params);
            }
        },

        registerHandler: function(name, handler) {
            _handlers[name] = handler;
        },

        _postMessage: function(message) {
            window.KuiklyNativeHandler.postMessage(message);
        }
    };
})();`;

/**
 * Bridge 回调接口
 */
export interface BridgeCallback {
  resolve: (result: string | null) => void;
  reject: (error: string) => void;
}

/**
 * Native 方法处理器类型
 */
export type NativeMethodHandler = (params: Record<string, Object>, callback: BridgeCallback) => void;

/**
 * javaScriptProxy 暴露给 JS 的对象接口
 */
export interface JSProxyObject {
  postMessage: (message: string) => void;
}

/**
 * KRWebView JSBridge OHOS 实现
 * 通过 javaScriptProxy 注入 KuiklyNativeHandler 对象
 */
export class KRWebViewJSBridge {
  private controller: webview.WebviewController;
  private onMessage: (message: string) => void;
  private nativeHandlers: Map<string, NativeMethodHandler> = new Map();

  constructor(controller: webview.WebviewController, onMessage: (message: string) => void) {
    this.controller = controller;
    this.onMessage = onMessage;
  }

  /**
   * 注册 Native 方法供 JS 调用
   */
  registerNativeHandler(name: string, handler: NativeMethodHandler): void {
    this.nativeHandlers.set(name, handler);
  }

  /**
   * 注入 JSBridge 脚本（使用预缓存的完整脚本）
   */
  injectBridgeScript(): void {
    try {
      this.controller.runJavaScript(CACHED_BRIDGE_SCRIPT);
    } catch (e) {
      console.error('KRWebViewJSBridge: Failed to inject bridge script', JSON.stringify(e));
    }
  }

  /**
   * JS 端调用此方法发送消息到 Native
   * 此方法会作为 javaScriptProxy 暴露给 JS
   */
  postMessage(message: string): void {
    this.handleMessage(message);
  }

  /**
   * 发送事件到 JS 端
   */
  sendEventToJS(method: string, params: string): void {
    const escapedMethod = this.escapeJSString(method);
    const script = `window.KuiklyBridge._onNativeEvent({method:'${escapedMethod}',params:${params}});`;
    try {
      this.controller.runJavaScript(script);
    } catch (e) {
      console.error('KRWebViewJSBridge: Failed to send event', JSON.stringify(e));
    }
  }

  /**
   * 获取 javaScriptProxy 对象定义
   * 用于 Web 组件的 javaScriptAccess 配置
   */
  getJavaScriptProxyObject(): JSProxyObject {
    const self = this;
    const proxyObj: JSProxyObject = {
      postMessage: (message: string) => {
        self.postMessage(message);
      }
    };
    return proxyObj;
  }

  dispose(): void {
    this.nativeHandlers.clear();
  }

  private handleMessage(message: string): void {
    try {
      const json: Record<string, Object> = JSON.parse(message) as Record<string, Object>;
      const type = json['type'] as string;

      if (type === 'call') {
        this.handleNativeCall(json);
      } else {
        this.onMessage(message);
      }
    } catch (e) {
      this.onMessage(message);
    }
  }

  private handleNativeCall(json: Record<string, Object>): void {
    const callId = (json['callId'] as string) || '';
    const method = (json['method'] as string) || '';
    const params = (json['params'] as Record<string, Object>) || {};

    const handler = this.nativeHandlers.get(method);
    if (handler) {
      const callback: BridgeCallback = {
        resolve: (result: string | null) => {
          const script = this.buildResponseScript(callId, result, null);
          try {
            this.controller.runJavaScript(script);
          } catch (e) {
            console.error('KRWebViewJSBridge: Failed to send response', JSON.stringify(e));
          }
        },
        reject: (error: string) => {
          const script = this.buildResponseScript(callId, null, error);
          try {
            this.controller.runJavaScript(script);
          } catch (e) {
            console.error('KRWebViewJSBridge: Failed to send error', JSON.stringify(e));
          }
        }
      };

      try {
        handler(params, callback);
      } catch (e) {
        const errorMsg = e instanceof Error ? e.message : 'Unknown error';
        callback.reject(errorMsg);
      }
    } else {
      const script = this.buildResponseScript(callId, null, `Method '${method}' not registered`);
      try {
        this.controller.runJavaScript(script);
      } catch (e) {
        console.error('KRWebViewJSBridge: Failed to send error response', JSON.stringify(e));
      }
    }
  }

  private buildResponseScript(callId: string, result: string | null, error: string | null): string {
    const resultStr = result !== null ? result : 'null';
    const errorStr = error !== null ? `'${this.escapeJSString(error)}'` : 'null';
    return `window.KuiklyBridge._onNativeResponse('${this.escapeJSString(callId)}',${resultStr},${errorStr});`;
  }

  /**
   * 单次遍历转义 JS 字符串（O(n) 复杂度）
   * 替代 10 次正则 replace 链式调用，避免产生 10 个中间 String 对象
   */
  private escapeJSString(str: string): string {
    const len = str.length;
    if (len === 0) return str;

    const parts: string[] = [];
    let i = 0;
    let lastIndex = 0;

    while (i < len) {
      const c = str.charCodeAt(i);
      let replacement: string | null = null;

      switch (c) {
        case 0x5C: replacement = '\\\\'; break;     // backslash
        case 0x27: replacement = "\\'"; break;       // single quote
        case 0x22: replacement = '\\"'; break;       // double quote
        case 0x0A: replacement = '\\n'; break;       // newline
        case 0x0D: replacement = '\\r'; break;       // carriage return
        case 0x09: replacement = '\\t'; break;       // tab
        case 0x2028: replacement = '\\u2028'; break; // line separator
        case 0x2029: replacement = '\\u2029'; break; // paragraph separator
        case 0x00: replacement = '\\0'; break;       // null
        case 0x3C: { // '<'
          // 检测 </script>（不区分大小写）
          if (i + 8 < len && str[i + 1] === '/' &&
              str.substring(i, i + 9).toLowerCase() === '</script>') {
            if (i > lastIndex) {
              parts.push(str.substring(lastIndex, i));
            }
            parts.push('<\\/script>');
            i += 9;
            lastIndex = i;
            continue;
          }
          break;
        }
        default: break;
      }

      if (replacement !== null) {
        if (i > lastIndex) {
          parts.push(str.substring(lastIndex, i));
        }
        parts.push(replacement);
        lastIndex = i + 1;
      }
      i++;
    }

    // 无需转义时直接返回原字符串，零分配
    if (lastIndex === 0) return str;

    if (lastIndex < len) {
      parts.push(str.substring(lastIndex));
    }
    return parts.join('');
  }
}
