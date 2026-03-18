# ========================================
# KuiklyWebview ProGuard Rules
# ========================================

# --- WebView JavascriptInterface ---
# @JavascriptInterface 方法不能被混淆，否则 JS 调用会失败
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# --- KRWebView JSBridge ---
# 保留 KRWebViewJSBridge 的 postMessage 方法（JS 通过反射调用）
-keep class com.tencent.kuiklybase.android.KRWebViewJSBridge {
    @android.webkit.JavascriptInterface *;
}

# 保留 BridgeCallback 接口
-keep interface com.tencent.kuiklybase.android.BridgeCallback { *; }

# --- Kuikly 框架 ---
# 保留 IKuiklyRenderViewExport 实现类（框架通过反射创建）
-keep class * implements com.tencent.kuikly.core.render.android.export.IKuiklyRenderViewExport {
    <init>(...);
    public *;
}

# 保留 KuiklyRenderBaseModule 子类（框架通过反射注册）
-keep class * extends com.tencent.kuikly.core.render.android.export.KuiklyRenderBaseModule {
    <init>(...);
    public *;
}

# 保留 KuiklyRenderCallback（框架回调）
-keep interface com.tencent.kuikly.core.render.android.export.KuiklyRenderCallback { *; }

# --- Android WebView ---
-keepclassmembers class * extends android.webkit.WebViewClient {
    public void *(android.webkit.WebView, java.lang.String);
    public void *(android.webkit.WebView, java.lang.String, android.graphics.Bitmap);
    public boolean *(android.webkit.WebView, java.lang.String);
}
-keepclassmembers class * extends android.webkit.WebChromeClient {
    public void *(android.webkit.WebView, java.lang.String);
}

# --- 通用 ---
# 保留枚举
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# 保留 Parcelable
-keep class * implements android.os.Parcelable {
    public static final android.os.Parcelable$Creator *;
}

# 保留 Serializable
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    !static !transient <fields>;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}
