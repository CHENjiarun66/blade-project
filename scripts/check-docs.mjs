#!/usr/bin/env node
// Offline consistency check: local links, document catalog and portfolio task references.
import { readFileSync, readdirSync, existsSync } from 'node:fs'
import { dirname, resolve, relative } from 'node:path'
import { fileURLToPath } from 'node:url'
const root = resolve(dirname(fileURLToPath(import.meta.url)), '..')
function walk(dir) {
  return readdirSync(dir, { withFileTypes: true }).flatMap(e => e.isDirectory() ? walk(resolve(dir, e.name)) : e.name.endsWith('.md') ? [resolve(dir, e.name)] : [])
}
const docs = walk(resolve(root, 'docs'))
const files = [...docs, ...['README.md', 'CLAUDE.md', 'blade-backend/README.md', 'blade-backend/CLAUDE.md', 'blade-admin/README.md', 'blade-admin/CLAUDE.md', 'blade-mobile/README.md', 'blade-mobile/CLAUDE.md', 'deploy/nas/README.md', 'tools/blade-agent-key-manager/README.md'].map(f => resolve(root, f))]
const errors = []
let links = 0
for (const file of files) {
  // Ignore shell/JSON examples; their Markdown-looking syntax is not a link.
  const content = readFileSync(file, 'utf8').replace(/(^|\n)(`{3,}|~{3,})[^\n]*\n[\s\S]*?\n\2[^\n]*(?=\n|$)/g, '')
  for (const match of content.matchAll(/\[[^\]\n]+\]\((<[^>]+>|[^)\n]+)\)/g)) {
    const url = match[1].replace(/^<|>$/g, '').replace(/\s+["'][^]*$/, '')
    if (/^(?:[a-z][a-z\d+.-]*:|#|\/)/i.test(url)) continue
    const path = decodeURIComponent(url.split(/[?#]/)[0])
    if (!path) continue
    links++
    if (!existsSync(resolve(dirname(file), path))) errors.push(`${relative(root, file)}: missing ${url}`)
  }
}
const data = JSON.parse(readFileSync(resolve(root, 'docs/project-streams.json'), 'utf8'))
const tasks = readFileSync(resolve(root, 'docs/03-TASKS.md'), 'utf8')
const ids = new Set()
const scope = readFileSync(resolve(root, 'docs/PROJECT_SCOPE.md'), 'utf8')
if (!data.updated || !data.focus || !Array.isArray(data.streams) || !data.streams.length) errors.push('project-streams.json: missing portfolio fields')
for (const stream of data.streams ?? []) {
  if (ids.has(stream.id)) errors.push(`duplicate stream: ${stream.id}`)
  ids.add(stream.id)
  for (const key of ['id', 'name', 'priority', 'execution', 'implementation', 'acceptance', 'next']) {
    if (typeof stream[key] !== 'string' || !stream[key].trim()) errors.push(`${stream.id}: missing ${key}`)
  }
  for (const key of ['name', 'priority', 'execution', 'implementation', 'acceptance']) {
    if (!scope.includes(stream[key])) errors.push(`${stream.id}: scope document differs at ${key}`)
  }
  if (!Array.isArray(stream.tasks)) errors.push(`${stream.id}: tasks must be an array`)
  for (const id of stream.tasks ?? []) {
    if (!tasks.includes(`| ${id} |`)) errors.push(`${stream.id}: unknown task ${id}`)
  }
}
const schemaIndex = readFileSync(resolve(root, 'docs/architecture/SCHEMA_MIGRATIONS.md'), 'utf8')
for (const file of readdirSync(resolve(root, 'blade-backend/src/main/resources/db/migration')).filter(f => /^V.*\.sql$/.test(f))) {
  if (!schemaIndex.includes(file)) errors.push(`schema index missing: ${file}`)
}
const catalogPath = resolve(root, 'docs/DOCUMENT_CATALOG.md')
if (!existsSync(catalogPath)) errors.push('missing docs/DOCUMENT_CATALOG.md')
else {
  const catalog = readFileSync(catalogPath, 'utf8')
  for (const file of docs) {
    if (!catalog.includes(relative(resolve(root, 'docs'), file))) errors.push(`catalog missing: ${relative(root, file)}`)
  }
}
if (errors.length) {
  console.error(errors.join('\n'))
  console.error(`FAILED: ${errors.length} issues, ${files.length} documents, ${links} local links`)
  process.exitCode = 1
} else console.log(`OK: ${files.length} documents, ${links} local links, ${data.streams.length} streams; catalog and task references aligned`)
