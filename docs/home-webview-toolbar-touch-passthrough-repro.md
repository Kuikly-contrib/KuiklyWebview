# WebView 悬浮工具栏点击穿透复现

这个 Demo 演示 `KuiklyWebview.touchShields()` / `touchShieldBottom()` 组件能力：Compose overlay 浮在原生 `WebView` 上时，网页内容仍绘制到 overlay 下面，但声明区域的原生触摸被组件遮挡，不会触发下层网页链接。

## 结论先说

现象成立：这是「Compose 虚拟节点命中」和「Android 原生 WebView 命中」两套触摸分发链同时收到事件造成的，和 `weight` 无关，`zIndex` 也不会改变 Android 子 View 的命中顺序。

真机结果（Kuikly `2.30.0-2.1.21`，设备 `PFTM10` / Android WebView，网页 `www.baidu.com`）：

- `Baseline`：不使用 shield，点一次 `Voice / text` 后 `Toolbar +1`，百度首页跳到新闻详情页，穿透复现。
- `Fixed`：使用 `touchShields` 声明一个 `BOTTOM_STRETCH` 底部区域，点一次 `Voice / text` 后 `Toolbar +1`，百度首页不跳，日志只有 `toolbar: 1`。
- `Regions`：同时声明顶部、左侧、底部区域，演示任意位置和多区域协议；Android 已编译，待设备回归。
- `Padding`：使用 `padding(bottom = 96.dp)` 物理避让，网页内容不再绘制到工具栏下方；作为兼容兜底对照。

反馈文档里的根因结论需要修正，但不是把结论反过来：

- `KRView.dispatchTouchEvent` 的 SuperTouch 路径会先调用 `tryFireTouchEvent(event)` 把触摸发给 Compose，再调用 `super.dispatchTouchEvent(event)` 走 Android 子 View 分发。
- `touchConsumeByNative` 的路径不是「让出触摸、不再走 Compose 点击判定」；它在 `generateBaseParamsWithTouch` 里把 `CONSUMED=1` 带进事件，`SuperTouchManager` 再把 `nativeEvent="cancel"` 交给 Compose。这是取消后续 Compose 手势 / 滚动的仲裁信号。
- `SuperTouchManager.setTouchDown` 会在 DOWN 命中 pointer input modifier 后调用 `consumeTouchDown(true)`。disabled `clickable` 也仍会创建 `SuspendingPointerInputModifierNode`，所以理论上能命中这个开关。
- 但 `KRView.dispatchTouchEvent` 无条件继续调用 `super.dispatchTouchEvent(event)`；Webview 原生子 View 返回 `true` 时，代码还会在 `handle == true` 分支把 `touchDownConsumeOnce` 清掉。`consumeTouchDown(true)` 只在「原生子 View 没有消费 DOWN」时兜底返回 `true`，无法撤回已经交给 WebView 的这次事件。这解释了 `Block` 模式真机仍穿透。
- 因此，在保持物理重叠时，`KuiklyWebview` 通过 `touchShields` 在原生 WebView 容器内消费声明区域的 MotionEvent；Compose 事件由 SuperTouch 先注入，工具栏点击仍正常。业务侧 `clickable` / `zIndex` 不需要改。

源码位置（本地 KuiklyUI 源码树）：

- `core-render-android/.../expand/component/KRView.kt`：`dispatchTouchEvent` / `tryFireSuperTouchCanceled` / `generateBaseParamsWithTouch`
- `compose/.../container/SuperTouchManager.kt`：`setTouchDown` / `setTouchMove` / `nativeEvent="cancel"`
- `core-render-android/.../css/ktx/KRCSSViewExtension.kt`：`setPreventTouch` / `setTouchDownConsumeOnce`
- `compose/.../extension/MakeKuiklyNode.kt`：core `DeclarativeBaseView` 经 `KNode` 进入 Compose 树
- `core-render-android/.../layer/KuiklyRenderLayerHandler.kt`：`insertSubRenderView` 按 index 调用 `parentView.addView`

## 运行

Demo 页面名是 `WebViewTouchPassthroughDemo`。Android 宿主默认仍打开原 `router` 页面；用下面的命令直接打开复现页：

```bash
./gradlew :androidApp:assembleDebug
adb install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
adb shell am start -n com.tencent.kuiklybase/com.tencent.kuiklybase.KuiklyRenderActivity \
  --es pageName WebViewTouchPassthroughDemo
```

页面加载 `https://www.baidu.com/`，顶部显示工具栏计数、网页标题、当前 URL 和最近事件日志；网页被误触时可直接从首页跳到新闻详情页观察。

## 观测方式

当前 Demo 四种模式：

1. `Fixed`：WebView 全屏并通过 `touchShields` 声明底部 96dp；预期 `Toolbar +1`，网页标题/URL 不变。
2. `Regions`：同时声明顶部、左侧和底部区域；用于验证多区域协议和运行时替换。
3. `Baseline`：WebView 全屏且不启用 shield；预期 `Toolbar +1`，网页发生误触跳转。
4. `Padding`：WebView 底部让出 96dp；预期 `Toolbar +1`，网页不误触，但失去工具栏下方网页内容。

每次切换模式后点 `Reset`，再只点一次 `Voice / text`。使用 `adb` 时以 `uiautomator dump` 的实时 bounds 为准；实测 `Voice / text` 约 `(389,2132)`，`Fixed` 约 `(144,329)`，`Baseline` 约 `(419,329)`，`Padding` 约 `(690,329)`，`Reset` 约 `(708,521)`。

## 注意

本 Demo 为 Compose 验证把 `shared` 的 Kuikly 依赖调到 `2.30.0-2.1.21`，iOS 渲染器同步为 `OpenKuiklyIOSRender 2.30.0`，OHOS 渲染器同步为 `@kuikly-open/render 2.30.0`。`gradle.properties` 的发布版本为 `1.0.4`；业务工程若使用其他 Kuikly 版本，建议用同一页面回归一次。Android 多区域 shield 已编译并做过单底部 shield 真机验证；iOS 多区域 shield 通过 iOS Simulator 编译；OHOS 多区域 shield 通过 DevEco hvigor `assembleHar` 编译和模拟器启动验证。iOS/OHOS 设备手势回归仍建议补做。

OHOS 的版本一致是启动前提，不是可选优化：`libshared.so` 由当前 Gradle core 生成，`libkuikly.so` 来自 OHPM 渲染器，两者跨 `.so` 边界交换 `CallKotlin` / `CallNative` 的 C ABI。若 Gradle core 已到 `2.30.0` 但 OHOS 渲染器仍为 `2.7.0`，会在 `KRRenderCore::DidInit()` 阶段 `SIGSEGV`，甚至来不及创建页面；升级后应重新 `ohpm install --all`、link `libshared.so`、重建 HAP 并确认 HAP 内两个 `.so` 的 BuildID 与刚生成的工件一致。
