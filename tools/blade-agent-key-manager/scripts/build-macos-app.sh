#!/bin/bash

set -euo pipefail

PACKAGE_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
OUTPUT_ROOT="$PACKAGE_ROOT/dist"

if [[ "${1:-}" == "--output-dir" ]]; then
  if [[ -z "${2:-}" ]]; then
    echo "--output-dir 需要一个目标目录" >&2
    exit 64
  fi
  OUTPUT_ROOT="$2"
fi

APP_BUNDLE="$OUTPUT_ROOT/Blade Agent Key Manager.app"
case "$APP_BUNDLE" in
  */Blade\ Agent\ Key\ Manager.app) ;;
  *)
    echo "拒绝写入非预期应用路径：$APP_BUNDLE" >&2
    exit 64
    ;;
esac

swift build -c release --package-path "$PACKAGE_ROOT"
BIN_ROOT="$(swift build -c release --package-path "$PACKAGE_ROOT" --show-bin-path)"

mkdir -p "$OUTPUT_ROOT"
if [[ -e "$APP_BUNDLE" ]]; then
  rm -rf "$APP_BUNDLE"
fi
mkdir -p "$APP_BUNDLE/Contents/MacOS" "$APP_BUNDLE/Contents/Helpers" "$APP_BUNDLE/Contents/Resources"

cp "$BIN_ROOT/BladeAgentKeyManager" "$APP_BUNDLE/Contents/MacOS/BladeAgentKeyManager"
cp "$BIN_ROOT/blade-agent-request" "$APP_BUNDLE/Contents/Helpers/blade-agent-request"
cp "$PACKAGE_ROOT/Resources/Info.plist" "$APP_BUNDLE/Contents/Info.plist"
chmod 755 "$APP_BUNDLE/Contents/MacOS/BladeAgentKeyManager" "$APP_BUNDLE/Contents/Helpers/blade-agent-request"

codesign --force --sign - "$APP_BUNDLE/Contents/Helpers/blade-agent-request"
codesign --force --deep --sign - "$APP_BUNDLE"
codesign --verify --deep --strict "$APP_BUNDLE"

echo "$APP_BUNDLE"
