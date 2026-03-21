<template>
  <v-container>
    <div class="d-flex justify-space-between align-center mb-4">
      <div class="text-h6">库存查询</div>
      <div class="d-flex gap-2">
        <v-btn size="small" color="primary" to="/inventory/in">入库</v-btn>
        <v-btn size="small" color="secondary" to="/inventory/out">出库</v-btn>
      </div>
    </div>

    <v-text-field
      v-model="search"
      prepend-inner-icon="mdi-magnify"
      label="搜索SKU/商品名称"
      density="compact"
      hide-details
      class="mb-4"
    />

    <v-tabs v-model="warehouseFilter" color="primary" density="compact" class="mb-4">
      <v-tab :value="null">全部仓库</v-tab>
      <v-tab v-for="w in warehouses" :key="w.id" :value="w.id">
        {{ w.name }}
      </v-tab>
    </v-tabs>

    <v-switch
      v-model="alertOnly"
      label="仅显示预警"
      color="error"
      density="compact"
      hide-details
      class="mb-2"
    />

    <v-list lines="two">
      <v-list-item
        v-for="inv in inventory"
        :key="inv.id"
        class="mb-2"
      >
        <template #prepend>
          <v-avatar :color="inv.availableQty <= inv.alertThreshold ? 'error' : 'primary'" variant="tonal">
            <v-icon>mdi-warehouse</v-icon>
          </v-avatar>
        </template>

        <v-list-item-title>{{ inv.productName }} ({{ inv.skuCode }})</v-list-item-title>
        <v-list-item-subtitle>
          {{ inv.colorName }} | {{ inv.sizeName }} | {{ inv.warehouseName }}
        </v-list-item-subtitle>

        <template #append>
          <div class="text-right">
            <div class="d-flex gap-2">
              <v-chip size="small" variant="tonal" color="primary">
                库存: {{ inv.quantity }}
              </v-chip>
              <v-chip size="small" variant="tonal" :color="inv.reservedQty > 0 ? 'warning' : 'success'">
                预留: {{ inv.reservedQty }}
              </v-chip>
              <v-chip size="small" variant="tonal" :color="inv.availableQty <= inv.alertThreshold ? 'error' : 'success'">
                可用: {{ inv.availableQty }}
              </v-chip>
            </div>
          </div>
        </template>
      </v-list-item>
    </v-list>

    <v-skeleton-loader v-if="loading" type="list-item-two-line" />

    <v-empty-state
      v-if="!loading && inventory.length === 0"
      icon="mdi-warehouse"
      title="暂无库存数据"
    />
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getInventoryList, getWarehouseList, getInventoryAlerts } from '@/api/inventory'
import type { InventoryVO, WarehouseVO, InventoryPageDTO } from '@/types/inventory'
import { showToast } from '@/utils/toast'

const search = ref('')
const warehouseFilter = ref<number | null>(null)
const alertOnly = ref(false)
const loading = ref(false)
const inventory = ref<InventoryVO[]>([])
const warehouses = ref<WarehouseVO[]>([])

async function fetchInventory() {
  loading.value = true
  try {
    const params: InventoryPageDTO = {
      current: 1,
      size: 100
    }
    if (warehouseFilter.value) {
      params.warehouseId = warehouseFilter.value
    }
    if (alertOnly.value) {
      const res = await getInventoryAlerts()
      inventory.value = res.data
    } else {
      const res = await getInventoryList(params)
      inventory.value = res.data.records
    }
  } catch (error: any) {
    showToast(error.message || '获取库存列表失败', 'error')
  } finally {
    loading.value = false
  }
}

watch([warehouseFilter, alertOnly], () => {
  fetchInventory()
})

onMounted(async () => {
  try {
    const res = await getWarehouseList()
    warehouses.value = res.data
    fetchInventory()
  } catch (error) {
    console.error('Failed to load warehouses', error)
  }
})
</script>
