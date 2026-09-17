# 预编译产物

两个包，**包名 / 签名 / versionCode 完全一样**，互相覆盖安装即可切换：

| 文件 | 版本 | 用途 |
| --- | --- | --- |
| `DualAppWidget-add.apk` | 可添加版（1.0-add） | 桌面小部件面板里能找到它 → 用它新建实例 |
| `DualAppWidget-stack.apk` | 可堆叠版（1.0-stack） | 实例可以拖到一起堆叠；面板里没有入口 |

安装 / 切换：

```bash
adb install -r DualAppWidget-add.apk     # 覆盖安装，不需要先卸载
```

⚠️ **不要卸载** —— 卸载会清掉 App 配置，桌面上的小部件实例也会一起消失。
覆盖安装不会丢任何东西。装完建议重启一次手机，让桌面刷新小部件列表。

用法：可添加版加好实例 → 换可堆叠版堆叠 → 以后要加实例再换回可添加版。

- 包名：`com.example.dualappwidget`
- debug 签名，仅自用，勿用于分发
- minSdk 21 / targetSdk 34

重新构建：

```bash
./gradlew assembleAddableDebug assembleStackableDebug
```

产物在 `app/build/outputs/apk/{addable,stackable}/debug/`。
