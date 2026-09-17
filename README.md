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

构建（会出两个 APK，见下一节）：

```bash
./gradlew assembleAddableDebug assembleStackableDebug
```

产物：

| 文件 | 版本 |
| --- | --- |
| `app/build/outputs/apk/addable/debug/DualAppWidget-add-debug.apk` | 可添加版 |
| `app/build/outputs/apk/stackable/debug/DualAppWidget-stack-debug.apk` | 可堆叠版 |

仓库里的 `prebuilt/` 目录另存了这两个包（与当前源码一致），可直接安装。

安装：

```bash
adb install -r prebuilt/DualAppWidget-add.apk     # 或 DualAppWidget-stack.apk
```

---

## 两个版本：可添加版 / 可堆叠版

澎湃 OS 4 上，「能在小部件面板里找到并添加」和「能堆叠」这两件事**互斥**：

| | 可添加版 `add` | 可堆叠版 `stack` |
| --- | --- | --- |
| 桌面小部件面板里能找到 | ✅ | ❌（米系小部件的条目由小米服务端下发，本地包给不出） |
| 新建实例 | ✅ | ❌ |
| 两个实例拖到一起堆叠 | ❌ | ✅ |

所以同一个包名出两个 APK，靠**覆盖安装**来回切换。关键点：

- 两个 APK 包名、签名、versionCode 完全相同，**互相覆盖安装即可切换**；
- **不要卸载**——卸载会清掉 App 配置，桌面上的小部件实例也会一起消失；
- 覆盖安装不会丢配置、不会丢桌面上的实例。因为小部件身份是桌面每次用 `PackageManager`
  **实时读当前安装包清单**判断的，不是记录在实例里的（已实测：无标识时期建的实例，
  换成带标识版本重装后直接就能叠）；
- 装完之后建议重启一次手机，让桌面的小部件列表缓存刷新。

典型用法：用可添加版把实例都加好 → 换成可堆叠版 → 把实例拖到一起叠成一组
→ 以后想再加实例，换回可添加版，加完再换回来。

App 首页顶部会明示当前装的是哪个版本，按钮也会按版本给对应提示。

---

## 目录结构

```
app/
├── build.gradle                             # 两种 flavor（addable / stackable），各自出 APK
└── src/
    ├── main/
    │   ├── AndroidManifest.xml              # 权限 / queries / 两个 Activity（receicver 见下）
    │   ├── java/com/example/dualappwidget/
    │   │   ├── DualAppWidgetProvider.java   # AppWidgetProvider：onUpdate 里拼 RemoteViews、绑 PendingIntent
    │   │   ├── ConfigActivity.java          # 配置页：列已装应用，选上半 / 下半
    │   │   ├── WidgetListActivity.java      # 管理页：列出所有实例 + 添加新实例入口 + 版本提示
    │   │   ├── AppListAdapter.java          # 应用列表适配器
    │   │   ├── AppInfo.java                 # 已装应用信息（包名 / 名称 / 图标）
    │   │   ├── ImageUtils.java              # 图标缩放、圆角等处理
    │   │   └── Prefs.java                   # SharedPreferences 封装
    │   └── res/
    │       ├── layout/                      # 小部件 / 配置页 / 管理页 / 预览图
    │       ├── drawable/                    # 卡片、槽位、占位图等
    │       ├── xml/dual_app_widget_info.xml         # 小部件元信息（Android 11 及以下）
    │       └── xml-v31/dual_app_widget_info.xml     # 同上，带 targetCell 尺寸（Android 12+）
    ├── addable/AndroidManifest.xml          # 普通身份的小部件 receiver（面板可见）
    └── stackable/AndroidManifest.xml        # 米系身份的小部件 receiver（miuiWidget=true）
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

> 这套声明放在 `app/src/stackable/AndroidManifest.xml`；普通身份那份在 `app/src/addable/`。
> 两份的差别只有这个 receiver。

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
- **`miuiWidget` 身份是双刃剑（已用「两个 APK」绕过）**：带上它才能堆叠；但米系小部件在小部件面板里的
  条目由小米服务端下发，本地自建 APK 给不出那份数据，所以它不会出现在面板的列表里。
  结论就是**「能加」和「能叠」互斥**，用 `addable` / `stackable` 两个包覆盖安装切换来兼顾。
- 切换版本后如果堆叠 / 添加行为没变，**重启一次手机**再看（桌面有小部件列表缓存）。
- 请勿在未授权的情况下用本工程的 `QUERY_ALL_PACKAGES` 权限上架商店（本项目为个人自用）。

---

## 验证环境

- 机型：小米 M511CD
- 系统：Android 17 / 澎湃 OS 4.0（V816）
- 桌面：`com.miui.home`（Flutter + Rust 全量重写版）
- 构建：JDK 17.0.13 + Gradle 8.9 + Android SDK 34
