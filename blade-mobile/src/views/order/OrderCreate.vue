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
      >
        提交订单
      </v-btn>
    </v-form>
  </v-container>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { createOrder } from '@/api/order'
import { getWarehouseList } from '@/api/inventory'
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

async function handleSubmit() {
  const { valid } = await formRef.value.validate()
  if (!valid) return

  loading.value = true
  try {
    const data = {
      customerName: form.customerName,
      customerPhone: form.customerPhone,
      customerAddress: form.customerAddress,
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

onMounted(async () => {
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
})
</script>
