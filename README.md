# 双应用快捷启动 · DualAppWidget

一个 **1×2 桌面小部件**，上半 / 下半各放一个 App 图标，点一下直接启动对应 App。
每个小部件实例独立配置，可以往桌面放任意多个；在小米澎湃 OS 上还能把多个实例 **拖到一起堆叠成一组**。

纯 Java 实现（无 Kotlin 源码），Gradle 命令行即可构建。

---

## 功能

| 功能 | 说明 |
| --- | --- |
| 上下双图标 | 1 列 × 2 行的小部件，两个图标各指向一个 App |
| 点击直启 | 用 `getLaunchIntentForPackage` 拿启动 Intent，包成 `PendingIntent` |
| 独立配置 | 每实例一组配置，存 `SharedPreferences`（`top_app_<widgetId>` / `bottom_app_<widgetId>`） |
| 多实例 | 小部件管理页列出桌面上所有实例，可逐个编辑 |
| 桌面堆叠 | 澎湃 OS 4 上两个实例可拖到一起叠成一组（见下文「关键实现」） |
| 未配置提示 | 没配过的小部件显示「请先配置」，点击进配置页 |

---

## 构建

环境要求：

- JDK 17
- Android SDK：`platforms;android-34`、`build-tools;34.0.0`
- Gradle 8.9（仓库自带 wrapper，无需另装）

在 `local.properties` 里写本机 SDK 路径：

```properties
sdk.dir=D\:\\App\\Android\\Sdk
```

构建：

```bash
./gradlew assembleDebug
```

产物：`app/build/outputs/apk/debug/app-debug.apk`

仓库里的 `prebuilt/` 目录另存了一份可直接安装的调试包（与当前源码一致）。

安装：

```bash
adb install -r prebuilt/DualAppWidget-debug.apk
```

---

## 目录结构

```
app/src/main/
├── AndroidManifest.xml                      # 小部件 receiver（米系声明，见下）+ 配置页 + 管理页
├── java/com/example/dualappwidget/
│   ├── DualAppWidgetProvider.java           # AppWidgetProvider：onUpdate 里拼 RemoteViews、绑 PendingIntent
│   ├── ConfigActivity.java                  # 配置页：列已装应用，选上半 / 下半
│   ├── WidgetListActivity.java              # 管理页：列出所有实例 + 添加新实例入口
│   ├── AppListAdapter.java                  # 应用列表适配器
│   ├── AppInfo.java                         # 已装应用信息（包名 / 名称 / 图标）
│   ├── ImageUtils.java                      # 图标缩放、圆角等处理
│   └── Prefs.java                           # SharedPreferences 封装
└── res/
    ├── layout/                              # 小部件 / 配置页 / 管理页 / 预览图
    ├── drawable/                            # 卡片、槽位、占位图等
    ├── xml/dual_app_widget_info.xml         # 小部件元信息（Android 11 及以下）
    └── xml-v31/dual_app_widget_info.xml     # 同上，带 targetCell 尺寸（Android 12+）
```

---

## 关键实现

### 1. 尺寸：1×2

小部件的单元格尺寸在 Android 12+ 用 `targetCellWidth` / `targetCellHeight` 精确指定：

```xml
<!-- res/xml-v31/dual_app_widget_info.xml -->
android:targetCellWidth="1"
android:targetCellHeight="2"
```

低版本走 `minWidth` / `minHeight`（按官方公式 `n 列 = 73n - 16 dp` 换算，1×2 即 40dp × 110dp）。

> ⚠️ 这两个属性比 `minSdk` 新。AAPT2 在 link 阶段会把「比 minSdk 新」的 `android:` 属性**静默丢弃**，
> 所以必须放进 `res/xml-v31/` 这个限定目录，直接写在 `res/xml/` 里会不生效且没有任何报错。

### 2. RemoteViews 布局只能用白名单控件

`RemoteViews` 只认带 `@RemoteView` 注解的 View。`LinearLayout` / `ImageView` / `TextView` 都有，
但 `android.view.View` 没有 —— 在布局里写一个 `<View>` 做分隔线，桌面 inflate 时会直接失败，
桌面上表现为「载入窗口小部件出现问题」。分隔线用 1dp 高的 `TextView` 代替。

### 3. 澎湃 OS 4 堆叠（核心）

澎湃 OS 4 桌面的堆叠只对 **米系小部件（miui widget）** 放行。普通原生 AppWidget 无论什么尺寸，
拖拽都会被拒绝：

```
WidgetStackDropHandler can not stack: dragItem is not miui widget
```

所谓「米系小部件」并不是另一套框架 —— 按小米官方《小部件技术规范》，它就是**原生 AppWidget 加一个标识**。
真机抓取澎湃 OS 自带米系小部件（`com.miui.player` 的 `MiuiHomeSmallWidget`、`com.mipay.wallet` 的 `VideoWidget`）
的清单后，比对出完整的「配方」，缺任何一条都会被判成非米系：

```xml
<receiver
    android:name=".DualAppWidgetProvider"
    android:exported="true"                 <!-- ① 必须 exported：桌面用 PackageManager 跨应用读 meta-data -->
    android:label="@string/widget_label"
    android:process=":widgetProvider">      <!-- ⑤ 独立进程 -->
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />  <!-- ② 两条广播 -->
        <action android:name="miui.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>

    <meta-data android:name="android.appwidget.provider"
               android:resource="@xml/dual_app_widget_info" />

    <meta-data android:name="miuiWidget" android:value="true" />            <!-- ③ 身份标识 -->

    <meta-data android:name="miuiWidgetRefresh" android:value="exposure" /> <!-- ④ 曝光刷新 -->
    <meta-data android:name="miuiWidgetRefreshMinInterval" android:value="60000" />
</receiver>
```

补齐之后，两个实例可以真的叠成一组（桌面日志：`stackWidget done: widgetCount=2`）。

**几个排查要点：**

- `exported="false"` 时桌面读不到 meta-data，日志会打
  `is_miui_widget: no meta data found for provider`，直接判非米系。
- `requestPinAppWidget` 的 `extras` 传 `null` 会被桌面丢弃
  （`start_widget_detail_page return false: extras is None`），要传一个非空 `Bundle`。
- 桌面/小部件中心有缓存的 provider 列表，改完 manifest 要**重启手机**才会刷新，
  否则会误判成「manifest 改坏了」。
- 用 `adb logcat | grep -i -E "is_miui_widget|can not stack|stackWidget"` 看桌面（Flutter 引擎）的判定。

### 4. 多实例

桌面上的每个小部件实例都有独立的 `appWidgetId`。`ConfigActivity` 从
`AppWidgetManager.EXTRA_APPWIDGET_ID` 拿 id 存配置，`onUpdate` 时按 id 取回，
所以实例之间互不干扰。

> `ConfigActivity` 不要用 `singleTop`：连续配置不同实例时会被复用，`widgetId` 会串掉。

---

## 已知限制

- **添加新实例必须手动**：澎湃 OS 拒绝第三方应用发起的 `requestPinAppWidget`
  （桌面日志 `setup_widget abort: permission denied`），管理页里的按钮只能给出手动步骤指引，
  没法真正一键添加。手动加一次桌面就有入口，重复添加就是多实例。
- **`miuiWidget` 身份是双刃剑**：带上它才能堆叠；但米系小部件在小部件面板里的条目由小米服务端下发，
  本地自建 APK 给不出那份数据，所以它不会出现在「米系小部件」分类列表里。
  用「支持小部件应用 → 全部」或桌面已有实例重新配置即可。
- 请勿在未授权的情况下用本工程的 `QUERY_ALL_PACKAGES` 权限上架商店（本项目为个人自用）。

---

## 验证环境

- 机型：小米 M511CD
- 系统：Android 17 / 澎湃 OS 4.0（V816）
- 桌面：`com.miui.home`（Flutter + Rust 全量重写版）
- 构建：JDK 17.0.13 + Gradle 8.9 + Android SDK 34
