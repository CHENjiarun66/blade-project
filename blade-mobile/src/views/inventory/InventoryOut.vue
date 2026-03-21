<template>
  <v-container>
    <v-btn icon="mdi-arrow-left" variant="text" @click="router.back()" class="mb-2" />

    <div class="text-h6 mb-4">出库</div>

    <v-form ref="formRef" @submit.prevent="handleSubmit">
      <v-select
        v-model="form.warehouseId"
        :items="warehouses"
        item-title="name"
        item-value="id"
        label="出库仓库"
        :rules="[rules.required]"
        class="mb-2"
      />

      <v-radio-group v-model="form.source" inline>
        <v-radio label="订单出库" value="ORDER" color="primary" />
        <v-radio label="其他出库" value="OTHER" color="secondary" />
      </v-radio-group>

      <v-textarea
        v-if="form.source === 'OTHER'"
        v-model="form.reason"
        label="出库原因"
        :rules="form.source === 'OTHER' ? [rules.required] : []"
        rows="2"
        class="mb-2"
      />

      <div class="text-subtitle-1 mb-2">出库明细</div>

      <v-card
        v-for="(item, index) in form.items"
        :key="index"
        class="mb-2"
        variant="outlined"
      >
        <v-card-text>
          <v-row dense>
            <v-col cols="7">
              <v-select
                v-model="item.skuId"
                :items="skuOptions"
                item-title="label"
                item-value="id"
                label="商品SKU"
                density="compact"
                :rules="[rules.required]"
              />
            </v-col>
            <v-col cols="3">
              <v-text-field
                v-model.number="item.quantity"
                label="数量"
                type="number"
                min="1"
                density="compact"
                :rules="[rules.required]"
              />
            </v-col>
            <v-col cols="2" class="d-flex align-center">
              <v-btn
                icon="mdi-delete"
                size="small"
                variant="text"
                color="error"
                @click="removeItem(index)"
                v-if="form.items.length > 1"
              />
            </v-col>
          </v-row>
        </v-card-text>
      </v-card>

      <v-btn variant="outlined" block class="mb-4" @click="addItem">
        <v-icon>mdi-plus</v-icon>
        添加商品
      </v-btn>

      <v-btn type="submit" color="primary" size="large" block :loading="loading">
        确认出库
      </v-btn>
    </v-form>
  </v-container>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { inventoryOut, getWarehouseList } from '@/api/inventory'
import { getProductList } from '@/api/product'
import type { WarehouseVO } from '@/types/inventory'
import type { ProductVO, SkuVO } from '@/types/product'
import { showToast } from '@/utils/toast'

const router = useRouter()

const formRef = ref()
const loading = ref(false)
const warehouses = ref<WarehouseVO[]>([])
const skuOptions = ref<{ id: number; label: string }[]>([])

const form = reactive({
  warehouseId: null as number | null,
  source: 'ORDER' as 'ORDER' | 'OTHER',
  reason: '',
  items: [{ skuId: null as number | null, quantity: 1 }]
})

const rules = {
  required: (v: any) => !!v || '必填'
}

function addItem() {
  form.items.push({ skuId: null, quantity: 1 })
}

function removeItem(index: number) {
  form.items.splice(index, 1)
}

async function handleSubmit() {
  const { valid } = await formRef.value.validate()
  if (!valid) return

  loading.value = true
  try {
    await inventoryOut({
      warehouseId: form.warehouseId!,
      source: form.source,
      reason: form.reason,
      items: form.items.map(item => ({
        skuId: item.skuId!,
        quantity: item.quantity
      }))
    })
    showToast('出库成功', 'success')
    router.push('/inventory')
  } catch (error: any) {
    showToast(error.response?.data?.message || '出库失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const [warehouseRes, productRes] = await Promise.all([
      getWarehouseList(),
      getProductList({ current: 1, size: 100 })
    ])
    warehouses.value = warehouseRes.data

    const skus: { id: number; label: string }[] = []
    productRes.data.records.forEach((product: ProductVO) => {
      product.skus?.forEach((sku: SkuVO) => {
        skus.push({
          id: sku.id,
          label: `${product.name} - ${sku.colorName} - ${sku.sizeName} (${sku.skuCode})`
        })
      })
    })
    skuOptions.value = skus
  } catch (error) {
    console.error('Failed to load data', error)
  }
})
</script>
