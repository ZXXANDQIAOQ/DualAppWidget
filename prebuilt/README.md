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

更推荐用仓库里的切换脚本，它会「覆盖安装 → 校验已装版本 → 重启桌面让身份缓存刷新」一次做完：

```bat
tools\switch-to-add.bat       :: Windows 双击即可
tools\switch-to-stack.bat
```

```bash
./tools/switch.sh add         # 或 stack / status
```

⚠️ **不要卸载** —— 卸载会清掉 App 配置，桌面上的小部件实例也会一起消失。
覆盖安装不会丢任何东西。

⚠️ **装完必须刷新一次桌面**（脚本已包含；手动装的话执行
`adb shell am force-stop com.miui.home`，或直接重启手机）。
桌面会缓存小部件的身份，不刷新的话还是按旧身份判定，看起来就像「覆盖安装没生效」。

装没装对，打开 App 看首页顶部那块「当前 APK 清单里的真实身份」即可确认。

用法：可添加版加好实例 → 换可堆叠版堆叠 → 以后要加实例再换回可添加版。

- 包名：`com.example.dualappwidget`
- debug 签名，仅自用，勿用于分发
- minSdk 21 / targetSdk 34

重新构建：

```bash
./gradlew assembleAddableDebug assembleStackableDebug
```

产物在 `app/build/outputs/apk/{addable,stackable}/debug/`。
