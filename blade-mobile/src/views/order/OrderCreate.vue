<template>
  <v-container>
    <div class="text-h6 mb-4">创建订单</div>

    <v-form ref="formRef" @submit.prevent="handleSubmit">
      <v-text-field
        v-model="form.customerName"
        label="客户名称"
        :rules="[rules.required]"
        class="mb-2"
      />

      <v-text-field
        v-model="form.customerPhone"
        label="联系电话"
        :rules="[rules.required]"
        class="mb-2"
      />

      <v-textarea
        v-model="form.customerAddress"
        label="收货地址"
        :rules="[rules.required]"
        rows="2"
        class="mb-2"
      />

      <!-- 单档口/默认锁定：只读展示自动带出；多档口：显式选择 -->
      <v-text-field
        v-if="outletLocked"
        label="档口"
        :model-value="lockedOutletLabel"
        readonly
        class="mb-2"
        hint="已绑定档口，自动带出"
        persistent-hint
      />
      <v-select
        v-else
        v-model="selectedOutletId"
        :items="outletItems"
        item-title="outletName"
        item-value="id"
        label="档口"
        :loading="outletsLoading"
        :disabled="outletsLoading"
        :error-messages="outletError"
        class="mb-2"
        @update:model-value="outletError = ''"
      />
      <div
        v-if="outletError"
        role="alert"
        aria-live="polite"
        class="text-error text-body-2 mb-2"
      >
        {{ outletError }}
      </div>
      <v-btn
        v-if="outletError && !outletsLoading"
        variant="outlined"
        block
        min-height="44"
        class="mb-4"
        @click="loadOutletOptions"
      >
        重新加载档口
      </v-btn>

      <v-select
        v-model="form.warehouseId"
        :items="warehouses"
        item-title="name"
        item-value="id"
        label="发货仓库"
        :rules="[rules.required]"
        class="mb-2"
      />

      <v-textarea
        v-model="form.remark"
        label="备注"
        rows="2"
        class="mb-4"
      />

      <div class="text-subtitle-1 mb-2">订单明细</div>

      <v-card
        v-for="(item, index) in form.items"
        :key="index"
        class="mb-2"
        variant="outlined"
      >
        <v-card-text>
          <v-row dense>
            <v-col cols="8">
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
            <v-col cols="4">
              <v-text-field
                v-model.number="item.quantity"
                label="数量"
                type="number"
                min="1"
                density="compact"
                :rules="[rules.required, rules.min]"
              />
            </v-col>
          </v-row>
          <v-btn
            icon="mdi-delete"
            size="small"
            variant="text"
            color="error"
            @click="removeItem(index)"
            v-if="form.items.length > 1"
          />
        </v-card-text>
      </v-card>

      <v-btn
        variant="outlined"
        block
        class="mb-4"
        @click="addItem"
      >
        <v-icon>mdi-plus</v-icon>
        添加商品
      </v-btn>

      <v-btn
        type="submit"
        color="primary"
        size="large"
        block
        :loading="loading"
        :disabled="loading || outletsLoading || !outletOptions"
      >
        提交订单
      </v-btn>
    </v-form>
  </v-container>
</template>

<script setup lang="ts">
import { ref, reactive, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { createOrder } from '@/api/order'
import { getWarehouseList } from '@/api/inventory'
import { getProductList } from '@/api/product'
import { getOutletOptions } from '@/api/outlet'
import type { WarehouseVO } from '@/types/inventory'
import type { ProductVO, SkuVO } from '@/types/product'
import type { OutletOptionsVO } from '@/types/outlet'
import { showToast } from '@/utils/toast'

const router = useRouter()

const formRef = ref()
const loading = ref(false)
const warehouses = ref<WarehouseVO[]>([])
const skuOptions = ref<{ id: number; label: string }[]>([])

const outletOptions = ref<OutletOptionsVO | null>(null)
const selectedOutletId = ref<number | null>(null)
const outletsLoading = ref(false)
const outletError = ref('')

const outletItems = computed(() => outletOptions.value?.items ?? [])
const outletLocked = computed(() => outletOptions.value?.locked === true)
const lockedOutletLabel = computed(() => {
  const first = outletItems.value[0]
  return first ? (first.outletName || first.outletCode) : ''
})

const form = reactive({
  customerName: '',
  customerPhone: '',
  customerAddress: '',
  warehouseId: null as number | null,
  remark: '',
  items: [{ skuId: null as number | null, quantity: 1 }]
})

const rules = {
  required: (v: any) => !!v || '必填',
  min: (v: number) => v >= 1 || '最小为1'
}

function addItem() {
  form.items.push({ skuId: null, quantity: 1 })
}

function removeItem(index: number) {
  form.items.splice(index, 1)
}

async function loadOutletOptions() {
  outletsLoading.value = true
  outletError.value = ''
  try {
    const response = await getOutletOptions()
    const data = response.data
    outletOptions.value = data
    if (data.locked && data.items.length === 1) {
      selectedOutletId.value = data.items[0].id
    } else if (
      data.defaultOutletId != null &&
      data.items.some(outlet => outlet.id === data.defaultOutletId)
    ) {
      selectedOutletId.value = data.defaultOutletId
    } else {
      selectedOutletId.value = null
    }
  } catch (error) {
    outletOptions.value = null
    selectedOutletId.value = null
    outletError.value = '档口加载失败，请重试后再提交'
  } finally {
    outletsLoading.value = false
  }
}

async function loadWarehouseAndProducts() {
  try {
    const [warehouseRes, productRes] = await Promise.all([
      getWarehouseList(),
      getProductList({ current: 1, size: 100 })
    ])
    warehouses.value = warehouseRes.data

    // Flatten SKUs from all products
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
}

async function handleSubmit() {
  const { valid } = await formRef.value.validate()
  if (!valid) return

  // 档口必须已加载且已选定，不能静默依赖后端默认
  if (outletsLoading.value) {
    outletError.value = '档口加载中，请稍候'
    return
  }
  if (!outletOptions.value) {
    outletError.value = '档口未加载，请先重新加载档口'
    return
  }
  if (selectedOutletId.value == null) {
    outletError.value = '请选择档口'
    return
  }
  outletError.value = ''
  const outletId: number = selectedOutletId.value

  loading.value = true
  try {
    const data = {
      customerName: form.customerName,
      customerPhone: form.customerPhone,
      customerAddress: form.customerAddress,
      sourceOutletId: outletId,
      warehouseId: form.warehouseId!,
      remark: form.remark,
      items: form.items.map(item => ({
        skuId: item.skuId!,
        quantity: item.quantity
      }))
    }
    await createOrder(data)
    showToast('订单创建成功', 'success')
    router.push('/orders')
  } catch (error: any) {
    showToast(error.response?.data?.message || '创建失败', 'error')
  } finally {
    loading.value = false
  }
}

onMounted(() => {
  loadOutletOptions()
  loadWarehouseAndProducts()
})
</script>
