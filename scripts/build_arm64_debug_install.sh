#!/usr/bin/env bash
# 打包指定 flavor (mobile/leanback) 的 Debug APK 并推送到 Android 模拟器/设备。
#
# 用法:
#   bash scripts/build_arm64_debug_install.sh [--flavor mobile|leanback]
#                                            [--abi arm64-v8a|armeabi-v7a]
#                                            [--serial 192.168.50.3:5555]
#                                            [--adb /path/to/adb]
#                                            [--skip-check]
# 示例:
#   bash scripts/build_arm64_debug_install.sh                      # 手机版 arm64
#   bash scripts/build_arm64_debug_install.sh --flavor leanback    # TV/电脑版 arm64
#   bash scripts/build_arm64_debug_install.sh --flavor leanback --serial 192.168.50.3:5559
#
# 流程: 检查 Gradle 守护进程是否空闲 -> gradlew assemble<Flavor><Abi>Debug
#       -> adb 连接/安装 -> 校验模拟器上包存在。
# 手机版 package 为 com.silent.android.webhtv，TV/电脑版(leanback) 同用该包名。

set -euo pipefail

FLAVOR="mobile"
ABI="arm64-v8a"
SERIAL="192.168.50.3:5555"
ADB=""
SKIP_IDLE_CHECK=0

usage() {
  sed -n '3,14p' "$0" | sed 's/^# //; s/^#$//'
  exit 0
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    -f|--flavor) FLAVOR="${2:?missing flavor}"; shift 2 ;;
    -a|--abi) ABI="${2:?missing abi}"; shift 2 ;;
    -s|--serial) SERIAL="${2:?missing serial}"; shift 2 ;;
    --adb) ADB="${2:?missing adb path}"; shift 2 ;;
    --skip-check) SKIP_IDLE_CHECK=1; shift ;;
    -h|--help) usage ;;
    *) echo "未知参数: $1" >&2; usage ;;
  esac
done

case "$FLAVOR" in
  mobile|leanback) ;;
  *) echo "❌ --flavor 只能是 mobile 或 leanback" >&2; usage ;;
esac

case "$ABI" in
  arm64-v8a|armeabi-v7a) ;;
  *) echo "❌ --abi 只能是 arm64-v8a 或 armeabi-v7a" >&2; usage ;;
esac

# 组装 Gradle flavor/ABI 名
case "$FLAVOR" in
  mobile)   FLAVOR_GRADLE="Mobile" ;;
  leanback) FLAVOR_GRADLE="Leanback" ;;
esac
case "$ABI" in
  arm64-v8a)   ABI_GRADLE="Arm64_v8a";   ABISUFFIX="arm64_v8a" ;;
  armeabi-v7a) ABI_GRADLE="Armeabi_v7a"; ABISUFFIX="armeabi_v7a" ;;
esac
TASK=":app:assemble${FLAVOR_GRADLE}${ABI_GRADLE}Debug"
APK="app/build/outputs/apk/${FLAVOR}${ABI_GRADLE}/debug/app-${FLAVOR}-${ABISUFFIX}-debug.apk"


ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd -P)"
cd "$ROOT_DIR"

if [[ -z "$ADB" ]]; then
  for candidate in \
    "$HOME/android-sdk/platform-tools/adb" \
    "$HOME/Android/Sdk/platform-tools/adb" \
    "/usr/local/android-sdk/platform-tools/adb" \
    "adb"; do
    if command -v "$candidate" >/dev/null 2>&1 || [[ -x "$candidate" ]]; then
      ADB="$candidate"
      break
    fi
  done
fi
if [[ -z "$ADB" ]]; then
  echo "❌ 未找到 adb，请用 --adb 指定路径" >&2
  exit 1
fi

echo "==> 构建类型: ${FLAVOR}/${ABI} Debug"
echo "==> adb: $ADB"
echo "==> 设备: $SERIAL"

if [[ "$SKIP_IDLE_CHECK" -eq 0 ]]; then
  echo "==> 检查 Gradle 守护进程是否空闲（避免与其他打包任务冲突）..."
  STATUS_OUTPUT="$(./gradlew --status 2>&1 || true)"
  BUSY_PIDS="$(echo "$STATUS_OUTPUT" | awk '$2 == "BUSY" {print $1}')"
  if [[ -n "$BUSY_PIDS" ]]; then
    echo "❌ 检测到 Gradle 守护进程正在忙（PID: $BUSY_PIDS），可能有其他打包任务在进行。" >&2
    echo "   请等待其结束后再运行，或用 --skip-check 跳过此检查。" >&2
    exit 1
  fi
  echo "==> Gradle 守护进程空闲，可以开始打包"
fi

echo "==> 开始打包 $TASK ..."
./gradlew "$TASK"

if [[ ! -f "$APK" ]]; then
  echo "❌ 打包结束但未找到 APK: $APK" >&2
  exit 1
fi
echo "==> APK: $APK ($(du -h "$APK" | cut -f1))"

echo "==> 连接设备 $SERIAL ..."
"$ADB" connect "$SERIAL" >/dev/null 2>&1 || true

echo "==> 安装 APK 到 $SERIAL ..."
"$ADB" -s "$SERIAL" install -r "$APK"

echo "==> 校验安装 ..."
PKG="com.silent.android.webhtv"
if "$ADB" -s "$SERIAL" shell "pm list packages | grep -q '$PKG'"; then
  echo "✅ 安装成功: $PKG 已存在于 $SERIAL"
else
  echo "❌ 安装后未在设备上找到 $PKG" >&2
  exit 1
fi

echo "✅ 全部完成: ${FLAVOR}/${ABI} Debug 打包并安装到 $SERIAL"
