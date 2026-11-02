<template>
  <div class="wholesale-header"><div class="wholesale-heading"><button class="icon-button" aria-label="继续浏览款式" @click="emit('close')"><CatalogIcon name="back" :size="20" /></button><div><h2>批发选款单 <span>{{ lines.length }} 款</span></h2><p>按款号填写，单价按本次客户报价</p></div></div><span class="sheet-brand">JIAJIA<span>嘉嘉服饰</span></span></div>
  <div class="wholesale-tools">
    <label class="check-label"><input type="checkbox" :checked="!!lines.length && lines.every(line => line.checked)" :indeterminate="lines.some(line => line.checked) && !lines.every(line => line.checked)" aria-label="全选本次确认项" @change="setAll(($event.target as HTMLInputElement).checked)">全选本次确认项</label>
    <form class="quick-add" @submit.prevent="emit('lookup', quickCode)"><input v-model="quickCode" maxlength="100" aria-label="输入款号添加" placeholder="输入款号添加" autocomplete="off"><button class="quick-add-button" type="submit" :disabled="lookupBusy"><CatalogIcon name="plus" :size="16" />{{ lookupBusy ? '查找中' : '添加款号' }}</button></form>
    <button class="clear-list" :disabled="!lines.length" @click="emit('clear')">清空清单</button>
  </div>
  <div class="wholesale-scroll">
    <p v-if="refreshing" class="sheet-loading" role="status">正在核对款式状态…</p>
    <p v-if="refreshError" class="catalog-notice" role="alert">{{ refreshError }} <button @click="emit('refresh')">重试</button></p>
    <table v-if="lines.length" class="wholesale-table">
      <colgroup><col class="col-check"><col class="col-style"><col class="col-color"><col class="col-qty"><col class="col-price"><col class="col-amount"><col class="col-remove"></colgroup>
      <thead><tr><th aria-label="勾选"></th><th>款号 / 商品</th><th>颜色说明</th><th>数量</th><th>单价</th><th>金额</th><th aria-label="移除"></th></tr></thead>
      <tbody><tr v-for="line in lines" :key="line.product.id" :data-item="line.product.id" :class="{ 'not-chosen': !line.checked, 'unavailable': line.unavailable }">
        <td class="td-check"><input v-model="line.checked" type="checkbox" :aria-label="`勾选${line.product.productCode}用于本次移交`"></td>
        <td class="td-style"><div class="style-cell"><CatalogImage :src="coverImage(line.product)" :alt="line.product.name" /><div><strong>{{ line.product.productCode }}</strong><span>{{ line.product.name }}</span><span class="wholesale-status" :class="lineStatus(line) === '已填写' ? 'complete' : 'pending'">{{ lineStatus(line) }}</span></div></div></td>
        <td class="td-color" data-caption="颜色说明"><input v-model="line.colorNote" class="color-note" maxlength="100" :list="`colors-${line.product.id}`" :aria-label="`${line.product.productCode}颜色说明`" placeholder="混色 / 3色 / 具体颜色"><datalist :id="`colors-${line.product.id}`"><option value="混色"/><option value="2色"/><option value="3色"/><option v-for="color in line.product.colors" :key="color.id" :value="color.name" /></datalist></td>
        <td class="td-qty" data-caption="数量"><input v-model="line.qty" data-field="qty" type="text" inputmode="numeric" maxlength="6" :aria-label="`${line.product.productCode}总数量`" :aria-invalid="!!line.qty && quantity(line.qty) === null" :class="{ invalid: !!line.qty && quantity(line.qty) === null }" placeholder="总数量"></td>
        <td class="td-price" data-caption="单价"><input v-model="line.unitPrice" data-field="unitPrice" type="text" inputmode="decimal" maxlength="12" :aria-label="`${line.product.productCode}本次单价`" :aria-invalid="!!line.unitPrice && priceCents(line.unitPrice) === null" :class="{ invalid: !!line.unitPrice && priceCents(line.unitPrice) === null }" placeholder="待报价" @change="formatPrice(line)"></td>
        <td class="td-amount" data-caption="金额"><output :data-line-amount="line.product.id" :class="{ unquoted: lineAmount(line) === null }">{{ money(lineAmount(line)) }}</output><small>{{ lineAmount(line) === null ? '数量 × 单价' : '自动计算' }}</small></td>
        <td class="td-remove"><button class="icon-button" :aria-label="`移除${line.product.productCode}`" @click="emit('remove', line.product.id)"><CatalogIcon name="close" :size="16" /></button></td>
      </tr></tbody>
    </table>
    <div v-if="lines.length" class="wholesale-note"><span><CatalogIcon name="check" :size="14" />颜色支持“混色”“3色”或具体说明，数量按整款填写</span><span>未填单价的款保留为待报价，不按零元计入合计</span></div>
    <div v-else class="panel-empty wholesale-empty"><CatalogIcon name="bag" :size="40" /><h3>批发清单为空</h3><p>可返回选款，也可以在上方直接输入款号。</p><button class="secondary-button" @click="emit('close')">继续选款<CatalogIcon name="arrow" :size="18" /></button></div>
  </div>
  <div class="wholesale-footer">
    <div class="wholesale-counts"><span>本次勾选</span><strong>{{ totals.styles }} <small>款</small><i></i>{{ totals.qty.toLocaleString('zh-CN') }} <small>件</small></strong></div>
    <div class="wholesale-money"><span>{{ totals.pending ? '已填金额' : '合计金额' }}</span><strong>{{ totals.styles && totals.pending === totals.styles ? '待报价' : money(totals.amount) }}</strong><small>{{ totals.pending ? `${totals.pending} 款待报价，未计入金额` : totals.colorPending ? `${totals.colorPending} 款颜色待确认` : '金额 = 数量 × 本次单价' }}</small></div>
    <button id="handoffButton" class="primary-button" :disabled="!totals.styles || refreshing" @click="emit('handoff')">交给店员确认<CatalogIcon name="arrow" :size="20" /></button><p class="wholesale-footer-note">数量和本次单价可直接修改，店员确认后再开单</p>
  </div>
</template>
<script setup lang="ts">
import { ref, computed } from 'vue'
import CatalogIcon from './CatalogIcon.vue'
import CatalogImage from './CatalogImage.vue'
import { coverImage } from './catalogImages'
import { selectionTotals, quantity, priceCents, decimalPrice, money, lineAmount, lineStatus, type SelectionLine } from './selectionModel'
const props = defineProps<{ lines: SelectionLine[]; lookupBusy: boolean; refreshing: boolean; refreshError: string }>()
const emit = defineEmits<{ close: []; clear: []; lookup: [string]; remove: [number]; refresh: []; handoff: [] }>()
const quickCode = ref('')
const totals = computed(() => selectionTotals(props.lines))
function setAll(value: boolean) { props.lines.forEach(line => { line.checked = value }) }
function formatPrice(line: SelectionLine) { const price = priceCents(line.unitPrice); if (price !== null) line.unitPrice = decimalPrice(price) }
</script>
