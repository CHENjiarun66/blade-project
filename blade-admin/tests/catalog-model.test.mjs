import { test } from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'
import ts from 'typescript'
function load(name, stubs = {}) {
  const source = fs.readFileSync(new URL(`../src/views/catalog/${name}.ts`, import.meta.url), 'utf8')
  const { outputText } = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022 } })
  const exports = {}
  vm.runInNewContext(outputText, { exports, require: path => { if (path in stubs) return stubs[path]; throw Error(`Unexpected import ${path}`) } })
  return exports
}
const model = load('selectionModel'), gestures = load('galleryGesture')
const images = load('catalogImages', { '@/api/file': { filePreviewUrl: id => `/api/files/${id}/preview?fresh=1`, fileVariantUrl: (id,type) => `/api/files/${id}/variant?type=${type}` } })
const line = (extra = {}) => ({ product: { id:1 }, checked:true,colorNote:'混色（3色）',qty:'120',unitPrice:'28.00',...extra })
test('wholesale amount uses style total, without multiplying colour count', () => assert.equal(model.lineAmount(line()), 336000n))
test('customer quotation changes the calculated amount', () => assert.equal(model.lineAmount(line({ unitPrice:'29.50' })), 354000n))
test('decimal arithmetic is exact', () => assert.equal(model.lineAmount(line({ qty:'3',unitPrice:'0.10' })), 30n))
test('blank price is different from explicitly entered zero', () => { assert.equal(model.lineAmount(line({unitPrice:''})),null); assert.equal(model.lineAmount(line({unitPrice:'0'})),0n) })
test('invalid prices are not silently rounded', () => { for(const value of ['-1','1.001','abc','1000000']) assert.equal(model.priceCents(value),null) })
test('quantities must be positive whole pieces', () => { for(const value of ['0','-1','1.5','1e3','1000000']) assert.equal(model.quantity(value),null) })
test('unpriced pieces count but are flagged outside quoted amount', () => { const t=model.selectionTotals([line(),line({product:{id:2},qty:'80',unitPrice:''})]);assert.equal(t.qty,200);assert.equal(t.amount,336000n);assert.equal(t.pending,1) })
test('unchecked styles remain stored and are excluded from handoff totals', () => { const t=model.selectionTotals([line(),line({checked:false})]);assert.equal(t.styles,1);assert.equal(t.qty,120) })
test('large legitimate totals remain exact above Number safe integer', () => { const t=model.selectionTotals(Array.from({length:200},()=>line({qty:'999999',unitPrice:'999999.99'})));assert.equal(t.amount,19999979800000200n);assert.equal(model.money(t.amount),'199,999,798,000,002.00') })
test('image order includes main, uploaded galleries and SKU images only once', () => { const list=images.productImages({mainImageUrl:'/api/files/1/preview?old=1',imageUrls:['/api/files/2/preview','/api/files/1/preview'],skus:[{imageUrls:['/api/files/3/preview']}]});assert.equal(list.join('|'),'/api/files/1/preview|/api/files/2/preview|/api/files/3/preview') })
test('display resolves file variants without persisting preview credentials', () => { assert.equal(images.canonicalImage('/api/files/3/variant?type=card&previewToken=old'),'/api/files/3/preview');assert.equal(images.imageUrl('/api/files/3/preview?old=1','card'),'/api/files/3/variant?type=card') })
for (const [name, dx,dy,expected] of [['jitter',5,4,'pending'],['vertical with drift',9,25,'vertical'],['clear horizontal',-22,5,'horizontal'],['ambiguous diagonal',22,19,'vertical'],['45 degree diagonal',30,30,'vertical']]) test(name,()=>assert.equal(gestures.gestureAxis(dx,dy,'pending'),expected))
test('a vertical gesture cannot change axis midway',()=>assert.equal(gestures.gestureAxis(120,25,'vertical'),'vertical'))
test('a horizontal gesture remains horizontal',()=>assert.equal(gestures.gestureAxis(75,90,'horizontal'),'horizontal'))
const target=(axis,dx,velocity=0,index=1,count=3,width=380)=>gestures.swipeTarget(axis,dx,velocity,width,index,count)
for(const [name,args,expected] of [
 ['vertical never changes photo',['vertical',-250],1],['pending tap',['pending',-4],1],['short slow slide rebounds',['horizontal',-32],1],['long left slide advances one',['horizontal',-90],2],['right slide returns one',['horizontal',90],0],['fast deliberate flick',['horizontal',-30,-.8],2],['fast jitter does not advance',['horizontal',-18,-2],1],['reversal does not count as flick',['horizontal',-30,1],1],['first photo never wraps',['horizontal',100,0,0],0],['last photo never wraps',['horizontal',-100,0,2],2],['single photo never switches',['horizontal',-100,0,0,1],0],['phone threshold',['horizontal',-45,0,1,3,182],2],['large image threshold',['horizontal',-90,0,1,3,1000],2],['cannot skip multiple photos',['horizontal',-1600],2],
]) test(name,()=>assert.equal(target(...args),expected))
test('pause does not retain an old fling velocity',()=>assert.equal(gestures.recentVelocity([{x:100,time:0},{x:50,time:80},{x:50,time:300}],300),0))
test('recent fling speed follows the last 100ms',()=>assert.equal(gestures.recentVelocity([{x:100,time:0},{x:50,time:50}],50),-1))
test('edge resistance is bounded',()=>assert.equal(gestures.swipeOffset(600,0,3,380),45.6))
test('interior drag follows finger movement',()=>assert.equal(gestures.swipeOffset(-70,1,3,380),-70))

const { ref, reactive, computed, watch } = await import('vue')
function productData(id) { return { id, productCode:`P${id}`,name:`Product ${id}`,imageUrls:[],mainImageUrl:null,colors:[],sizes:[],skus:[],hasImage:false,hasStock:true } }
function productQuery(api, cache = {}) {
  return load('useCatalogProducts', { vue:{ref,reactive,computed}, '@/api/catalog':{getCatalogProducts:api,getCatalogFilters:async()=>({data:{categories:[],colors:[],sizes:[],stockModes:[]}})}, '@/utils/catalogCache':{readCatalogProductsCache:()=>null,writeCatalogProductsCache:()=>{},...cache} }).useCatalogProducts()
}
function deferred() { let resolve,reject;const promise=new Promise((yes,no)=>{resolve=yes;reject=no});return {promise,resolve,reject} }
function page(records,total=records.length,current=1) { return {data:{records,total,current,size:20,pages:Math.ceil(total/20)}} }
test('paging merges product IDs once and requests the next page',async()=>{const requested=[];const query=productQuery(async p=>{requested.push(p.current);return p.current===1?page(Array.from({length:20},(_,i)=>productData(i+1)),25):page([productData(20),...Array.from({length:5},(_,i)=>productData(i+21))],25,2)});await query.fetchProducts();await query.fetchProducts(false);assert.equal(query.products.value.length,25);assert.equal(query.hasMore.value,false);assert.equal(requested.join(','),'1,2')})
test('an older search response cannot overwrite a newer one',async()=>{const a=deferred(),b=deferred();let calls=0;const query=productQuery(()=>++calls===1?a.promise:b.promise);const first=query.fetchProducts();query.filters.keyword='B';const second=query.fetchProducts();b.resolve(page([productData(2)]));await second;a.resolve(page([productData(1)]));await first;assert.equal(query.products.value[0].id,2)})
test('cache is written for the requested filters, not later selected-view filters',async()=>{const d=deferred();let cached;const query=productQuery(()=>d.promise,{writeCatalogProductsCache:filters=>{cached=filters.keyword}});query.filters.keyword='original';const pending=query.fetchProducts();query.filters.keyword='';d.resolve(page([productData(1)]));await pending;assert.equal(cached,'original')})
test('failed load-more can retry the same page',async()=>{let failed=false;const pages=[];const query=productQuery(async p=>{pages.push(p.current);if(p.current===2&&!failed){failed=true;throw Error('offline')}return page([productData(p.current)],2,p.current)});await query.fetchProducts();await query.fetchProducts(false);assert.equal(query.loadingMore.value,false);await query.fetchProducts(false);assert.equal(pages.join(','),'1,2,2');assert.equal(query.products.value.length,2)})
test('cached records can render before refreshing the network',()=>{const query=productQuery(async()=>page([]),{readCatalogProductsCache:()=>({products:[productData(9)],total:1,current:1})});query.applyCache();assert.equal(query.products.value[0].id,9)})

function selectionHarness() {
  const values=new Map(),hooks=[]
  const token=tenant=>`e30.${Buffer.from(JSON.stringify({tenantId:tenant})).toString('base64url')}.test`
  const auth={token:token(1),userInfo:{userId:'4'}}
  values.set('token',auth.token)
  const storage={getItem:key=>values.get(key)||null,setItem:(key,value)=>values.set(key,value),removeItem:key=>values.delete(key)}
  const source=fs.readFileSync(new URL('../src/views/catalog/useCatalogSelection.ts',import.meta.url),'utf8')
  const exports={}
  vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require:name=>({vue:{ref,computed,watch,onBeforeUnmount:fn=>hooks.push(fn)},'@/stores/auth':{useAuthStore:()=>auth},'./selectionModel':model,'./catalogImages':images})[name],localStorage:storage,window:{addEventListener(){},removeEventListener(){}},atob,crypto:globalThis.crypto,setTimeout,clearTimeout})
  return {create:exports.useCatalogSelection,values,auth,token,cleanup:()=>hooks.forEach(fn=>fn())}
}
test('selection is deduplicated and saves customer quotes',()=>{const h=selectionHarness(),s=h.create();s.save(line({product:productData(1)}));s.add(productData(1));assert.equal(s.lines.value.length,1);assert.equal(s.lines.value[0].unitPrice,'28.00');h.cleanup()})
test('selection restores quotations only for the same user and tenant',()=>{const h=selectionHarness();const s=h.create();s.save(line({product:productData(1)}));assert.equal(h.create().lines.value[0].unitPrice,'28.00');h.auth.token=h.token(2);h.values.set('token',h.auth.token);assert.equal(h.create().lines.value.length,0);h.cleanup()})
test('handoff retry keeps its request ID through reload',()=>{const h=selectionHarness(),s=h.create();const id=s.idFor('same payload');assert.equal(s.idFor('same payload'),id);assert.equal(h.create().idFor('same payload'),id);assert.notEqual(s.idFor('changed payload'),id);h.cleanup()})
test('removal undo preserves colour, quantity and quote',()=>{const h=selectionHarness(),s=h.create();s.save(line({product:productData(1)}));const undo=s.remove(1);assert.equal(s.lines.value.length,0);undo();assert.equal(s.lines.value[0].colorNote,'混色（3色）');assert.equal(s.lines.value[0].qty,'120');h.cleanup()})
test('logout does not recreate a cleared quote cache during unmount',()=>{const h=selectionHarness(),s=h.create();s.save(line({product:productData(1)}));h.values.delete('token');h.values.delete('catalog:selection:v1:1:4');h.cleanup();assert.equal(h.values.has('catalog:selection:v1:1:4'),false)})
test('malformed local storage is recoverable instead of crashing',()=>{const h=selectionHarness();h.values.set('catalog:selection:v1:1:4','{bad');const s=h.create();assert.equal(s.lines.value.length,0);assert.equal(s.storageError.value,true);h.cleanup()})
