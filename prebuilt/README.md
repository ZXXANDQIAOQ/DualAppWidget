# 预编译产物

`DualAppWidget-debug.apk` —— 与当前源码一致的调试包，可直接安装：

```bash
adb install -r DualAppWidget-debug.apk
```

- 包名：`com.example.dualappwidget`
- 版本：1.0（debug 签名，仅自用，勿用于分发）
- minSdk 21 / targetSdk 34

重新构建：在工程根目录执行 `./gradlew assembleDebug`。
