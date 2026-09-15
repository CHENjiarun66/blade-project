#!/bin/sh
# 打开 Blade 项目全景台：先重建到最新，再打开浏览器。
#
# 这样你每次点开看到的都是当前工作区的真实状态，而不是上一次生成的旧快照。
# 需要「打开即可用」时用这个；只想重建不开浏览器用 node scripts/gen-cockpit.mjs。

set -e

# 双击 .command 时 PATH 可能很干净，补齐常见的 node 位置
if ! command -v node >/dev/null 2>&1; then
  for d in /opt/homebrew/bin /usr/local/bin "$HOME/.local/bin" "$HOME/.nvm/versions/node"; do
    [ -d "$d" ] && PATH="$d:$PATH"
  done
  # nvm 的版本目录要再展开一层
  if [ -d "$HOME/.nvm/versions/node" ]; then
    latest=$(ls -1 "$HOME/.nvm/versions/node" 2>/dev/null | sort -V | tail -1)
    [ -n "$latest" ] && PATH="$HOME/.nvm/versions/node/$latest/bin:$PATH"
  fi
  export PATH
fi

root=$(git rev-parse --show-toplevel 2>/dev/null || echo "$(cd "$(dirname "$0")/.." && pwd)")
cd "$root"

if ! command -v node >/dev/null 2>&1; then
  echo "❌ 找不到 node，请先安装 Node.js"
  exit 1
fi

echo "⏳ 正在同步到最新（读取 git、源码与文档）…"
if node scripts/gen-cockpit.mjs; then
  echo
  echo "🚀 正在打开看板：outputs/cockpit.html"
  open outputs/cockpit.html 2>/dev/null || xdg-open outputs/cockpit.html 2>/dev/null || echo "请手动打开 outputs/cockpit.html"
else
  echo "❌ 重建失败，未打开旧版本看板。请检查上面的报错。"
  exit 1
fi
