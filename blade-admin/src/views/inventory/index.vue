<template>
  <div class="page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>库存列表</span>
          <div class="header-actions">
            <el-button type="primary">入库</el-button>
            <el-button type="success">出库</el-button>
            <el-button type="warning">调整</el-button>
          </div>
        </div>
      </template>

      <el-table :data="tableData" stripe style="width: 100%">
        <el-table-column prop="skuCode" label="SKU编码" width="120" />
        <el-table-column prop="productName" label="商品名称" />
        <el-table-column prop="warehouse" label="仓库" width="100" />
        <el-table-column prop="quantity" label="库存数量" width="100" />
        <el-table-column prop="reserved" label="预占数量" width="100" />
        <el-table-column prop="available" label="可用数量" width="100" />
        <el-table-column label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.quantity < row.alertThreshold ? 'danger' : 'success'">
              {{ row.quantity < row.alertThreshold ? '预警' : '正常' }}
            </el-tag>
          </template>
        </el-table-column>
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'

const tableData = ref([
  { skuCode: 'SKU001', productName: '蓝色T恤 M码', warehouse: '主仓库', quantity: 100, reserved: 10, available: 90, alertThreshold: 20 },
  { skuCode: 'SKU002', productName: '红色连衣裙 L码', warehouse: '主仓库', quantity: 15, reserved: 5, available: 10, alertThreshold: 20 },
])
</script>

<style scoped>
.page-container {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-actions {
  display: flex;
  gap: 8px;
}
</style>
