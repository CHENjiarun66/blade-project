#!/bin/sh
# 安装 Blade 项目全景台的 git 钩子。
#
# 设计取舍：
# - 版本化的钩子源码放在 .githooks/，安装时复制到 .git/hooks/。
# - 刻意不使用 core.hooksPath，因为本仓库的 .git/hooks/prepare-commit-msg 是
#   lefthook 生成的 shim，改 hooksPath 会让它失效。
# - 只新增 post-commit / post-merge / post-checkout 三个钩子名，不覆盖既有文件。
#
# 可重复执行。

set -e
root=$(git rev-parse --show-toplevel)
src="$root/.githooks"
dst="$root/.git/hooks"

[ -d "$src" ] || { echo "找不到 $src"; exit 1; }

for h in post-commit post-merge post-checkout; do
  if [ -e "$dst/$h" ] && ! grep -q "Blade 项目全景台" "$dst/$h" 2>/dev/null; then
    echo "⚠️  $dst/$h 已存在且不是本工具安装的，跳过（未覆盖）"
    continue
  fi
  cp "$src/_cockpit.sh" "$dst/_cockpit.sh"
  cp "$src/$h" "$dst/$h"
  chmod +x "$dst/$h" "$dst/_cockpit.sh"
  echo "✅ 已安装 $h"
done

echo
echo "提交 / 合并 / 切换分支后会自动重建 outputs/cockpit.html 与 outputs/ask/。"
echo "手动重建：node scripts/gen-cockpit.mjs"
echo "详见：docs/reference/COCKPIT.md"
