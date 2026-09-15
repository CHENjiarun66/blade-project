#!/bin/sh
# Blade 项目全景台：在看板输入发生变化时自动重建
# 由 scripts/install-hooks.sh 安装；本文件被 post-* 钩子 source
root=$(git rev-parse --show-toplevel 2>/dev/null) || exit 0
[ -f "$root/scripts/gen-cockpit.mjs" ] || exit 0
command -v node >/dev/null 2>&1 || exit 0
( cd "$root" && node scripts/gen-cockpit.mjs >/dev/null 2>&1 )
exit 0
