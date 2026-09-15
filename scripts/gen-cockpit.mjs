#!/usr/bin/env node
/**
 * Blade 项目全景台生成器 v2
 *
 * 三源交叉验证：
 *   1. git      —— 分支合并状态、模块提交历史
 *   2. 代码     —— 后端端点（@GetMapping/@PostMapping…）、前端 api 调用、页面、迁移
 *   3. 文档     —— 03-TASKS.md 任务勾选、05-CHANGELOG.md 里程碑
 *
 * v2 新增下钻：模块 → 资源 → 增删改查，逐个接口对比 master 与工作分支，
 * 回答「这个功能的增删改查到底做没做、上线没有」。
 *
 * 产物：outputs/cockpit.html（单文件、零 CDN 依赖、可离线打开）
 * 用法：node scripts/gen-cockpit.mjs
 */

import { execSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const OUT = path.join(ROOT, 'outputs', 'cockpit.html');

const sh = (cmd) => execSync(cmd, { cwd: ROOT, encoding: 'utf8', maxBuffer: 1 << 28 }).trim();
const trySh = (cmd, fallback = '') => { try { return sh(cmd); } catch { return fallback; } };
const readRel = (p) => { const abs = path.join(ROOT, p); return fs.existsSync(abs) ? fs.readFileSync(abs, 'utf8') : ''; };
const esc = (s) => String(s ?? '').replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
const jesc = (s) => String(s ?? '').replace(/</g, '\\u003c').replace(/>/g, '\\u003e').replace(/&/g, '\\u0026');

const SKIP_DIRS = new Set(['node_modules', '.git', 'target', 'dist', '.mimosa', '.v2c', '.video_agent', '.runtime', 'tmp', 'out']);
function walk(dir, filter, out = []) {
  const abs = path.join(ROOT, dir);
  if (!fs.existsSync(abs)) return out;
  for (const e of fs.readdirSync(abs, { withFileTypes: true })) {
    const rel = path.join(dir, e.name);
    if (e.isDirectory()) { if (!SKIP_DIRS.has(e.name)) walk(rel, filter, out); }
    else if (filter(rel)) out.push(rel);
  }
  return out;
}
const daysAgo = (d) => Math.round((Date.now() - new Date(d + 'T00:00:00Z').getTime()) / 86400000);

/* ═══════════════ 1. Git 证据层 ═══════════════ */

const HEAD_BRANCH = trySh('git rev-parse --abbrev-ref HEAD', 'unknown');
const MASTER_FILES = new Set(trySh('git ls-tree -r master --name-only', '').split('\n').filter(Boolean));
const HEAD_FILES = new Set(trySh('git ls-tree -r HEAD --name-only', '').split('\n').filter(Boolean));
const masterHas = (frag) => [...MASTER_FILES].filter((f) => f.includes(frag)).length;
const headHas = (frag) => [...HEAD_FILES].filter((f) => f.includes(frag)).length;

const branches = trySh("git for-each-ref --format='%(refname:short)' refs/heads", '')
  .split('\n').filter(Boolean).map((name) => {
    const [behind, ahead] = trySh(`git rev-list --left-right --count master...${name}`, '0\t0').split(/\s+/).map(Number);
    const date = trySh(`git log -1 --format=%cs ${name}`);
    const base = trySh(`git merge-base master ${name}`);
    const files = base ? trySh(`git diff --name-only ${base} ${name}`, '').split('\n').filter(Boolean) : [];
    const ctrls = [...new Set(files.map((f) => (f.match(/controller\/(\w+)Controller\.java$/) || [])[1]).filter(Boolean))];
    const adminViews = [...new Set(files.map((f) => (f.match(/blade-admin\/src\/views\/([^/]+)\//) || [])[1]).filter(Boolean))];
    const mobileViews = [...new Set(files.map((f) => (f.match(/blade-mobile\/src\/views\/([^/]+)\//) || [])[1]).filter(Boolean))];
    return {
      name, ahead, behind, date, merged: ahead === 0, age: date ? daysAgo(date) : 999,
      subject: trySh(`git log -1 --format=%s ${name}`),
      totalFiles: files.length,
      backend: files.filter((f) => f.startsWith('blade-backend/')).length,
      admin: files.filter((f) => f.startsWith('blade-admin/')).length,
      mobile: files.filter((f) => f.startsWith('blade-mobile/')).length,
      docs: files.filter((f) => f.startsWith('docs/')).length,
      ctrls, adminViews, mobileViews,
    };
  }).sort((a, b) => b.ahead - a.ahead || a.name.localeCompare(b.name));

const unmerged = branches.filter((b) => !b.merged);
const stale = unmerged.filter((b) => b.age > 60);
const maxAhead = unmerged.reduce((m, b) => Math.max(m, b.ahead), 0);

/* ═══════════════ 2. 后端端点解析 ═══════════════ */

function parseController(src, fallbackPrefix) {
  const lines = src.split('\n');
  let prefix = null, pending = null;
  const endpoints = [];
  for (const line of lines) {
    if (!prefix) {
      const rm = line.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]+)"/);
      if (rm) { prefix = rm[1]; continue; }
    }
    const mm = line.match(/@(Get|Post|Put|Delete|Patch)Mapping(?:\s*\(\s*(?:value\s*=\s*)?"([^"]*)")?/);
    if (mm) { pending = { method: mm[1].toUpperCase(), sub: mm[2] || '' }; continue; }
    if (pending) {
      const hm = line.match(/public\s+[\w<>,\[\]\s.?!]+\s+(\w+)\s*\(/);
      if (hm) {
        endpoints.push({ method: pending.method, path: (prefix || fallbackPrefix || '') + pending.sub, handler: hm[1] });
        pending = null;
      } else if (/^\s*}/.test(line)) { pending = null; }
    }
  }
  return { prefix: prefix || fallbackPrefix || '', endpoints };
}

const norm = (p) => p.replace(/^\/api/, '').replace(/\/+$/, '') || '/';

const controllerFiles = walk('blade-backend', (f) => /controller\/\w+Controller\.java$/.test(f));
const controllers = controllerFiles.map((file) => {
  const name = (file.match(/(\w+)Controller\.java$/) || [])[1];
  const dir = file.replace(/\/controller\/[^/]+$/, '');
  const head = parseController(readRel(file), '/api/' + name.toLowerCase());
  const prefix = head.prefix;
  const inMaster = MASTER_FILES.has(file);
  const master = inMaster ? parseController(trySh(`git show master:${file}`, ''), prefix) : { endpoints: [] };
  const masterSet = new Set(master.endpoints.map((e) => e.method + ' ' + e.path));
  return {
    name, file, dir, prefix, resource: norm(prefix),
    endpoints: head.endpoints.map((e) => ({ ...e, inMaster: masterSet.has(e.method + ' ' + e.path) })),
    masterCount: master.endpoints.length,
    fileInMaster: inMaster,
  };
}).filter((c) => c.prefix);

const prefixIndex = controllers.map((c) => ({ prefix: c.resource, resource: c.resource }))
  .sort((a, b) => b.prefix.length - a.prefix.length);

function matchResource(callPath) {
  const p = norm(callPath);
  const hit = prefixIndex.find((x) => p === x.prefix || p.startsWith(x.prefix + '/'));
  return hit ? hit.resource : null;
}

/* ═══════════════ 3. 前端 API 调用解析 ═══════════════ */

function parseFrontendApi(src) {
  const out = [];
  const re = /client\s*\.\s*(get|post|put|delete|patch)\s*(?:<[^(]*>)?\s*\(\s*[`'"]([^`'"]+)[`'"]/g;
  let m;
  while ((m = re.exec(src))) out.push({ method: m[1].toUpperCase(), path: m[2] });
  return out;
}

const frontend = { admin: [], mobile: [] };
for (const f of walk('blade-admin/src/api', (x) => x.endsWith('.ts'))) {
  const file = path.basename(f, '.ts');
  for (const ep of parseFrontendApi(readRel(f))) frontend.admin.push({ ...ep, file, resource: matchResource(ep.path) });
}
for (const f of walk('blade-mobile/src/api', (x) => x.endsWith('.ts'))) {
  const file = path.basename(f, '.ts');
  for (const ep of parseFrontendApi(readRel(f))) frontend.mobile.push({ ...ep, file, resource: matchResource(ep.path) });
}

/* ═══════════════ 4. CRUD 归类 ═══════════════ */

const OPS = [
  { key: 'read', label: '查', hint: 'GET：列表 / 详情 / 导出' },
  { key: 'create', label: '增', hint: 'POST 到资源根路径' },
  { key: 'update', label: '改', hint: 'PUT / PATCH' },
  { key: 'delete', label: '删', hint: 'DELETE' },
  { key: 'action', label: '业务动作', hint: 'POST 到子路径：付款、发货、取消…' },
];

function classify(ep, resourcePrefix) {
  if (ep.method === 'GET') return 'read';
  if (ep.method === 'DELETE') return 'delete';
  if (ep.method === 'PUT' || ep.method === 'PATCH') return 'update';
  const rest = norm(ep.path).slice(resourcePrefix.length).replace(/^\//, '');
  return rest === '' ? 'create' : 'action';
}

const resources = {};
for (const c of controllers) {
  const res = resources[c.resource] || (resources[c.resource] = {
    key: c.resource, controllers: [], endpoints: [], ops: {}, adminOps: {}, mobileOps: {}, adminCalls: [], mobileCalls: [],
  });
  res.controllers.push(c.name);
  for (const ep of c.endpoints) res.endpoints.push({ ...ep, op: classify(ep, c.resource), controller: c.name });
}
for (const [key, res] of Object.entries(resources)) {
  for (const op of OPS) {
    const eps = res.endpoints.filter((e) => e.op === op.key);
    res.ops[op.key] = { head: eps.length, master: eps.filter((e) => e.inMaster).length };
  }
  for (const src of ['admin', 'mobile']) {
    const calls = frontend[src].filter((c) => c.resource === key);
    if (src === 'admin') res.adminCalls = calls; else res.mobileCalls = calls;
    const target = src === 'admin' ? res.adminOps : res.mobileOps;
    for (const op of OPS) target[op.key] = calls.filter((c) => classify(c, key) === op.key).length;
  }
}

/* ═══════════════ 5. 代码资产 ═══════════════ */

const migrations = walk('blade-backend', (f) => /\/db\/migration\/V\d+__.+\.sql$/.test(f))
  .map((f) => (f.match(/V(\d+)__/) || [])[1]).filter(Boolean).map(Number).sort((a, b) => a - b);
const entities = walk('blade-backend', (f) => /\/entity\/\w+\.java$/.test(f)).length;
const testFiles = walk('blade-backend', (f) => /\w*Test\w*\.java$/.test(f)).map((f) => path.basename(f, '.java'));
const adminViews = walk('blade-admin/src/views', (f) => f.endsWith('.vue'));
const mobileViews = walk('blade-mobile/src', (f) => f.endsWith('.vue'));

const migMaster = [...MASTER_FILES].map((f) => (f.match(/V(\d+)__/) || [])[1]).filter(Boolean).map(Number);
const V_MASTER = migMaster.length ? Math.max(...migMaster) : 0;
const V_HEAD = migrations.length ? Math.max(...migrations) : 0;

/* ═══════════════ 6. 文档证据层 ═══════════════ */

const PHASE_RULES = [
  [/iPad/i, 'catalog'], [/ERP/i, 'erp'], [/文件|资产|存储/, 'file'],
  [/WhatsApp|纸单|Agent/i, 'agent'], [/权限|账号|凭证/, 'permission'],
  [/看板|统计|分析/, 'analytics'], [/客户/, 'customer'],
  [/订单|履约|状态机|录单/, 'order'], [/库存/, 'inventory'],
  [/商品/, 'catalog'], [/骨架|测试基线|基础/, 'platform'],
];
const pickModule = (phase) => (PHASE_RULES.find(([re]) => re.test(phase)) || [, 'other'])[1];
const GROUP_LABEL = { '后端开发任务': '后端', 'PC 管理端开发任务（blade-admin）': 'PC 管理端', '移动端开发任务（blade-mobile）': '移动端' };

function normStatus(s) {
  s = (s || '').trim();
  if (/^✅/.test(s)) return 'done';
  if (/^⏸/.test(s)) return 'deferred';
  if (/进行中/.test(s)) return 'doing';
  if (/^⏳/.test(s)) return 'todo';
  if (/部分/.test(s)) return 'partial';
  return 'other';
}

const tasks = [];
{
  const lines = readRel('docs/03-TASKS.md').split('\n');
  let group = null, phase = null;
  for (const line of lines) {
    const h2 = line.match(/^## (.+)$/);
    if (h2) { group = GROUP_LABEL[h2[1].trim()] || null; phase = null; continue; }
    const h3 = line.match(/^### (.+)$/);
    if (h3) { phase = h3[1].trim(); continue; }
    if (!group || !phase) continue;
    const row = line.match(/^\|\s*([A-Z][A-Z0-9-]*)\s*\|([^|]+)\|([^|]+)\|([^|]*)\|/);
    if (!row) continue;
    tasks.push({
      id: row[1], name: row[2].trim(), status: normStatus(row[3]),
      note: row[4].trim(), group, phase, module: pickModule(phase),
    });
  }
}
const active = tasks.filter((t) => t.status !== 'deferred' && t.status !== 'other');
const groupStats = {};
for (const t of active) { const s = groupStats[t.group] || (groupStats[t.group] = { done: 0, total: 0 }); s.total++; if (t.status === 'done') s.done++; }

/* ═══════════════ 7. 里程碑 / 文档导航 ═══════════════ */

const milestones = [];
{
  let cur = null;
  for (const line of readRel('docs/05-CHANGELOG.md').split('\n')) {
    const d = line.match(/^## (\d{4}-\d{2}-\d{2})/);
    if (d) { cur = { date: d[1], items: [] }; milestones.push(cur); continue; }
    const s = line.match(/^### \[([^\]]+)\]\s*[-–]?\s*(.*)$/);
    if (s && cur) cur.items.push({ tag: s[1].trim(), title: s[2].trim() });
  }
}

const DOC_ORDER = ['CLAUDE.md', 'docs/STATUS.md', 'docs/01-README.md', 'docs/02-PRD.md', 'docs/03-TASKS.md', 'docs/SESSION_CONTEXT.md'];
const docs = ['CLAUDE.md', 'README.md', ...walk('docs', (f) => f.endsWith('.md'))].filter((f) => fs.existsSync(path.join(ROOT, f))).map((f) => {
  const st = fs.statSync(path.join(ROOT, f));
  const mtime = st.mtime.toISOString().slice(0, 10);
  let cat = '参考';
  if (DOC_ORDER.includes(f)) cat = '必读';
  else if (f.startsWith('docs/superpowers/')) cat = '实施计划';
  else if (/^docs\/(reference|architecture|testing)\//.test(f)) cat = '参考';
  else if (/^docs\/(0[6-9]|1[0-9])-/.test(f)) cat = '设计文档';
  return { path: f, cat, size: st.size, mtime, age: daysAgo(mtime), stale: daysAgo(mtime) > 90 && cat !== '必读', rank: DOC_ORDER.indexOf(f) };
}).sort((a, b) => {
  if (a.rank >= 0 || b.rank >= 0) return a.rank < 0 ? 1 : b.rank < 0 ? -1 : a.rank - b.rank;
  const order = ['设计文档', '实施计划', '参考'];
  return order.indexOf(a.cat) - order.indexOf(b.cat) || a.path.localeCompare(b.path);
});

/* ═══════════════ 8. 模块模型 ═══════════════ */

const MODULES = {
  catalog:    { name: '商品与 SKU',   color: '#58a6ff', ctrl: ['Product', 'ProductCategory', 'Catalog'], resources: ['/catalog', '/products', '/product-categories'], admin: ['products', 'catalog'], mobile: ['product'] },
  inventory:  { name: '库存',         color: '#3fb950', ctrl: ['Inventory', 'Warehouse'], resources: ['/inventory', '/warehouse'], admin: ['inventory'], mobile: ['inventory'] },
  order:      { name: '订单与履约',   color: '#f0883e', ctrl: ['Order', 'OrderDelivery', 'OrderDraft'], resources: ['/orders', '/order-deliveries', '/order-drafts'], admin: ['orders'], mobile: ['order'] },
  customer:   { name: '客户',         color: '#bc8cff', ctrl: ['Customer', 'CustomerTag'], resources: ['/customers', '/customer-tags'], admin: ['customers', 'clients'], mobile: [] },
  file:       { name: '文件与资产',   color: '#39c5cf', ctrl: ['File', 'FileBinding', 'FileFolder', 'FileCleanup'], resources: ['/files', '/file-folders', '/files/cleanup'], admin: ['files'], mobile: [] },
  analytics:  { name: '看板与分析',   color: '#d2a8ff', ctrl: ['Analytics', 'Dashboard'], resources: ['/analytics', '/dashboard'], admin: ['analytics', 'dashboard'], mobile: ['dashboard'] },
  permission: { name: '权限与账号',   color: '#ff7b72', ctrl: ['Permission', 'Role', 'User', 'Login'], resources: ['/permissions', '/system/roles', '/system/users', '/auth'], admin: ['system', 'personal', 'login'], mobile: ['Login'] },
  agent:      { name: '外部 Agent',   color: '#e3b341', ctrl: ['AgentAnalytics', 'AgentCatalog', 'AgentOrderDraft', 'WhatsappAdmin', 'WhatsappAgent', 'WhatsappCollector'], resources: ['/agent/analytics', '/agent/catalog', '/agent/order-drafts', '/agent/whatsapp', '/whatsapp', '/internal/whatsapp'], admin: ['whatsapp'], mobile: [] },
  erp:        { name: 'ERP 接替',     color: '#7d8590', ctrl: [], resources: [], admin: [], mobile: [] },
  platform:   { name: '平台基座',     color: '#7d8590', ctrl: ['VbenAdmin'], resources: ['/vbenadmin'], admin: ['layout'], mobile: [] },
};

const model = {};
for (const [key, m] of Object.entries(MODULES)) {
  const inMaster = m.ctrl.filter((c) => masterHas(`${c}Controller.java`)).length;
  const inHead = m.ctrl.filter((c) => headHas(`${c}Controller.java`)).length;
  const verdict = !m.ctrl.length ? 'n/a' : inMaster === m.ctrl.length ? 'master' : inHead > 0 ? 'branch' : 'missing';

  const tt = tasks.filter((t) => t.module === key && t.status !== 'other');
  const act = tt.filter((t) => t.status !== 'deferred');
  const done = act.filter((t) => t.status === 'done').length;

  const phases = {};
  for (const t of act) {
    const p = phases[t.phase] || (phases[t.phase] = { group: t.group, done: 0, total: 0 });
    p.total++; if (t.status === 'done') p.done++;
  }

  const dirs = [
    ...controllers.filter((c) => m.ctrl.includes(c.name)).map((c) => c.dir),
    ...(m.admin || []).map((a) => `blade-admin/src/views/${a}`),
    ...(m.mobile || []).map((x) => `blade-mobile/src/views/${x}`),
  ];
  const recentCommits = dirs.length
    ? trySh(`git log -6 --format='%h|%cs|%s' -- ${dirs.join(' ')}`, '').split('\n').filter(Boolean).map((l) => {
        const [hash, date, subject] = l.split('|'); return { hash, date, subject };
      })
    : [];

  model[key] = {
    key, name: m.name, color: m.color, verdict, inMaster, inHead, ctrlCount: m.ctrl.length,
    resources: m.resources.filter((r) => resources[r]),
    phases: Object.entries(phases).map(([name, p]) => ({ name, ...p })),
    tasks: tt.map((t) => ({ id: t.id, name: t.name, status: t.status, note: t.note, group: t.group, phase: t.phase })),
    done, total: act.length,
    adminPages: adminViews.filter((v) => (m.admin || []).some((a) => v.includes(`/views/${a}/`))).map((v) => v.replace('blade-admin/src/views/', '')),
    mobilePages: mobileViews.filter((v) => (m.mobile || []).some((x) => v.includes(`/views/${x}/`))).map((v) => v.replace('blade-mobile/src/', '')),
    recentCommits,
  };
}

const branchOnlyModules = Object.entries(model).filter(([, v]) => v.verdict === 'branch').map(([k]) => k);
const branchOnlyEndpoints = Object.values(resources).flatMap((r) => r.endpoints.filter((e) => !e.inMaster).map((e) => ({ ...e, resource: r.key })));

/* ═══════════════ 8.5 规划与在途 ═══════════════ */

const TASK_ID_RE = /\b(?:BE|BA|FE|TEST|ERP|ARCH|ORDER|DEC)-[0-9][0-9A-Z-]*\b/g;
const taskById = new Map(tasks.map((t) => [t.id, t]));
const branchByName = new Map(branches.map((b) => [b.name, b]));

const PLAN_STATE = {
  active: { label: '进行中', tone: 'info' },
  pending: { label: '待上线', tone: 'warn' },
  planned: { label: '规划中', tone: 'muted' },
  done: { label: '已完成', tone: 'ok' },
};
// 主干分支不算「关联分支」，否则文档里提一句 master 就会被误判成已落地
const TRUNK = new Set(['master', 'develop', 'HEAD']);

const plans = walk('docs/superpowers/plans', (f) => f.endsWith('.md')).map((file) => {
  const src = readRel(file);
  const base = path.basename(file);
  const title = (src.match(/^#\s+(.+)$/m) || [, base])[1].trim();
  const statusLine = (src.match(/^(?:>\s*)?状态[:：]\s*(.+)$/m) || [, ''])[1].trim();
  const declared = statusLine.replace(/[`。，,]+$/, '').trim();

  const ids = [...new Set(src.match(TASK_ID_RE) || [])];
  const refTasks = ids.map((id) => taskById.get(id)).filter(Boolean);
  const unknownIds = ids.filter((id) => !taskById.has(id));
  const refBranches = [...branchByName.keys()].filter((b) => !TRUNK.has(b) && src.includes(b));
  const refMigs = [...new Set((src.match(/\bV\d+__/g) || []).map((s) => Number(s.match(/\d+/)[0])))].sort((a, b) => a - b);
  const liveMig = refMigs.filter((v) => v <= V_MASTER);
  const pendingMig = refMigs.filter((v) => v > V_MASTER);

  const act = refTasks.filter((t) => t.status !== 'deferred');
  const done = act.filter((t) => t.status === 'done').length;
  const mergedRef = refBranches.filter((b) => branchByName.get(b).merged);
  const unmergedRef = refBranches.filter((b) => !branchByName.get(b).merged);
  const moduleKey = pickModule(title + ' ' + base);

  let state, reason;
  if (act.length) {
    if (done === act.length) {
      if (pendingMig.length || unmergedRef.length) {
        state = 'pending';
        reason = `引用 ${act.length} 项任务全部完成，但${pendingMig.length ? `迁移 ${pendingMig.map((v) => 'V' + v).join('/')} 未上线` : ''}${pendingMig.length && unmergedRef.length ? '、' : ''}${unmergedRef.length ? `分支 ${unmergedRef.join('、')} 未合并` : ''}`;
      } else { state = 'done'; reason = `引用 ${act.length} 项任务全部完成${refMigs.length ? `，迁移 ${refMigs.map((v) => 'V' + v).join('/')} 均已上线` : ''}`; }
    } else if (done === 0 && !unmergedRef.length && !mergedRef.length && !liveMig.length) { state = 'planned'; reason = `引用 ${act.length} 项任务全部未开始`; }
    else {
      state = 'active';
      const live = unmergedRef.map((b) => `${b}（${branchByName.get(b).ahead} 个提交未合并）`);
      reason = `引用任务勾选完成 ${done}/${act.length}` +
        (live.length ? `，但分支 ${live.join('、')} 已有实际开发，03-TASKS.md 的勾选落后于代码` : '') +
        (pendingMig.length ? `，${pendingMig.length} 个迁移未上线` : '');
    }
  } else if (declared) {
    if (/待生产|待上线|待发布|待执行|待验收/.test(declared)) { state = 'pending'; reason = `无任务引用，文档声明：${declared}`; }
    else if (/完成|已交付|已上线|已实施|已收口/.test(declared)) { state = 'done'; reason = `无任务引用，文档声明：${declared}`; }
    else if (/暂缓|待定|搁置|未启动|后续实施|后续/.test(declared)) { state = 'planned'; reason = `无任务引用，文档声明：${declared}`; }
    else { state = 'active'; reason = `无任务引用，文档声明：${declared}`; }
  } else if (mergedRef.length) { state = 'done'; reason = `关联分支 ${mergedRef.join('、')} 已并入 master`; }
  else { state = 'planned'; reason = '无任务引用、无关联分支'; }

  let conflict = '';
  if (/暂缓|搁置|未启动|待定/.test(declared) && state === 'done') conflict = '文档写暂缓，证据显示已完成';
  if (/完成|已上线|已交付/.test(declared) && state === 'planned') conflict = '文档写完成，证据显示未开始';

  return {
    file, base, title, date: (base.match(/^(\d{4}-\d{2}-\d{2})/) || [, ''])[1],
    declared, state, reason, conflict, moduleKey,
    taskIds: act.map((t) => t.id), taskDone: done, taskTotal: act.length,
    unknownIds: unknownIds.slice(0, 8),
    branches: refBranches.map((b) => ({ name: b, merged: branchByName.get(b).merged })),
    migs: refMigs.map((v) => ({ v, live: v <= V_MASTER })),
    size: src.length,
  };
}).sort((a, b) => b.date.localeCompare(a.date));

const planByState = { active: [], pending: [], planned: [], done: [] };
for (const p of plans) planByState[p.state].push(p);

// 需求池与决策
const requisitions = [];
{
  const src = readRel('docs/04-REQUISITION_LOG.md');
  const parts = src.split(/^###\s+\[(\d{4}-\d{2}-\d{2})\]\s*需求\s*#(\d+)\s*[-–]\s*(.+?)\s*$/m);
  for (let i = 1; i < parts.length; i += 4) {
    const body = parts[i + 3] || '';
    const result = ((body.match(/\*\*结果\*\*[：:]\s*(.+)/) || [, ''])[1] || '').trim();
    if (!result || /采纳 \/ /.test(result)) continue; // 跳过格式说明模板
    requisitions.push({ date: parts[i], no: parts[i + 1], title: parts[i + 2].trim(), result });
  }
}
const decisions = [];
{
  for (const line of readRel('docs/reference/DECISIONS_LOG.md').split('\n')) {
    const m = line.match(/^\|\s*(DEC-\d+)\s*\|([^|]+)\|([^|]+)\|([^|]+)\|/);
    if (m) decisions.push({ id: m[1], title: m[2].trim(), date: m[3].trim(), status: m[4].trim() });
  }
}

// 未开始的任务（按模块归组）——「后续要开发什么」的最细粒度
const notStarted = {};
for (const t of tasks) {
  if (t.status !== 'todo' && t.status !== 'doing' && t.status !== 'partial') continue;
  (notStarted[t.module] || (notStarted[t.module] = [])).push(t);
}
for (const k of Object.keys(notStarted)) notStarted[k].sort((a, b) => a.id.localeCompare(b.id));
const notStartedTotal = Object.values(notStarted).reduce((a, l) => a + l.length, 0);

/* ═══════════════ 9. 风险清单 ═══════════════ */

const risks = [];
if (V_HEAD > V_MASTER) risks.push({ level: 'high', title: `生产分支 master 落后 ${V_HEAD - V_MASTER} 个数据库迁移版本`, detail: `master 停在 V${V_MASTER}，工作分支已到 V${V_HEAD}。NAS 只部署 master，这些迁移涉及的功能尚未上线。` });
if (branchOnlyModules.length) risks.push({ level: 'high', title: `${branchOnlyModules.length} 个模块的代码只存在于分支，master 上不存在`, detail: branchOnlyModules.map((k) => model[k].name).join('、') + '。这些模块在 03-TASKS.md 中已有大量 ✅ 勾选，容易被误读为已上线。' });
if (branchOnlyEndpoints.length) risks.push({ level: 'high', title: `${branchOnlyEndpoints.length} 个接口只存在于工作分支，master 上没有`, detail: '在「接口与 CRUD」标签页可以逐个查到，这是「文档说完成、生产不可用」最直接的证据。' });
if (maxAhead >= 20) risks.push({ level: 'high', title: `单个未合并分支领先 master ${maxAhead} 个提交`, detail: unmerged.find((b) => b.ahead === maxAhead).name + ' 长期未回流，分支越大合并冲突和回归风险越高。' });
const md = trySh('git rev-list --left-right --count master...develop', '0\t0').split(/\s+/).map(Number);
if (md[0] === 0 && md[1] === 0) risks.push({ level: 'medium', title: 'master 与 develop 完全同点，规则 6 的 develop/release 流程实际未使用', detail: 'CLAUDE.md 规则 6 规定集成测试用 develop、上线候选用 release/*，但两者无差异。' });
if (stale.length) risks.push({ level: 'medium', title: `${stale.length} 个未合并分支超过 60 天未提交`, detail: stale.map((b) => `${b.name}（${b.age} 天，领先 ${b.ahead}）`).join('、') + '。' });
const staleDocs = docs.filter((d) => d.stale).length;
if (staleDocs >= 5) risks.push({ level: 'low', title: `${staleDocs} 篇文档超过 90 天未更新`, detail: '文档越多，过期内容越容易被当成现状。' });

/* ═══════════════ 10. 渲染数据 ═══════════════ */

const pct = (a, b) => (b ? Math.round((a / b) * 100) : 0);
const TOTAL = ['后端', 'PC 管理端', '移动端'].reduce((a, g) => ({ done: a.done + (groupStats[g]?.done || 0), total: a.total + (groupStats[g]?.total || 0) }), { done: 0, total: 0 });
const TOTAL_ENDPOINTS = Object.values(resources).reduce((a, r) => a + r.endpoints.length, 0);
const bar = (done, total, color, w = 220) => `<div class="bar" style="--w:${w}px"><i style="width:${pct(done, total)}%;background:${color}"></i></div><span class="pct" style="color:${color}">${pct(done, total)}%</span>`;
const statCard = (l, v, s, tone = '') => `<div class="stat ${tone}"><div class="stat-l">${esc(l)}</div><div class="stat-v">${esc(v)}</div>${s ? `<div class="stat-s">${esc(s)}</div>` : ''}</div>`;
const VERDICT = { master: ['ok', '已在 master'], branch: ['warn', '只在分支'], missing: ['bad', '无代码证据'], 'n/a': ['muted', '无独立接口'] };
const STATUS_LABEL = { done: '✅ 完成', todo: '⏳ 待办', doing: '🔄 进行中', partial: '部分完成', deferred: '⏸ 暂缓' };

function opCell(res, op) {
  const be = res.ops[op.key], pc = res.adminOps[op.key] || 0, mb = res.mobileOps[op.key] || 0;
  const beCls = be.head === 0 ? 'off' : be.master === be.head ? 'on' : 'half';
  const beTxt = be.head === 0 ? '' : be.master === be.head ? String(be.head) : `${be.master}/${be.head}`;
  const chip = (cls, label, n) => `<span class="chip ${cls}" title="${label}：${n ? n + ' 个' : '无'}">${label}<b>${n || '—'}</b></span>`;
  return `<td class="opcell">${chip(beCls, 'BE', beTxt)}${chip(pc > 0 ? 'on' : 'off', 'PC', pc)}${chip(mb > 0 ? 'on' : 'off', 'MB', mb)}</td>`;
}

const moduleCards = Object.entries(model).filter(([, v]) => v.total).map(([key, v]) => {
  const [tone, label] = VERDICT[v.verdict];
  const phaseRows = [...v.phases].sort((a, b) => (a.group + a.name).localeCompare(b.group + b.name)).map((p) => {
    const cls = p.done === p.total ? 'ph-done' : p.done === 0 ? 'ph-todo' : 'ph-part';
    return `<li class="${cls}"><span class="ph-name">${esc(p.name)}</span><span class="ph-tag">${esc(p.group)}</span><span class="ph-num">${p.done}/${p.total}</span></li>`;
  }).join('');
  const doneTasks = v.tasks.filter((t) => t.status === 'done').slice(0, 5).map((t) => t.name);
  const eps = v.resources.reduce((a, r) => a + resources[r].endpoints.length, 0);
  const off = v.resources.reduce((a, r) => a + resources[r].endpoints.filter((e) => !e.inMaster).length, 0);
  return `<section class="mcard" style="--c:${v.color}" data-search="${esc([v.name, ...v.tasks.map((t) => t.id + ' ' + t.name), ...v.resources].join(' '))}">
  <header>
    <span class="dot" style="background:${v.color}"></span>
    <h3>${esc(v.name)}</h3>
    <span class="badge ${tone}">${label}</span>
    <span class="spacer"></span>
    <span class="mnum">${v.done}/${v.total}</span>
    ${bar(v.done, v.total, v.color, 110)}
  </header>
  <div class="mevi">${v.ctrlCount ? `控制器 ${v.inMaster}/${v.ctrlCount} 在 master` : '无独立控制器'}　·　资源 ${v.resources.length} 个　·　接口 ${eps} 个${off ? `（<span style="color:var(--warn)">${off} 未上线</span>）` : ''}</div>
  <ul class="phase-list">${phaseRows || '<li class="ph-todo"><span class="ph-name">（暂无任务登记）</span></li>'}</ul>
  ${doneTasks.length ? `<div class="mdone">已完成：${doneTasks.map(esc).join('　·　')}</div>` : ''}
  <button class="drill" data-open="${key}">查看详情 · 增删改查 →</button>
</section>`;
}).join('');

const detailData = {};
for (const [key, v] of Object.entries(model)) {
  detailData[key] = {
    key, name: v.name, color: v.color, verdictLabel: VERDICT[v.verdict][1], verdict: v.verdict,
    done: v.done, total: v.total, ctrlCount: v.ctrlCount, inMaster: v.inMaster, inHead: v.inHead,
    phases: v.phases, tasks: v.tasks, adminPages: v.adminPages, mobilePages: v.mobilePages, recentCommits: v.recentCommits,
    resources: v.resources.map((r) => ({
      key: r, controllers: resources[r].controllers, ops: resources[r].ops,
      adminOps: resources[r].adminOps, mobileOps: resources[r].mobileOps,
      endpoints: resources[r].endpoints,
      adminCalls: resources[r].adminCalls.map((c) => ({ method: c.method, path: c.path })),
      mobileCalls: resources[r].mobileCalls.map((c) => ({ method: c.method, path: c.path })),
    })),
  };
}

/* ═══════════════ 8.55 历史快照与进展 ═══════════════ */
/*
 * 每次生成顺手把关键指标存成一天一份的小快照，下次生成时和上一份相减，
 * 得到「这几天变了什么」。纯文件，不起服务，不联网。
 * 整段包在 try/catch 里：历史记录失败绝不影响看板本身的生成。
 */

const HIST_DIR = path.join(ROOT, 'outputs', 'cockpit-history');
const KEEP_SNAPSHOTS = 180;
const TODAY = new Date().toISOString().slice(0, 10);

function snapshot() {
  const modules = {};
  for (const [k, m] of Object.entries(model)) {
    modules[k] = {
      done: m.done, total: m.total,
      eps: m.resources.reduce((a, r) => a + resources[r].endpoints.length, 0),
      unlive: m.resources.reduce((a, r) => a + resources[r].endpoints.filter((e) => !e.inMaster).length, 0),
    };
  }
  return {
    date: TODAY, generatedAt: new Date().toISOString(),
    branch: HEAD_BRANCH, head: trySh('git rev-parse --short HEAD'),
    vMaster: V_MASTER, vHead: V_HEAD,
    tasksDone: TOTAL.done, tasksTotal: TOTAL.total,
    endpoints: TOTAL_ENDPOINTS,
    endpointsLive: TOTAL_ENDPOINTS - branchOnlyEndpoints.length,
    endpointsUnlive: branchOnlyEndpoints.length,
    branches: branches.length, unmerged: unmerged.length,
    unmergedNames: unmerged.map((b) => b.name),
    plans: {
      active: planByState.active.length, pending: planByState.pending.length,
      planned: planByState.planned.length, done: planByState.done.length,
    },
    notStarted: notStartedTotal,
    modules,
  };
}
const CURRENT = snapshot();

let historyNote = '';
let priorSnaps = [];
let weekSnap = null;
let lastRun = null;
const LAST_RUN = path.join(HIST_DIR, 'last-run.json');
try {
  fs.mkdirSync(HIST_DIR, { recursive: true });
  try { lastRun = JSON.parse(fs.readFileSync(LAST_RUN, 'utf8')); } catch { lastRun = null; }
  const files = fs.readdirSync(HIST_DIR).filter((f) => /^\d{4}-\d{2}-\d{2}\.json$/.test(f)).sort();
  priorSnaps = files.filter((f) => f.slice(0, 10) < TODAY).map((f) => {
    try { return JSON.parse(fs.readFileSync(path.join(HIST_DIR, f), 'utf8')); } catch { return null; }
  }).filter(Boolean);
  const cutoff = new Date(Date.now() - 7 * 86400000).toISOString().slice(0, 10);
  const older = priorSnaps.filter((s) => s.date <= cutoff);
  weekSnap = older.length ? older[older.length - 1] : null;
  if (files.length > KEEP_SNAPSHOTS) files.slice(0, files.length - KEEP_SNAPSHOTS).forEach((f) => { try { fs.unlinkSync(path.join(HIST_DIR, f)); } catch { /* ignore */ } });
  fs.writeFileSync(path.join(HIST_DIR, TODAY + '.json'), JSON.stringify(CURRENT, null, 2));
  fs.writeFileSync(LAST_RUN, JSON.stringify(CURRENT, null, 2));
} catch (e) {
  historyNote = String(e.message || e);
}

const sub = (a, b) => (Number(a) || 0) - (Number(b) || 0);

function diffWith(base) {
  if (!base || !base.modules) return null;
  const modChanges = Object.entries(CURRENT.modules).map(([k, m]) => {
    const b = base.modules[k] || { done: 0, total: 0, eps: 0, unlive: 0 };
    return {
      key: k, name: MODULES[k].name, color: MODULES[k].color,
      dDone: sub(m.done, b.done), dEps: sub(m.eps, b.eps), dUnlive: sub(m.unlive, b.unlive),
      now: m.done, total: m.total, before: b.done,
    };
  }).filter((x) => x.dDone || x.dEps || x.dUnlive)
    .sort((a, b) => Math.abs(b.dDone) + Math.abs(b.dEps) - Math.abs(a.dDone) - Math.abs(a.dEps));
  return {
    base: base.date,
    baseLabel: whenLabel(base),
    days: Math.max(0, Math.round((new Date(CURRENT.date) - new Date(base.date)) / 86400000)),
    baseBranch: base.branch, baseHead: base.head,
    tasksDone: sub(CURRENT.tasksDone, base.tasksDone),
    endpoints: sub(CURRENT.endpoints, base.endpoints),
    endpointsLive: sub(CURRENT.endpointsLive, base.endpointsLive),
    endpointsUnlive: sub(CURRENT.endpointsUnlive, base.endpointsUnlive),
    notStarted: sub(CURRENT.notStarted, base.notStarted),
    unmerged: sub(CURRENT.unmerged, base.unmerged),
    vHead: sub(CURRENT.vHead, base.vHead),
    plansBefore: base.plans || {}, modChanges,
    closed: (base.unmergedNames || []).filter((n) => !CURRENT.unmergedNames.includes(n)),
    opened: CURRENT.unmergedNames.filter((n) => !(base.unmergedNames || []).includes(n)),
  };
}
const histPrev = diffWith(lastRun);
const histWeek = weekSnap && (!histPrev || weekSnap.date !== histPrev.base) ? diffWith(weekSnap) : null;
const trend = [...priorSnaps, CURRENT].slice(-14);

/** 把快照时间写成「今天 14:30 / 昨天 / N 天前」 */
function whenLabel(snap) {
  if (!snap || !snap.generatedAt) return '';
  const t = new Date(snap.generatedAt);
  const d = new Date(); d.setHours(0, 0, 0, 0);
  const days = Math.round((d - new Date(t.getFullYear(), t.getMonth(), t.getDate())) / 86400000);
  const hm = String(t.getHours()).padStart(2, '0') + ':' + String(t.getMinutes()).padStart(2, '0');
  if (days <= 0) return '今天 ' + hm;
  if (days === 1) return '昨天 ' + hm;
  return days + ' 天前';
}

/* ═══════════════ 8.6 询问上下文包 ═══════════════ */

const ASK_DIR = path.join(ROOT, 'outputs', 'ask');
const VERDICT_TEXT = { master: '已在 master，生产可用', branch: '只在分支上，生产不可用', missing: '无代码证据', 'n/a': '无独立接口' };

function contextOf(key) {
  const m = model[key], mod = MODULES[key];
  const L = [];
  L.push(`# 询问上下文：${m.name}`);
  L.push('');
  L.push('你是 Blade 项目（服装批发 ERP，后端 Spring Boot 3 + MyBatis-Plus + MySQL，PC 管理端 Vue3 + Element Plus，移动端 Vue3 PWA + Vuetify）的开发助手。');
  L.push('下面这份快照由 `scripts/gen-cockpit.mjs` 从 git 分支状态、后端控制器注解、前端 api 调用和项目文档自动生成，属于客观事实。');
  L.push('回答要求：只基于本快照回答；快照里没有的信息，明确说「快照里没有」，不要推测。');
  L.push('');
  L.push('## 一、模块概况');
  L.push(`- 模块：${m.name}（${key}）`);
  L.push(`- 生产状态判定：${VERDICT_TEXT[m.verdict]}`);
  L.push(`- 任务完成度：${m.done} / ${m.total}（来源 docs/03-TASKS.md 的人工勾选，不等于生产可用）`);
  if (m.ctrlCount) L.push(`- 控制器：master 上 ${m.inMaster} / ${m.ctrlCount}`);
  const eps = m.resources.reduce((a, r) => a + resources[r].endpoints.length, 0);
  const off = m.resources.reduce((a, r) => a + resources[r].endpoints.filter((e) => !e.inMaster).length, 0);
  L.push(`- 接口：共 ${eps} 个，其中 ${off} 个未上线`);

  if (m.resources.length) {
    L.push('');
    L.push('## 二、资源与增删改查（BE = 后端接口「已上线/总数」，PC = 管理端调用数，MB = 移动端调用数）');
    for (const r of m.resources) {
      L.push(`### ${r}`);
      const res = resources[r];
      for (const op of OPS) {
        const be = res.ops[op.key], pc = res.adminOps[op.key] || 0, mb = res.mobileOps[op.key] || 0;
        L.push(`- ${op.label}：BE ${be.master}/${be.head}　PC ${pc}　MB ${mb}`);
      }
      const unlive = res.endpoints.filter((e) => !e.inMaster);
      if (unlive.length) {
        L.push('  未上线接口：');
        for (const e of unlive) L.push(`  - ${e.method} ${e.path} → ${e.handler}`);
      }
    }
  }

  const doneTasks = m.tasks.filter((t) => t.status === 'done');
  const openTasks = m.tasks.filter((t) => t.status !== 'done');
  if (doneTasks.length) {
    L.push('');
    L.push(`## 三、已登记完成的任务（${doneTasks.length} 项）`);
    for (const t of doneTasks) L.push(`- ${t.id} ${t.name}${t.note ? `　备注：${t.note.slice(0, 90)}` : ''}`);
  }
  if (openTasks.length) {
    L.push('');
    L.push(`## 四、未完成的任务（${openTasks.length} 项）`);
    for (const t of openTasks) L.push(`- ${t.id} ${t.name}［${STATUS_TEXT[t.status] || t.status}］　${t.group} · ${t.phase}${t.note ? `　备注：${t.note.slice(0, 90)}` : ''}`);
  }

  const myPlans = plans.filter((p) => p.moduleKey === key && p.state !== 'done');
  if (myPlans.length) {
    L.push('');
    L.push('## 五、相关规划方案（未完成）');
    for (const p of myPlans) {
      L.push(`- ${p.title}（${PLAN_STATE[p.state].label}，${p.date || '无日期'}）`);
      L.push(`  文档：${p.file}`);
      L.push(`  派生依据：${p.reason}`);
      if (p.taskTotal) L.push(`  引用任务：${p.taskDone}/${p.taskTotal} 完成`);
      if (p.branches.length) L.push(`  关联分支：${p.branches.map((b) => b.name + (b.merged ? '（已合并）' : '（未合并）')).join('、')}`);
      if (p.declared) L.push(`  文档声明状态：${p.declared}`);
    }
  }

  if (m.adminPages.length || m.mobilePages.length) {
    L.push('');
    L.push('## 六、相关页面');
    if (m.adminPages.length) L.push(`- PC 管理端：${m.adminPages.join('、')}`);
    if (m.mobilePages.length) L.push(`- 移动端：${m.mobilePages.join('、')}`);
  }
  if (m.recentCommits.length) {
    L.push('');
    L.push('## 七、最近提交（按模块目录过滤）');
    for (const c of m.recentCommits) L.push(`- ${c.date} ${c.hash} ${c.subject}`);
  }

  L.push('');
  L.push('## 八、请回答');
  L.push('1. 这个模块具体实现了哪些功能？（按业务能力归类，不要逐个罗列接口）');
  L.push('2. 哪些能力已经上线到 master、哪些只在分支上、分别影响什么？');
  L.push('3. 规划中的方案是什么，距离可用还差哪些工作？');
  L.push('4. 如果只能推进一件事，最该做的是哪件，为什么？');
  L.push('');
  return L.join('\n');
}

const STATUS_TEXT = { done: '已完成', todo: '待办', doing: '进行中', partial: '部分完成', deferred: '暂缓' };

const ASK = {};
fs.mkdirSync(ASK_DIR, { recursive: true });
for (const [key, m] of Object.entries(model)) {
  const ctx = contextOf(key);
  const rel = `outputs/ask/${key}.md`;
  fs.writeFileSync(path.join(ROOT, rel), ctx);
  ASK[key] = {
    file: rel,
    size: ctx.length,
    questions: [
      '这个模块具体实现了哪些功能？',
      '哪些已经上线到 master，哪些还没上线？',
      '规划中的方案是什么，还差哪些没做？',
      '下一步最该做什么？',
    ],
  };
}
// 项目级上下文
{
  const L = [];
  L.push('# 询问上下文：Blade 项目全景');
  L.push('');
  L.push('你是 Blade 项目（服装批发 ERP）的开发助手。以下是 `scripts/gen-cockpit.mjs` 从 git、代码、文档三源交叉验证得到的项目级快照。');
  L.push('回答要求：只基于本快照回答，缺信息就说「快照里没有」。');
  L.push('');
  L.push('## 关键事实');
  L.push(`- 工作分支：${HEAD_BRANCH}；生产分支 master 的迁移版本：V${V_MASTER}；工作分支已到 V${V_HEAD}`);
  L.push(`- 任务完成度（文档口径）：${TOTAL.done}/${TOTAL.total}`);
  L.push(`- 本地分支 ${branches.length} 个，未并入 master ${unmerged.length} 个`);
  L.push(`- 后端接口 ${TOTAL_ENDPOINTS} 个，其中 ${branchOnlyEndpoints.length} 个只在分支上、未上线`);
  L.push('');
  L.push('## 模块状态');
  for (const [key, m] of Object.entries(model)) {
    const eps = m.resources.reduce((a, r) => a + resources[r].endpoints.length, 0);
    const off = m.resources.reduce((a, r) => a + resources[r].endpoints.filter((e) => !e.inMaster).length, 0);
    L.push(`- ${m.name}：任务 ${m.done}/${m.total}，接口 ${eps}（未上线 ${off}），判定「${VERDICT_TEXT[m.verdict]}」`);
  }
  L.push('');
  L.push('## 风险');
  for (const r of risks) L.push(`- [${r.level}] ${r.title}：${r.detail}`);
  L.push('');
  L.push('## 未合并分支');
  for (const b of unmerged) L.push(`- ${b.name}：领先 master ${b.ahead} 个提交，落后 ${b.behind}，最后提交 ${b.date}（${b.age} 天前）`);
  L.push('');
  L.push('## 规划与在途');
  for (const p of [...planByState.active, ...planByState.planned]) {
    L.push(`- [${PLAN_STATE[p.state].label}] ${p.title}（${p.date || '无日期'}）${p.file}`);
  }
  L.push('');
  L.push('## 请回答');
  L.push('1. 这个项目现在到底完成了什么？');
  L.push('2. 文档说的进度和生产实际可用之间差在哪里？');
  L.push('3. 规划中还有哪些方向，优先级应该怎么排？');
  fs.writeFileSync(path.join(ROOT, 'outputs/ask/_project.md'), L.join('\n'));
  ASK._project = { file: 'outputs/ask/_project.md', size: L.join('\n').length, questions: [] };
}


function archSvg() {
  const W = 1180, L = 40, IW = W - L * 2;
  const box = (x, y, w, h, title, sub, state) => {
    const stroke = state === 'branch' ? '#e3b341' : state === 'todo' ? '#39414d' : '#2a3a52';
    const fill = state === 'branch' ? 'rgba(227,179,65,.08)' : state === 'todo' ? 'rgba(255,255,255,.02)' : 'rgba(88,166,255,.06)';
    const dash = state === 'todo' ? ' stroke-dasharray="4 3"' : state === 'branch' ? ' stroke-dasharray="6 3"' : '';
    return `<g><rect x="${x}" y="${y}" width="${w}" height="${h}" rx="8" fill="${fill}" stroke="${stroke}"${dash} stroke-width="1.2"/>
      <text x="${x + w / 2}" y="${y + (sub ? h / 2 - 3 : h / 2 + 5)}" text-anchor="middle" class="sv-t">${esc(title)}</text>
      ${sub ? `<text x="${x + w / 2}" y="${y + h / 2 + 14}" text-anchor="middle" class="sv-s">${esc(sub)}</text>` : ''}</g>`;
  };
  const rows = [
    { y: 60, h: 66, label: '客户端层', items: [
      ['blade-admin', 'Vue3 · Element Plus · Tailwind', 'master'],
      ['blade-mobile', 'Vue3 PWA · Vuetify', 'master'],
      ['外部 Agent', '纸单识别 · WhatsApp Collector · 经营分析', 'branch'],
    ]},
    { y: 176, h: 56, label: '接入与安全', items: [
      ['Spring Security + JWT', '登录 / 刷新 / 多租户上下文', 'master'],
      ['权限与角色', 'Permission / Role / Agent Key 签发', 'master'],
    ]},
    { y: 282, h: 108, label: '业务域层', items: [
      ['商品与 SKU', 'Product · Catalog', 'master'],
      ['库存', 'Inventory · Warehouse', 'master'],
      ['订单与履约', 'Order · Delivery · Draft', 'master'],
      ['客户', 'Customer · Tag', 'master'],
      ['文件与资产', 'File · Binding · Folder', 'master'],
      ['看板与分析', 'Dashboard · Analytics', 'master'],
      ['外部集成', 'WhatsApp · Agent 网关', 'branch'],
      ['ERP 接替', '供应商 · AR/AP · 库存专项（未开发）', 'todo'],
    ]},
    { y: 440, h: 62, label: '数据层', items: [
      [`MySQL 8 · Flyway V1→V${V_HEAD}`, `master 当前为 V${V_MASTER}`, 'master'],
      ['Redis 7', '缓存 · 会话 · 分布式锁', 'master'],
      ['NAS 文件存储', '图片 · 纸单原图 · 派生文件', 'master'],
    ]},
  ];
  const parts = [];
  for (const r of rows) {
    parts.push(`<text x="${L}" y="${r.y - 10}" class="sv-l">${esc(r.label)}</text>`);
    const n = r.items.length, gap = 12, w = (IW - gap * (n - 1)) / n;
    r.items.forEach((it, i) => parts.push(box(L + i * (w + gap), r.y, w, r.h, it[0], it[1], it[2])));
  }
  const conns = [150, 226, 405, 495];
  const lines = conns.slice(0, -1).map((cy, i) => `<line x1="${W / 2}" y1="${cy}" x2="${W / 2}" y2="${conns[i + 1]}" class="sv-link"/>`).join('');
  return `<svg viewBox="0 0 ${W} 540" class="arch" role="img" aria-label="Blade 系统架构图">
  <style>
    .sv-t{fill:#e6edf3;font-size:13px;font-weight:600;font-family:system-ui,-apple-system,"PingFang SC",sans-serif}
    .sv-s{fill:#8b98a8;font-size:10.5px;font-family:system-ui,-apple-system,"PingFang SC",sans-serif}
    .sv-l{fill:#6e7b8b;font-size:11px;letter-spacing:.14em;font-family:system-ui,-apple-system,"PingFang SC",sans-serif}
    .sv-link{stroke:#2a3a52;stroke-width:1.2;stroke-dasharray:3 4}
  </style>${lines}${parts.join('')}</svg>`;
}

function planCard(p) {
  const st = PLAN_STATE[p.state];
  const mod = MODULES[p.moduleKey];
  const row = (label, val) => `<div class="prow"><span class="plabel">${label}</span><span class="pval">${val}</span></div>`;
  return `<article class="pcard" style="--c:${mod ? mod.color : '#7d8590'}">
  <header>
    <span class="badge ${st.tone}">${st.label}</span>
    <h3>${esc(p.title)}</h3>
    ${p.date ? `<span class="pdate">${esc(p.date)}</span>` : ''}
    ${p.conflict ? `<span class="badge bad" title="${esc(p.conflict)}">声明与证据不一致</span>` : ''}
    <span style="flex:1"></span>
    <button class="ask-btn" data-ask-plan="${esc(p.file)}">询问 AI</button>
  </header>
  ${row('模块', esc(mod ? mod.name : p.moduleKey))}
  ${row('派生依据', esc(p.reason))}
  ${p.declared ? row('文档声明', esc(p.declared)) : ''}
  ${row('引用任务', p.taskTotal ? `${p.taskDone} / ${p.taskTotal} 完成` : '<span class="sub" style="margin:0">无任务引用</span>')}
  ${p.branches.length ? row('关联分支', p.branches.map((b) => `<code>${esc(b.name)}</code>${b.merged ? ' <span class="badge ok">已合并</span>' : ' <span class="badge warn">未合并</span>'}`).join('　')) : ''}
  ${p.migs.length ? row('迁移版本', p.migs.map((m) => `<span class="chip ${m.live ? 'on' : 'half'}">V${m.v}<b>${m.live ? '已上线' : '未上线'}</b></span>`).join('')) : ''}
  ${row('文档', `<code>${esc(p.file)}</code>`)}
</article>`;
}

function planSection(title, list, tone, collapsed) {
  if (!list.length) return `<h2>${title}（0）</h2><p class="note">暂无。</p>`;
  const cards = list.map(planCard).join('');
  if (collapsed) return `<details class="nst" style="--c:#3fb950"><summary><span class="dot" style="background:#3fb950"></span>${title}<span class="cnt">${list.length} 个</span></summary><div class="body"><div class="plans">${cards}</div></div></details>`;
  return `<h2>${title}（${list.length}）</h2><div class="plans">${cards}</div>`;
}

/* ── 进展页渲染 ── */

const dnum = (n, o = {}) => {
  if (!n) return '<span style="color:var(--dim)">无变化</span>';
  const good = o.invert ? n < 0 : n > 0;
  return `<span style="color:${good ? 'var(--ok)' : 'var(--bad)'}">${n > 0 ? '+' : ''}${n}${o.suffix || ''}</span>`;
};

function deltaCard(label, value, n, o) {
  return `<div class="stat"><div class="stat-l">${esc(label)}</div><div class="stat-v">${esc(value)}</div>
  <div class="stat-s">${dnum(n, o)}　相比上次</div></div>`;
}

function sparkline(points) {
  if (points.length < 2) return '<p class="note">只有一份快照，还没有趋势可画。下次打开时这里会出现折线。</p>';
  const W = 620, H = 96, P = 12;
  const vals = points.map((p) => p.tasksDone);
  const min = Math.min(...vals), max = Math.max(...vals);
  const span = max - min || 1;
  const X = (i) => P + (i * (W - P * 2)) / (points.length - 1);
  const Y = (v) => H - P - ((v - min) / span) * (H - P * 2);
  const line = points.map((p, i) => `${i ? 'L' : 'M'}${X(i).toFixed(1)},${Y(p.tasksDone).toFixed(1)}`).join(' ');
  const area = `${line} L${X(points.length - 1).toFixed(1)},${H - P} L${X(0).toFixed(1)},${H - P} Z`;
  const dots = points.map((p, i) => `<circle cx="${X(i).toFixed(1)}" cy="${Y(p.tasksDone).toFixed(1)}" r="2.4" fill="#58a6ff"><title>${esc(p.date)}：${p.tasksDone} 项完成</title></circle>`).join('');
  return `<svg viewBox="0 0 ${W} ${H}" style="width:100%;height:auto;background:var(--panel-2);border:1px solid var(--line);border-radius:11px">
    <path d="${area}" fill="rgba(88,166,255,.10)"/>
    <path d="${line}" fill="none" stroke="#58a6ff" stroke-width="1.8" stroke-linejoin="round"/>
    ${dots}
    <text x="${P}" y="${H - 2}" fill="#6e7b8b" font-size="10" font-family="ui-monospace,monospace">${esc(points[0].date)}</text>
    <text x="${W - P}" y="${H - 2}" text-anchor="end" fill="#6e7b8b" font-size="10" font-family="ui-monospace,monospace">${esc(points[points.length - 1].date)}　完成 ${max}</text>
  </svg>`;
}

function planStateLine(before, now) {
  const keys = [['active', '进行中'], ['pending', '待上线'], ['planned', '规划中'], ['done', '已完成']];
  const parts = keys.filter(([k]) => (before[k] || 0) !== (now[k] || 0))
    .map(([k, label]) => `${label} ${before[k] || 0} → ${now[k] || 0}`);
  return parts.length ? parts.join('　·　') : '方案分布无变化';
}

function progressBlock(h, title) {
  const now = CURRENT.plans || {};
  return `<h2>${title}</h2>
  <div class="stats">
    ${deltaCard('任务完成', `${CURRENT.tasksDone} / ${CURRENT.tasksTotal}`, h.tasksDone)}
    ${deltaCard('后端接口', String(CURRENT.endpoints), h.endpoints)}
    ${deltaCard('已上线接口', String(CURRENT.endpointsLive), h.endpointsLive)}
    ${deltaCard('未上线接口', String(CURRENT.endpointsUnlive), h.endpointsUnlive, { invert: true })}
    ${deltaCard('待办任务', String(CURRENT.notStarted), h.notStarted, { invert: true })}
    ${deltaCard('未合并分支', `${CURRENT.unmerged} / ${CURRENT.branches}`, h.unmerged, { invert: true })}
  </div>
  <p class="note">基线：${esc(h.base)} 的 ${esc(h.baseLabel)}（当时在分支 <code>${esc(h.baseBranch || '?')}</code>）　·　
  迁移版本 V${h.baseHead ? '' : ''}${CURRENT.vMaster} 不变，工作分支迁移 ${h.vHead ? `+${h.vHead}` : '无变化'}　·　
  方案分布：${esc(planStateLine(h.plansBefore, now))}</p>

  ${h.modChanges.length ? `<div class="tbl-wrap" style="margin-top:14px"><table>
    <thead><tr><th>模块</th><th>任务完成</th><th>变化</th><th>接口</th><th>未上线</th></tr></thead>
    <tbody>${h.modChanges.map((c) => `<tr>
      <td><span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:${c.color};margin-right:8px"></span>${esc(c.name)}</td>
      <td class="num">${c.before} → <b style="color:var(--text)">${c.now}</b> / ${c.total}</td>
      <td>${dnum(c.dDone)}</td>
      <td>${dnum(c.dEps)}</td>
      <td>${dnum(c.dUnlive, { invert: true })}</td>
    </tr>`).join('')}</tbody>
  </table></div>` : '<p class="note">各模块进度和接口数都没有变化。</p>'}

  ${(h.opened.length || h.closed.length) ? `<div class="card" style="margin-top:14px">
    ${h.closed.length ? `<div style="margin-bottom:8px">✅ 分支已合并或删除：${h.closed.map((n) => `<code>${esc(n)}</code>`).join('　')}</div>` : ''}
    ${h.opened.length ? `<div>🆕 新增未合并分支：${h.opened.map((n) => `<code>${esc(n)}</code>`).join('　')}</div>` : ''}
  </div>` : ''}`;
}

function branchRow(b) {  const scope = [b.backend ? `后端 ${b.backend}` : '', b.admin ? `admin ${b.admin}` : '', b.mobile ? `mobile ${b.mobile}` : '', b.docs ? `docs ${b.docs}` : ''].filter(Boolean).join(' · ');
  const detail = [
    b.ctrls.length ? `控制器：${b.ctrls.slice(0, 6).join(', ')}${b.ctrls.length > 6 ? ` +${b.ctrls.length - 6}` : ''}` : '',
    b.adminViews.length ? `页面：${b.adminViews.join(', ')}` : '',
    b.mobileViews.length ? `移动端：${b.mobileViews.join(', ')}` : '',
  ].filter(Boolean).join('　|　');
  let advice, tone;
  if (b.merged) { advice = '已并入 master，可删除'; tone = 'muted'; }
  else if (b.ahead >= 20) { advice = '急需合并，风险最高的分支'; tone = 'bad'; }
  else if (b.age > 60) { advice = '超 60 天未动，评估后合并或废弃'; tone = 'warn'; }
  else { advice = '活跃分支，继续推进'; tone = 'ok'; }
  return `<tr class="tone-${tone}"><td><code>${esc(b.name)}</code>${b.name === HEAD_BRANCH ? ' <span class="badge info">当前</span>' : ''}</td>
  <td class="num">${b.ahead ? '+' + b.ahead : '0'}</td><td class="num">${b.behind ? '-' + b.behind : '0'}</td>
  <td class="num">${b.age} 天前<div class="sub">${esc(b.date)}</div></td>
  <td>${esc(scope)}${detail ? `<div class="sub">${esc(detail)}</div>` : ''}</td><td>${esc(advice)}</td></tr>`;
}

const html = `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Blade 项目全景台</title>
<style>
  :root{
    --bg:#0a0d12; --panel:#111722; --panel-2:#0d121b; --line:#1d2734; --line-2:#26313f;
    --text:#e6edf3; --muted:#8b98a8; --dim:#6e7b8b;
    --ok:#3fb950; --warn:#e3b341; --bad:#f85149; --info:#58a6ff;
    --mono:ui-monospace,SFMono-Regular,"SF Mono",Menlo,Consolas,monospace;
    --sans:system-ui,-apple-system,"PingFang SC","Hiragino Sans GB","Microsoft YaHei",sans-serif;
  }
  *{box-sizing:border-box}
  html{scroll-behavior:smooth}
  body{margin:0;background:var(--bg);color:var(--text);font-family:var(--sans);font-size:14px;line-height:1.65;
       background-image:radial-gradient(900px 420px at 12% -8%,rgba(88,166,255,.07),transparent 65%)}
  .wrap{max-width:1280px;margin:0 auto;padding:34px 26px 90px}
  header.top{display:flex;align-items:flex-end;gap:16px;flex-wrap:wrap;margin-bottom:8px}
  h1{font-size:26px;margin:0;letter-spacing:-.02em}
  .sub{color:var(--muted);font-size:12.5px}
  .meta{margin-left:auto;text-align:right;color:var(--dim);font-size:12px;font-family:var(--mono)}
  .searchbox{display:flex;align-items:center;gap:9px;margin:16px 0 0;background:var(--panel);border:1px solid var(--line);border-radius:10px;padding:9px 14px;max-width:520px}
  .searchbox input{flex:1;background:transparent;border:0;outline:0;color:var(--text);font:inherit;font-size:13px}
  .searchbox input::placeholder{color:var(--dim)}
  .searchbox .k{color:var(--dim);font-size:11px;font-family:var(--mono);border:1px solid var(--line-2);border-radius:5px;padding:1px 6px}
  nav.tabs{display:flex;gap:4px;flex-wrap:wrap;position:sticky;top:0;z-index:20;padding:14px 0 12px;margin-bottom:20px;
           background:linear-gradient(var(--bg) 72%,rgba(10,13,18,0));border-bottom:1px solid var(--line)}
  nav.tabs button{background:transparent;border:1px solid transparent;color:var(--muted);font:inherit;font-size:13px;padding:7px 15px;border-radius:999px;cursor:pointer;transition:.16s}
  nav.tabs button:hover{color:var(--text);background:#151c27}
  nav.tabs button.on{background:#182233;border-color:#2b3d59;color:var(--text)}
  nav.tabs button .n{color:var(--dim);margin-left:7px;font-family:var(--mono);font-size:11px}
  nav.tabs button.on .n{color:var(--info)}
  section.tab{display:none;animation:fade .22s ease}
  section.tab.on{display:block}
  @keyframes fade{from{opacity:0;transform:translateY(5px)}to{opacity:1;transform:none}}
  h2{font-size:16px;margin:34px 0 14px;display:flex;align-items:center;gap:10px}
  h2::before{content:"";width:3px;height:15px;border-radius:2px;background:var(--info);display:inline-block}
  h2:first-child{margin-top:6px}
  .card{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:16px 18px}
  .stats{display:grid;grid-template-columns:repeat(auto-fit,minmax(190px,1fr));gap:12px}
  .stat{background:var(--panel);border:1px solid var(--line);border-radius:11px;padding:14px 15px}
  .stat-l{color:var(--muted);font-size:12px}
  .stat-v{font-size:25px;font-weight:650;margin:5px 0 2px;font-family:var(--mono);letter-spacing:-.02em}
  .stat-s{color:var(--dim);font-size:11.5px}
  .stat.bad{border-color:#4a2326;background:linear-gradient(180deg,rgba(248,81,73,.07),transparent)}
  .stat.bad .stat-v{color:var(--bad)}
  .stat.warn .stat-v{color:var(--warn)}
  ul.risks{list-style:none;padding:0;margin:0;display:grid;gap:10px}
  ul.risks li{border:1px solid var(--line);border-left-width:3px;border-radius:9px;padding:12px 15px;background:var(--panel-2)}
  ul.risks li.high{border-left-color:var(--bad)}
  ul.risks li.medium{border-left-color:var(--warn)}
  ul.risks li.low{border-left-color:var(--dim)}
  ul.risks .rt{font-weight:600}
  ul.risks .rd{color:var(--muted);font-size:12.5px;margin-top:3px}
  .prog{display:grid;gap:11px}
  .prog-row{display:grid;grid-template-columns:120px 1fr auto;align-items:center;gap:14px}
  .prog-row .nm{color:var(--muted);font-size:13px}
  .prog-row .num{font-family:var(--mono);font-size:12.5px;white-space:nowrap}
  .bar{height:8px;border-radius:5px;background:#1a2130;overflow:hidden;display:inline-block;width:var(--w,220px);vertical-align:middle}
  .bar i{display:block;height:100%;border-radius:5px}
  .pct{font-family:var(--mono);font-size:12px;margin-left:9px;display:inline-block;min-width:38px}
  .ver{font-size:11px;font-weight:600;color:var(--info);border:1px solid #20344f;background:rgba(88,166,255,.1);
    border-radius:6px;padding:2px 8px;vertical-align:middle;margin-left:8px;letter-spacing:.04em;font-family:var(--mono)}
  table{width:100%;border-collapse:collapse;font-size:13px}
  th{text-align:left;color:var(--dim);font-weight:500;font-size:11.5px;letter-spacing:.05em;padding:9px 11px;border-bottom:1px solid var(--line-2);white-space:nowrap}
  td{padding:10px 11px;border-bottom:1px solid var(--line);vertical-align:middle}
  tr:last-child td{border-bottom:0}
  tbody tr:hover{background:#141b26}
  td.num{font-family:var(--mono);text-align:right;white-space:nowrap}
  code{font-family:var(--mono);font-size:12.5px;color:#a5d6ff;background:#111a26;padding:1.5px 6px;border-radius:5px}
  .sub{color:var(--dim);font-size:11.5px;margin-top:3px;line-height:1.5}
  .tbl-wrap{overflow-x:auto;border:1px solid var(--line);border-radius:12px;background:var(--panel)}
  .badge{display:inline-block;font-size:11px;padding:2px 9px;border-radius:999px;border:1px solid;line-height:1.6}
  .badge.ok{color:var(--ok);border-color:#1f4a2a;background:rgba(63,185,80,.1)}
  .badge.warn{color:var(--warn);border-color:#4d3f14;background:rgba(227,179,65,.1)}
  .badge.bad{color:var(--bad);border-color:#4a2326;background:rgba(248,81,73,.1)}
  .badge.muted{color:var(--dim);border-color:var(--line-2)}
  .badge.info{color:var(--info);border-color:#20344f;background:rgba(88,166,255,.1)}
  .mgrid{display:grid;grid-template-columns:repeat(auto-fit,minmax(370px,1fr));gap:14px}
  .mcard{background:var(--panel);border:1px solid var(--line);border-radius:12px;padding:15px 16px;border-top:2px solid var(--c);transition:.18s}
  .mcard:hover{border-color:var(--line-2);transform:translateY(-1px)}
  .mcard header{display:flex;align-items:center;gap:9px;flex-wrap:wrap;margin-bottom:9px}
  .mcard h3{font-size:14.5px;margin:0;font-weight:650}
  .mcard .dot{width:7px;height:7px;border-radius:50%}
  .mcard .spacer{flex:1}
  .mcard .mnum{font-family:var(--mono);font-size:12.5px;color:var(--muted)}
  .mcard .mevi{color:var(--dim);font-size:11.5px;padding-bottom:9px;border-bottom:1px dashed var(--line-2);margin-bottom:9px}
  ul.phase-list{list-style:none;margin:0;padding:0;display:grid;gap:3px}
  ul.phase-list li{display:grid;grid-template-columns:1fr auto auto;gap:10px;font-size:12.5px;padding:4px 7px;border-radius:6px}
  ul.phase-list li:hover{background:#151d29}
  .ph-name{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
  .ph-tag{color:var(--dim);font-size:11px}
  .ph-num{font-family:var(--mono);font-size:11.5px;color:var(--muted)}
  li.ph-done .ph-name{color:#9fb0c2}
  li.ph-done .ph-num{color:var(--ok)}
  li.ph-part .ph-num{color:var(--warn)}
  li.ph-todo .ph-num{color:var(--dim)}
  .mdone{margin-top:10px;padding-top:9px;border-top:1px dashed var(--line-2);color:var(--dim);font-size:11.5px;line-height:1.8}
  .drill{margin-top:11px;width:100%;background:#16202e;border:1px solid #24344a;color:var(--info);font:inherit;font-size:12px;padding:7px;border-radius:8px;cursor:pointer;transition:.16s}
  .drill:hover{background:#1c2a3d;border-color:#31465f}
  .arch{width:100%;height:auto;display:block;background:var(--panel-2);border:1px solid var(--line);border-radius:12px}
  .legend{display:flex;gap:20px;flex-wrap:wrap;color:var(--muted);font-size:12px;margin-top:12px}
  .legend span{display:flex;align-items:center;gap:7px}
  .legend i{width:22px;height:11px;border-radius:3px;display:inline-block}
  ol.tl{list-style:none;padding:0;margin:0;position:relative}
  ol.tl::before{content:"";position:absolute;left:118px;top:6px;bottom:6px;width:1px;background:var(--line-2)}
  ol.tl li{display:grid;grid-template-columns:104px 1fr;gap:30px;padding:9px 0;position:relative}
  ol.tl li::before{content:"";position:absolute;left:114px;top:16px;width:9px;height:9px;border-radius:50%;background:var(--bg);border:2px solid var(--info)}
  ol.tl li.recent::before{border-color:var(--ok);box-shadow:0 0 0 4px rgba(63,185,80,.12)}
  ol.tl .d{font-family:var(--mono);font-size:12px;color:var(--muted);text-align:right;padding-top:2px}
  ol.tl .it{font-size:13px;padding-bottom:5px}
  ol.tl .it .tag{color:var(--dim);font-size:11.5px;border:1px solid var(--line-2);padding:1px 7px;border-radius:999px;margin-right:8px}
  .note{color:var(--dim);font-size:12px;margin:10px 0 0}
  .chip{display:inline-flex;align-items:center;gap:3px;font-family:var(--mono);font-size:10.5px;padding:1px 5px;border-radius:4px;border:1px solid var(--line-2);color:var(--dim);margin-right:3px}
  .chip b{font-weight:600}
  .chip.on{color:var(--ok);border-color:#1f4a2a;background:rgba(63,185,80,.09)}
  .chip.half{color:var(--warn);border-color:#4d3f14;background:rgba(227,179,65,.09)}
  .chip.off{opacity:.42}
  td.opcell{white-space:nowrap;padding:8px}
  .linkrow{cursor:pointer}
  .linkrow:hover td{background:#161f2c}
  .linkrow:hover .modname{color:var(--info)}
  footer{margin-top:56px;padding-top:18px;border-top:1px solid var(--line);color:var(--dim);font-size:12px}
  .overlay{position:fixed;inset:0;background:rgba(4,7,11,.78);backdrop-filter:blur(5px);z-index:60;display:none;padding:28px 20px;overflow:auto}
  .overlay.on{display:block}
  .sheet{max-width:1180px;margin:0 auto;background:var(--panel);border:1px solid var(--line-2);border-radius:15px;box-shadow:0 24px 70px rgba(0,0,0,.6);animation:pop .2s ease}
  @keyframes pop{from{opacity:0;transform:translateY(12px) scale(.995)}to{opacity:1;transform:none}}
  .sheet-head{display:flex;align-items:center;gap:12px;padding:18px 24px;border-bottom:1px solid var(--line);position:sticky;top:0;background:var(--panel);border-radius:15px 15px 0 0;z-index:2;flex-wrap:wrap}
  .sheet-head h2{margin:0;font-size:18px}
  .sheet-head h2::before{background:var(--mc,var(--info))}
  .sheet-body{padding:6px 24px 28px}
  .close{margin-left:auto;background:#182233;border:1px solid var(--line-2);color:var(--muted);font:inherit;font-size:12px;padding:6px 13px;border-radius:8px;cursor:pointer}
  .close:hover{color:var(--text);border-color:#31465f}
  .kpis{display:flex;gap:26px;flex-wrap:wrap;padding:16px 0 4px;color:var(--muted);font-size:12.5px}
  .kpis b{color:var(--text);font-family:var(--mono);font-size:15px;font-weight:600;margin-left:5px}
  .sect{margin-top:26px}
  .sect>h4{font-size:13px;margin:0 0 11px;display:flex;align-items:center;gap:9px}
  .sect>h4::before{content:"";width:3px;height:13px;border-radius:2px;background:var(--mc,var(--info));display:inline-block}
  .sect>h4 .hint{color:var(--dim);font-size:11.5px;font-weight:400}
  details.res{border:1px solid var(--line);border-radius:10px;background:var(--panel-2);margin-bottom:9px;overflow:hidden}
  details.res>summary{padding:11px 15px;cursor:pointer;display:flex;align-items:center;gap:11px;font-size:13px;list-style:none}
  details.res>summary::-webkit-details-marker{display:none}
  details.res>summary::before{content:"▸";color:var(--dim);font-size:11px}
  details.res[open]>summary::before{content:"▾"}
  details.res>summary:hover{background:#141c27}
  details.res .body{padding:4px 15px 14px;border-top:1px solid var(--line)}
  .ep{display:grid;grid-template-columns:56px 1fr auto;gap:12px;align-items:center;padding:6px 0;border-bottom:1px dashed var(--line);font-size:12.5px}
  .ep:last-child{border-bottom:0}
  .m{font-family:var(--mono);font-size:10.5px;text-align:center;border-radius:4px;padding:1px 0;border:1px solid}
  .m.GET{color:#79c0ff;border-color:#20344f;background:rgba(88,166,255,.08)}
  .m.POST{color:#7ee787;border-color:#1f4a2a;background:rgba(63,185,80,.08)}
  .m.PUT,.m.PATCH{color:#e3b341;border-color:#4d3f14;background:rgba(227,179,65,.08)}
  .m.DELETE{color:#ff7b72;border-color:#4a2326;background:rgba(248,81,73,.08)}
  .ep .p{font-family:var(--mono);font-size:12px;color:#a5d6ff;word-break:break-all}
  .ep .h{color:var(--dim);font-size:11.5px;font-family:var(--mono)}
  .ep.branch .p{color:#e3b341}
  .flist{display:inline-flex;flex-wrap:wrap;gap:5px;margin:2px 5px 2px 0}
  .flist span{font-family:var(--mono);font-size:11.5px;color:var(--muted);background:#131b26;border:1px solid var(--line);border-radius:6px;padding:3px 8px}
  .cl{display:grid;grid-template-columns:74px 96px 1fr;gap:12px;font-size:12.5px;padding:6px 0;border-bottom:1px dashed var(--line)}
  .cl:last-child{border-bottom:0}
  .cl .hh{font-family:var(--mono);color:var(--info);font-size:11.5px}
  .cl .dd{font-family:var(--mono);color:var(--dim);font-size:11.5px}
  .empty{color:var(--dim);font-size:12.5px;padding:8px 0}
  .hidden{display:none !important}

  /* 规划与在途 */
  .plans{display:grid;gap:12px}
  .pcard{background:var(--panel);border:1px solid var(--line);border-left:3px solid var(--c);border-radius:11px;padding:14px 17px}
  .pcard header{display:flex;align-items:center;gap:10px;flex-wrap:wrap;margin-bottom:10px}
  .pcard h3{font-size:14.5px;margin:0;font-weight:640}
  .pcard .pdate{font-family:var(--mono);font-size:11.5px;color:var(--dim)}
  .prow{display:grid;grid-template-columns:74px 1fr;gap:12px;padding:3px 0;font-size:12.5px;align-items:baseline}
  .plabel{color:var(--dim);font-size:11.5px;text-align:right}
  .pval{color:var(--muted);word-break:break-word}
  .pval code{font-size:11.5px}
  .ask-btn{background:#1a2637;border:1px solid #2b3d59;color:var(--info);font:inherit;font-size:11.5px;padding:5px 12px;border-radius:7px;cursor:pointer;transition:.16s}
  .ask-btn:hover{background:#22314a;border-color:#3b5480}
  details.nst{border:1px solid var(--line);border-radius:11px;background:var(--panel-2);margin-bottom:10px;overflow:hidden;border-left:3px solid var(--c)}
  details.nst>summary{padding:12px 16px;cursor:pointer;display:flex;align-items:center;gap:10px;font-size:13.5px;font-weight:520;list-style:none}
  details.nst>summary::-webkit-details-marker{display:none}
  details.nst>summary::before{content:"▸";color:var(--dim);font-size:11px}
  details.nst[open]>summary::before{content:"▾"}
  details.nst>summary:hover{background:#141c27}
  details.nst>summary .dot{width:7px;height:7px;border-radius:50%;display:inline-block}
  details.nst>summary .cnt{margin-left:auto;color:var(--dim);font-family:var(--mono);font-size:11.5px}
  details.nst .body{padding:0 6px 10px;border-top:1px solid var(--line)}
  details.nst .body .plans{padding:12px 10px 4px}

  /* 询问 AI 面板 */
  .ask-sheet{max-width:780px;margin:60px auto;background:var(--panel);border:1px solid var(--line-2);border-radius:15px;box-shadow:0 24px 70px rgba(0,0,0,.65);animation:pop .2s ease}
  .ask-head{display:flex;align-items:center;gap:11px;padding:16px 22px;border-bottom:1px solid var(--line);flex-wrap:wrap}
  .ask-head h3{margin:0;font-size:16px}
  .ask-head .who{color:var(--dim);font-size:12px}
  .ask-body{padding:18px 22px 22px}
  .ask-q{display:flex;flex-wrap:wrap;gap:7px;margin-bottom:14px}
  .ask-q button{background:#151d29;border:1px solid var(--line-2);color:var(--muted);font:inherit;font-size:12px;padding:6px 12px;border-radius:999px;cursor:pointer;transition:.16s}
  .ask-q button:hover{color:var(--text);border-color:#3b5480;background:#1a2637}
  .ask-q button.on{color:var(--info);border-color:#2b3d59;background:#182233}
  #askText{width:100%;height:230px;background:#0b1119;border:1px solid var(--line-2);border-radius:9px;color:var(--text);
    font-family:var(--mono);font-size:12px;line-height:1.65;padding:13px 15px;resize:vertical;outline:0}
  #askText:focus{border-color:#2b3d59}
  .ask-actions{display:flex;gap:9px;flex-wrap:wrap;align-items:center;margin-top:13px}
  .ask-actions button{background:#182233;border:1px solid var(--line-2);color:var(--text);font:inherit;font-size:12.5px;padding:8px 15px;border-radius:8px;cursor:pointer;transition:.16s}
  .ask-actions button:hover{border-color:#3b5480;background:#1f2b3d}
  .ask-actions button.primary{background:#1d3a5c;border-color:#2f5b8a;color:#cfe4ff}
  .ask-actions button.primary:hover{background:#24476f}
  .ask-actions .ok-msg{color:var(--ok);font-size:12px}
  .ask-tip{margin-top:16px;border-top:1px dashed var(--line-2);padding-top:14px;color:var(--dim);font-size:12px;line-height:1.9}
  .ask-tip b{color:var(--muted);font-weight:600}
  .ask-tip code{font-size:11.5px}

</style>
</head>
<body>
<div class="wrap">
  <header class="top">
    <div>
      <h1>Blade 项目全景台 <span class="ver">v1</span></h1>
      <div class="sub">git · 代码 · 文档三源交叉验证，点击模块可下钻到增删改查</div>
    </div>
    <div class="meta">生成于 ${new Date().toLocaleString('zh-CN')}<br>工作分支 ${esc(HEAD_BRANCH)}　·　master 迁移版本 V${V_MASTER}</div>
  </header>

  <div class="searchbox">
    <span class="k">⌕</span>
    <input id="q" type="search" placeholder="搜索模块、功能、任务 ID、接口路径…（如「订单」「BE-1042」「/orders」）">
    <span class="k" id="qcount"></span>
  </div>
  <div style="margin-top:10px">
    <button class="ask-btn" id="askProjectBtn" style="padding:8px 16px;font-size:12.5px">询问 AI · 整个项目现状</button>
  </div>

  <nav class="tabs">
    <button class="on" data-t="overview">一眼总览<span class="n">${risks.length}</span></button>
    <button data-t="progress">进展<span class="n">${histPrev ? histPrev.modChanges.length + ' 处变化' : '首次'}</span></button>
    <button data-t="arch">架构图<span class="n">${controllers.length}</span></button>
    <button data-t="features">模块功能地图<span class="n">${Object.keys(MODULES).length}</span></button>
    <button data-t="api">接口与 CRUD<span class="n">${Object.keys(resources).length}</span></button>
    <button data-t="roadmap">规划与在途<span class="n">${planByState.active.length + planByState.pending.length + planByState.planned.length}</span></button>
    <button data-t="branches">分支地图<span class="n">${branches.length}</span></button>
    <button data-t="timeline">里程碑<span class="n">${milestones.length}</span></button>
    <button data-t="docs">文档导航<span class="n">${docs.length}</span></button>
  </nav>

  <section class="tab on" id="overview">
    <div class="stats">
      ${statCard('文档口径完成度', pct(TOTAL.done, TOTAL.total) + '%', `${TOTAL.done} / ${TOTAL.total} 项任务`)}
      ${statCard('生产分支实际版本', 'V' + V_MASTER, `工作分支已到 V${V_HEAD}`, V_HEAD > V_MASTER ? 'bad' : '')}
      ${statCard('只在分支上的接口', branchOnlyEndpoints.length, `共 ${TOTAL_ENDPOINTS} 个接口`, branchOnlyEndpoints.length ? 'bad' : '')}
      ${statCard('未合并分支', unmerged.length + ' / ' + branches.length, `最大领先 ${maxAhead} 个提交`, unmerged.length ? 'warn' : '')}
    </div>

    <h2>风险清单</h2>
    <ul class="risks">${risks.map((r) => `<li class="${r.level}"><div class="rt">${esc(r.title)}</div><div class="rd">${esc(r.detail)}</div></li>`).join('')}</ul>

    <h2>三端进度（按任务勾选口径）</h2>
    <div class="card prog">
      ${['后端', 'PC 管理端', '移动端'].map((g) => {
        const s = groupStats[g] || { done: 0, total: 0 };
        const c = g === '后端' ? '#58a6ff' : g === 'PC 管理端' ? '#3fb950' : '#e3b341';
        return `<div class="prog-row"><span class="nm">${g}</span><div>${bar(s.done, s.total, c)}</div><span class="num">${s.done} / ${s.total}</span></div>`;
      }).join('')}
    </div>
    <p class="note">百分比只统计活跃任务（暂缓不计入分母），来源是 03-TASKS.md 的人工勾选，不等于生产可用。</p>

    <h2>模块 × 证据矩阵</h2>
    <div class="tbl-wrap"><table>
      <thead><tr><th>模块</th><th>任务进度</th><th>控制器在 master</th><th>控制器在工作分支</th><th>接口</th><th>判定</th><th></th></tr></thead>
      <tbody>
      ${Object.entries(model).map(([k, v]) => {
        const m = MODULES[k], [tone, label] = VERDICT[v.verdict];
        const eps = v.resources.reduce((a, r) => a + resources[r].endpoints.length, 0);
        const off = v.resources.reduce((a, r) => a + resources[r].endpoints.filter((e) => !e.inMaster).length, 0);
        return `<tr class="linkrow" data-open="${k}">
          <td><span style="display:inline-block;width:7px;height:7px;border-radius:50%;background:${m.color};margin-right:8px"></span><span class="modname">${esc(v.name)}</span></td>
          <td>${v.total ? bar(v.done, v.total, m.color, 100) + `<span class="pct">${v.done}/${v.total}</span>` : '<span class="sub">无任务登记</span>'}</td>
          <td class="num">${v.ctrlCount ? `${v.inMaster} / ${v.ctrlCount}` : '—'}</td>
          <td class="num">${v.ctrlCount ? `${v.inHead} / ${v.ctrlCount}` : '—'}</td>
          <td class="num">${eps || '—'}${off ? `<div class="sub" style="color:var(--warn)">${off} 未上线</div>` : ''}</td>
          <td><span class="badge ${tone}">${label}</span></td>
          <td style="color:var(--dim);font-size:12px">详情 →</td>
        </tr>`;
      }).join('')}
      </tbody>
    </table></div>
    <p class="note">任务进度是「文档说的」，控制器与接口数量是「代码里真的有的」。差距越大，说明文档进度越不能当生产状态使用。</p>
  </section>

  <section class="tab" id="progress">
    <h2>进展</h2>
    <p class="note">每次生成看板都会自动存一份当天的指标快照到 <code>outputs/cockpit-history/</code>（一天一份，几 KB）。
    这里显示和「上一次打开」以及「7 天前」相比的变化。不需要你额外做任何事。</p>
    ${historyNote ? `<div class="card" style="border-color:#4a2326;color:var(--muted);font-size:12.5px">⚠️ 历史快照读写失败，看板其余部分不受影响：${esc(historyNote)}</div>` : ''}
    ${histPrev ? progressBlock(histPrev, `与上一次打开相比（${esc(whenLabel(lastRun))}）`)
      : `<div class="card" style="margin-top:16px">
        <div style="font-size:14px;margin-bottom:8px">📌 这是第一次记录</div>
        <div class="sub" style="margin:0;font-size:12.5px;line-height:1.9">
          今天已经存下第一份快照：<code>outputs/cockpit-history/${TODAY}.json</code>，
          并且每次打开都会记下「上次打开时的状态」。<br>
          等你下次打开看板，这里就会告诉你：这期间完成了多少任务、新增了几个接口、上线了几个、
          哪些模块在动、哪些停了、有没有东西从「已完成」退回「未上线」。<br>
          不需要你做任何操作，也不用记得存。
        </div>
      </div>`}

    ${histWeek ? progressBlock(histWeek, `与 7 天前相比（基线 ${histWeek.base}）`) : ''}

    <h2>任务完成数趋势（最近 ${trend.length} 次记录）</h2>
    ${sparkline(trend)}
    <p class="note">只记录「任务完成总数」一个指标，用来快速看出项目是在推进还是停下来了。
    完整的逐项对比在上面的模块变化表里。</p>
  </section>

  <section class="tab" id="arch">
    <h2>系统架构（颜色即生产状态）</h2>
    ${archSvg()}
    <div class="legend">
      <span><i style="background:rgba(88,166,255,.18);border:1.2px solid #2a3a52"></i>已在 master，生产可用</span>
      <span><i style="background:rgba(227,179,65,.14);border:1.2px dashed #e3b341"></i>只在功能分支，尚未上线</span>
      <span><i style="background:rgba(255,255,255,.03);border:1.2px dashed #39414d"></i>仅规划，无代码</span>
    </div>
    <h2>代码资产盘点</h2>
    <div class="stats">
      ${statCard('后端控制器', String(controllers.length), `共 ${TOTAL_ENDPOINTS} 个接口`)}
      ${statCard('实体', String(entities), 'entity/*.java')}
      ${statCard('数据库迁移', `${migrations.length} 个`, `V1 → V${V_HEAD}（master 到 V${V_MASTER}）`)}
      ${statCard('后端测试类', String(testFiles.length), '*Test*.java')}
      ${statCard('PC 管理端视图', String(adminViews.length), `${frontend.admin.length} 个接口调用`)}
      ${statCard('移动端页面', String(mobileViews.length), `${frontend.mobile.length} 个接口调用`)}
    </div>
  </section>

  <section class="tab" id="features">
    <h2>模块功能地图</h2>
    <p class="note">点击任意模块或「查看详情」下钻，可看到该模块每个资源的增删改查实现情况与未上线接口。</p>
    <div class="mgrid">${moduleCards}</div>
  </section>

  <section class="tab" id="api">
    <h2>接口与 CRUD 矩阵（${Object.keys(resources).length} 个资源）</h2>
    <p class="note">每格三个来源：<b>BE</b> 后端接口、<b>PC</b> 管理端调用、<b>MB</b> 移动端调用。<span class="chip on">绿</span> 已实现，<span class="chip half">黄</span> 只在分支未上线，<span class="chip off">灰</span> 无。BE 显示「已上线/总数」。</p>
    <div class="tbl-wrap"><table>
      <thead><tr><th>资源</th><th>控制器</th>${OPS.map((o) => `<th title="${esc(o.hint)}">${o.label}</th>`).join('')}<th>接口</th></tr></thead>
      <tbody>
      ${Object.values(resources).sort((a, b) => a.key.localeCompare(b.key)).map((r) => {
        const off = r.endpoints.filter((e) => !e.inMaster).length;
        return `<tr><td><code>${esc(r.key)}</code></td><td><span class="sub">${esc(r.controllers.join(', '))}</span></td>
        ${OPS.map((o) => opCell(r, o)).join('')}
        <td class="num">${r.endpoints.length}${off ? `<div class="sub" style="color:var(--warn)">${off} 未上线</div>` : ''}</td></tr>`;
      }).join('')}
      </tbody>
    </table></div>

    <h2>未上线接口清单（${branchOnlyEndpoints.length} 个）</h2>
    <div class="tbl-wrap"><table>
      <thead><tr><th>方法</th><th>路径</th><th>Handler</th><th>控制器</th></tr></thead>
      <tbody>${branchOnlyEndpoints.map((e) => `<tr><td><span class="m ${e.method}">${e.method}</span></td><td><code>${esc(e.path)}</code></td><td class="sub">${esc(e.handler)}</td><td class="sub">${esc(e.controller)}</td></tr>`).join('') || '<tr><td colspan="4" class="empty">没有未上线接口</td></tr>'}</tbody>
    </table></div>
  </section>

  <section class="tab" id="roadmap">
    <h2>规划与在途</h2>
    <p class="note">来自 <code>docs/superpowers/plans/</code> 的实施方案与 03-TASKS.md 的待办任务。每个方案的状态不是照抄文档声明，而是用它引用的任务、分支、迁移版本反推出来的；文档声明与证据不一致时会单独标红。</p>
    <div class="stats">
      ${statCard('进行中的方案', String(planByState.active.length), '已开工、未完成')}
      ${statCard('待上线方案', String(planByState.pending.length), '功能做完但未上生产', planByState.pending.length ? 'warn' : '')}
      ${statCard('规划中的方案', String(planByState.planned.length), '有方案文档、未开工')}
      ${statCard('已完成的方案', String(planByState.done.length), '证据显示已落地')}
      ${statCard('未开始的任务', String(notStartedTotal), '03-TASKS.md 中非完成项')}
    </div>

    ${planSection('进行中的方案', planByState.active, 'info')}
    ${planSection('待上线（功能完成、尚未上生产）', planByState.pending, 'warn')}
    ${planSection('规划中的方案', planByState.planned, 'muted')}

    <h2>未开始 / 未完成的任务（${notStartedTotal} 项）</h2>
    <p class="note">这是「后续要开发什么」的最细粒度清单，按模块归组。状态为「待办」表示未开工，「进行中 / 部分完成」表示已开工未收口。</p>
    ${Object.entries(notStarted).sort((a, b) => b[1].length - a[1].length).map(([k, list]) => {
      const nm = MODULES[k] ? MODULES[k].name : k;
      const color = MODULES[k] ? MODULES[k].color : '#7d8590';
      return `<details class="nst" style="--c:${color}"><summary><span class="dot" style="background:${color}"></span>${esc(nm)}<span class="cnt">${list.length} 项</span></summary>
      <div class="body"><table><thead><tr><th>ID</th><th>任务</th><th>状态</th><th>阶段</th><th>备注</th></tr></thead><tbody>
      ${list.map((t) => `<tr><td><code>${esc(t.id)}</code></td><td>${esc(t.name)}</td>
        <td><span class="badge ${t.status === 'todo' ? 'muted' : t.status === 'partial' ? 'warn' : 'info'}">${esc(STATUS_LABEL[t.status] || t.status)}</span></td>
        <td class="sub" style="margin:0">${esc(t.group)} · ${esc(t.phase)}</td>
        <td class="sub" style="margin:0;max-width:360px">${esc((t.note || '').slice(0, 150))}</td></tr>`).join('')}
      </tbody></table></div></details>`;
    }).join('')}

    ${planSection('已完成的方案', planByState.done, 'ok', true)}

    <h2>需求池与决策记录</h2>
    <p class="note">需求讨论 ${requisitions.length} 条、技术决策 ${decisions.length} 条，全部已采纳，仅作背景参考。</p>
    <details class="nst" style="--c:#7d8590"><summary><span class="dot" style="background:#7d8590"></span>已采纳的需求<span class="cnt">${requisitions.length} 条</span></summary>
      <div class="body"><table><thead><tr><th>日期</th><th>#</th><th>需求</th><th>结果</th></tr></thead><tbody>
      ${requisitions.map((r) => `<tr><td class="sub" style="margin:0">${esc(r.date)}</td><td class="num">${esc(r.no)}</td><td>${esc(r.title)}</td><td class="sub" style="margin:0">${esc(r.result)}</td></tr>`).join('')}
      </tbody></table></div></details>
    <details class="nst" style="--c:#7d8590"><summary><span class="dot" style="background:#7d8590"></span>技术决策<span class="cnt">${decisions.length} 条</span></summary>
      <div class="body"><table><thead><tr><th>ID</th><th>决策</th><th>日期</th><th>状态</th></tr></thead><tbody>
      ${decisions.map((d) => `<tr><td><code>${esc(d.id)}</code></td><td>${esc(d.title)}</td><td class="sub" style="margin:0">${esc(d.date)}</td><td class="sub" style="margin:0">${esc(d.status)}</td></tr>`).join('')}
      </tbody></table></div></details>
  </section>

  <section class="tab" id="branches">
    <h2>分支地图（${branches.length} 个本地分支）</h2>
    <div class="tbl-wrap"><table>
      <thead><tr><th>分支</th><th>领先</th><th>落后</th><th>最后提交</th><th>改动范围</th><th>建议</th></tr></thead>
      <tbody>${branches.map(branchRow).join('')}</tbody>
    </table></div>
    <p class="note">领先 = 该分支有而 master 没有的提交数；落后 = master 有而该分支没有的提交数。判定基准是 master，因为 NAS 生产只部署 master。</p>
  </section>

  <section class="tab" id="timeline">
    <h2>里程碑时间线</h2>
    <p class="note">从 05-CHANGELOG.md（${(readRel('docs/05-CHANGELOG.md').length / 1024).toFixed(0)}KB）抽取，替代通读全文。</p>
    <ol class="tl">${milestones.slice(0, 22).map((m, i) => `<li class="${i < 3 ? 'recent' : ''}">
      <div class="d">${m.date}</div>
      <div>${m.items.map((it) => `<div class="it"><span class="tag">${esc(it.tag)}</span>${esc(it.title)}</div>`).join('') || '<div class="it sub">（无小节）</div>'}</div>
    </li>`).join('')}</ol>
  </section>

  <section class="tab" id="docs">
    <h2>文档导航（${docs.length} 篇）</h2>
    <div class="tbl-wrap"><table>
      <thead><tr><th>文档</th><th>类别</th><th>大小</th><th>最后更新</th><th>状态</th></tr></thead>
      <tbody>${docs.map((d) => `<tr>
        <td><code>${esc(d.path)}</code></td>
        <td><span class="badge ${d.cat === '必读' ? 'info' : 'muted'}">${d.cat}</span></td>
        <td class="num">${(d.size / 1024).toFixed(0)} KB</td>
        <td class="num">${d.mtime}<div class="sub">${d.age} 天前</div></td>
        <td>${d.stale ? '<span class="badge warn">可能已过期</span>' : '<span class="sub">—</span>'}</td>
      </tr>`).join('')}</tbody>
    </table></div>
  </section>

  <footer>
    <b style="color:var(--muted)">全景台 v1</b>　·　由 <code>node scripts/gen-cockpit.mjs</code> 生成（约 1.6 秒）　·　单文件、零 CDN 依赖，可离线打开<br>
    数据源：git 分支与 tree、控制器注解、前端 api 调用、03-TASKS.md、05-CHANGELOG.md、docs/superpowers/plans<br>
    页面里的每个数字都在生成时重新计算，没有任何手工维护的数据；提交 / 合并 / 切换分支后由 <code>.githooks</code> 自动重建。判定口径见 <code>docs/reference/COCKPIT.md</code>
  </footer>
</div>

<div class="overlay" id="ov"><div class="sheet" id="sheet"></div></div>

<div class="overlay" id="askOv"><div class="ask-sheet">
  <div class="ask-head">
    <span style="width:8px;height:8px;border-radius:50%;background:var(--info)"></span>
    <h3 id="askTitle">询问 AI</h3>
    <span class="who" id="askWho"></span>
    <span style="flex:1"></span>
    <button class="close" id="askClose">关闭 ✕</button>
  </div>
  <div class="ask-body">
    <div class="ask-q" id="askQs"></div>
    <textarea id="askText" spellcheck="false"></textarea>
    <div class="ask-actions">
      <button class="primary" id="askCopy">复制 Prompt</button>
      <button id="askCmd">复制命令行</button>
      <button id="askDl">下载上下文 .md</button>
      <span class="ok-msg" id="askMsg"></span>
    </div>
    <div class="ask-tip">
      <b>三种用法，按你顺手来：</b><br>
      ① <b>复制 Prompt</b> 后直接粘到 DSH 对话框（<a href="http://127.0.0.1:43120" style="color:var(--info)">127.0.0.1:43120</a>）或 Codex，上下文文件会自动被读取；<br>
      ② <b>复制命令行</b> 后在项目根目录运行，DSH 会跑一次性问答并把结果打印出来；<br>
      ③ <b>下载上下文 .md</b> 得到一份自包含的快照，随手丢给任何 AI 都能用。<br>
      上下文文件：<code id="askFile"></code>（由 <code>gen-cockpit.mjs</code> 生成，与看板同源）
    </div>
  </div>
</div></div>

<script>
const DETAIL = ${jesc(JSON.stringify(detailData))};
const ASK = ${jesc(JSON.stringify(ASK))};
const PLANS = ${jesc(JSON.stringify(plans.map((p) => ({ file: p.file, title: p.title, date: p.date, state: p.state, reason: p.reason, declared: p.declared, moduleKey: p.moduleKey, taskDone: p.taskDone, taskTotal: p.taskTotal, branches: p.branches, migs: p.migs }))))};
const PROMPT_PREFIX = ${jesc(JSON.stringify('我在做 Blade 项目（服装批发 ERP）。请先读取 '))};
const PROMPT_SUFFIX = ${jesc(JSON.stringify('（这是从看板导出的客观快照：模块任务进度、每个资源的增删改查上线情况、未上线接口、规划方案、最近提交）。读完后再回答，只基于该文件的事实，不要臆测。\\n\\n请回答：'))};
const OPS = ${jesc(JSON.stringify(OPS))};
const STATUS_LABEL = ${jesc(JSON.stringify(STATUS_LABEL))};
const H = (s) => String(s == null ? '' : s).replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');

const tabs = document.querySelectorAll('nav.tabs button');
function activate(name, push) {
  const btn = [...tabs].find((b) => b.dataset.t === name);
  if (!btn) return;
  tabs.forEach((x) => x.classList.remove('on'));
  document.querySelectorAll('section.tab').forEach((s) => s.classList.remove('on'));
  btn.classList.add('on');
  document.getElementById(name).classList.add('on');
  if (push && location.hash !== '#' + name) history.replaceState(null, '', '#' + name);
}
tabs.forEach((b) => b.addEventListener('click', () => { activate(b.dataset.t, true); window.scrollTo({ top: 0, behavior: 'smooth' }); }));
if (location.hash && !location.hash.startsWith('#m=')) activate(location.hash.slice(1), false);

const ov = document.getElementById('ov'), sheet = document.getElementById('sheet');
const chip = (state, label, n) => '<span class="chip ' + state + '">' + label + '<b>' + (n || '—') + '</b></span>';
const opHeader = () => '<th>资源</th><th>控制器</th>' + OPS.map((o) => '<th title="' + H(o.hint) + '">' + o.label + '</th>').join('') + '<th>接口</th>';

function opRow(r) {
  const cells = OPS.map((o) => {
    const be = r.ops[o.key] || { head: 0, master: 0 };
    const pc = (r.adminOps || {})[o.key] || 0, mb = (r.mobileOps || {})[o.key] || 0;
    const st = be.head === 0 ? 'off' : be.master === be.head ? 'on' : 'half';
    const bt = be.head === 0 ? '' : (be.master === be.head ? String(be.head) : be.master + '/' + be.head);
    return '<td class="opcell">' + chip(st, 'BE', bt) + chip(pc ? 'on' : 'off', 'PC', pc) + chip(mb ? 'on' : 'off', 'MB', mb) + '</td>';
  }).join('');
  const off = r.endpoints.filter((e) => !e.inMaster).length;
  return '<tr><td><code>' + H(r.key) + '</code></td><td class="sub" style="margin:0">' + H(r.controllers.join(', ')) + '</td>' + cells
    + '<td class="num">' + r.endpoints.length + (off ? '<div class="sub" style="color:var(--warn)">' + off + ' 未上线</div>' : '') + '</td></tr>';
}

function resourceBlock(r) {
  const eps = r.endpoints.map((e) => '<div class="ep' + (e.inMaster ? '' : ' branch') + '">'
    + '<span class="m ' + e.method + '">' + e.method + '</span>'
    + '<span class="p">' + H(e.path) + (e.inMaster ? '' : ' <span class="badge warn">未上线</span>') + '</span>'
    + '<span class="h">' + H(e.handler) + '</span></div>').join('') || '<div class="empty">没有后端接口</div>';
  const calls = (label, list) => '<div class="sub" style="margin-top:10px">' + label + '（' + list.length + '）：'
    + (list.length ? list.map((c) => '<span class="flist"><span>' + H(c.method + ' ' + c.path) + '</span></span>').join('') : '<span style="color:var(--dim)">无调用</span>') + '</div>';
  const off = r.endpoints.filter((e) => !e.inMaster).length;
  return '<details class="res"><summary><code>' + H(r.key) + '</code>'
    + '<span class="sub" style="margin:0">' + H(r.controllers.join(', ')) + '</span>'
    + '<span style="flex:1"></span>'
    + '<span class="sub" style="margin:0">' + r.endpoints.length + ' 个接口'
    + (off ? ' · <span style="color:var(--warn)">' + off + ' 未上线</span>' : '') + '</span></summary>'
    + '<div class="body">' + eps + calls('PC 管理端调用', r.adminCalls) + calls('移动端调用', r.mobileCalls) + '</div></details>';
}

function progressBar(a, b, color) {
  const p = b ? Math.round(a / b * 100) : 0;
  return '<div class="bar" style="--w:180px"><i style="width:' + p + '%;background:' + color + '"></i></div><span class="pct" style="color:' + color + '">' + p + '%</span>';
}

function openModule(key) {
  const d = DETAIL[key];
  if (!d) return;
  sheet.style.setProperty('--mc', d.color);
  const cls = d.verdict === 'master' ? 'ok' : d.verdict === 'branch' ? 'warn' : d.verdict === 'missing' ? 'bad' : 'muted';
  const todo = d.tasks.filter((t) => t.status !== 'done');
  const eps = d.resources.reduce((a, r) => a + r.endpoints.length, 0);
  const off = d.resources.reduce((a, r) => a + r.endpoints.filter((e) => !e.inMaster).length, 0);

  const taskRows = d.tasks.map((t) => '<tr><td><code>' + H(t.id) + '</code></td><td>' + H(t.name) + '</td>'
    + '<td><span class="badge ' + (t.status === 'done' ? 'ok' : t.status === 'doing' ? 'info' : t.status === 'partial' ? 'warn' : 'muted') + '">'
    + H(STATUS_LABEL[t.status] || t.status) + '</span></td>'
    + '<td class="sub" style="margin:0">' + H(t.group) + '<br>' + H(t.phase) + '</td>'
    + '<td class="sub" style="margin:0;max-width:330px">' + H((t.note || '').slice(0, 170)) + '</td></tr>').join('');

  sheet.innerHTML =
    '<div class="sheet-head"><span style="width:9px;height:9px;border-radius:50%;background:' + d.color + '"></span>'
    + '<h2>' + H(d.name) + '</h2><span class="badge ' + cls + '">' + H(d.verdictLabel) + '</span>'
    + '<button class="ask-btn" id="askModBtn">询问 AI</button>'
    + '<button class="close" id="closeBtn">关闭 ✕</button></div>'
    + '<div class="sheet-body">'
    + '<div class="kpis">'
    + '<span>任务完成<b>' + d.done + '/' + d.total + '</b></span>'
    + (d.ctrlCount ? '<span>控制器在 master<b>' + d.inMaster + '/' + d.ctrlCount + '</b></span>' : '')
    + '<span>资源<b>' + d.resources.length + '</b></span>'
    + '<span>接口<b>' + eps + '</b></span>'
    + '<span>未上线<b style="color:var(--warn)">' + off + '</b></span>'
    + '<span>待办任务<b>' + todo.length + '</b></span>'
    + '</div>'

    + (d.resources.length ? '<div class="sect"><h4>增删改查实现情况 <span class="hint">BE 后端 / PC 管理端 / MB 移动端</span></h4>'
      + '<div class="tbl-wrap"><table><thead><tr>' + opHeader() + '</tr></thead><tbody>' + d.resources.map(opRow).join('') + '</tbody></table></div>'
      + '<p class="note">BE 显示「已上线/总数」，黄色表示部分接口只在分支上；展开下方资源可看到具体是哪些接口未上线。</p></div>' : '')

    + (d.resources.length ? '<div class="sect"><h4>接口明细 <span class="hint">展开查看每个资源实际存在的端点</span></h4>'
      + d.resources.map(resourceBlock).join('') + '</div>' : '')

    + '<div class="sect"><h4>功能分组与完成度 <span class="hint">来自 03-TASKS.md 的阶段分组</span></h4>'
    + '<div class="tbl-wrap"><table><thead><tr><th>阶段</th><th>端</th><th>完成</th><th>进度</th></tr></thead><tbody>'
    + (d.phases.length ? d.phases.map((p) => '<tr><td>' + H(p.name) + '</td><td class="sub" style="margin:0">' + H(p.group) + '</td>'
      + '<td class="num">' + p.done + ' / ' + p.total + '</td><td>' + progressBar(p.done, p.total, d.color) + '</td></tr>').join('')
      : '<tr><td colspan="4" class="empty">没有登记的功能分组</td></tr>')
    + '</tbody></table></div></div>'

    + '<div class="sect"><h4>任务明细（' + d.tasks.length + ' 项）</h4>'
    + '<div class="tbl-wrap"><table><thead><tr><th>ID</th><th>任务</th><th>状态</th><th>归属</th><th>备注</th></tr></thead><tbody>'
    + (taskRows || '<tr><td colspan="5" class="empty">没有登记任务</td></tr>') + '</tbody></table></div></div>'

    + ((d.adminPages.length || d.mobilePages.length) ? '<div class="sect"><h4>相关页面</h4>'
      + (d.adminPages.length ? '<div class="sub">PC 管理端</div><div style="margin:6px 0 12px">' + d.adminPages.map((p) => '<span class="flist"><span>' + H(p) + '</span></span>').join('') + '</div>' : '')
      + (d.mobilePages.length ? '<div class="sub">移动端</div><div style="margin-top:6px">' + d.mobilePages.map((p) => '<span class="flist"><span>' + H(p) + '</span></span>').join('') + '</div>' : '')
      + '</div>' : '')

    + (d.recentCommits.length ? '<div class="sect"><h4>最近提交 <span class="hint">按该模块目录过滤</span></h4>'
      + d.recentCommits.map((c) => '<div class="cl"><span class="hh">' + H(c.hash) + '</span><span class="dd">' + H(c.date) + '</span><span>' + H(c.subject) + '</span></div>').join('')
      + '</div>' : '')

    + '</div>';
  ov.classList.add('on');
  document.body.style.overflow = 'hidden';
  document.getElementById('closeBtn').onclick = closeSheet;
  document.getElementById('askModBtn').onclick = () => openAskModule(key);
  ov.scrollTop = 0;
}
function closeSheet() { ov.classList.remove('on'); document.body.style.overflow = ''; }
ov.addEventListener('click', (e) => { if (e.target === ov) closeSheet(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeSheet(); });
document.querySelectorAll('[data-open]').forEach((el) => el.addEventListener('click', () => openModule(el.dataset.open)));

/* ── 询问 AI ── */
const ROOT_PATH = ${jesc(JSON.stringify(ROOT))};
const askOv = document.getElementById('askOv');
const askText = document.getElementById('askText');
const askTitle = document.getElementById('askTitle');
const askWho = document.getElementById('askWho');
const askFileEl = document.getElementById('askFile');
const askMsg = document.getElementById('askMsg');
const askQs = document.getElementById('askQs');
let askCtx = null;

function buildPrompt(question) {
  return PROMPT_PREFIX + askCtx.file + PROMPT_SUFFIX + '\\n' + question + (askCtx.extra || '');
}

function flash(msg, ok) {
  askMsg.textContent = msg;
  askMsg.style.color = ok === false ? 'var(--bad)' : 'var(--ok)';
  setTimeout(() => { askMsg.textContent = ''; }, 2600);
}

function copyText(text, done) {
  const fallback = () => {
    const ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed'; ta.style.left = '-9999px';
    document.body.appendChild(ta); ta.select();
    let ok = false;
    try { ok = document.execCommand('copy'); } catch (e) { ok = false; }
    document.body.removeChild(ta);
    done(ok);
  };
  if (navigator.clipboard && navigator.clipboard.writeText) {
    navigator.clipboard.writeText(text).then(() => done(true)).catch(fallback);
  } else fallback();
}

function renderQs(list, onPick) {
  askQs.innerHTML = list.map((q, i) => '<button data-q="' + i + '">' + H(q) + '</button>').join('');
  [...askQs.children].forEach((b) => b.addEventListener('click', () => {
    [...askQs.children].forEach((x) => x.classList.remove('on'));
    b.classList.add('on');
    onPick(list[Number(b.dataset.q)]);
  }));
}

function openAskModule(key) {
  const d = DETAIL[key], a = ASK[key];
  if (!a) return;
  const all = a.questions.map((q, i) => (i + 1) + '. ' + q).join('\\n');
  askCtx = { file: a.file, name: d.name, extra: '' };
  askTitle.textContent = '询问 AI · ' + d.name;
  askWho.textContent = '模块上下文　·　' + (d.total ? '任务 ' + d.done + '/' + d.total : '无任务登记');
  askFileEl.textContent = a.file;
  renderQs(a.questions.concat(['整体讲一下这个模块的现状与风险']), (q) => {
    askText.value = buildPrompt(q);
  });
  [...askQs.children].forEach((x) => x.classList.remove('on'));
  askText.value = buildPrompt(all);
  askOv.classList.add('on');
  document.body.style.overflow = 'hidden';
}

function openAskPlan(file) {
  const p = PLANS.find((x) => x.file === file);
  if (!p) return;
  const a = ASK[p.moduleKey] || ASK._project;
  const qs = ['这个方案要解决什么问题？', '这个方案做到哪一步了？', '还差哪些工作才能上线？', '这个方案最大的风险是什么？'];
  askCtx = {
    file: a.file, name: p.title,
    extra: '\\n\\n另外请一并阅读方案文档：' + p.file + '\\n（该方案的看板派生状态：' + p.reason + (p.declared ? '；文档自称状态：' + p.declared : '') + '）',
  };
  askTitle.textContent = '询问 AI · ' + p.title;
  askWho.textContent = '方案上下文　·　' + (p.taskTotal ? '引用任务 ' + p.taskDone + '/' + p.taskTotal : '无任务引用');
  askFileEl.textContent = a.file;
  renderQs(qs, (q) => { askText.value = buildPrompt(q); });
  askText.value = buildPrompt(qs.join('\\n'));
  askOv.classList.add('on');
  document.body.style.overflow = 'hidden';
}

function openAskProject() {
  const a = ASK._project;
  askCtx = { file: a.file, name: '整个项目', extra: '' };
  askTitle.textContent = '询问 AI · 整个项目';
  askWho.textContent = '项目级上下文';
  askFileEl.textContent = a.file;
  renderQs(['这个项目现在到底完成了什么？', '文档进度和生产可用差在哪里？', '规划中的方向优先级该怎么排？', '最该先合并哪个分支？'], (q) => { askText.value = buildPrompt(q); });
  askText.value = buildPrompt(['这个项目现在到底完成了什么？', '文档进度和生产可用差在哪里？', '规划中的方向优先级该怎么排？'].join('\\n'));
  askOv.classList.add('on');
  document.body.style.overflow = 'hidden';
}

function closeAsk() { askOv.classList.remove('on'); document.body.style.overflow = ''; }
document.getElementById('askClose').onclick = closeAsk;
askOv.addEventListener('click', (e) => { if (e.target === askOv) closeAsk(); });
document.addEventListener('keydown', (e) => { if (e.key === 'Escape') closeAsk(); });
document.getElementById('askCopy').onclick = () => copyText(askText.value, (ok) => flash(ok ? '已复制，去 DSH 或 Codex 粘贴即可' : '复制失败，请手动全选复制', ok));
document.getElementById('askCmd').onclick = () => {
  const cmd = 'cd ' + ROOT_PATH + ' && dsh --profile headless "读取 ' + askCtx.file + '，按文件末尾列出的问题回答"';
  copyText(cmd, (ok) => flash(ok ? '命令行已复制，在终端粘贴运行' : '复制失败，请手动复制', ok));
};
document.getElementById('askDl').onclick = () => {
  const name = askCtx.file.split('/').pop();
  const blob = new Blob([askText.value + askCtx.extra], { type: 'text/markdown;charset=utf-8' });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = name.replace('.md', '-prompt.md');
  document.body.appendChild(a); a.click(); document.body.removeChild(a);
  setTimeout(() => URL.revokeObjectURL(a.href), 4000);
  flash('已下载');
};

document.getElementById('askProjectBtn').onclick = openAskProject;
document.querySelectorAll('[data-ask-plan]').forEach((el) => el.addEventListener('click', () => openAskPlan(el.dataset.askPlan)));

const q = document.getElementById('q'), qc = document.getElementById('qcount');
q.addEventListener('input', () => {
  const s = q.value.trim().toLowerCase();
  let shown = 0;
  document.querySelectorAll('.mcard').forEach((c) => {
    const hit = !s || (c.dataset.search || '').toLowerCase().includes(s);
    c.classList.toggle('hidden', !hit);
    if (hit) shown++;
  });
  document.querySelectorAll('.linkrow').forEach((r) => {
    const d = DETAIL[r.dataset.open];
    const hay = d ? (d.name + ' ' + d.resources.map((x) => x.key).join(' ') + ' ' + d.tasks.map((t) => t.id + ' ' + t.name).join(' ')).toLowerCase() : '';
    r.classList.toggle('hidden', !!s && !hay.includes(s));
  });
  qc.textContent = s ? shown + ' 个模块' : '';
  if (s) { const cb = [...tabs].find((b) => b.dataset.t === 'features'); if (cb && !cb.classList.contains('on')) activate('features', true); }
});
</script>
</body>
</html>`;

fs.mkdirSync(path.dirname(OUT), { recursive: true });
fs.writeFileSync(OUT, html);
console.log(`✅ 已生成 ${path.relative(ROOT, OUT)}`);
console.log(`   分支 ${branches.length}（未合并 ${unmerged.length}）· 任务 ${TOTAL.done}/${TOTAL.total} · 迁移 master V${V_MASTER} / HEAD V${V_HEAD}`);
console.log(`   资源 ${Object.keys(resources).length} 个 · 后端接口 ${TOTAL_ENDPOINTS} 个 · 未上线 ${branchOnlyEndpoints.length} 个`);
console.log(`   前端调用 PC ${frontend.admin.length} / 移动端 ${frontend.mobile.length} · 风险 ${risks.length} 条`);
const unrouted = [...new Set(tasks.filter((t) => t.module === 'other').map((t) => t.phase))];
if (unrouted.length) console.log('   ⚠️ 未归入模块的阶段：', unrouted.join(' / '));
