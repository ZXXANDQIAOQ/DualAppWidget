#!/usr/bin/env bash
#
# 在「可添加版 / 可堆叠版」之间切换（覆盖安装，绝不卸载）
#
#   ./tools/switch.sh stack     切到可堆叠版
#   ./tools/switch.sh add       切到可添加版
#   ./tools/switch.sh status    只查看当前装的是哪个版本
#
# 两个 APK 包名、签名、versionCode 完全相同，覆盖安装即可切换；
# 卸载会连同桌面上的小部件实例和 App 配置一起清掉，别卸载。
#
set -euo pipefail

cd "$(dirname "$0")/.."

ADB="${ADB:-adb}"
if ! command -v "$ADB" >/dev/null 2>&1; then
  for c in "/d/App/Android/Sdk/platform-tools/adb.exe" "$HOME/Android/Sdk/platform-tools/adb"; do
    [ -x "$c" ] && ADB="$c" && break
  done
fi
if ! command -v "$ADB" >/dev/null 2>&1 && [ ! -x "$ADB" ]; then
  echo "[x] 找不到 adb，请把 platform-tools 加进 PATH，或用 ADB=/path/to/adb 指定。" >&2
  exit 1
fi

PKG="com.example.dualappwidget"

show_status() {
  echo "--- 当前安装版本 ---"
  "$ADB" shell dumpsys package "$PKG" 2>/dev/null \
    | grep -E "versionName|lastUpdateTime" | head -3 || echo "(未安装)"
  echo "--- 桌面上的小部件实例数 ---"
  "$ADB" shell dumpsys appwidget 2>/dev/null \
    | grep -c "provider=ProviderId" || true
}

case "${1:-}" in
  stack) MODE="stack"; APK="prebuilt/DualAppWidget-stack.apk"; EXPECT="1.0-stack" ;;
  add)   MODE="add";   APK="prebuilt/DualAppWidget-add.apk";   EXPECT="1.0-add"   ;;
  status) show_status; exit 0 ;;
  *) echo "用法: $0 {stack|add|status}" >&2; exit 1 ;;
esac

[ -f "$APK" ] || { echo "[x] 缺少 $APK，先构建：./gradlew assemble${MODE^}ableDebug" >&2; exit 1; }

echo "[1/4] 检查设备 ..."
"$ADB" devices

echo
echo "[2/4] 覆盖安装 $APK（不要卸载！）..."
"$ADB" install -r "$APK"

echo
echo "[3/4] 校验已安装版本（期望 $EXPECT）..."
show_status

echo
echo "[4/4] 重启桌面，让它重新读取小部件身份 ..."
"$ADB" shell am force-stop com.miui.home
sleep 4

echo
echo "============================================================"
if [ "$MODE" = "stack" ]; then
  echo " 已切到可堆叠版。把桌面上两个「双应用快捷启动」拖到一起试试堆叠。"
else
  echo " 已切到可添加版。长按桌面空白处 → 添加小部件 → 支持小部件应用「全部」"
  echo " → 「双应用快捷启动」，想加几个加几个。"
fi
echo " 如果行为没变，重启一次手机再试。"
echo "============================================================"
