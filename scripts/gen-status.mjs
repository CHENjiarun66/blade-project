#!/usr/bin/env node
/**
 * gen-status.mjs — 生成项目状态看板
 *
 * 数据源：docs/03-TASKS.md（任务表格）
 * 产物：
 *   - outputs/status.html  —— 可视化看板（浏览器打开，人看）
 *   - docs/STATUS.md       —— 紧凑状态摘要（AI / GitHub 直接看）
 *
 * 用法：
 *   node scripts/gen-status.mjs
 *
 * 维护约定（AGENTS.md 规则 8）：
 *   - 每次 AI 任务交接 / 收工时运行本脚本刷新看板，并把 docs/STATUS.md 提交入库。
 *   - 本脚本只读 03-TASKS.md，不修改任何业务数据。
 */
import { readFileSync, writeFileSync, mkdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const TASKS_PATH = join(ROOT, 'docs/03-TASKS.md')
const HTML_PATH = join(ROOT, 'outputs/status.html')
const MD_PATH = join(ROOT, 'docs/STATUS.md')

/** 模块名 → 看板短名 */
const MODULE_SHORT = {
  '后端开发任务': '后端',
  'PC 管理端开发任务（blade-admin）': 'PC 管理端',
  '移动端开发任务（blade-mobile）': '移动端',
}

/** 状态归类 */
const STATUS = {
  done: 'done',
  doing: 'doing',
  todo: 'todo',
  partial: 'partial',
  deferred: 'deferred',
  other: 'other',
}

const STATUS_LABEL = {
  done: '✅ 完成',
  doing: '⏳ 进行中',
  todo: '⏳ 待办',
  partial: '⏳ 部分完成',
  deferred: '⏸ 暂缓/外部承担',
  other: '⚠️ 其他',
}

/** 解析一行表格状态文本 → { status, executor } */
function parseStatus(raw) {
  const s = raw
  if (s.includes('✅')) return { status: STATUS.done, executor: '' }
  if (s.includes('⏸') || s.includes('暂缓') || s.includes('转外部')) {
    return { status: STATUS.deferred, executor: '' }
  }
  if (s.includes('⏳') || s.includes('待办')) {
    if (s.includes('TODO')) return { status: STATUS.todo, executor: '' }
    if (s.includes('进行中')) {
      const m = s.match(/执行人[:：]\s*([^）)]+)/)
      return { status: STATUS.doing, executor: m ? m[1].trim() : '' }
    }
    if (s.includes('部分完成')) return { status: STATUS.partial, executor: '' }
    return { status: STATUS.todo, executor: '' }
  }
  if (s.includes('部分完成')) return { status: STATUS.partial, executor: '' }
  return { status: STATUS.other, executor: '' }
}

/** 解析 TASKS.md → 任务数组 */
function parseTasks(content) {
  const tasks = []
  let module = ''
  let phase = ''
  for (const line of content.split('\n')) {
    if (line.startsWith('## ')) {
      module = line.slice(3).trim()
      phase = ''
      continue
    }
    if (line.startsWith('### ')) {
      phase = line.slice(4).trim()
      continue
    }
    // 表格行：| BE-001 | 任务名 | 状态 | 备注 |（ID 支持 TEST-ORDER-INV-001 多段格式）
    const m = line.match(/^\| ((?:BE|BA|FE|TEST|DOC|AGENT)-[0-9A-Z]+(?:-[0-9A-Z]+)*) \| (.+?) \| (.+?) \|(.*)$/)
    if (!m) continue
    const { status, executor } = parseStatus(m[3].trim())
    tasks.push({
      id: m[1],
      name: m[2].trim(),
      status,
      executor,
      remark: m[4] ? m[4].trim() : '',
      module: MODULE_SHORT[module] || module,
      phase,
    })
  }
  return tasks
}

/** 模块统计 */
function summarize(tasks) {
  const modules = {}
  const phaseMap = {}
  for (const t of tasks) {
    if (!modules[t.module]) {
      modules[t.module] = { name: t.module, total: 0, done: 0, doing: 0, todo: 0, partial: 0, deferred: 0, other: 0, items: [] }
    }
    const mod = modules[t.module]
    mod.total++
    mod[t.status]++
    mod.items.push(t)
    const key = `${t.module} › ${t.phase}`
    if (t.phase && !phaseMap[key]) phaseMap[key] = { module: t.module, phase: t.phase, total: 0, done: 0, doing: 0, todo: 0, partial: 0, deferred: 0, other: 0 }
    if (t.phase) {
      const p = phaseMap[key]
      p.total++
      p[t.status]++
    }
  }
  return { modules, phases: Object.values(phaseMap) }
}

/** 文本进度条（STATUS.md 用） */
function barText(ratio, width = 20) {
  const filled = Math.round(ratio * width)
  return '█'.repeat(filled) + '░'.repeat(width - filled)
}

/** 转义 HTML */
function esc(s) {
  return String(s)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
}

/** 生成 STATUS.md */
function renderMarkdown(now, stats) {
  const lines = []
  lines.push('# 项目状态总览（自动生成）')
  lines.push('')
  lines.push('> 生成时间：' + now + '　|　数据源：[03-TASKS.md](./03-TASKS.md)　|　本文件由 `node scripts/gen-status.mjs` 自动生成，请勿手工编辑。')
  lines.push('> 可视化看板：`outputs/status.html`（浏览器打开）')
  lines.push('> 进度百分比按活跃范围计算，不把“暂缓或由外部流程承担”计入分母。')
  lines.push('')
  lines.push('## 模块进度')
  lines.push('')
  lines.push('| 模块 | 进度 | 统计 |')
  lines.push('|------|------|------|')
  const mods = Object.values(stats.modules).sort((a, b) => b.total - a.total)
  for (const m of mods) {
    const activeTotal = m.total - m.deferred
    const ratio = activeTotal ? m.done / activeTotal : 0
    const pct = Math.round(ratio * 100)
    lines.push(`| ${m.name} | \`${barText(ratio)} ${pct}%\` | ${m.done} 完成 / ${m.doing} 进行中 / ${m.todo} 待办 / ${m.partial} 部分 / ${m.deferred} 暂缓 |`)
  }
  const total = stats.total
  const activeTotal = total.total - total.deferred
  const totalRatio = activeTotal ? total.done / activeTotal : 0
  lines.push(`| **合计** | \`${barText(totalRatio)} ${Math.round(totalRatio * 100)}%\` | ${total.done} 完成 / ${total.doing} 进行中 / ${total.todo} 待办 / ${total.partial} 部分 / ${total.deferred} 暂缓 |`)
  lines.push('')
  lines.push('## 正在做（' + stats.doing.length + '）')
  lines.push('')
  if (stats.doing.length) {
    for (const t of stats.doing) {
      lines.push(`- ${t.id} ${t.name}${t.executor ? `（执行人：${t.executor}）` : ''} — ${t.module}${t.phase ? ` › ${t.phase}` : ''}`)
    }
  } else {
    lines.push('- （无）')
  }
  lines.push('')
  lines.push('## 还没做（' + stats.todo.length + '）')
  lines.push('')
  if (stats.todo.length) {
    for (const t of stats.todo) {
      lines.push(`- ${t.id} ${t.name} — ${t.module}${t.phase ? ` › ${t.phase}` : ''}`)
    }
  } else {
    lines.push('- （全部完成 🎉）')
  }
  lines.push('')
  lines.push('## 部分完成（' + stats.partial.length + '）')
  lines.push('')
  if (stats.partial.length) {
    for (const t of stats.partial) {
      lines.push(`- ${t.id} ${t.name} — ${t.module}${t.phase ? ` › ${t.phase}` : ''}`)
    }
  } else {
    lines.push('- （无）')
  }
  lines.push('')
  lines.push('## 暂缓或由外部流程承担（' + stats.deferred.length + '）')
  lines.push('')
  if (stats.deferred.length) {
    for (const t of stats.deferred) {
      lines.push(`- ${t.id} ${t.name} — ${t.module}${t.phase ? ` › ${t.phase}` : ''}`)
    }
  } else {
    lines.push('- （无）')
  }
  lines.push('')
  lines.push('## 已完成任务样本（按表格顺序取前 15）')
  lines.push('')
  for (const t of stats.done.slice(0, 15)) {
    lines.push(`- ${t.id} ${t.name}`)
  }
  lines.push('')
  lines.push('---')
  lines.push('> 完整任务明细以 [03-TASKS.md](./03-TASKS.md) 为准。')
  return lines.join('\n')
}

/** 生成 HTML 看板 */
function renderHtml(now, stats) {
  const modCards = Object.values(stats.modules)
    .sort((a, b) => b.total - a.total)
    .map((m) => {
      const activeTotal = m.total - m.deferred
      const ratio = activeTotal ? m.done / activeTotal : 0
      const pct = Math.round(ratio * 100)
      return `
      <div class="card">
        <div class="card-head">
          <span class="card-title">${esc(m.name)}</span>
          <span class="card-nums">${m.done} 完成 · ${m.doing} 进行中 · ${m.todo} 待办 · ${m.partial} 部分 · ${m.deferred} 暂缓</span>
        </div>
        <div class="bar"><div class="bar-fill" style="width:${pct}%"></div></div>
        <div class="bar-label">${pct}% 完成（活跃 ${activeTotal} 项，暂缓 ${m.deferred} 项）</div>
      </div>`
    })
    .join('')

  const taskRows = (list) =>
    list
      .map(
        (t) => `
      <tr data-status="${t.status}">
        <td class="td-id">${esc(t.id)}</td>
        <td class="td-name">${esc(t.name)}${t.executor ? `<span class="tag executor">执行人：${esc(t.executor)}</span>` : ''}</td>
        <td class="td-status"><span class="pill pill-${t.status}">${STATUS_LABEL[t.status]}</span></td>
        <td class="td-meta">${esc(t.module)}${t.phase ? ` › ${esc(t.phase)}` : ''}</td>
        <td class="td-remark">${esc(t.remark)}</td>
      </tr>`
      )
      .join('')

  const section = (title, list, cls) => `
    <section class="section ${cls}">
      <h2>${title} <span class="count">${list.length}</span></h2>
      ${list.length ? `<table class="task-table">${taskRows(list)}</table>` : '<p class="empty">（无）</p>'}
    </section>`

  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0">
<title>BladeProject 项目状态看板</title>
<style>
  :root { --bg:#f6f7f9; --card:#fff; --line:#e5e7eb; --text:#1f2328; --muted:#6b7280;
          --done:#16a34a; --doing:#f59e0b; --todo:#ef4444; --partial:#3b82f6; --deferred:#64748b; --other:#8b5cf6; }
  * { box-sizing: border-box; }
  body { margin:0; background:var(--bg); color:var(--text); font:14px/1.6 -apple-system,"PingFang SC","Microsoft YaHei",sans-serif; }
  .wrap { max-width:1100px; margin:0 auto; padding:24px 20px 60px; }
  header h1 { font-size:22px; margin:0 0 4px; }
  header .meta { color:var(--muted); font-size:12px; }
  .controls { display:flex; gap:8px; flex-wrap:wrap; margin:16px 0; align-items:center; }
  .controls input[type=search] { flex:1; min-width:220px; padding:8px 12px; border:1px solid var(--line); border-radius:8px; font-size:14px; }
  .controls button { padding:7px 14px; border:1px solid var(--line); background:#fff; border-radius:8px; cursor:pointer; font-size:13px; }
  .controls button.active { background:#1f2328; color:#fff; border-color:#1f2328; }
  .grid { display:grid; grid-template-columns:repeat(auto-fit,minmax(260px,1fr)); gap:12px; margin-bottom:20px; }
  .card { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:14px 16px; }
  .card-head { display:flex; justify-content:space-between; align-items:baseline; gap:8px; margin-bottom:10px; }
  .card-title { font-weight:600; font-size:15px; }
  .card-nums { font-size:12px; color:var(--muted); white-space:nowrap; }
  .bar { height:10px; background:#eceef1; border-radius:5px; overflow:hidden; }
  .bar-fill { height:100%; background:linear-gradient(90deg,#22c55e,#16a34a); border-radius:5px; }
  .bar-label { font-size:12px; color:var(--muted); margin-top:6px; }
  .section { background:var(--card); border:1px solid var(--line); border-radius:12px; padding:16px 18px; margin-bottom:14px; }
  .section h2 { font-size:16px; margin:0 0 10px; display:flex; align-items:center; gap:8px; }
  .section .count { background:#eceef1; border-radius:999px; font-size:12px; padding:1px 10px; color:var(--muted); }
  .section.doing { border-left:4px solid var(--doing); }
  .section.todo { border-left:4px solid var(--todo); }
  .section.partial { border-left:4px solid var(--partial); }
  .section.deferred { border-left:4px solid var(--deferred); }
  .section.done { border-left:4px solid var(--done); }
  .task-table { width:100%; border-collapse:collapse; font-size:13px; }
  .task-table th, .task-table td { text-align:left; padding:7px 10px; border-bottom:1px solid var(--line); vertical-align:top; }
  .task-table th { color:var(--muted); font-weight:500; font-size:12px; background:#fafbfc; }
  .td-id { white-space:nowrap; font-weight:600; color:#374151; }
  .td-meta { white-space:nowrap; color:var(--muted); font-size:12px; }
  .td-remark { color:var(--muted); font-size:12px; max-width:320px; }
  .pill { display:inline-block; border-radius:999px; padding:1px 10px; font-size:12px; white-space:nowrap; }
  .pill-done { background:#dcfce7; color:#15803d; }
  .pill-doing { background:#fef3c7; color:#b45309; }
  .pill-todo { background:#fee2e2; color:#b91c1c; }
  .pill-partial { background:#dbeafe; color:#1d4ed8; }
  .pill-deferred { background:#e2e8f0; color:#475569; }
  .pill-other { background:#ede9fe; color:#6d28d9; }
  .tag { display:inline-block; margin-left:8px; background:#fef3c7; color:#b45309; border-radius:6px; padding:0 8px; font-size:12px; }
  .empty { color:var(--muted); font-size:13px; }
  details.done-box summary { cursor:pointer; font-size:14px; font-weight:600; margin-bottom:8px; }
  footer { color:var(--muted); font-size:12px; margin-top:24px; text-align:center; }
  .hidden { display:none !important; }
</style>
</head>
<body>
<div class="wrap">
  <header>
    <h1>📊 BladeProject 项目状态看板</h1>
    <div class="meta">生成时间：${now}　·　数据源：docs/03-TASKS.md　·　运行 <code>node scripts/gen-status.mjs</code> 刷新</div>
  </header>

  <div class="controls">
    <input type="search" id="search" placeholder="搜索任务 ID / 名称 / 备注…">
    <button data-filter="all" class="active">全部</button>
    <button data-filter="doing">🔴 进行中</button>
    <button data-filter="todo">🟡 待办</button>
    <button data-filter="partial">🔵 部分完成</button>
    <button data-filter="deferred">⏸ 暂缓</button>
    <button data-filter="done">✅ 已完成</button>
  </div>

  <div class="grid">${modCards}</div>

  ${section('🔴 正在做', stats.doing, 'doing')}
  ${section('🟡 还没做', stats.todo, 'todo')}
  ${section('🔵 部分完成', stats.partial, 'partial')}
  ${section('⏸ 暂缓或由外部流程承担', stats.deferred, 'deferred')}
  ${section('✅ 已完成（共 ' + stats.done.length + ' 项）', stats.done, 'done')}

  <footer>本页面由 <code>node scripts/gen-status.mjs</code> 自动生成，完整任务明细见 docs/03-TASKS.md</footer>
</div>

<script>
  const search = document.getElementById('search')
  const buttons = document.querySelectorAll('.controls button')
  let filter = 'all'
  const rows = () => Array.from(document.querySelectorAll('.task-table tr[data-status]'))
  const apply = () => {
    const q = search.value.trim().toLowerCase()
    rows().forEach((tr) => {
      const statusOk = filter === 'all' || tr.dataset.status === filter
      const textOk = !q || tr.textContent.toLowerCase().includes(q)
      tr.classList.toggle('hidden', !(statusOk && textOk))
    })
    document.querySelectorAll('.section').forEach((sec) => {
      const visible = Array.from(sec.querySelectorAll('tr[data-status]')).some((tr) => !tr.classList.contains('hidden'))
      sec.classList.toggle('hidden', !visible)
    })
  }
  search.addEventListener('input', apply)
  buttons.forEach((btn) => {
    btn.addEventListener('click', () => {
      buttons.forEach((b) => b.classList.remove('active'))
      btn.classList.add('active')
      filter = btn.dataset.filter
      apply()
    })
  })
</script>
</body>
</html>`
}

// ─── 主流程 ───
const tasks = parseTasks(readFileSync(TASKS_PATH, 'utf8'))
const byModule = summarize(tasks)
const done = tasks.filter((t) => t.status === STATUS.done)
const doing = tasks.filter((t) => t.status === STATUS.doing)
const todo = tasks.filter((t) => t.status === STATUS.todo)
const partial = tasks.filter((t) => t.status === STATUS.partial)
const deferred = tasks.filter((t) => t.status === STATUS.deferred)
const other = tasks.filter((t) => t.status === STATUS.other)
const total = {
  total: tasks.length,
  done: done.length,
  doing: doing.length,
  todo: todo.length,
  partial: partial.length,
  deferred: deferred.length,
  other: other.length,
}
const stats = { modules: byModule.modules, phases: byModule.phases, total, done, doing, todo, partial, deferred, other }
const now = new Date().toLocaleString('zh-CN', { timeZone: 'Asia/Shanghai', hour12: false })

mkdirSync(dirname(HTML_PATH), { recursive: true })
writeFileSync(HTML_PATH, renderHtml(now, stats).replace(/[ \t]+$/gm, ''), 'utf8')
writeFileSync(MD_PATH, renderMarkdown(now, stats).replace(/[ \t]+$/gm, ''), 'utf8')

console.log(`✅ 看板已生成：`)
console.log(`  HTML  → ${HTML_PATH}`)
console.log(`  MD    → ${MD_PATH}`)
console.log(`  任务统计：${total.total} 项（${total.done} 完成 / ${total.doing} 进行中 / ${total.todo} 待办 / ${total.partial} 部分 / ${total.deferred} 暂缓 / ${total.other} 其他）`)
