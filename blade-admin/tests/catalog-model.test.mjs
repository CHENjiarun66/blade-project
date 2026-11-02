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
for (const [name, dx,dy,expected] of [['jitter',5,4,'pending'],['vertical with drift',9,25,'vertical'],['clear horizontal',-22,5,'horizontal'],['natural diagonal',22,19,'horizontal'],['45 degree diagonal',30,30,'horizontal']]) test(name,()=>assert.equal(gestures.gestureAxis(dx,dy,'pending'),expected))
test('a vertical gesture cannot change axis midway',()=>assert.equal(gestures.gestureAxis(120,25,'vertical'),'vertical'))
test('a horizontal gesture remains horizontal',()=>assert.equal(gestures.gestureAxis(75,90,'horizontal'),'horizontal'))
const target=(axis,dx,velocity=0,index=1,count=3,width=380)=>gestures.swipeTarget(axis,dx,velocity,width,index,count)
for(const [name,args,expected] of [
 ['vertical never changes photo',['vertical',-250],1],['pending tap',['pending',-4],1],['short slow slide rebounds',['horizontal',-12],1],['long left slide advances one',['horizontal',-90],2],['right slide returns one',['horizontal',90],0],['fast deliberate flick',['horizontal',-30,-.8],2],['fast jitter does not advance',['horizontal',-8,-2],1],['reversal does not count as flick',['horizontal',-12,1],1],['first photo never wraps',['horizontal',100,0,0],0],['last photo never wraps',['horizontal',-100,0,2],2],['single photo never switches',['horizontal',-100,0,0,1],0],['phone threshold',['horizontal',-45,0,1,3,182],2],['large image threshold',['horizontal',-90,0,1,3,1000],2],['cannot skip multiple photos',['horizontal',-1600],2],
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

const keypad = load('numericKeypadModel', { './selectionModel':model })
for (const [name, value, key, mode, replace, result] of [
  ['replace existing quantity','120','8','quantity',true,'8'],
  ['append quantity','12','0','quantity',false,'120'],
  ['whole quantity rejects decimal','12','.','quantity',false,'12'],
  ['decimal starts with zero','','.','price',false,'0.'],
  ['customer price cents','19.9','5','price',false,'19.95'],
  ['price does not add a third decimal','19.95','9','price',false,'19.95'],
  ['quantity has a practical bound','999999','0','quantity',false,'999999'],
  ['backspace respects replacement','120','backspace','quantity',true,''],
  ['backspace removes one digit','120','backspace','quantity',false,'12'],
  ['clear keeps quote pending','28','clear','price',false,''],
  ['zero followed by digits','0','8','quantity',false,'8'],
]) test(name,()=>assert.equal(keypad.keypadValue(value,key,mode,replace),result))
test('keypad keeps an empty price distinct from zero',()=>{assert.equal(keypad.committedKeypadValue('','price'),'');assert.equal(keypad.committedKeypadValue('0','price'),'0.00')})
test('keypad confirmation rejects zero quantity but accepts pending quantity',()=>{assert.equal(keypad.validKeypadValue('0','quantity'),false);assert.equal(keypad.validKeypadValue('','quantity'),true)})
test('a deliberate 40px slow swipe advances on an iPad card',()=>assert.equal(gestures.swipeTarget('horizontal',-40,0,393,0,3),1))
test('a 55px slow swipe advances in a large lightbox',()=>assert.equal(gestures.swipeTarget('horizontal',-55,0,850,0,3),1))
test('a gentle 22px flick advances',()=>assert.equal(gestures.swipeTarget('horizontal',-22,-.35,470,0,3),1))
test('horizontal intent tolerates a natural vertical drift',()=>assert.equal(gestures.gestureAxis(18,13,'pending'),'horizontal'))
const stocks = load('stockDisplay')
test('available stock is shown without treating old cached data as zero',()=>{assert.equal(stocks.stockLabel({hasStock:true,availableQty:120}),'可选现货 120 件');assert.equal(stocks.stockLabel({hasStock:true}),'有现货');assert.equal(stocks.stockLabel({hasStock:false,availableQty:0}),'暂无现货')})

function viewportHarness({standalone=false,layoutHeight=1194,visibleHeight=1194}={}) {
  class Element { isContentEditable=false; blur(){ doc.activeElement=null; fire(documentEvents,'focusout') } }
  class Input extends Element { readOnly=false; type='text' }
  class Textarea extends Element {}
  const documentEvents=new Map(),windowEvents=new Map(),visualEvents=new Map(),frames=[],mounted=[],unmount=[]
  const styleValues=new Map(),scrolls=[]
  const style={setProperty:(k,v)=>styleValues.set(k,v),removeProperty:k=>styleValues.delete(k)}
  const classes=()=>{const values=new Set();return {contains:k=>values.has(k),add:k=>values.add(k),remove:k=>values.delete(k)}}
  const doc={activeElement:null,body:{classList:classes()},documentElement:{clientHeight:layoutHeight,classList:classes()},addEventListener:(k,f)=>documentEvents.set(k,f),removeEventListener:k=>documentEvents.delete(k)}
  const visual={height:visibleHeight,scale:1,addEventListener:(k,f)=>visualEvents.set(k,f),removeEventListener:k=>visualEvents.delete(k)}
  const win={innerHeight:layoutHeight,visualViewport:visual,matchMedia:()=>({matches:standalone}),scrollTo:(...args)=>scrolls.push(args),addEventListener:(k,f)=>windowEvents.set(k,f),removeEventListener:k=>windowEvents.delete(k)}
  const exports={}
  const source=fs.readFileSync(new URL('../src/views/catalog/useCatalogViewport.ts',import.meta.url),'utf8')
  vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require:name=>name==='vue'?{onMounted:fn=>mounted.push(fn),onBeforeUnmount:fn=>unmount.push(fn)}:{usePwaViewportReset(){}},document:doc,window:win,HTMLElement:Element,HTMLInputElement:Input,HTMLTextAreaElement:Textarea,requestAnimationFrame:fn=>{frames.push(fn);return frames.length},cancelAnimationFrame(){},setTimeout:()=>0,clearTimeout(){}})
  exports.useCatalogViewport({value:{style}});mounted.forEach(fn=>fn())
  function fire(map,event){map.get(event)?.()}
  return {input:new Input(),doc,visual,win,styleValues,scrolls,focus(input){doc.activeElement=input;fire(documentEvents,'focusin')},blur(){doc.activeElement=null;fire(documentEvents,'focusout')},resize(){fire(visualEvents,'resize')},flush(){while(frames.length)frames.shift()()},cleanup(){unmount.forEach(fn=>fn())}}
}
test('keyboard dismissal recovers height and root scroll after blur',()=>{const h=viewportHarness();h.focus(h.input);h.visual.height=700;h.resize();h.flush();assert.equal(h.scrolls.length,0);h.blur();h.visual.height=1194;h.resize();h.flush();assert.equal(h.styleValues.get('--catalog-viewport-height'),'1194px');assert.equal(h.scrolls.length,1);h.cleanup()})
test('dedicated iPad keyboard hide also recovers when input keeps focus',()=>{const h=viewportHarness();h.focus(h.input);h.visual.height=700;h.resize();h.flush();h.visual.height=1194;h.resize();h.flush();assert.equal(h.doc.activeElement,null);assert.equal(h.scrolls.length,1);h.cleanup()})
test('pinch zoom is not mistaken for keyboard dismissal',()=>{const h=viewportHarness();h.focus(h.input);h.blur();h.visual.scale=1.5;h.visual.height=796;h.resize();h.flush();assert.equal(h.scrolls.length,0);assert.equal(h.styleValues.get('--catalog-viewport-height'),'1194px');h.cleanup()})
test('readonly numeric fields never arm native keyboard recovery',()=>{const h=viewportHarness();h.input.readOnly=true;h.focus(h.input);h.blur();h.resize();h.flush();assert.equal(h.scrolls.length,0);h.cleanup()})

// Drive the actual Touch-event handlers: mouse-only regressions cannot catch Safari arbitration.
function gestureHarness({lightbox=false, count=3, index=0, modal=false, scale=1}={}) {
  const mounted=[],unmount=[],frames=new Map(),events=new Map(),windowEvents=new Map()
  class Element { style={}; listeners=new Map(); closest(selector){return selector==='dialog[open]' && lightbox ? {} : null} getBoundingClientRect(){return {width:850}} addEventListener(k,fn,options){this.listeners.set(k,{fn,options})} removeEventListener(k){this.listeners.delete(k)} }
  const root=new Element(),track=new Element(),dragging={value:false},emitted=[]
  const doc={hidden:false,querySelector:()=>modal?{}:null,addEventListener:(k,fn)=>events.set(k,fn),removeEventListener:k=>events.delete(k)}
  const win={ontouchstart:null,visualViewport:{scale},addEventListener:(k,fn)=>windowEvents.set(k,fn),removeEventListener:k=>windowEvents.delete(k)}
  const exports={},source=fs.readFileSync(new URL('../src/views/catalog/usePhotoGesture.ts',import.meta.url),'utf8')
  vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require:name=>name==='vue'?{onMounted:fn=>mounted.push(fn),onBeforeUnmount:fn=>unmount.push(fn)}:gestures,window:win,document:doc,Element,getComputedStyle:()=>({transform:'none'}),requestAnimationFrame:fn=>{const id=frames.size+1;frames.set(id,fn);return id},cancelAnimationFrame:id=>frames.delete(id),performance:{now:()=>100}})
  const api=exports.usePhotoGesture({root:{value:root},track:{value:track},dragging,index:()=>index,count:()=>count,lightbox:()=>lightbox,setIndex:value=>{index=value;emitted.push(value)}})
  mounted.forEach(fn=>fn())
  const point=(x,y=0,id=7)=>({clientX:x,clientY:y,identifier:id})
  function fire(type,x,y=0,time=0,extras={}) {const event={target:root,touches:type==='touchend'?[]:[point(x,y)],changedTouches:[point(x,y)],timeStamp:time,cancelable:true,prevented:false,preventDefault(){this.prevented=true},...extras};root.listeners.get(type)?.fn(event);return event}
  return {api,root,track,dragging,events,windowEvents,win,emitted,point,fire,flush(){const callbacks=[...frames.values()];frames.clear();callbacks.forEach(fn=>fn())},cleanup(){unmount.forEach(fn=>fn())},get index(){return index}}
}
test('touch move is registered explicitly non-passive on the gallery',()=>{const h=gestureHarness();assert.equal(h.root.listeners.get('touchmove').options.passive,false);h.cleanup();assert.equal(h.root.listeners.size,0)})
test('a slow 20px touch swipe with diagonal drift advances even a large image',()=>{const h=gestureHarness();h.fire('touchstart',100,100);assert.equal(h.fire('touchmove',80,118,200).prevented,true);h.fire('touchend',80,118,400);assert.equal(h.index,1)})
test('vertical touch scrolling is never prevented or converted into a photo swipe',()=>{const h=gestureHarness();h.fire('touchstart',100,100);assert.equal(h.fire('touchmove',107,125,100).prevented,false);h.fire('touchend',140,190,200);assert.equal(h.index,0)})
test('a tiny touch jitter remains a tap',()=>{const h=gestureHarness();h.fire('touchstart',100,100);assert.equal(h.fire('touchmove',96,102,50).prevented,false);h.fire('touchend',96,102,100);assert.equal(h.index,0)})
test('touch dragging writes only to the track once per animation frame',()=>{const h=gestureHarness();h.fire('touchstart',100);h.fire('touchmove',75,3,50);h.fire('touchmove',70,5,80);assert.equal(h.track.style.transform,undefined);h.flush();assert.match(h.track.style.transform,/-30px/);assert.equal(h.root.style.transform,undefined)})
test('touch cancellation never advances and removes document listeners',()=>{const h=gestureHarness();h.fire('touchstart',100);h.fire('touchmove',40,0,100);h.fire('touchcancel',40);assert.equal(h.index,0);assert.equal(h.dragging.value,false);assert.equal(h.events.has('touchstart'),false)})
test('a second finger outside the gallery cancels the current swipe for pinch zoom',()=>{const h=gestureHarness();h.fire('touchstart',100);h.fire('touchmove',60,0,50);h.events.get('touchstart')({touches:[h.point(60),h.point(80,0,8)]});h.fire('touchend',40,0,100);assert.equal(h.index,0)})
test('a pinch-zoomed page leaves touch handling to the browser',()=>{const h=gestureHarness({scale:2});h.fire('touchstart',100);assert.equal(h.fire('touchmove',60).prevented,false);h.fire('touchend',60);assert.equal(h.index,0)})
test('touch pointers cannot start a duplicate pointer gesture',()=>{const h=gestureHarness();h.api.pointerStart({pointerType:'touch',button:0,pointerId:7});assert.equal(h.events.has('pointermove'),false)})
test('a background gallery cannot begin a gesture behind an open modal',()=>{const h=gestureHarness({modal:true});h.fire('touchstart',100);h.fire('touchmove',60);h.fire('touchend',60);assert.equal(h.emitted.length,0)})
test('lightbox touch gestures tolerate greater drift without affecting a background gesture',()=>{const h=gestureHarness({lightbox:true,modal:true});h.fire('touchstart',100,100);assert.equal(h.fire('touchmove',80,130,100).prevented,true);h.fire('touchend',80,130,200);assert.equal(h.index,1)})
test('a completed swipe suppresses the compatibility click that would open a lightbox',()=>{const h=gestureHarness();h.fire('touchstart',100);h.fire('touchmove',70,0,100);h.fire('touchend',70,0,200);const event={detail:1,preventDefault(){this.prevented=true},stopImmediatePropagation(){this.stopped=true}};h.api.guardClick(event);assert.equal(event.prevented,true);assert.equal(event.stopped,true)})
test('consecutive short swipes each advance one photo',()=>{const h=gestureHarness();for(let i=0;i<2;i++){h.fire('touchstart',100);h.fire('touchmove',80,10,200);h.fire('touchend',80,10,400)}assert.equal(h.index,2)})
test('lightbox and card require the same short distance independent of image width',()=>{for(const width of [180,380,850,1194]) assert.equal(gestures.swipeTarget('horizontal',-18,0,width,0,3),1)})
function scrollLockHarness() {
 const unmount=[],events=new Map(),scrolls=[]
 const element=(top=0)=>({style:{overflow:'auto',overscrollBehavior:'contain',position:'',top:'',left:'',width:''},scrollTop:top,scrollLeft:0})
 const html=element(),body=element(),grid=element(340),panel=element(140),win={scrollX:0,scrollY:12,visualViewport:{scale:1},scrollTo:(...args)=>scrolls.push(args)}
 const exports={},source=fs.readFileSync(new URL('../src/views/catalog/useLightboxScrollLock.ts',import.meta.url),'utf8')
 vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require:()=>({onBeforeUnmount:fn=>unmount.push(fn)}),window:win,document:{documentElement:html,body,addEventListener:(k,fn)=>events.set(k,fn),removeEventListener:k=>events.delete(k)}})
 const api=exports.useLightboxScrollLock({value:{querySelectorAll:()=>[grid,panel]}})
 return {api,html,body,grid,panel,events,win,scrolls,cleanup:()=>unmount.forEach(fn=>fn())}
}
test('lightbox freezes root, list and detail scrolling and restores their exact prior positions',()=>{const h=scrollLockHarness();h.api.lock();h.api.lock();assert.equal(h.body.style.position,'fixed');assert.equal(h.grid.style.overflow,'hidden');assert.equal(h.panel.style.overflow,'hidden');h.grid.scrollTop=0;h.api.unlock();assert.equal(h.grid.scrollTop,340);assert.equal(h.panel.scrollTop,140);assert.equal(h.grid.style.overflow,'auto');assert.equal(h.body.style.position,'');assert.equal(h.events.size,0);assert.deepEqual(h.scrolls,[[0,12]])})
test('lightbox backdrop blocks single-finger scroll but retains native pinch zoom',()=>{const h=scrollLockHarness();h.api.lock();const event=n=>({touches:Array(n),cancelable:true,prevented:false,preventDefault(){this.prevented=true}});const one=event(1),two=event(2);h.events.get('touchmove')(one);h.events.get('touchmove')(two);assert.equal(one.prevented,true);assert.equal(two.prevented,false);h.win.visualViewport.scale=2;const zoomed=event(1);h.events.get('touchmove')(zoomed);assert.equal(zoomed.prevented,false);h.cleanup();assert.equal(h.events.size,0)})
test('lightbox stops wheel scroll chaining while retaining control-wheel zoom',()=>{const h=scrollLockHarness();h.api.lock();const event=ctrlKey=>({ctrlKey,cancelable:true,prevented:false,preventDefault(){this.prevented=true}});const scroll=event(false),zoom=event(true);h.events.get('wheel')(scroll);h.events.get('wheel')(zoom);assert.equal(scroll.prevented,true);assert.equal(zoom.prevented,false)})

test('an intentional new button tap is usable immediately after swiping',()=>{const h=gestureHarness();h.fire('touchstart',100);h.fire('touchmove',70,0,100);h.fire('touchend',70,0,200);const control=new h.root.constructor();control.closest=()=>({});h.fire('touchstart',80,0,250,{target:control});const event={detail:1,preventDefault(){this.prevented=true},stopImmediatePropagation(){}};h.api.guardClick(event);assert.equal(event.prevented,undefined)})

test('PWA entry uses the available rectangle without adding a system-owned status bar',()=>{const h=viewportHarness({standalone:true,layoutHeight:852,visibleHeight:793});assert.equal(h.styleValues.get('--catalog-viewport-height'),'793px');assert.equal(h.scrolls.length,0)})
test('browser entry fits the visible viewport without reserving hidden browser chrome',()=>{const h=viewportHarness({layoutHeight:852,visibleHeight:759});assert.equal(h.styleValues.get('--catalog-viewport-height'),'759px')})
test('rotation updates height without resetting normal browse scroll',()=>{const h=viewportHarness({standalone:true});h.win.innerHeight=393;h.doc.documentElement.clientHeight=393;h.visual.height=393;h.resize();h.flush();assert.equal(h.styleValues.get('--catalog-viewport-height'),'393px');assert.equal(h.scrolls.length,0)})

function pwaResetHarness({ios=true,standalone=true,scale=1.18,width=393,height=793}={}) {
 const mounted=[],unmount=[],docEvents=new Map(),winEvents=new Map(),frames=new Map(),timers=new Map(),writes=[],scrolls=[];let seq=0,settled=0
 class Element {isContentEditable=false}class Input extends Element{readOnly=false}class Textarea extends Element{}
 const meta={content:'width=device-width, initial-scale=1.0, viewport-fit=cover',setAttribute(k,v){this.content=v;writes.push(v)}}
 const doc={hidden:false,activeElement:null,querySelector:()=>meta,addEventListener:(k,fn)=>docEvents.set(k,fn),removeEventListener:k=>docEvents.delete(k)}
 const win={navigator:{userAgent:ios?'iPhone OS 16_1_1':'Chrome',platform:ios?'iPhone':'Linux',maxTouchPoints:ios?5:0,standalone},screen:{width:393,height:852},innerWidth:width,innerHeight:height,visualViewport:{scale},matchMedia:()=>({matches:standalone}),scrollTo:(...args)=>scrolls.push(args),addEventListener:(k,fn)=>winEvents.set(k,fn),removeEventListener:k=>winEvents.delete(k)}
 const source=fs.readFileSync(new URL('../src/views/catalog/usePwaViewportReset.ts',import.meta.url),'utf8'),exports={}
 vm.runInNewContext(ts.transpileModule(source,{compilerOptions:{module:ts.ModuleKind.CommonJS,target:ts.ScriptTarget.ES2022}}).outputText,{exports,require:()=>({onMounted:fn=>mounted.push(fn),onBeforeUnmount:fn=>unmount.push(fn)}),window:win,document:doc,HTMLElement:Element,HTMLInputElement:Input,HTMLTextAreaElement:Textarea,requestAnimationFrame:fn=>{const id=++seq;frames.set(id,fn);return id},cancelAnimationFrame:id=>frames.delete(id),setTimeout:(fn,ms)=>{const id=++seq;timers.set(id,{fn,ms});return id},clearTimeout:id=>timers.delete(id)})
 exports.usePwaViewportReset(()=>settled++);mounted.forEach(fn=>fn())
 return {doc,win,meta,writes,scrolls,docEvents,winEvents,Input,frame(){const batch=[...frames.values()];frames.clear();batch.forEach(fn=>fn())},fire(map,event,arg){map.get(event)?.(arg)},timeout(ms){const batch=[...timers.entries()].filter(([,t])=>t.ms===ms);batch.forEach(([id,t])=>{timers.delete(id);t.fn()})},cleanup:()=>unmount.forEach(fn=>fn()),get settled(){return settled}}
}
test('iOS PWA launch flattens restored zoom then removes all temporary zoom limits',()=>{const h=pwaResetHarness();assert.match(h.meta.content,/maximum-scale=1/);h.frame();h.frame();assert.doesNotMatch(h.meta.content,/maximum-scale|minimum-scale|user-scalable/);assert.equal(h.settled,1);assert.deepEqual(h.scrolls,[[0,0]])})
test('ordinary Safari tabs and non-iOS browsers do not get the PWA reset',()=>{for(const options of [{standalone:false},{ios:false}]){const h=pwaResetHarness(options);assert.equal(h.writes.length,0);assert.equal(h.docEvents.size,0)}})
test('backgrounding releases a startup clamp even if frames were suspended',()=>{const h=pwaResetHarness();h.doc.hidden=true;h.fire(h.docEvents,'visibilitychange');assert.doesNotMatch(h.meta.content,/maximum-scale/);assert.equal(h.scrolls.length,0)})
test('resume repairs a stale desktop-width viewport',()=>{const h=pwaResetHarness();h.frame();h.frame();h.win.visualViewport.scale=1;h.win.innerWidth=980;h.win.innerHeight=1800;h.fire(h.winEvents,'pageshow');assert.match(h.meta.content,/maximum-scale=1/)})
test('manual pinch immediately releases the startup clamp and is preserved on resume',()=>{const h=pwaResetHarness();h.fire(h.docEvents,'touchstart',{touches:[{},{}]});assert.doesNotMatch(h.meta.content,/maximum-scale/);const n=h.writes.length;h.fire(h.winEvents,'pageshow');h.timeout(500);assert.equal(h.writes.length,n);assert.equal(h.scrolls.length,0)})
test('zoom recovery never interrupts an actively edited text field',()=>{const h=pwaResetHarness();h.frame();h.frame();h.doc.activeElement=new h.Input();const n=h.writes.length;h.fire(h.winEvents,'pageshow');h.timeout(500);assert.equal(h.writes.length,n)})
test('delayed PWA restoration is checked again after pageshow',()=>{const h=pwaResetHarness({scale:1});h.frame();h.frame();h.fire(h.winEvents,'pageshow');const n=h.writes.length;h.win.visualViewport.scale=1.18;h.timeout(500);assert.equal(h.writes.length,n+1)})
test('PWA reset timeout and unmount cannot leave pinch zoom disabled',()=>{const h=pwaResetHarness();h.timeout(250);assert.doesNotMatch(h.meta.content,/maximum-scale/);h.cleanup();assert.equal(h.docEvents.size,0);assert.equal(h.winEvents.size,0)})
test('catalog host ownership is removed when leaving the page',()=>{const h=viewportHarness();assert.equal(h.doc.body.classList.contains('catalog-host'),true);h.cleanup();assert.equal(h.doc.body.classList.contains('catalog-host'),false)})
