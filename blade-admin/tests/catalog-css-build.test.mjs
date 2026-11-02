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
