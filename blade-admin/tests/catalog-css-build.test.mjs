import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
const assets = new URL('../dist/assets/', import.meta.url)
test('production catalogue breakpoints preserve legacy Safari media-query syntax', () => {
  const styles = fs.readdirSync(assets).filter(name => name.endsWith('.css')).map(name => fs.readFileSync(new URL(name, assets), 'utf8')).filter(css => css.includes('.catalog-page'))
  assert.ok(styles.length > 0, 'Run npm run build before this artifact check')
  const css = styles.join('\n'), queries = [...css.matchAll(/@media([^{}]+)\{/g)].map(match => match[1])
  assert.ok(queries.some(query => /max-width:\s*640px/.test(query)), 'Phone breakpoint must survive production minification')
  assert.ok(queries.every(query => !/[<>]=?/.test(query)), 'Range media queries are unsupported before Safari 16.4')
})

test('PWA startup uses a system-owned status bar without permanently restricting user zoom',()=>{
 const html=fs.readFileSync(new URL('../dist/index.html',import.meta.url),'utf8')
 assert.match(html,/name="apple-mobile-web-app-status-bar-style" content="default"/)
 const viewport=html.match(/name="viewport" content="([^"]+)"/)[1]
 assert.match(viewport,/width=device-width/);assert.match(viewport,/initial-scale=1/)
 assert.doesNotMatch(viewport,/maximum-scale|user-scalable/)
})
