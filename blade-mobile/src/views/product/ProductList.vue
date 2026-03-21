<template>
  <v-container>
    <div class="text-h6 mb-4">商品管理</div>

    <v-text-field
      v-model="search"
      prepend-inner-icon="mdi-magnify"
      label="搜索商品名称"
      density="compact"
      hide-details
      class="mb-4"
    />

    <v-list lines="two">
      <v-list-item
        v-for="product in products"
        :key="product.id"
        class="mb-2"
      >
        <template #prepend>
          <v-avatar color="primary" variant="tonal">
            <v-icon>mdi-tshirt-crew</v-icon>
          </v-avatar>
        </template>

        <v-list-item-title>{{ product.name }}</v-list-item-title>
        <v-list-item-subtitle>
          {{ product.category }} | {{ product.skus?.length || 0 }} SKU
        </v-list-item-subtitle>

        <template #append>
          <div class="text-right">
            <div class="text-primary font-weight-bold">¥{{ product.price }}</div>
          </div>
        </template>
      </v-list-item>
    </v-list>

    <v-skeleton-loader v-if="loading" type="list-item-two-line" />

    <v-empty-state
      v-if="!loading && products.length === 0"
      icon="mdi-tshirt-crew-outline"
      title="暂无商品"
      text="请联系管理员添加商品"
    />
  </v-container>
</template>

<script setup lang="ts">
import { ref, onMounted, watch } from 'vue'
import { getProductList } from '@/api/product'
import type { ProductVO } from '@/types/product'
import { showToast } from '@/utils/toast'

const search = ref('')
const loading = ref(false)
const products = ref<ProductVO[]>([])

async function fetchProducts() {
  loading.value = true
  try {
    const res = await getProductList({
      current: 1,
      size: 50,
      name: search.value || undefined
    })
    products.value = res.data.records
  } catch (error: any) {
    showToast(error.message || '获取商品列表失败', 'error')
  } finally {
    loading.value = false
  }
}

watch(search, () => {
  fetchProducts()
})

onMounted(() => {
  fetchProducts()
})
</script>
