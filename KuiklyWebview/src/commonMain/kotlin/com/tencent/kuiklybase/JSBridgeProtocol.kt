package com.tencent.kuiklybase

/**
 * KuiklyWebView JSBridge 协议定义
 * 统一各端 JSBridge 的消息格式和 JS 注入脚本
 */
object JSBridgeProtocol {

    /** Bridge 对象名，JS 端通过 window.KuiklyBridge 访问 */
    const val BRIDGE_NAME = "KuiklyBridge"

    /** Native 端消息处理器名称 */
    const val NATIVE_HANDLER_NAME = "KuiklyNativeHandler"

    // ---- 消息 JSON Key ----
    const val KEY_CALL_ID = "callId"
    const val KEY_METHOD = "method"
    const val KEY_PARAMS = "params"
    const val KEY_RESULT = "result"
    const val KEY_ERROR = "error"
    const val KEY_TYPE = "type"

    // ---- 消息类型 ----
    /** JS 调用 Native 方法 */
    const val TYPE_CALL = "call"
    /** Native 返回结果给 JS */
    const val TYPE_RESPONSE = "response"
    /** Native 主动推送消息给 JS */
    const val TYPE_EVENT = "event"

    /**
     * 需要在 WebView 页面加载时注入的 JS 脚本
     * 定义 window.KuiklyBridge 对象，提供：
     * - callNative(method, params): Promise — JS 调用 Native 方法
     * - _onNativeResponse(callId, result, error) — Native 回调 Promise
     * - _onNativeEvent(data) — Native 主动推送事件
     * - registerHandler(name, handler) — JS 端注册消息处理器
     * - _callbacks 超时清理机制（30秒）
     */
    const val BRIDGE_JS_CODE = """
(function() {
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
            // 各平台实现不同，由原生端覆盖此方法
            // Android: KuiklyNativeHandler.postMessage(message)
            // iOS: webkit.messageHandlers.KuiklyNativeHandler.postMessage(message)
            // OHOS: KuiklyNativeHandler.postMessage(message)
        }
    };
})();
"""

    /**
     * Android 平台的 postMessage 桥接脚本
     * 通过 JavascriptInterface 传递消息
     */
    const val ANDROID_POST_MESSAGE_JS = """
(function() {
    window.KuiklyBridge._postMessage = function(message) {
        window.KuiklyNativeHandler.postMessage(message);
    };
})();
"""

    /**
     * iOS 平台的 postMessage 桥接脚本
     * 通过 WKScriptMessageHandler 传递消息
     */
    const val IOS_POST_MESSAGE_JS = """
(function() {
    window.KuiklyBridge._postMessage = function(message) {
        window.webkit.messageHandlers.KuiklyNativeHandler.postMessage(message);
    };
})();
"""

    /**
     * OHOS 平台的 postMessage 桥接脚本
     * 通过 javaScriptProxy 传递消息
     */
    const val OHOS_POST_MESSAGE_JS = """
(function() {
    window.KuiklyBridge._postMessage = function(message) {
        window.KuiklyNativeHandler.postMessage(message);
    };
})();
"""

    /**
     * 构建 Native 回调 JS 的调用脚本
     * @param callId 调用 ID
     * @param result 结果 JSON 字符串（可为 null）
     * @param error 错误信息（可为 null）
     */
    fun buildResponseScript(callId: String, result: String?, error: String?): String {
        val resultStr = result ?: "null"
        val errorStr = if (error != null) "'${escapeJsString(error)}'" else "null"
        return "window.KuiklyBridge._onNativeResponse('${escapeJsString(callId)}', $resultStr, $errorStr);"
    }

    /**
     * 构建 Native 主动推送事件的 JS 脚本
     * @param method 事件方法名
     * @param params 参数 JSON 字符串
     */
    fun buildEventScript(method: String, params: String): String {
        return "window.KuiklyBridge._onNativeEvent({method:'${escapeJsString(method)}',params:$params});"
    }

    /**
     * 转义 JS 字符串中的特殊字符（单次遍历，O(n) 时间复杂度）
     * 覆盖：反斜杠、引号、换行符、Unicode 行/段分隔符、null 字符、script 标签
     *
     * 性能优化：使用 StringBuilder 单次遍历替代 10 次 replace 链式调用，
     * 避免产生 10 个中间 String 对象，对大型字符串（HTML/JSON）效果显著
     */
    fun escapeJsString(str: String): String {
        val len = str.length
        // 预分配 StringBuilder 容量，略大于原字符串以容纳转义字符
        val sb = StringBuilder(len + (len shr 3) + 16)
        var i = 0
        while (i < len) {
            val c = str[i]
            when (c) {
                '\\' -> sb.append("\\\\")
                '\'' -> sb.append("\\'")
                '"' -> sb.append("\\\"")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                '\u2028' -> sb.append("\\u2028") // 行分隔符，JS 字符串字面量中非法
                '\u2029' -> sb.append("\\u2029") // 段分隔符，同上
                '\u0000' -> sb.append("\\0")     // null 字符
                '<' -> {
                    // 检测 </script> 标签（不区分大小写），防止 script 注入
                    if (i + 8 < len &&
                        str[i + 1] == '/' &&
                        str.regionMatches(i + 2, "script>", 0, 7, ignoreCase = true)
                    ) {
                        sb.append("<\\/script>")
                        i += 9 // 跳过 "</script>" 共 9 个字符
                        continue
                    }
                    sb.append(c)
                }
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }
}
