#!/bin/sh
# 一条命令出 iOS 包：KMP framework 由 Xcode 构建阶段自动调 Gradle 生成。
#
# 用法:
#   iosApp/scripts/build-ios-app.sh                 # 真机 Debug（需图形会话 + 描述文件）
#   iosApp/scripts/build-ios-app.sh device Release
#   iosApp/scripts/build-ios-app.sh compile         # 只验编译（不签名，不需要证书/设备）
#
# 参数: [device|compile] [Debug|Release]
# 目标以真机为准：工程只配了 iosArm64 / iosSimulatorArm64，Intel 主机没有可用的模拟器切片。
# 真机签名需要 login 钥匙串私钥；SSH 会话拿不到（codesign 报 errSecInternalComponent），
# 因此 device 模式经 sudo launchctl asuser 放进图形会话执行。一次性配置：
#   sudo visudo -f /etc/sudoers.d/idea-capture-ios-build
#   <用户名> ALL=(ALL) NOPASSWD: /bin/launchctl asuser *
set -eu

cd "$(dirname "$0")/../.."

MODE=${1:-device}
CONFIGURATION=${2:-Debug}
SCHEME=iosApp

case "$MODE" in
  device)
    SDK=iphoneos
    DEST="generic/platform=iOS"
    SIGN_ARGS=""
    ;;
  compile)
    SDK=iphoneos
    DEST="generic/platform=iOS"
    SIGN_ARGS="CODE_SIGNING_ALLOWED=NO"
    ;;
  *) echo "error: mode 只能是 device 或 compile" >&2; exit 2 ;;
esac

OUT_DIR="build/ios/$MODE-$CONFIGURATION"

echo "==> scheme=$SCHEME configuration=$CONFIGURATION sdk=$SDK mode=$MODE"

if [ "$MODE" = device ]; then
  if ! sudo -n /bin/launchctl asuser "$(id -u)" true >/dev/null 2>&1; then
    echo "error: 真机构建需要 sudo 非交互可用（把构建放进图形会话，否则 codesign 取不到钥匙串私钥）。" >&2
    echo "一次性配置：sudo visudo -f /etc/sudoers.d/idea-capture-ios-build" >&2
    echo "  $(whoami) ALL=(ALL) NOPASSWD: /bin/launchctl asuser *" >&2
    exit 3
  fi
  USER_NAME=$(id -un)
  exec sudo launchctl asuser "$(id -u)" sudo -u "$USER_NAME" env HOME="$HOME" USER="$USER_NAME" PATH="$PATH" \
    xcodebuild -project iosApp/iosApp.xcodeproj -scheme "$SCHEME" -configuration "$CONFIGURATION" -sdk "$SDK" \
    -destination "$DEST" -derivedDataPath "$OUT_DIR" $SIGN_ARGS
fi

exec xcodebuild -project iosApp/iosApp.xcodeproj -scheme "$SCHEME" -configuration "$CONFIGURATION" -sdk "$SDK" \
  -destination "$DEST" -derivedDataPath "$OUT_DIR" $SIGN_ARGS
