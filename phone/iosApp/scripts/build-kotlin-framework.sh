#!/bin/sh
# Xcode 构建阶段：把 Kotlin/Native framework 编出来并签名塞进 app。
set -eu
: "${SRCROOT:?}" "${CONFIGURATION:?}" "${SDK_NAME:?}" "${TARGET_TEMP_DIR:?}" "${BUILT_PRODUCTS_DIR:?}"

cd "$SRCROOT/.."

SKIP=""
if ! command -v node >/dev/null 2>&1; then
  echo "warning: node 不可用，构建期跳过 token-guard" >&2
  SKIP="-PskipTokenGuard"
fi

./gradlew :shared:embedAndSignAppleFrameworkForXcode $SKIP
./gradlew :shared:syncComposeResourcesForIos $SKIP
